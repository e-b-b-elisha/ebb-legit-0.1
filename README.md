# JARVIS — Android launcher

A home-screen replacement with a Stark-style HUD and Claude wired into it as the
assistant. Tap the reactor, say what you want, and it acts on the phone: opens apps,
sets timers and alarms, drafts messages, reads out device state, searches the web when
the answer depends on current facts.

Built as a personal, sideloaded launcher — not a Play Store app.

## What it does

- **Registers as HOME.** Press the home button and this is what you get. The app
  drawer is a swipe up (or the grid button); back never drops you out.
- **Voice or text.** Tap the reactor to talk, or type into the command bar. Replies
  are spoken aloud by default and typed out on the HUD.
- **Acts on the device** through the tools below. Anything irreversible opens
  prefilled and waits for your tap; nothing is sent or dialled on your behalf.
- **Live telemetry** on the HUD: clock, battery and charge state, network transport,
  memory pressure, free storage.

### Tools the model can call

| Tool | What it does |
|---|---|
| `launch_app` | Opens an installed app by fuzzy name |
| `list_apps` | Lists or filters what's installed |
| `device_status` | Battery, network, memory, storage, local time |
| `set_timer` / `set_alarm` | Countdown or wall-clock alarm in the clock app |
| `open_url` | Opens a link in the browser |
| `set_torch` | Camera flash on/off |
| `media_control` | Play/pause, next, previous, stop |
| `create_calendar_event` | Opens the calendar editor prefilled |
| `compose_message` | Opens the SMS composer prefilled — **not sent** |
| `dial_number` | Opens the dialler with the number entered — **not called** |
| `open_settings` | Jumps to a system settings screen |
| `web_search` | Anthropic's server-side web search, when enabled |

Every tool reaches the system through a public intent or a permission-free manager
call, so the app never holds `SEND_SMS`, `CALL_PHONE`, or calendar write access.

## Getting it on your phone

The APK is built by CI on every push — no local Android toolchain needed.

1. Open the **Actions** tab, pick the latest **Build APK** run, and download the
   `jarvis-debug-apk` artifact.
2. Unzip it and install the APK (allow install-from-unknown-sources when asked).
3. Open JARVIS from your current launcher's drawer.
4. Tap the gear, paste an Anthropic API key from
   [console.anthropic.com](https://console.anthropic.com/settings/keys), and save.
5. Tap **SET AS DEFAULT HOME** and pick JARVIS. Android only allows that change from
   its own settings screen.

To build locally instead: `./gradlew assembleDebug` with Android SDK 35 installed.

## Configuration

Settings live behind the gear on the HUD:

- **API key** — held in `EncryptedSharedPreferences`, keystore-backed, excluded from
  backups. Requests go straight from the phone to `api.anthropic.com`; there is no
  intermediary server and no telemetry.
- **Model** — Opus 5 (default), Sonnet 5, or Haiku 4.5.
- **Effort** — `low` by default so spoken replies come back fast; raise it when you
  want the model to think first, at more tokens and more latency.
- **Address me as** — what it calls you. Defaults to "Sir".
- **Speak replies**, **allow web search**, **boot sequence** — toggles.

### A note on cost and on the key

Every request is billed to your Anthropic account at
[standard API rates](https://www.anthropic.com/pricing#api). The key sits on the
device in encrypted storage, which is the right call for a personal launcher, but it
is still a key on a phone: anyone who can unlock the phone and read app storage as
root could recover it. Revoke it from the console if the phone goes missing.

## Architecture

```
ui/
  JarvisActivity      HOME activity; owns the mic permission flow
  JarvisRoot          HUD + drawer + settings overlays + scanlines
  JarvisViewModel     single source of launcher state
  hud/                arc reactor, panels, status header, boot sequence
  drawer/             app grid with filter
  settings/           configuration sheet
core/
  ai/JarvisBrain      Messages API conversation + manual tool loop
  ai/tools/           the device tools above
  apps/               installed-app index (LauncherApps, PackageManager fallback)
  speech/             SpeechRecognizer + TextToSpeech
  system/             battery / network / memory / storage telemetry
  data/SecureStore    encrypted preferences
```

The brain is deliberately **non-streaming**: replies are two or three spoken
sentences, so one round trip per turn is simpler and less fragile than reassembling a
stream across tool-use turns. The HUD types the finished text out to keep the feel.

## Known limits

- `QUERY_ALL_PACKAGES` is declared because a launcher has to enumerate every
  installed activity. That alone makes the build unsuitable for Play Store
  distribution, which is fine — it is meant to be sideloaded.
- No widget host, no wallpaper scrolling, no icon packs, no gesture customisation yet.
- No wake word. Android has no custom-wake-word API; it would need an on-device
  keyword engine and a foreground service, which is a battery decision worth making
  deliberately rather than by default.
- Voice recognition uses the system recogniser, so it needs network on most phones.
- Only the debug variant is exercised by CI; the release variant's R8 rules for the
  Anthropic SDK are written but untested.
