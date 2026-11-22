# Evaluation evidence — CS312 §5A

**Field results: pending for all five metrics.** No physical receipt set, boundary walks, paired-device renders or battery profile were collected in this run. The [proposal, p. 5](reference/CS312-LA2-Group4-Proposal.pdf#page=5) defines the endpoints. A unit test of `PresenceResolver` or an HTTP 200 from the webhook cannot stand in for them.

## What is verified

- Hosted Supabase Auth/PostgREST: two anonymous users created and joined one household; both member rows were visible to a member, and a separate user saw zero rows.
- A real bill insert queued a webhook. Postgres recorded HTTP **200**, `{"sent":0,"skipped":"no-targets"}`. After redeployment, a wrong webhook secret returned **401**, and a signed `pg_net` request without device targets again returned **200**. These check routing and authentication; no phone received a push in either check.
- `PresenceResolverTest` exercises home/campus entry, exit, simultaneous entry and unrelated exits. The JVM suite also covers receipt text parsing, splits and quiet hours. Parser fixtures start *after* image recognition.
- Windows verification, 22 Nov 2026: `.\gradlew.bat test assembleDebug` succeeded; **27 tests**, zero failures, errors or skips (8 presence, 7 quiet hours, 7 parser, 5 splitting). The APK is `app/build/outputs/apk/debug/app-debug.apk`.
- A separate in-memory SQLite migration check applied the app's v1→v2 SQL to a v1 household with a pending edit. It retained the home radius and pending flag, initialized the campus radius to 100 m with no campus center, and matched all four exported v2 table shapes.

## Collect and score a run

From the repo root in PowerShell, create a **new** run directory, copy the five CSV headers and record conditions in a `notes.md` next to them. Include APK commit, date, Android versions, device nicknames, permission state, network type and Supabase region. Save redacted rows and profiler evidence with the run. Use a new bill per timing trial; avoid receipt photos, coordinates, tokens and member names in shared evidence.

```powershell
$run = "docs/evaluation/runs/$(Get-Date -Format yyyyMMdd-HHmmss)"
New-Item -ItemType Directory $run
Copy-Item docs/evaluation/*.csv $run
adb devices -l
adb -s SERIAL logcat -v epoch -s SortMateMetrics:D > "$run/device-A.log"
# Use a separate terminal per device; stop capture with Ctrl+C.
.\docs\evaluation\score.ps1 -Directory $run | Format-Table -AutoSize
```

Markers are emitted only by **debug** builds. `wall_ms` is Unix epoch milliseconds; `elapsed_ms` is time since that device booted. Never subtract two devices' elapsed clocks. Do one trial at a time with the relevant card visible. The scorer leaves header-only sheets `PENDING`, rejects malformed input and reports individual trials so a timeout is not averaged away.

1. **OCR — ≥90% (18/20).** Scan 20 distinct physical receipts under varied lighting. Transcribe the printed final total independently in integer cents. Before editing anything, enter the detected total, or `miss` for a blank/failed scan. One row per receipt, including mistakes; the parser's confidence badge is not the score.
2. **Geofence — <30,000 ms.** Save distinct home and campus centers, enable precise/background location, stand outside a measured boundary, and keep the Home badge visible. At the *physical crossing* have an observer mark the phone log (`adb -s SERIAL shell log -t SortMateMetrics "physical_cross T01"` over wireless debugging). Convert that log line's epoch seconds to milliseconds; pair it with the first matching `presence_draw wall_ms`. `geofence_event` is an intermediate marker, not time zero. Record the observer's timing uncertainty; repeat near the cutoff. Include both fences. If no draw within the observation window (use at least 60 s), enter `timeout`. Indoor buttons and emulator location injections are rehearsal only.
3. **Realtime — <300 ms.** Two separately signed-in members, same cloud household. Keep B's Bills screen open with space for a new card. On A create a bill; match `bill_send` and `bill_ack` on A and the **first** `bill_draw synced=true` on B by `id`. Run `.\docs\evaluation\measure-clock.ps1 -Serial SERIAL` on each phone against the same host immediately before and after. Add each phone's offset to its `wall_ms`; enter those adjusted times. Sum both error bounds, allowing for changes in offset between calibrations. Discard trials with a clock step or unrelated write. Since the database write lies between send and ACK, the scorer reports a **range** for write-to-draw; a range crossing 300 ms is inconclusive. Record Wi-Fi and 4G separately; include missed draws as `timeout`.
4. **Battery — <3 percentage points per 4 h.** Start unplugged with active background geofences, record starting charge, then background the app for 240 minutes. Capture the Energy/Power Profiler trace and ending charge. Record screen time, radios and other workload. The CSV scores whole-device discharge; a matched run with geofences disabled helps distinguish the app's contribution. Keep the trace reference in `notes.md`. A shorter run cannot be extrapolated to a pass.
5. **Offline recovery — 5/5 within 5,000 ms.** On a joined device disable Wi-Fi and mobile data; confirm `network online=false`. Create five distinct bills, confirm they remain visible after reopening offline, then restore the network. Capture the **first** `network online=true elapsed_ms` and the last of the five distinct `bill_ack elapsed_ms` on that same phone. Confirm all five IDs exactly once on the server/other member. Enter the number confirmed; use `timeout` for the last ACK if fewer than five complete. A pending badge disappearing alone is not proof.

## Defense handoff

After each run, add a link here to its worksheets, redacted evidence, conditions and scored results (including failures). Report §3A separately: R1 **<200 ms**, R2 **<3 s** and 100% persistence, R3 **<1.5 s** foreground-to-background delivery, R4 **<3% per 8 h**. R3 needs another phone with a valid FCM token and a visible notification; R4 needs its own eight-hour run. Passing §5A does not establish those stricter claims. See [[05 - Defense]].
