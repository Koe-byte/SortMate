# SortMate agent instructions

## Project

SortMate is a native Android app (Kotlin, Jetpack Compose, Room, Firebase) for student households. See `README.md` and `docs/ARCHITECTURE.md`.

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
