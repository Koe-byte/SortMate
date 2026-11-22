# SortMate — Roadmap

Status as of the CS312 final project submission.

Legend: ✅ done · 🟡 partial / demo-grade · ⬜ future work

Related notes: [[00 - SortMate]] · [[04 - Evaluation]] · [[06 - Tickets]]

---

## Milestone 0 — Project foundation ✅

- [x] Review proposal PDF and flowchart, copy both into `docs/reference/`
- [x] Create single-module Android project `SortMate`
- [x] Pin toolchain: Kotlin 2.3.10, AGP 9.2.1, Gradle 9.7.1, daemon JDK 25 / Java target 17, compile/target SDK 37, min SDK 26
- [x] Compose Material 3 theme (teal/coral, light + dark), typography, launcher icons
- [x] Optional cloud build: credentials read from gitignored `local.properties`

## Milestone 1 — Domain logic ✅

- [x] `PresenceStatus` (`HOME`, `CAMPUS`, `AWAY`)
- [x] `SplitType` + exact-cent `SplitCalculator`
- [x] `QuietHours` overnight-aware window logic
- [x] `ReceiptParser` heuristics + `ReceiptScanResult`
- [x] `PresenceResolver` for overlapping home/campus geofence events
- [x] Currency/date formatters

## Milestone 2 — Local data ✅

- [x] Room entities, converters, DAOs with `Flow` queries
- [x] Database v2 with campus columns and committed schema JSON
- [x] DataStore session + notification toggles
- [x] Delivery-time soft delete and `isSynced` outbox flags

## Milestone 3 — Cloud + background ✅

- [x] `RemoteDataSource` seam; `SupabaseDataSource` is the only vendor adapter
- [x] Supabase anonymous auth with persisted session
- [x] Postgres schema, RLS, `create_household` / `join_household` RPCs
- [x] Realtime publication + per-table channels that refetch into Room
- [x] Reconnect-triggered outbox flush (`NetworkMonitor`) + `SyncWorker` fallback
- [x] Database webhook (`pg_net`) → `notify-household` Edge Function → FCM HTTP v1
- [x] FCM token registration and messaging service
- [x] Notification channels and daily bill/chore/quiet-hours workers
- [x] Home **and** campus geofences, boot restoration, shared receiver

## Milestone 4 — UI ✅

- [x] Onboarding: create / join / demo, validation, loading and error states
- [x] Root session gate (DataStore → onboarding or main app)
- [x] Main scaffold with bottom navigation + contextual "Scan bill" FAB
- [x] Home dashboard: greeting, presence switcher, stats, roommates, due bills/chores
- [x] Bills: outstanding summary, open/settled lists, settle/delete
- [x] Bill editor: CameraX scanner, OCR prefill, paid-by/due chips, split preview
- [x] Chores: add dialog, assignee/due chips, toggle, delete
- [x] Household: join code copy, rename, members, separate home/campus geofence cards, demo buttons, quiet-hours editor, notification toggles, reset
- [x] Notification permission request on Android 13+
- [x] Runtime location permission handling

## Milestone 5 — Verification & documentation 🟡

- [x] JVM tests for `ReceiptParser`, `SplitCalculator`, `QuietHours`, `PresenceResolver`
- [x] `:app:assembleDebug` succeeds
- [x] `:app:testDebugUnitTest` passes
- [x] Backend verified over REST: two anonymous users, outsider sees zero rows
- [x] Webhook → Edge Function verified (`no-targets` without device tokens)
- [x] README, architecture, roadmap, demo script, ADRs, glossary
- [x] Obsidian vault index and 5A evaluation harness
- [ ] Physical device pass: two-device sync, FCM push, geofence walk, battery
- [ ] 20-receipt OCR accuracy measurement

---

## Known gaps and risks 🟡

- Equal split only; `SplitType` is stored but no weighted UI.
- Receipt photos are local-only (no Supabase Storage upload).
- Anonymous auth is demo-grade: an install cannot recover its identity on a new device. Production needs real accounts and invitations.
- Location background permission must be granted manually on Android 10+; geofencing while the app is closed is not exercised in a typical indoor demo.
- Free Supabase projects pause after inactivity. Wake the project and warm the Edge Function before the defense.
- The 5A measurements are pending physical devices and receipts. The harness and CSV templates are ready.
- Conflicts resolve last-write-wins; no offline conflict prompt.
- The FCM service-account key used during setup was exposed in a terminal log. It must be rotated in the Firebase console and the Supabase secret updated; the current key remains valid until then.

## Next steps ⬜

1. **Storage:** upload receipt JPEGs to Supabase Storage, keep a download URL, show thumbnails on other devices.
2. **Split types:** weighted and per-item splitting; receipt line-item assignment UI.
3. **Recurring bills:** monthly rent/internet templates with generated due dates.
4. **Chore rotation:** weekly rotation rules and fair-share stats.
5. **Notification actions:** "Mark settled" / "Mark done" directly from the notification.
6. **Widget / quick settings tile:** today's chores and balance at a glance.
7. **Testing:** Room DAO instrumentation tests, Compose UI tests for onboarding and bill creation, fake `RemoteDataSource` for repository tests.
8. **Security:** real accounts (email link or Google), household invitations, Edge Function rate limiting.
9. **Polish:** dynamic color option, accessibility audit, localization (English/Filipino), tablet layout.
10. **Analytics:** opt-in household activity summary for the documentation chapter.

---

## Submission checklist

- [x] `local.properties` excluded from the repository (already in `.gitignore`)
- [x] `google-services.json` and service-account JSON not committed
- [x] Supabase migrations and Edge Function committed
- [ ] Demo household loaded on the presentation phone
- [ ] Notifications and camera permissions pre-granted on the presentation phone
- [x] Debug APK built; keep the presentation copy in the ignored `.scratch/defense-artifacts/` directory
- [ ] Record the backup walkthrough on presentation hardware
- [x] Proposal PDF and original flowchart included under `docs/reference/`; implemented flowchart in [[01 - Architecture]]
- [ ] Supabase project awake and Edge Function warmed
- [ ] Rotated FCM service-account key stored as a Supabase secret
