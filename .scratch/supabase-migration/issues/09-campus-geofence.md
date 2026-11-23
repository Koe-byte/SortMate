# T9 — Campus geofence

**Status:** done-code, device-pending
**Blocked by:** T5

## Delivered

- `HouseholdEntity` campus fields.
- Room schema v2 migration and committed `2.json`.
- `GeofenceManager` registers `sortmate-home` and `sortmate-campus`.
- `PresenceResolver` pure state machine with unit tests.
- Receiver resolves triggering IDs against current status.
- Boot restore registers both.
- Household UI has separate Home and Campus cards + Home/Campus/Away demo buttons.

## Remaining

- Physical walk test: campus enter → `CAMPUS`, campus exit → `AWAY`, home transitions, reboot restore.
- Confirm the status reaches the other device via Realtime.
