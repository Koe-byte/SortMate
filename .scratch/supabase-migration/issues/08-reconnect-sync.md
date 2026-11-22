# T8 — Reconnect sync

**Status:** done-backend, device-pending
**Blocked by:** T5

## Delivered

- `NetworkMonitor` wraps `ConnectivityManager.registerDefaultNetworkCallback`.
- `AppContainer` combines household ID + connectivity.
- On reconnect: stop/start realtime, `syncPending()` for household/bills/chores, then `refreshFromRemote()`.
- `SyncWorker` remains the 15-minute fallback.
- Pending badge clears when `markSynced` runs.

## Remaining

- Device test: create five bills offline, reconnect, confirm all sync within 5 s.
