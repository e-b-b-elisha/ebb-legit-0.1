# JARVIS — notes for Claude

A sideloaded Android home-screen replacement (Kotlin + Compose) with the Anthropic
Messages API wired in as the assistant. See `README.md` for what it does and
`README.md#architecture` for the package layout.

## Build

```sh
./gradlew assembleDebug      # needs Android SDK 35
./gradlew compileDebugKotlin # faster check when you only changed Kotlin
```

CI builds the debug APK on every push. Only the debug variant is exercised — the
release variant's R8 rules for the Anthropic SDK are written but untested, and the
SDK deserializes reflectively, so do not claim a release build works without a
device check.

Nothing in this repo has been run on hardware. When reporting on a change, say it
compiles — not that it works.

## Model IDs

The settings sheet offers Opus 5 (`claude-opus-5`), Sonnet 5 (`claude-sonnet-5-5`)
and Haiku 4.5 (`claude-haiku-4-5-20251001`). Keep those current; do not invent IDs.

## Hard rules in this codebase

- **No new dangerous permissions.** Every device tool reaches the system through a
  public intent or a permission-free manager call. The app deliberately does not hold
  `SEND_SMS`, `CALL_PHONE`, or calendar write access. A tool that would need one is
  the wrong design — open the system composer prefilled instead and let the user tap.
- **Irreversible actions open prefilled and wait.** Messages are not sent, numbers
  are not dialled.
- **The API key stays in `EncryptedSharedPreferences`.** Never log it, never put it in
  a crash report, never send it anywhere but `api.anthropic.com`.
- **The brain is non-streaming on purpose.** Replies are two or three spoken
  sentences; one round trip per turn is simpler than reassembling a stream across
  tool-use turns. Do not "upgrade" it to streaming without being asked.

## Design work

`.claude/` carries a design toolchain: Emil Kowalski's design-engineering and
animation skills, Impeccable, and the taste skill, plus Figma and Playwright MCP
servers. **Read [`.claude/README.md`](.claude/README.md) before any UI or visual
work** — it says which skill handles which kind of request and how the browser loop
works.

Short version:

- A visual brief with no direction yet → `design-taste-frontend`, then
  `impeccable shape`.
- Existing UI that feels wrong → `impeccable audit` or `impeccable critique`.
- One axis off → `impeccable typeset` / `layout` / `colorize`.
- Anything that moves → `animate` to write it, `review-animations` to check it,
  `emil-design-eng` for whether it should move at all.

Those skills are web-first. The judgment (typography, spacing, colour, motion) carries
over to Compose; Playwright and `design-taste-frontend` do not. For Compose work, take
the principles and ignore the browser tooling.

Everything under `.claude/skills/` is vendored third-party content — do not hand-edit
it. Re-sync with `.claude/scripts/update-design-skills.sh`; provenance is in
`.claude/skills/VENDORED.md`.
