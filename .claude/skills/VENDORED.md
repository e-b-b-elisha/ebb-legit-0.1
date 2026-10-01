# Vendored skills — provenance

These skill folders are copied verbatim from upstream repositories. Do not hand-edit
them; changes are lost on the next sync. To update, run
`.claude/scripts/update-design-skills.sh`, which re-pulls each upstream and rewrites
the folders below.

| Skill folder | Upstream | Path in upstream | Pinned commit | License |
| --- | --- | --- | --- | --- |
| `emil-design-eng/` | [emilkowalski/skills](https://github.com/emilkowalski/skills) | `skills/emil-design-eng` | `d16ebe60d09a5ba2afcb7054ede9d0a10c9f6128` | see upstream `LICENSE` |
| `animate/` | [emilkowalski/skills](https://github.com/emilkowalski/skills) | `skills/animate` | `d16ebe60d09a5ba2afcb7054ede9d0a10c9f6128` | see upstream `LICENSE` |
| `review-animations/` | [emilkowalski/skills](https://github.com/emilkowalski/skills) | `skills/review-animations` | `d16ebe60d09a5ba2afcb7054ede9d0a10c9f6128` | see upstream `LICENSE` |
| `impeccable/` | [pbakaus/impeccable](https://github.com/pbakaus/impeccable) | `.claude/skills/impeccable` | `4adabaf2c2bd3148f162d35aaa6acd7025343649` | Apache-2.0 |
| `design-taste-frontend/` | [leonxlnx/taste-skill](https://github.com/leonxlnx/taste-skill) | `skills/taste-skill` | `ce26fc25c0e5e8cab638f883de62d9a86ee5e45b` | see upstream `LICENSE` |

Vendored 2026-10-01. Impeccable engine version at vendor time: `0.1.10`.

## Things to know about the upstream code

- **`impeccable/scripts/impeccable`** is a shell launcher. On first run it downloads a
  self-contained engine binary for the host platform from
  `https://github.com/pbakaus/impeccable/releases` (tag `engine-v<VERSION>`), verifies it
  against a published `.sha256` sidecar, and caches it outside the repo. Nothing is
  committed here except the launcher, its `command-metadata.json`, a bundled font index,
  and the browser-side JS it injects for live DOM editing. Set `IMPECCABLE_BIN` to point
  at a pre-installed engine if you would rather not have it fetch anything.
- **`impeccable/scripts/live-browser.js`** (~550 KB) is a prebuilt bundle. It is upstream's
  artifact, not readable source.
- `emil-design-eng` deliberately answers only with a one-line greeting when invoked with
  no question. That is upstream behaviour, not a broken install.
- One upstream link in Impeccable is broken at the vendored commit:
  `reference/degraded/asset-producer.md` links to `../reference/component-review.md`,
  which resolves outside the reference tree (it should be `../component-review.md`). Left
  as-is rather than patched, so the folder stays a clean copy. It only affects Impeccable's
  degraded mode.
