-- Triggers, RLS, and the two household RPCs used by the Android client.

-- ---------------------------------------------------------------------------
-- Timestamp + audit helpers
-- ---------------------------------------------------------------------------

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create or replace function public.touch_audit_fields()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
    if tg_op = 'INSERT' then
        new.created_by = coalesce(new.created_by, auth.uid());
        new.created_at = coalesce(new.created_at, now());
    end if;
    new.updated_by = auth.uid();
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists households_touch_updated_at on public.households;
create trigger households_touch_updated_at
    before insert or update on public.households
    for each row execute function public.touch_updated_at();

drop trigger if exists members_touch_updated_at on public.members;
create trigger members_touch_updated_at
    before insert or update on public.members
    for each row execute function public.touch_updated_at();

drop trigger if exists bills_touch_audit_fields on public.bills;
create trigger bills_touch_audit_fields
    before insert or update on public.bills
    for each row execute function public.touch_audit_fields();

drop trigger if exists chores_touch_audit_fields on public.chores;
create trigger chores_touch_audit_fields
    before insert or update on public.chores
    for each row execute function public.touch_audit_fields();

-- ---------------------------------------------------------------------------
-- Membership helper (security definer so RLS can call it without recursion)
-- ---------------------------------------------------------------------------

create or replace function public.is_household_member(hid uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.members
        where household_id = hid
          and auth_user_id = auth.uid()
    );
$$;

revoke all on function public.is_household_member(uuid) from public, anon;
grant execute on function public.is_household_member(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Create / join RPCs
-- ---------------------------------------------------------------------------

create or replace function public.create_household(
    p_id uuid,
    p_name text,
    p_join_code text,
    p_display_name text,
    p_quiet_start integer default 1320,
    p_quiet_end integer default 360
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_household public.households;
begin
    if v_uid is null then
        raise exception 'Sign in before creating a household.'
            using errcode = '28000';
    end if;

    if exists (select 1 from public.members where auth_user_id = v_uid) then
        raise exception 'This account already belongs to a household.'
            using errcode = '23505';
    end if;

    insert into public.households (
        id, name, join_code, quiet_start_minutes, quiet_end_minutes
    )
    values (
        p_id, btrim(p_name), upper(btrim(p_join_code)),
        p_quiet_start, p_quiet_end
    )
    returning * into v_household;

    insert into public.members (
        id, household_id, auth_user_id, name, role, status
    )
    values (
        v_uid, v_household.id, v_uid, btrim(p_display_name), 'Leader', 'HOME'
    );

    return to_jsonb(v_household);
end;
$$;

create or replace function public.join_household(
    p_join_code text,
    p_display_name text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_household public.households;
    v_member public.members;
begin
    if v_uid is null then
        raise exception 'Sign in before joining a household.'
            using errcode = '28000';
    end if;

    select *
    into v_household
    from public.households
    where join_code = upper(btrim(p_join_code));

    if not found then
        raise exception 'No household found for that code.'
            using errcode = 'P0002';
    end if;

    insert into public.members (
        id, household_id, auth_user_id, name, role, status
    )
    values (
        v_uid, v_household.id, v_uid, btrim(p_display_name), 'Roommate', 'HOME'
    )
    on conflict (auth_user_id) do update
        set household_id = excluded.household_id,
            name = excluded.name,
            status = excluded.status,
            updated_at = now()
    returning * into v_member;

    return jsonb_build_object(
        'household', to_jsonb(v_household),
        'member', to_jsonb(v_member)
    );
end;
$$;

revoke all on function public.create_household(uuid, text, text, text, integer, integer)
    from public, anon;
revoke all on function public.join_household(text, text) from public, anon;
grant execute on function public.create_household(uuid, text, text, text, integer, integer)
    to authenticated;
grant execute on function public.join_household(text, text) to authenticated;

-- ---------------------------------------------------------------------------
-- Referential checks that RLS alone cannot express
-- ---------------------------------------------------------------------------

create or replace function public.validate_bill_links()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
    if not exists (
        select 1 from public.members
        where id = new.paid_by_member_id
          and household_id = new.household_id
    ) then
        raise exception 'paid_by_member_id must belong to the bill household.'
            using errcode = '23503';
    end if;

    if exists (
        select 1
        from unnest(new.split_among_member_ids) as split_id
        where not exists (
            select 1 from public.members
            where id = split_id
              and household_id = new.household_id
        )
    ) then
        raise exception 'Every split member must belong to the bill household.'
            using errcode = '23503';
    end if;

    return new;
end;
$$;

drop trigger if exists bills_validate_links on public.bills;
create trigger bills_validate_links
    before insert or update on public.bills
    for each row execute function public.validate_bill_links();

create or replace function public.validate_chore_links()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
    if new.assigned_member_id is not null and not exists (
        select 1 from public.members
        where id = new.assigned_member_id
          and household_id = new.household_id
    ) then
        raise exception 'assigned_member_id must belong to the chore household.'
            using errcode = '23503';
    end if;

    return new;
end;
$$;

drop trigger if exists chores_validate_links on public.chores;
create trigger chores_validate_links
    before insert or update on public.chores
    for each row execute function public.validate_chore_links();

-- ---------------------------------------------------------------------------
-- Row level security
-- ---------------------------------------------------------------------------

alter table public.households enable row level security;
alter table public.members enable row level security;
alter table public.bills enable row level security;
alter table public.chores enable row level security;

drop policy if exists households_select_members on public.households;
create policy households_select_members
    on public.households for select
    to authenticated
    using (public.is_household_member(id));

drop policy if exists households_update_members on public.households;
create policy households_update_members
    on public.households for update
    to authenticated
    using (public.is_household_member(id))
    with check (public.is_household_member(id));

drop policy if exists members_select_household on public.members;
create policy members_select_household
    on public.members for select
    to authenticated
    using (public.is_household_member(household_id));

drop policy if exists members_insert_self on public.members;
create policy members_insert_self
    on public.members for insert
    to authenticated
    with check (id = auth.uid() and auth_user_id = auth.uid());

drop policy if exists members_update_self on public.members;
create policy members_update_self
    on public.members for update
    to authenticated
    using (auth_user_id = auth.uid())
    with check (auth_user_id = auth.uid());

drop policy if exists bills_all_household on public.bills;
create policy bills_all_household
    on public.bills for all
    to authenticated
    using (public.is_household_member(household_id))
    with check (public.is_household_member(household_id));

drop policy if exists chores_all_household on public.chores;
create policy chores_all_household
    on public.chores for all
    to authenticated
    using (public.is_household_member(household_id))
    with check (public.is_household_member(household_id));

grant usage on schema public to authenticated;
grant select, insert, update, delete
    on public.households, public.members, public.bills, public.chores
    to authenticated;
