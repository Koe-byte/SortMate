# SortMate

**Student housemate & academic manager** — a native Android app that keeps a student household's bills, chores, quiet hours, and presence in sync.

> CS312 · Mobile Computing · Group 4
> Proposal title: *SortMate - Student housemate & Academic manager*

SortMate is built for co-living students: photograph a receipt and split it in seconds, see who is home or on campus without asking, assign chores, and let the house agree on quiet hours. It is offline-first — Room is always the UI source of truth — and uses Supabase for cloud data, anonymous auth, and realtime sync, with FCM for push. Both are optional at build time, so the app still runs in a fully local demo mode.

---

## Feature tour

| Area | What works |
| --- | --- |
| **Receipt OCR** | CameraX capture → on-device ML Kit text recognition → `ReceiptParser` picks the grand total, merchant, date, and line items. Confidence is stored with the bill. |
| **Bill splitting** | Equal split across every housemate, with leftover cents distributed deterministically. Shows each person's share and the household outstanding total. |
| **Home + campus presence** | Two Google Play Services geofences per household. `PresenceResolver` turns overlapping enter/exit events into `HOME`, `CAMPUS`, or `AWAY`. Demo buttons exercise the same path indoors. |
| **Chores** | Shared chore board with assignee, due date, completion toggle, and WorkManager reminders at 6:00 PM. |
| **Quiet hours** | Household-wide window that supports crossing midnight (e.g. 22:00 → 06:00), with a reminder when it begins. |
| **Realtime sync** | Supabase Realtime channels refetch changed tables into Room; reconnect flushes the outbox immediately; `SyncWorker` retries every 15 minutes as a fallback. |
| **Push alerts** | Postgres webhook → Supabase Edge Function → FCM HTTP v1, plus local notification channels for bills, chores, presence, and quiet hours. |
| **Offline / demo mode** | Without Supabase credentials the app seeds a complete household (3 roommates, 3 bills, 3 chores) so the entire pipeline can be demonstrated on one phone. |
| **Onboarding** | Create a household, join with a 6-character code, or open the demo household. Join codes are copyable from the Household tab. |

---

## Pipeline

The implementation follows the proposal flowchart:

```
User action / sensor event
        │
        ├── scan receipt ──► CameraX ──► ML Kit OCR ──► ReceiptParser
        │
        └── boundary cross ─► Geofence receiver ─► PresenceResolver
                                      │
                                      ▼
                         Validate + store in Room
                                      │
                                      ▼
                            Supabase Postgres
                              ├── Realtime → Room on B → roommate UI
                              └── pg_net → Edge Function → FCM alert
```

---

## Tech stack

- **Language / UI:** Kotlin 2.3.10, Jetpack Compose (Material 3), Navigation Compose, MVVM + `StateFlow`
- **Persistence:** Room 2.8.5 (KSP), DataStore Preferences
- **Camera / OCR:** CameraX 1.7.0, Google ML Kit Text Recognition 16.0.1
- **Location:** Google Play Services Location 21.4.0 (FusedLocationProviderClient + GeofencingClient)
- **Cloud data / auth / realtime:** Supabase (Postgres + RLS), supabase-kt 3.4.1, Ktor 3.4.0
- **Push:** Firebase BoM 35.2.0 — Cloud Messaging only (Spark plan, no Blaze)
- **Backend function:** Supabase Edge Function (Deno) triggered by `pg_net`
- **Background:** WorkManager 2.12.0
- **Build:** AGP 9.2.1, Gradle 9.7.1, Gradle daemon JDK 25, Java source/target 17, compileSdk/targetSdk 37, minSdk 26 (Android 8.0+)

No payment gateway, bank integration, or money movement is implemented — SortMate only records who paid and how much each housemate owes.

> **Why Supabase instead of Firestore?** Firebase Cloud Functions require the paid Blaze plan. Supabase provides Postgres, anonymous auth, realtime, and Edge Functions on a free tier, while FCM stays free on Spark. See [`docs/adr/0001-supabase-for-data-and-realtime.md`](docs/adr/0001-supabase-for-data-and-realtime.md).

---

## Getting started

### 1. Requirements

- Android Studio with Android SDK 37 and a full JDK 25 including `jlink`
- An Android 8.0+ device or emulator (camera and location are best tested on a physical phone)
- Optional: a Supabase project for multi-device sync
- Optional: a Firebase project (Spark) for push notifications

### 2. Build and install

```powershell
# from the project root
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

If Gradle selects a VS Code extension's JRE and reports `jlink.exe does not exist`, keep `JAVA_HOME` pointed at the **full JDK 25** above and run this once from PowerShell. It uses a fresh daemon with only that Java installation.

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=2 `
  '-Porg.gradle.java.installations.auto-detect=false' "-Porg.gradle.java.installations.paths=$env:JAVA_HOME" `
  '-Dorg.gradle.java.installations.auto-detect=false' "-Dorg.gradle.java.installations.paths=$env:JAVA_HOME"
```

Open the project in Android Studio and press **Run**, or install with:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 3. Optional Supabase setup

The app runs without Supabase. To enable multi-device sync:

1. Create a Supabase project.
2. Put the URL and publishable key in the gitignored `local.properties`:

   ```properties
   SUPABASE_URL=https://<project-ref>.supabase.co
   SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
   ```

