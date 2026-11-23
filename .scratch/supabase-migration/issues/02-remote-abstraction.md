# T2 — Remote abstraction

**Status:** done
**Blocked by:** none

## Delivered

- `RemoteDataSource` + `RemoteSnapshot` + `RemoteSubscription`.
- Repositories depend on the interface, not Firestore.
- `AppContainer` wires `SupabaseDataSource`.
- Firestore adapter, Firebase Auth, and Firestore dependencies removed.

## Verification

- `.\gradlew.bat test` passes.
- `.\gradlew.bat assembleDebug` passes.
- Demo mode still works without Supabase credentials.

## Notes

The Firestore adapter is recoverable from git history if the instructor requires the exact vendor.
