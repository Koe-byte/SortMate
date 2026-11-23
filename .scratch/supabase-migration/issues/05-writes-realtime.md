# T5 — Supabase writes + realtime

**Status:** done-backend, device-pending
**Blocked by:** T4

## Delivered

- Bill add/settle/delete, chore add/toggle/delete, presence, quiet hours, geofence, FCM token writes.
- `SupabaseDataSource` upserts and soft-deletes.
- Realtime channels per table with household filters.
- `RealtimeSyncManager` merges rows into Room and protects the outbox.
- Realtime starts only when authenticated and online.

## Remaining

- Two-device test: add a bill on A, confirm it renders on B without refresh.
- Two-device test: presence and chore changes.
