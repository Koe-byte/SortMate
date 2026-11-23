-- PostgREST switches to the service_role for Edge Function requests.
-- The tables were created by migrations without Supabase's default grants,
-- so grant the service role full access explicitly. RLS still applies to
-- anon/authenticated callers.
grant all on public.households, public.members, public.bills, public.chores
    to service_role;
