package com.ebb.jarvis.core.ai

import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlock
import com.anthropic.models.messages.WebSearchTool20260209
import com.ebb.jarvis.core.ai.tools.DeviceTools
import com.ebb.jarvis.core.data.SecureStore
import com.ebb.jarvis.core.system.SystemMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed interface BrainEvent {
    /** A tool landed; short line for the HUD ticker. */
    data class Action(val note: String) : BrainEvent

    /** The model's final spoken/printed answer. */
    data class Reply(val text: String) : BrainEvent

    /** Something went wrong in a way the user needs stated plainly. */
    data class Failure(val message: String) : BrainEvent
}

/**
 * The reasoning layer: one Messages API conversation plus the manual tool loop that
 * lets the model actually touch the phone.
 *
 * Deliberately non-streaming. Replies here are two or three spoken sentences, so a
 * single round trip is simpler and less fragile than reassembling a stream across
 * tool-use turns; the HUD types the finished text out to keep the feel of streaming.
 */
class JarvisBrain(
    private val store: SecureStore,
    private val tools: DeviceTools,
    private val system: SystemMonitor,
) {

    private val history = mutableListOf<MessageParam>()

    private var client: AnthropicClient? = null
    private var clientForKey: String? = null

    fun resetConversation() {
        history.clear()
    }

    val hasKey: Boolean get() = store.hasApiKey

    fun ask(userText: String): Flow<BrainEvent> = flow {
        val key = store.apiKey
        if (key.isBlank()) {
            emit(
                BrainEvent.Failure(
                    "No API key configured. Open settings and paste an Anthropic key.",
                ),
            )
            return@flow
        }

        val anthropic = clientFor(key)
        trimHistory()
        history += MessageParam.builder()
            .role(MessageParam.Role.USER)
            .contentOfBlockParams(listOf(ContentBlockParam.ofText(textBlock(userText))))
            .build()

        try {
            var turns = 0
            while (turns < MAX_TURNS) {
                turns++
                val response: Message = anthropic.messages().create(buildRequest())
                history += response.toParam()

                when (response.stopReason().orElse(null)) {
                    StopReason.TOOL_USE -> {
                        val calls = response.content().mapNotNull { it.toolUse().orElse(null) }
                        if (calls.isEmpty()) {
                            emit(BrainEvent.Reply(textOf(response)))
                            return@flow
                        }
                        // Every tool_use block must come back in ONE user message,
                        // or the model learns to stop batching calls.
                        val results = mutableListOf<ContentBlockParam>()
                        for (call in calls) {
                            val outcome = runTool(call)
                            outcome.uiNote?.let { emit(BrainEvent.Action(it)) }
                            results += ContentBlockParam.ofToolResult(
                                ToolResultBlockParam.builder()
                                    .toolUseId(call.id())
                                    .content(outcome.text)
                                    .isError(outcome.isError)
                                    .build(),
                            )
                        }
                        history += MessageParam.builder()
                            .role(MessageParam.Role.USER)
                            .contentOfBlockParams(results)
                            .build()
                    }

                    // Server-side tools (web search) pause the turn; resend to continue.
                    StopReason.PAUSE_TURN -> Unit

                    StopReason.REFUSAL -> {
                        emit(
                            BrainEvent.Failure(
                                "I have to decline that one. Try rephrasing it.",
                            ),
                        )
                        return@flow
                    }

                    StopReason.MAX_TOKENS -> {
                        val partial = textOf(response)
                        emit(
                            if (partial.isBlank()) {
                                BrainEvent.Failure("That answer ran past its length limit.")
                            } else {
                                BrainEvent.Reply(partial)
                            },
                        )
                        return@flow
                    }

                    else -> {
                        emit(BrainEvent.Reply(textOf(response)))
                        return@flow
                    }
                }
            }
            emit(BrainEvent.Failure("Gave up after $MAX_TURNS tool rounds."))
        } catch (e: UnauthorizedException) {
            Log.w(TAG, "Auth rejected", e)
            history.clear()
            emit(BrainEvent.Failure("The API key was rejected. Check it in settings."))
        } catch (e: RateLimitException) {
            Log.w(TAG, "Rate limited", e)
            emit(BrainEvent.Failure("Rate limited. Give it a moment."))
        } catch (e: AnthropicIoException) {
            Log.w(TAG, "Network failure", e)
            emit(BrainEvent.Failure("Cannot reach the API. Check the connection."))
        } catch (e: AnthropicServiceException) {
            Log.w(TAG, "Service error ${e.statusCode()}", e)
            emit(BrainEvent.Failure("API error ${e.statusCode()}."))
        } catch (c: CancellationException) {
            // A new question arriving mid-answer cancels this one. That is not a fault,
            // and emitting into a cancelled flow would throw on top of it.
            throw c
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected brain failure", t)
            emit(BrainEvent.Failure(t.message ?: "Something went wrong."))
        }
    }.flowOn(Dispatchers.IO)

    // --- request construction ------------------------------------------------

    private fun buildRequest(): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(store.model)
            .maxTokens(MAX_TOKENS)
            .system(systemPrompt())
            .thinking(ThinkingConfigAdaptive.builder().build())
            .outputConfig(OutputConfig.builder().effort(effort()).build())
            .messages(history.toList())

        tools.definitions().forEach { builder.addTool(it) }

        if (store.webSearch) {
            builder.addTool(
                WebSearchTool20260209.builder().maxUses(MAX_WEB_SEARCHES).build(),
            )
        }
        return builder.build()
    }

    private fun effort(): OutputConfig.Effort = when (store.effort.lowercase()) {
        "low" -> OutputConfig.Effort.LOW
        "medium" -> OutputConfig.Effort.MEDIUM
        "high" -> OutputConfig.Effort.HIGH
        "xhigh" -> OutputConfig.Effort.XHIGH
        "max" -> OutputConfig.Effort.MAX
        else -> OutputConfig.Effort.LOW
    }

    private fun systemPrompt(): String {
        val status = system.snapshot()
        val now = LocalDateTime.now().format(STAMP)
        return """
            You are JARVIS, the assistant built into this Android phone's home screen.
            You address the user as ${store.operatorName}.

            Manner: concise, composed, dryly witty when it fits. No filler, no
            preamble, no restating the request. Two or three sentences at most unless
            the user asks for detail, because your replies are read aloud.

            You control the phone through tools. Act first and report briefly rather
            than asking permission for reversible things: opening an app, setting a
            timer, checking status. Tools that commit something on the user's behalf
            (compose_message, dial_number, create_calendar_event) only open a prefilled
            screen, so say that it is waiting for a tap. If a tool fails, say what
            failed in one line; do not retry blindly.

            Use web search only when the answer depends on current facts you cannot
            know. Never claim to have done something a tool did not confirm.

            Device context for this turn:
            - Local time: $now
            - Battery: ${status.batteryPercent}%${if (status.charging) ", charging" else ""}
            - Network: ${status.network}
            Resolve relative times ("in 20 minutes", "tomorrow at 7") against the local
            time above before calling a tool.
        """.trimIndent()
    }

    private suspend fun runTool(call: ToolUseBlock) =
        tools.execute(call.name(), inputOf(call))

    @Suppress("UNCHECKED_CAST")
    private fun inputOf(call: ToolUseBlock): Map<String, Any?> = runCatching {
        call._input().convert(Map::class.java) as? Map<String, Any?>
    }.getOrNull().orEmpty()

    private fun textOf(message: Message): String = message.content()
        .mapNotNull { it.text().orElse(null)?.text() }
        .joinToString(" ")
        .trim()
        .ifBlank { "Done." }

    private fun textBlock(text: String) =
        com.anthropic.models.messages.TextBlockParam.builder().text(text).build()

    /**
     * Keeps the conversation bounded; the launcher is not a chat archive.
     *
     * Trimming has to land on a plain user turn. Cutting mid-exchange would leave a
     * tool_result whose tool_use has been dropped, which the API rejects outright, so
     * the cut slides forward to the next clean boundary and clears everything if no
     * such boundary is left.
     */
    private fun trimHistory() {
        if (history.size <= MAX_HISTORY) return
        var cut = history.size - MAX_HISTORY
        while (cut < history.size && !isPlainUserTurn(history[cut])) cut++
        if (cut >= history.size) {
            history.clear()
        } else {
            repeat(cut) { history.removeAt(0) }
        }
    }

    /** A user turn that opens an exchange, rather than one carrying tool results. */
    private fun isPlainUserTurn(message: MessageParam): Boolean {
        if (message.role() != MessageParam.Role.USER) return false
        val blocks = message.content().blockParams().orElse(null) ?: return true
        return blocks.none { it.isToolResult() }
    }

    private fun clientFor(key: String): AnthropicClient {
        val existing = client
        if (existing != null && clientForKey == key) return existing
        val built = AnthropicOkHttpClient.builder()
            .apiKey(key)
            .timeout(Duration.ofSeconds(90))
            .maxRetries(2)
            .build()
        client = built
        clientForKey = key
        return built
    }

    private companion object {
        const val TAG = "JarvisBrain"
        const val MAX_TOKENS = 2_048L
        const val MAX_TURNS = 6
        const val MAX_HISTORY = 24
        const val MAX_WEB_SEARCHES = 4L
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm")
    }
}
