# SortMate — Demo script

A 7–8 minute walkthrough for the CS312 final defense. The goal is to show the full pipeline from the proposal flowchart, not to tour every screen.

---

## Before the demo

### Phone / emulator

- [ ] Install the latest debug APK (`app/build/outputs/apk/debug/app-debug.apk`).
- [ ] Grant **Camera**, **Notifications**, and **Precise location** permissions.
- [ ] For the automatic geofence claim, set Location to **Allow all the time** in app settings.
- [ ] Turn off battery saver so WorkManager and geofences are not throttled.
- [ ] Load the **demo household** once so there are bills/chores on screen.
- [ ] Keep a second device/emulator signed into the same Firebase household if you want to show live sync. Otherwise use the local demo and say so honestly.
- [ ] Record a backup video of the receipt scan in case the venue lighting/camera fails.

### Firebase (optional)

If credentials are configured:

- [ ] `app/google-services.json` present and app rebuilt.
- [ ] Anonymous auth, Firestore, and FCM enabled.
- [ ] Rules deployed from `firestore.rules`.
- [ ] Second device joined with the 6-character code.

If not configured, the Household tab shows **Local demo mode**. That is a supported path, not a failure — explain it as an intentional offline-first design.

---

## The script

### 0:00 — Frame the problem (30 s)

> "Students who share a dorm or apartment juggle three things: who paid for what, whose turn it is to clean, and when the house should be quiet. SortMate is a native Android app that keeps those three things in one shared rhythm."

Mention the scope explicitly:

> "We do not process payments. We record who paid, calculate each person's share, and notify the household."

### 0:30 — Onboarding (30 s)

1. Open the app. Point out the three paths: **Create**, **Join**, **Explore the demo household**.
2. Tap **Explore the demo household** (name optional, defaults to Nash).
3. Land on **Home**.

> "The demo seeds three roommates, three bills, and three chores so every downstream feature can be shown on one phone."

### 1:00 — Dashboard (45 s)

Point at, in order:

- Greeting + household name.
- **Presence card** — current status and the row of quick switches (At home / Campus / Away).
- Three stat cards — your share, open bills, chores left.
- **Roommates** row with presence dots.
- **Bills due soon** and **Chore board**.

> "Everything here is a Room `Flow`. The UI never waits on the network."

### 1:45 — Receipt OCR pipeline (2 min)

1. Tap **Scan bill**.
2. If prompted, grant camera access.
3. Hold a real receipt (or a printed test receipt) so the total line is inside the frame.
4. Tap the capture button.

Narrate while ML Kit runs:

> "CameraX captures a still, we copy it into app-private storage, and ML Kit recognizes text on-device. Our pure-Kotlin parser then prefers 'grand total' over 'subtotal', ignores VAT, cash, and change, and falls back to the largest amount if no keyword exists."

5. Show the prefilled **title, merchant, total**, the receipt thumbnail, and the confidence badge.
6. Point out **Paid by** chips and the **Split preview**: total ÷ roommates, with exact cent distribution.
7. Tap **Save and split**.

Fallback if the scan fails: type the total manually and say:

> "The manual path exists on purpose — OCR is assistive, never blocking. The same validation runs."

### 3:45 — Bills (45 s)

1. The new bill appears at the top of **Bills** (navigate via the bottom bar if the editor returned to Home).
2. Point out the **household outstanding** card and **your share**.
3. Tap the checkbox to settle a bill; it moves to the **Settled** section. Tap again to reopen.
4. Mention sync indicators: `local` while a Firestore write is pending, gone after acknowledgement.

### 4:30 — Geofenced presence (1 min 15 s)

1. Go to **Household**.
2. Scroll to **Home geofence**. If coordinates are missing, tap **Save current location**; grant fine location. Explain that the radius is 100–120 m.
3. Point out the "Open location permissions" hint for "Allow all the time".
4. Use **Leave home**, then **Arrive home** under **Demo the pipeline**.

> "These buttons call exactly the same repository method as the `GeofenceBroadcastReceiver`. In the field, Play Services delivers the enter/exit event; here we simulate it so the defense does not depend on walking outside."

5. Watch the Home presence pill and member status change. If notifications are enabled, show the notification shade entry.

If Firebase is live, show the other device's presence update arriving.

### 5:45 — Quiet hours + chores (45 s)

1. On Household, tap **Edit quiet hours**, change 22:00 → 06:00 to another range, save. Point out midnight crossing is supported.
2. Toggle **Quiet hours** notifications off/on to show WorkManager cancellation/rescheduling.
3. Go to **Chores**. Add "Wipe the table", assign it to a roommate, due **Tomorrow**. Toggle it complete.

### 6:30 — Architecture recap (1 min)

Show `docs/ARCHITECTURE.md` or a slide with the pipeline:

> "One trigger type, two branches. Receipt branch: CameraX → ML Kit → Room → Firestore → FCM. Geofence branch: FusedLocation → geofence receiver → Room → Firestore → FCM. Room is always the source of truth; Firestore listeners and a 15-minute WorkManager job reconcile. Notifications are local channels plus an FCM service."

Mention tests:

> "We unit-test the three pure pieces where UI bugs would hide: receipt parsing, cent-exact splitting, and overnight quiet hours."

### 7:30 — Close

> "SortMate is offline-first, privacy-preserving because OCR is on-device, and honest about scope: it tracks shared living, it does not move money."

---

## Fallback matrix

| Problem | Fallback |
| --- | --- |
| Camera unavailable / poor lighting | Enter the bill manually; play the backup scan video |
| ML Kit returns the wrong total | Correct the field; explain the confidence badge and parser heuristics |
| No location fix indoors | Use **Arrive home / Leave home** simulation; explain the real receiver path |
| Background location denied | Use foreground location + simulation; point to the settings hint |
| Firebase not configured | Run local demo mode; explain Room-first design and guarded Firestore adapter |
| No Wi-Fi | Everything except multi-device sync works; `SyncWorker` retries when online |
| Second device fails | Show Firestore console documents or the recorded sync video |
| Notification does not appear | Check POST_NOTIFICATIONS in app settings; show channel configuration in code |

---

## Likely questions

**Why Room instead of only Firestore?**
Offline-first, instant UI, deterministic demo, and a clean separation between transport and presentation. Firestore listeners feed Room; the UI never observes Firestore directly.

**Why ML Kit instead of a cloud OCR API?**
Privacy (receipts stay on-device), no API keys or per-scan cost, and it works without connectivity. The trade-off is heuristic parsing on unusual receipts.

**How do you handle split rounding?**
`SplitCalculator` distributes leftover cents to the first participants; shares always sum exactly to the total. Bills store integer cents.

**What happens if two roommates edit offline?**
Writes are queued with `isSynced = false`; last-write-wins by `updatedAt` when connectivity returns. It is intentionally simple for a small household; conflict prompts are future work.

**Does it drain the battery?**
Geofences are OS-managed and responsive at 30 s; WorkManager batches daily reminders and a 15-minute sync. No continuous location polling.

**Is it secure?**
Demo rules require anonymous auth and scope documents under a household. For production we would add real accounts, membership checks, and App Check. We do not store payment credentials.

**Why no Hilt?**
A manual `AppContainer` keeps the dependency graph visible for the defense and avoids KSP overhead in a small app.

**What is not implemented?**
Weighted splits, receipt image upload, recurring bills, notification actions, and production-grade auth. These are listed in the roadmap.
