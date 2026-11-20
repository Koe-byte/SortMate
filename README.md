# SortMate

**Student housemate & academic manager** — a native Android app that keeps a student household's bills, chores, quiet hours, and presence in sync.

> CS312 · Mobile Computing · Group 4
> Proposal title: *SortMate - Student housemate & Academic manager*

SortMate is built for co-living students: photograph a receipt and split it in seconds, see who is home without asking, assign chores, and let the house agree on quiet hours. It is offline-first — Room is always the UI source of truth — and uses Firebase Firestore + FCM when a `google-services.json` is supplied.

---

## Feature tour

| Area | What works |
| --- | --- |
| **Receipt OCR** | CameraX capture → on-device ML Kit text recognition → `ReceiptParser` picks the grand total, merchant, date, and line items. Confidence is stored with the bill. |
| **Bill splitting** | Equal split across every housemate, with leftover cents distributed deterministically. Shows each person's share and the household outstanding total. |
| **Geofenced presence** | Google Play Services geofencing around the home coordinates. Entering marks you *At home*, exiting marks you *Away*. A manual "simulate boundary" control is included for demos. |
| **Chores** | Shared chore board with assignee, due date, completion toggle, and WorkManager reminders at 6:00 PM. |
| **Quiet hours** | Household-wide window that supports crossing midnight (e.g. 22:00 → 06:00), with a reminder when it begins. |
| **Realtime sync** | Firestore snapshot listeners keep Room fresh while the app runs; `SyncWorker` retries pending writes every 15 minutes. |
| **Push alerts** | FCM service for remote household alerts, plus local notification channels for bills, chores, presence, and quiet hours. |
| **Offline / demo mode** | Without Firebase the app seeds a complete household (3 roommates, 3 bills, 3 chores) so the entire pipeline can be demonstrated on one phone. |
| **Onboarding** | Create a household, join with a 6-character code, or open the demo household. Join codes are copyable from the Household tab. |

---

## Pipeline

The implementation follows the proposal flowchart:

```
User action / sensor event
        │
        ├── scan receipt ──► CameraX ──► ML Kit OCR ──► ReceiptParser
        │
        └── boundary cross ─► FusedLocationProvider ─► Geofence receiver
                                      │
                                      ▼
                         Validate + store in Room
                                      │
                                      ▼
                       Sync Firestore (snapshot + worker)
                                      │
                                      ▼
                     FCM push / local notification alert
                                      │
                                      ▼
                        Roommates' UI updates via Flow
                                      │
                                      ▼
                               Process complete
```

---

## Tech stack

- **Language / UI:** Kotlin 2.3.10, Jetpack Compose (Material 3), Navigation Compose, MVVM + `StateFlow`
- **Persistence:** Room 2.8.5 (KSP), DataStore Preferences
- **Camera / OCR:** CameraX 1.7.0, Google ML Kit Text Recognition 16.0.1
- **Location:** Google Play Services Location 21.4.0 (FusedLocationProviderClient + GeofencingClient)
- **Cloud:** Firebase BoM 35.2.0 — Anonymous Auth, Firestore, Cloud Messaging
- **Background:** WorkManager 2.12.0
- **Build:** AGP 9.2.1, Gradle 9.7.1, JDK 17, compileSdk/targetSdk 37, minSdk 26 (Android 8.0+)

No payment gateway, bank integration, or money movement is implemented — SortMate only records who paid and how much each housemate owes.

---

## Getting started

### 1. Requirements

- Android Studio with Android SDK 37 and JDK 17
- An Android 8.0+ device or emulator (camera and location are best tested on a physical phone)
- Optional: a Firebase project for multi-device sync

### 2. Build and install

```powershell
# from the project root
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Open the project in Android Studio and press **Run**, or install with:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 3. Optional Firebase setup

The app runs without Firebase. To enable multi-device sync and push:

1. Create a Firebase project and add an Android app with package name `com.example.sortmate`.
2. Download `google-services.json` into `app/google-services.json`.
3. Enable **Anonymous** authentication, **Cloud Firestore**, and **Firebase Cloud Messaging**.
4. Deploy the rules in [`firestore.rules`](firestore.rules) (`firebase deploy --only firestore:rules`).
5. Rebuild. The `google-services` Gradle plugin is applied automatically only when `app/google-services.json` exists.

Firestore layout:

```
households/{householdId}
  ├── members/{memberId}
  ├── bills/{billId}
  └── chores/{choreId}
```

Without Firebase the Household tab shows a **Local demo mode** banner, and join-by-code is disabled (there is nothing remote to join).

### 4. Run the demo

On first launch choose **Explore the demo household**. It seeds:

- Nash (Leader / Backend), Kotnie Edrae (UI/UX), Romelie Joy (Documentation)
- Weekly groceries, fiber internet, water bill
- Trash, kitchen, and dish-soap chores
- Quiet hours 10:00 PM – 6:00 AM

Tap **Scan bill** to capture a real receipt, or use the **Household → Demo the pipeline** buttons to simulate geofence arrival/exit without walking outside.

See [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md) for a defense-ready walkthrough.

---

## Testing

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest
```

Pure-JVM tests cover the three places where bugs would be invisible in the UI:

- `ReceiptParserTest` — grand total beats subtotal/VAT/change, PHP and comma formats, fallback selection, dates, empty receipts
- `SplitCalculatorTest` — leftover-cent distribution always sums back to the total
- `QuietHoursTest` — overnight windows, boundaries, same start/end, formatting

---

## Project structure

```
app/src/main/java/com/example/sortmate/
├── MainActivity.kt              # edge-to-edge Compose entry point
├── SortMateApplication.kt       # owns AppContainer
├── data/
│   ├── local/                   # Room database, DAOs, entities, converters
│   ├── prefs/                   # DataStore session + notification toggles
│   ├── remote/                  # guarded Firestore adapter and mappers
│   ├── repository/              # Auth, Household, Bill, Chore repositories
│   └── sync/                    # realtime snapshot listener manager
├── di/AppContainer.kt           # manual dependency container + app startup
├── domain/                      # pure Kotlin: ReceiptParser, SplitCalculator, QuietHours
├── location/                    # FusedLocationProvider, geofence manager + receiver
├── notifications/               # channels, FCM service, WorkManager jobs
├── ocr/                         # ML Kit processor + receipt image storage
└── ui/                          # Compose screens, ViewModels, navigation, theme
```

---

## Known limitations

- Split type is **equal only**; the domain model has a `SplitType` seam for weighted/custom splits later.
- Receipt photos are stored in app-private storage and are **not uploaded** to Firebase Storage.
- Anonymous auth is sufficient for a class demo; production would need household-membership checks in security rules.
- Background geofencing requires the user to choose **"Allow all the time"** in system settings (Android 10+). The demo buttons cover this without leaving the room.
- The proposal's solution overview refers to the app once as **"DormSync"**; this implementation standardizes on **SortMate**.
- Firestore conflict handling is last-write-wins using `updatedAt`. Fine for a household of 3–6 students; not a CRDT.

---

## Documentation

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — layers, flowchart mapping, data schemas, sync strategy
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — completed milestones and next steps
- [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md) — 7-minute defense script with fallbacks
- [`firestore.rules`](firestore.rules) — demo security rules
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
