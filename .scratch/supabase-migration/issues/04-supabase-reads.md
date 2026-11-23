# T4 — Supabase reads

**Status:** done-backend, device-pending
**Blocked by:** T3

## Delivered

- `joinHousehold` fetches household, members, bills, and chores after the RPC.
- Join writes the snapshot into Room in one transaction.
- Offline start reads the Room cache.
- `refreshFromRemote()` merges remote rows without clobbering newer unsynced local rows.

## Remaining

- Confirm Bills and Chores screens render the joined snapshot on a physical device.
