# ADR-0001 — Supabase for data, auth, and realtime

- **Status:** Accepted
- **Date:** 2026-11-22
- **Deciders:** SortMate group

## Context

The proposal selects Firestore and FCM and cites Firebase Authentication. A trusted service must send push messages after changes. Firestore and FCM work on Spark, but deploying Firebase Cloud Functions for the sender requires Blaze. The group needs realtime sync and push without enabling billing.

## Decision

Use **Supabase** for Postgres, anonymous auth, realtime, and the push-sending function, while keeping **FCM** as the Android push transport.

- Postgres tables + Row Level Security replace Firestore collections + rules.
- Supabase anonymous auth replaces Firebase anonymous auth.
- Supabase Realtime replaces Firestore snapshot listeners.
- A Supabase Edge Function triggered by a database webhook replaces Cloud Functions.
- FCM HTTP v1 remains the delivery channel to the Android app.

The Android app depends on a `RemoteDataSource` interface. `SupabaseDataSource` is the only vendor adapter, so the rest of the app is vendor-neutral.

## Consequences

### Positive

- No Blaze plan and no billing account.
- SQL constraints, foreign keys, arrays, and transactional RPCs are stronger than the previous Firestore rules.
- RLS is testable with two anonymous users; an outsider sees zero rows.
- Realtime and Edge Functions are included in the free tier.
- The same Postgres database can back a web dashboard later.

### Negative

- The submission no longer matches the proposal's vendor table verbatim; the defense must explain the cost-driven substitution.
- Free projects pause after inactivity; the project must be woken before the defense.
- Supabase's new publishable/secret API keys require the Edge Function to send opaque keys only in the `apikey` header.
- Realtime is best-effort; the outbox + `SyncWorker` remain necessary.

## Alternatives considered

1. **Firebase Blaze** — rejected: recurring cost and billing setup are unacceptable for the group.
2. **Firestore-only on Spark without Cloud Functions** — rejected: no server-side push sender; the proposal requires FCM alerts after a sync event.
3. **Self-hosted Postgres + WebSocket server** — rejected: deployment and uptime burden during the defense.
4. **Supabase Realtime + FCM via a local device** — rejected: the sender must run when all phones are backgrounded.

## Compliance notes

- Functional requirements R1–R4 and the 5A targets are unchanged.
- The flowchart is updated to say **Supabase Realtime** and **Supabase Edge Function → FCM**.
- [[ARCHITECTURE]] and [[05 - Defense]] record the substitution.
- The Firestore adapter is recoverable from git history if the instructor insists on the exact vendor.
