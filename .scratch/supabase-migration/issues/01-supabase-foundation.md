# T1 — Supabase foundation

**Status:** done
**Blocked by:** none

## Delivered

- Hosted project `uxppiaxtmasrrtryonxp` linked.
- Migrations: schema, RLS, RPCs, triggers, realtime publication, `pg_net` webhooks, service-role grants.
- Anonymous sign-ins enabled via `config push`.
- `local.properties` has `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`.
- Edge Function `notify-household` deployed with `FCM_SERVICE_ACCOUNT_JSON`.

## Verification

- `db push` applied all migrations.
- `create_household` + `join_household` worked for two anonymous users.
- An outsider received zero member rows.
- Webhook probe returned `{"sent":0,"skipped":"no-targets"}`.

## Remaining

- ADR, glossary, and agent docs are tracked under T11.
- Rotate the FCM service-account key (it was printed during setup).
