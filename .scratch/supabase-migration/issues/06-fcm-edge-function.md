# T6 — FCM via Edge Function

**Status:** backend-done, device-pending
**Blocked by:** T5; Firebase Spark project

## Delivered

- `pg_net` triggers on bills/chores/members.
- `notify-household` Edge Function with webhook-secret verification.
- FCM HTTP v1 sender with cached OAuth token.
- Actor and presence-subject exclusion.
- Invalid tokens cleared from `members.fcm_token`.
- Secret `FCM_SERVICE_ACCOUNT_JSON` stored in Supabase, never committed.

## Verification

- Invalid secret returns `401`.
- Correct secret with no device token returns `{"sent":0,"skipped":"no-targets"}`.
- A real bill insert produced a `200` response in `net._http_response`.

## Remaining

- Install the APK on a device, let `SortMateMessagingService` upload the token, then verify a background push in < 1.5 s.
- Rotate the service-account key that was printed during setup.
