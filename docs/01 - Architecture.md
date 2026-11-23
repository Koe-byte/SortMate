# From trigger to roommate

```mermaid
flowchart TD
  scan[Scan receipt] --> ocr[CameraX, ML Kit, review total]
  cross[Cross home or campus boundary] --> presence[Play Services receiver and PresenceResolver]
  ocr --> local[(Room on phone A)]
  presence --> local
  local -->|pending writes, retry on reconnect| cloud[(Supabase Postgres with RLS)]
  cloud -->|Realtime change| fetch[Phone B refetches affected table]
  fetch --> cache[(Room on phone B)]
  cache -->|Flow and StateFlow| screen[Compose renders]
  cloud -->|pg_net webhook| sender[Supabase Edge Function]
  sender -->|FCM HTTP v1| alert[Android notification]
```

The receipt image stays on A. B can render a bill even if notifications are disabled. For the diagram-to-file mapping, see [[ARCHITECTURE]]. For the rationale and the security boundary, see [[02 - Supabase]].