3. Link and push the schema:

   ```powershell
   npx --yes supabase@latest link --project-ref <project-ref>
   npx --yes supabase@latest db push --remote --include-all
   ```

4. Enable **anonymous sign-ins** in Auth settings.
5. Rebuild. `AppContainer` creates the client only when both values are present.

The schema, RLS policies, RPCs, realtime publication, and webhook trigger live in [`supabase/migrations/`](supabase/migrations/). Anonymous users are scoped to their household by `members.auth_user_id = auth.uid()`; an outsider sees zero rows.

### 4. Optional FCM push

Follow the [push setup](docs/02%20-%20Supabase.md) to configure Android Messaging, the server-side sender secret and the webhook URL for **your own** Supabase project. Firebase is optional for sync: without `google-services.json`, create/join and Realtime still work when Supabase is configured. Without Supabase, create a local household or explore the seeded demo.

### 5. Run the demo

On first launch choose **Explore the demo household**. It seeds:

- Nash (Leader / Backend), Kotnie Edrae (UI/UX), Romelie Joy (Documentation)
- Weekly groceries, fiber internet, water bill
- Trash, kitchen, and dish-soap chores
- Quiet hours 10:00 PM – 6:00 AM

Tap **Scan bill** to capture a real receipt, or use **Household → Demo the pipeline** to simulate home, campus, and away transitions without walking outside.

See [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md) for a defense-ready walkthrough.

---

## Testing

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest
```

Pure-JVM tests cover the places where bugs would be invisible in the UI:

- `ReceiptParserTest` — grand total beats subtotal/VAT/change, PHP and comma formats, fallback selection, dates, empty receipts
- `SplitCalculatorTest` — leftover-cent distribution always sums back to the total
- `QuietHoursTest` — overnight windows, boundaries, same start/end, formatting
- `PresenceResolverTest` — home/campus enter and exit, both entering at once, unrelated exits, and no transition

Backend verification commands and the 5A measurement harness are documented in [`docs/EVALUATION.md`](docs/EVALUATION.md).

---

## Project structure

```
app/src/main/java/com/example/sortmate/
├── MainActivity.kt              # edge-to-edge Compose entry point
├── SortMateApplication.kt       # owns AppContainer
├── data/
│   ├── local/                   # Room database, DAOs, entities, converters
│   ├── prefs/                   # DataStore session + notification toggles
│   ├── remote/                  # RemoteDataSource + Supabase adapter
│   ├── repository/              # Auth, Household, Bill, Chore repositories
│   └── sync/                    # realtime merge manager + network monitor
├── di/AppContainer.kt           # manual dependency container + app startup
├── domain/                      # pure Kotlin: ReceiptParser, SplitCalculator, QuietHours, PresenceResolver
├── location/                    # FusedLocationProvider, home/campus geofences + receiver
├── notifications/               # channels, FCM service, WorkManager jobs
├── ocr/                         # ML Kit processor + receipt image storage
└── ui/                          # Compose screens, ViewModels, navigation, theme
supabase/
├── migrations/                  # schema, RLS, RPCs, realtime, webhook trigger
└── functions/notify-household/  # Deno FCM sender
```

---

## Known limitations

- Split type is **equal only**; the domain model has a `SplitType` seam for weighted/custom splits later.
- Receipt photos are stored in app-private storage and are **not uploaded** to Supabase Storage.
- Anonymous auth is sufficient for a class demo; production would add real accounts and stricter household membership proofs.
- Background geofencing requires the user to choose **"Allow all the time"** in system settings (Android 10+). The demo buttons cover this without leaving the room.
- The proposal's solution overview refers to the app once as **"DormSync"**; this implementation standardizes on **SortMate**.
- Concurrent cloud edits resolve in server write order. A pending Room edit stays queued until the upload for that local version succeeds.
- The 5A device measurements (OCR accuracy, geofence/realtime latency, battery, offline recovery) are pending until physical devices and 20 receipts are available.

---

## Documentation

- [`docs/00 - SortMate.md`](docs/00%20-%20SortMate.md) — Obsidian vault index
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — layers, flowchart mapping, schemas, sync strategy
- [`docs/02 - Supabase.md`](docs/02%20-%20Supabase.md) — schema, RLS, RPCs, realtime, Edge Function
- [`docs/03 - Presence.md`](docs/03%20-%20Presence.md) — home + campus geofence state machine
- [`docs/EVALUATION.md`](docs/EVALUATION.md) — 5A results and measurement harness
- [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md) — 7-minute defense script with fallbacks
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — completed milestones and next steps
- [`GLOSSARY.md`](GLOSSARY.md) — shared vocabulary for code, docs, and defense
- [`docs/reference/`](docs/reference/) — original proposal PDF and flowchart
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — commit and branch conventions

---

## Contributing

This project uses **Conventional Commits** (`<type>[optional scope]: <description>`) and **Conventional Branch** (`feature/`, `fix/`, `hotfix/`, `release/`, `chore/`). Read [`CONTRIBUTING.md`](CONTRIBUTING.md) before opening a pull request, and [`AGENTS.md`](AGENTS.md) for AI agent instructions.

---

## Team

| Member | Role |
| --- | --- |
| Nash R. Ibon | Leader / Backend |
| Kotnie Edrae L. Bacay | UI / UX |
| Romelie Joy Dapoc | Documentation |

Course: **CS312 — Mobile Computing**, Group 4.
