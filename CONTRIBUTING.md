# Contributing to SortMate

This project follows [Conventional Commits](https://www.conventionalcommits.org/) for commit messages and [Conventional Branch](https://conventional-branch.github.io/) for branch names.

## Branching model

`main` is the trunk and should always be releasable. Do not commit directly to `main`; create a short-lived branch and open a pull request.

| Branch | Use for |
| --- | --- |
| `feature/<description>` | New user-facing functionality |
| `fix/<description>` | Non-urgent bug fixes |
| `hotfix/<description>` | Urgent fixes that must ship immediately |
| `release/<version>` | Release preparation, e.g. `release/v1.0.0` |
| `chore/<description>` | Docs, tooling, dependencies, maintenance |

Rules:

- Use lowercase letters, numbers, dots, and hyphens only. No spaces or underscores.
- Keep names short and descriptive.
- Include a ticket or issue number when one exists, e.g. `feature/12-receipt-ocr`.

Typical flow:

```bash
git switch main
git pull
git switch -c feature/bill-splitting
# ... work ...
git add .
git commit -m "feat(bills): split totals across housemates"
git push -u origin feature/bill-splitting
```

Then open a pull request into `main`. Prefer **Squash and merge**, and give the pull request a Conventional Commit title so `main` keeps a clean history.

## Commit message format

```
<type>[optional scope]: <description>
```

- Use the imperative mood: `add`, not `added` or `adds`.
- Keep the description lowercase and omit the trailing period.
- Keep the subject line at 72 characters or fewer.
- A scope is optional but encouraged when it clarifies the area.

### Types

| Type | Use for |
| --- | --- |
| `feat` | A new feature |
| `fix` | A bug fix |
| `docs` | Documentation only |
| `style` | Formatting or whitespace with no behavior change |
| `refactor` | A code change that neither fixes a bug nor adds a feature |
| `perf` | A performance improvement |
| `test` | Adding or fixing tests |
| `build` | Build system or dependency changes |
| `ci` | CI configuration |
| `chore` | Maintenance that does not fit the other types |
| `revert` | Reverting a previous commit |

Suggested scopes: `receipt`, `bills`, `chores`, `presence`, `quiet-hours`, `household`, `onboarding`, `db`, `sync`, `ui`, `deps`, `docs`.

### Examples

```
feat(receipt): parse totals from scanned receipts
fix(chores): keep due date when toggling completion
docs: document the demo household flow
chore(deps): bump Room to 2.7.0
refactor(db): move DAO queries into repositories
```

### Breaking changes

Add `!` after the type or scope, and describe the change in a `BREAKING CHANGE:` footer:

```
feat(db)!: replace household ID with join code

BREAKING CHANGE: existing local databases must be migrated.
```

## Local checks

```bash
./gradlew test
```

On Windows PowerShell, use `.\gradlew.bat test`.

## Commit message hook

The repository ships a hook that rejects non-conventional commit messages. Enable it once per clone:

```bash
git config core.hooksPath .githooks
```

Run the command from the repository root (Git Bash, PowerShell, or any shell).
