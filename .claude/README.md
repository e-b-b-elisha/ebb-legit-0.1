# Design toolchain

This directory turns Claude Code in this repo into something that can take a plain-English
brief and come back with a built, screenshotted, self-reviewed interface — instead of the
default centred-hero-with-a-gradient look that every model reaches for.

Three things make that work: **taste rules** (skills), **a design source** (Figma MCP), and
**eyes** (Playwright MCP).

## 1. Skills — the rules

Vendored verbatim from upstream into `skills/`. Provenance and pinned commits are in
[`skills/VENDORED.md`](skills/VENDORED.md); re-sync with
`.claude/scripts/update-design-skills.sh`.

| Skill | What it is for | Invoke |
| --- | --- | --- |
| `emil-design-eng` | Emil Kowalski's design-engineering philosophy — component design, the invisible details, when polish is worth it. The judgment layer. | `/emil-design-eng` or ask a design question |
| `animate` | How to actually write the motion: easing, duration, spring vs. tween, what to animate and what to leave alone. | `/animate` |
| `review-animations` | Audits motion already in the code against a standards file. The counterpart to `animate`. | `/review-animations` |
| `impeccable` | The big one. 23 sub-commands (`typeset`, `colorize`, `layout`, `animate`, `bolder`, `quieter`, `delight`, `audit`, `critique`, `polish`, `harden`, `live`, …) plus an anti-slop craft floor it reads before every UI edit. | `/impeccable shape <target>`, `/impeccable typeset`, … |
| `design-taste-frontend` | Reads the brief and infers a design *direction* before writing code, so the output is not templated. Landing pages, portfolios, redesigns. Explicitly not for dashboards or dense product UI. | `/design-taste-frontend` |

They overlap on purpose and are not meant to all fire at once. Rough division of labour:

- **New marketing surface, no direction yet** → `design-taste-frontend` to pick a direction,
  then `impeccable shape` to build it.
- **Existing UI that feels off** → `impeccable audit` or `impeccable critique`, fix, then
  `impeccable polish`.
- **One specific axis is wrong** → the matching sub-command: `impeccable typeset` for
  typography and spacing, `impeccable layout`, `impeccable colorize`.
- **Anything moving** → `animate` to write it, `review-animations` to check it, and
  `emil-design-eng` when the question is *whether* it should move at all.

## 2. MCP servers — the hands

Configured in [`../.mcp.json`](../.mcp.json). `.claude/settings.suggested.json` sets
`enableAllProjectMcpServers` so they come up without a per-session prompt.

### Figma (`https://mcp.figma.com/mcp`, HTTP)

The hosted server, so it needs no desktop app. One-time auth: start Claude Code and run
`/mcp`, pick `figma`, and complete the browser OAuth flow.

Gives the agent `get_design_context`, `get_screenshot`, `get_metadata`,
`get_variable_defs` and `search_design_system` for reading designs into code, plus
`use_figma`, `create_new_file` and `upload_assets` for pushing code *back* into Figma. Paste
a `figma.com` URL into a prompt and it reads that frame directly.

Figma gates MCP access by plan and seat. The account authenticated from this session is
on a Starter team with a **View** seat, which is enough to read files you can open but not
to use Dev Mode or the write tools (`use_figma`, `create_new_file`). If `get_design_context`
comes back with a permission error, that is why — check with `whoami` and see
[Figma's rate limits and access page](https://developers.figma.com/docs/figma-mcp-server/rate-limits-access/).

**If you want selection-based work** — "build what I have selected in Figma right now" —
swap to the desktop server instead: enable Dev Mode MCP in the Figma desktop app, then
change the `figma` entry in `.mcp.json` to `http://127.0.0.1:3845/mcp`. Only one of the two
needs to be configured.

### Playwright (`@playwright/mcp`, stdio)

Pinned to `0.0.83`. Launches Chromium at 1440×900. One-time browser install:

```sh
npx @playwright/mcp@0.0.83 install-browser chrome-for-testing
```

That is the MCP server's own installer, not `npx playwright install` — this version maps
`--browser=chromium` to a Chrome-for-Testing build it pins itself, and an unrelated
Playwright install on the machine will not satisfy it.

Artifacts land in `.claude/design/screenshots/` (git-ignored). Two conventions worth
knowing, both verified: `--output-dir` governs auto-named screenshots, console logs and
page snapshots, but an explicit `filename` is resolved against the repo root — so ask for
`.claude/design/screenshots/hero-desktop.png`, not a bare `hero-desktop.png`, or it lands
in the working tree.

`file://` URLs are blocked by default. Point it at a dev server over http.

This is what closes the loop. The agent can navigate to the dev server, screenshot desktop
and mobile widths, read the accessibility tree, click through a flow, check the console for
errors, and then fix what it sees — rather than asking you whether it looks right.

## 3. One-time setup

```sh
npx @playwright/mcp@0.0.83 install-browser chrome-for-testing
cp .claude/settings.suggested.json .claude/settings.json   # read it first — see below
# then in Claude Code:  /mcp  ->  figma  ->  authenticate
```

Running as root in a container (a Claude Code cloud session, Docker, CI) additionally
needs `--no-sandbox` added to the `playwright` args in `.mcp.json`, and an
`--executable-path` if the image already ships its own Chromium. Neither is needed on a
normal desktop, which is why they are not in the committed config.

### Read `settings.suggested.json` before copying it

It is not active until you rename it, and it does two things worth an informed decision:

1. **Pre-approves the `figma` and `playwright` MCP tools** so the agent is not stopped at a
   permission prompt mid-build. Playwright can then open any URL, and the Figma write tools
   can create and modify files in your Figma account, without asking each time. Drop
   `mcp__figma` from the allow list if you would rather approve writes case by case.
2. **Installs Impeccable's three hooks** (`SessionStart`, `PostToolUse` on Edit/Write, and
   `Stop`). These run `skills/impeccable/scripts/impeccable hook`, which is the design
   detector that flags slop as the agent writes. That launcher downloads a platform engine
   binary from Impeccable's GitHub releases on first run and verifies it against a published
   checksum — see [`skills/VENDORED.md`](skills/VENDORED.md). If you do not want a
   third-party binary executing after every file write, delete the `hooks` block. The skill
   still works; it just loses the automatic detector.

## 4. A note on this repo

JARVIS is an Android/Compose app, and the skills above are web-first — Impeccable has
`ios.md`/`android.md` references and Emil's upstream ships `mobile-native` and `apple-design`
skills that are not vendored here, but Playwright and `design-taste-frontend` assume a
browser. The typography, spacing, colour and motion judgment transfers to Compose; the
tooling does not. Add `mobile-native` from the same upstream if you want the native set —
it is one line in `.claude/scripts/update-design-skills.sh`.
