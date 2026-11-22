# Supabase setup and ownership

[[0001-supabase-for-data-and-realtime|ADR-0001]] records the cost decision. The Android app needs only the project URL and **publishable** key. Create/join require connectivity; a joined member can subsequently work offline. Firebase configuration is separate and only enables messaging.

## Bring up a new project

1. Set `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` in the gitignored `local.properties`. Enable anonymous sign-ins under **Authentication**. Link the CLI and apply [the migrations](../supabase/migrations/) as in the [README](../README.md).
2. Rebuild the app, create a household on A and join it from B using the code on A. Membership is assigned by the two transactional RPCs. Members can read their household; client updates cannot assign membership or change identity columns. Postgres publishes `households`, `members`, `bills` and `chores`.
3. For push, add `app/google-services.json` for `com.example.sortmate`. Enable FCM HTTP v1. Give the sender the **Firebase Cloud Messaging API Admin** role on the target Firebase project. Store its service-account JSON as `FCM_SERVICE_ACCOUNT_JSON` under **Supabase → Edge Functions → Secrets**. Only the function needs it.
4. Run `npx --yes supabase@latest functions deploy notify-household --remote --use-api`. Gateway JWT verification is disabled for this function in `supabase/config.toml`; the handler checks the database-generated webhook secret.
5. In **that project's** SQL editor set its destination. Replace `YOUR_PROJECT_REF` with the project you just deployed; never use another installation's URL. A new database skips push until this is configured.

   ```sql
   insert into public.app_config (key, value)
   values ('webhook_url', 'https://YOUR_PROJECT_REF.supabase.co/functions/v1/notify-household')
   on conflict (key) do update set value = excluded.value;
   ```

6. In a dedicated test household, add a bill on A while B is backgrounded. Inspect the latest `status_code`, `timed_out` and `content` in `net._http_response`. `no-targets` means the signed webhook ran but there was no eligible recipient. A positive send count means FCM accepted a request; only seeing the alert on B verifies delivery.

## Owner action: replace the exposed sender key

An earlier setup log included the Firebase sender's private key. Rotation was **not** completed: the available identity could not manage IAM keys. A project owner should open [Google Cloud Service Accounts](https://console.cloud.google.com/iam-admin/serviceaccounts?project=sortmate-ph), revoke the exposed key and generate a replacement for the sender. Update `FCM_SERVICE_ACCOUNT_JSON` in [the project's function secrets](https://supabase.com/dashboard/project/uxppiaxtmasrrtryonxp/settings/functions), then verify delivery on B. Store the replacement outside the working tree. Push may be unavailable during rotation; Room and Realtime are independent of FCM.

See [[EVALUATION]] for the evidence collected so far and [[05 - Defense]] for the presentation path.
