-- The Edge Function reads the private webhook secret with the service role.
-- Table grants are separate from RLS, so grant the read explicitly.
grant select on public.app_config to service_role;
