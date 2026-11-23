# T3 — Supabase auth + create/join

**Status:** done-backend, device-pending
**Blocked by:** T1, T2

## Delivered

- `AuthRepository` restores the persisted anonymous session and signs in on first use.
- `create_household` and `join_household` RPCs.
- Client-generated household/member IDs preserved.
- Clear errors for invalid codes and duplicate membership.

## Verification

- REST test: user A created a household; user B joined with the code.
- Both member rows were visible to members; an outsider saw zero.
- `join_household` returned the household and the joined member.

## Remaining

- Run the same flow from two Android devices/emulators and confirm the onboarding UI.
