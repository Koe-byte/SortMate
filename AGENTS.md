# SortMate agent instructions

## Project

SortMate is a Kotlin/Compose Android app for student households. Room feeds the UI; Supabase provides auth, data and Realtime; FCM delivers push. Start at `docs/00 - SortMate.md`.

## Agent skills

- **Code relationships:** if `graphify-out/graph.json` exists, query it before reading broad source ranges (`graphify query "<question>" --budget 1500`, `graphify path`, `graphify explain`). See `docs/agents/graph-navigation.md` for rebuilds and coverage.
- **Tickets:** use the local Markdown tracker (`docs/agents/issue-tracker.md`); for triage use `docs/agents/triage-labels.md`.
- **Domain decisions:** use the single glossary and ADRs described in `docs/agents/domain.md`.

## Git conventions

This repository follows Conventional Commits and Conventional Branch.

- Never commit directly to `main`. Create a branch first.
- Branch names must be `<type>/<description>` using `feature/`, `fix/`, `hotfix/`, `release/`, or `chore/` with lowercase kebab-case descriptions, e.g. `feature/receipt-splitting`.
- Commit messages must be `<type>[optional scope]: <description>` with a lowercase imperative description, 72 characters or fewer, and no trailing period.
- Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
- Mark breaking changes with `!` after the type or scope and add a `BREAKING CHANGE:` footer.
- Enable the local validation hook once per clone: `git config core.hooksPath .githooks`.

See `CONTRIBUTING.md` for the full workflow and examples.

## Build and test

- Windows: `.\gradlew.bat test` or `.\gradlew.bat assembleDebug`
- Never commit `local.properties`, `app/google-services.json`, build outputs, signing material, or secrets.
- Room schemas under `app/schemas/` are versioned and must be committed with schema changes.
