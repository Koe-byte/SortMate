# SortMate — Roadmap

Status as of the CS312 final project submission.

Legend: ✅ done · 🟡 partial / demo-grade · ⬜ future work

---

## Milestone 0 — Project foundation ✅

- [x] Review proposal PDF and flowchart, copy both into `docs/reference/`
- [x] Create single-module Android project `SortMate`
- [x] Pin toolchain: Kotlin 2.3.10, AGP 9.2.1, Gradle 9.7.1, JDK 17, compile/target SDK 37, min SDK 26
- [x] Compose Material 3 theme (teal/coral, light + dark), typography, launcher icons
- [x] Optional Firebase build: `google-services` plugin applies only when `app/google-services.json` exists

## Milestone 1 — Domain logic ✅

- [x] `PresenceStatus` (`HOME`, `CAMPUS`, `AWAY`)
- [x] `SplitType` + exact-cent `SplitCalculator`
- [x] `QuietHours` overnight-aware window logic
- [x] `ReceiptParser` heuristics + `ReceiptScanResult`
- [x] Currency/date formatters

## Milestone 2 — Local data ✅

- [x] Room entities, converters, DAOs with `Flow` queries
- [x] Database v1 with schema export
- [x] DataStore session + notification toggles
- [x] Delivery-time soft delete and `isSynced` outbox flags

## Milestone 3 — Cloud + background ✅

- [x] Guarded Firestore adapter (works with or without Firebase)
- [x] Anonymous auth with local fallback
- [x] Household create/join/demo-seed, bills, chores repositories
- [x] Realtime snapshot listeners into Room
- [x] `SyncWorker` retry loop, boot restoration
- [x] FCM token registration and messaging service
- [x] Notification channels and daily bill/chore/quiet-hours workers
- [x] Geofence manager, broadcast receiver, fused location provider

## Milestone 4 — UI ✅

- [x] Onboarding: create / join / demo, validation, loading and error states
- [x] Root session gate (DataStore → onboarding or main app)
- [x] Main scaffold with bottom navigation + contextual "Scan bill" FAB
- [x] Home dashboard: greeting, presence switcher, stats, roommates, due bills/chores
- [x] Bills: outstanding summary, open/settled lists, settle/delete
- [x] Bill editor: CameraX scanner, OCR prefill, paid-by/due chips, split preview
- [x] Chores: add dialog, assignee/due chips, toggle, delete
- [x] Household: join code copy, rename, members, geofence controls, simulated boundary, quiet-hours editor, notification toggles, reset
- [x] Notification permission request on Android 13+
- [x] Runtime location permission handling

## Milestone 5 — Verification & documentation ✅

- [x] JVM tests for `ReceiptParser`, `SplitCalculator`, `QuietHours`
- [x] `:app:assembleDebug` succeeds
- [x] `:app:testDebugUnitTest` passes
- [x] Runtime smoke test on emulator (see `docs/DEMO_SCRIPT.md` checks)
- [x] README, architecture, roadmap, demo script, Firestore rules

---

## Known gaps and risks 🟡

- Equal split only; `SplitType` is stored but no weighted UI.
- Receipt photos are local-only (no Firebase Storage upload).
- Firestore rules are demo-grade: any signed-in anonymous user can read households if they know/guess a code. Production needs custom claims or membership documents.
- Location background permission must be granted manually on Android 10+; geofencing while the app is closed is not exercised in a typical indoor demo.
- FCM remote alerts require a trusted sender (console or Cloud Function). There is no admin UI to compose pushes.
- No instrumented/UI tests yet; the demo script is the manual regression suite.
- Firestore conflicts resolve last-write-wins; no offline conflict prompt.

## Next steps ⬜

1. **Storage:** upload receipt JPEGs to Firebase Storage, keep a download URL, show thumbnails on other devices.
2. **Split types:** weighted and per-item splitting; receipt line-item assignment UI.
3. **Recurring bills:** monthly rent/internet templates with generated due dates.
4. **Chore rotation:** weekly rotation rules and fair-share stats.
5. **Notification actions:** "Mark settled" / "Mark done" directly from the notification.
6. **Widget / quick settings tile:** today's chores and balance at a glance.
7. **Testing:** Room DAO instrumentation tests, Compose UI tests for onboarding and bill creation, fake Firestore for repository tests.
8. **Security:** real accounts (email link or Google), household membership checks in rules, App Check.
9. **Polish:** dynamic color option, accessibility audit, localization (English/Filipino), tablet layout.
10. **Analytics:** opt-in household activity summary for the documentation chapter.

---

## Submission checklist

- [ ] `local.properties` excluded from the repository (already in `.gitignore`)
- [ ] `google-services.json` **not** committed if it contains project secrets
- [ ] Demo household loaded on the presentation phone
- [ ] Notifications and camera permissions pre-granted on the presentation phone
- [ ] Backup APK and screen recording prepared
- [ ] Proposal PDF and flowchart included under `docs/reference/`
