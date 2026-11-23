-- Create/join are the only operations allowed to assign membership. RLS
-- checks rows; column grants keep identity, household and role out of PATCH.
revoke all on public.households, public.members, public.bills, public.chores
    from public, anon, authenticated;
grant select on public.households, public.members to authenticated;
grant update (name, quiet_start_minutes, quiet_end_minutes,
    geofence_radius_meters, home_latitude, home_longitude,
    campus_latitude, campus_longitude, campus_radius_meters)
    on public.households to authenticated;
grant update (name, status, fcm_token) on public.members to authenticated;
grant select, insert, update on public.bills, public.chores to authenticated;
