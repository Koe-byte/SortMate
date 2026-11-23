# T10 — Evaluation harness

**Status:** ready-for-human (harness verified; field measurements pending)
**Blocked by:** T6, T8, T9

## Delivered

- Metric table and CSV templates under `docs/evaluation/`.
- `docs/EVALUATION.md` results page.
- ADB/logcat collection commands.
- First-draw, network and bill ACK markers; clock calibration and worksheet scorer. Empty sheets return five pending results. Synthetic boundary checks were kept outside the field worksheets.
- Unit-test evidence for `PresenceResolver`.

## Remaining

- 20 physical receipts → OCR accuracy.
- Geofence latency on device.
- Two-device realtime latency.
- 4-hour battery profile.
- Offline recovery timing.
