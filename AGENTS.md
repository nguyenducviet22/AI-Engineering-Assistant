# AGENTS.md — AI Engineering Assistant

Standing instructions for any coding agent (Codex CLI, Claude Code, etc.)
working in this repository. These apply on top of, not instead of, the
`harness-skills` skills already installed in this project. Read this file
at the start of every session, in addition to whatever `session-state`
already loads.

## Spec is authoritative

The locked spec lives at
`.harness/specs/ai-engineering-assistant/requirements.md` and `design.md`.
Read them before acting. Do not infer or expand scope beyond what they say
— if something is ambiguous, flag it rather than guessing.

## Per-turn logging (applies to every phase, every session, no exceptions)

After you finish responding to **each** prompt I send you — regardless of
which phase we're working on, and without me having to ask again — append a
summary of what you did in that turn to a phase-specific log file:

```
.harness/specs/ai-engineering-assistant/phase-summaries/phase{N}-summary.md
```

Where `{N}` is the current roadmap phase number from `design.md` (1–5).
Determine the current phase from context (what the active spec/plan
references, what I explicitly said, or what `hs-plan`/`hs-build` is
currently scoped to). If the phase is genuinely unclear, ask once, then use
that answer for the rest of the session instead of asking again.

Rules for the log file:

- Create the file with a top-level heading `# Phase {N} Summary` if it
  doesn't exist yet. Create the `phase-summaries/` directory if needed.
- **Always append a new entry — never overwrite or edit prior entries.**
- Each entry is a new subsection (`## <timestamp or turn label>`) containing:
  - What was requested
  - What was actually done (decisions made, files touched, plan/code
    produced)
  - Open questions or items still pending my approval
- Keep entries brief — bullet points, not prose paragraphs.
- If the current phase summary file exceeds roughly 300 lines, **do not
  condense, overwrite, or edit older entries**. Instead, create a new
  numbered continuation file in the same directory and append there:
  `phase{N}-summary-2.md`, `phase{N}-summary-3.md`, and so on. Give each
  continuation file the heading `# Phase {N} Summary (Part X)` and continue
  append-only logging in that newest file.
- If we switch from one phase to another mid-session, start logging to the
  new phase's file — do not keep appending to the old one.

This logging step is not optional and not something I should have to
re-request per prompt. Treat it the same way you'd treat a `hs-verify`
evidence note: part of finishing the turn, not a separate task.

## Working with harness-skills in this project

- Prefer `hs-plan` before `hs-build` for anything nontrivial or risky per
  the roadmap phase currently in progress.
- Use the `hs-scout` subagent for broad/exploratory reading (existing code,
  library docs, schema) instead of loading it into the main context
  yourself.
- Use `hs-reviewer` for independent review before shipping, especially for
  higher-risk phases (2 and 3 per `design.md`).
- `hs-shipper` only reports and drafts — it never commits, pushes, or
  merges. All commit/push/PR actions require my explicit confirmation.

## Secrets

Never read, log, or print `.env`, `application-local.yml`, or any file
matching `*secret*`. These should already be in `privacyBlock.denyList` in
`hs.settings.json` — if they aren't, tell me instead of reading them anyway.
