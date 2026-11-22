# T7 — Retire Firestore

**Status:** done
**Blocked by:** T5, T6

## Delivered

- Removed `FirestoreDataSource.kt`.
- Removed `firebase-auth` and `firebase-firestore` dependencies.
- Firebase Messaging remains for push.
- Repositories and `AppContainer` no longer reference Firestore.
- UI copy updated to Supabase.

## Verification

- `rg Firestore app/src` returns no runtime references.
- Build and unit tests pass.

## Handoff

- Firestore rules retired, docs updated; Firebase Messaging remains the only Firebase SDK used directly.
