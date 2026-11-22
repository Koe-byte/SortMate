-- SortMate cloud schema.
-- Room stays the on-device source of truth; Postgres is the multi-device transport.
-- Column names are snake_case to match PostgREST and the Kotlin DTOs.

create table if not exists public.households (
    id uuid primary key default gen_random_uuid(),
    name text not null check (char_length(btrim(name)) between 1 and 80),
    join_code text not null unique check (join_code ~ '^[A-Z2-9]{6}$'),
    quiet_start_minutes integer not null default 1320
        check (quiet_start_minutes between 0 and 1439),
    quiet_end_minutes integer not null default 360
        check (quiet_end_minutes between 0 and 1439),
    geofence_radius_meters real not null default 100
        check (geofence_radius_meters between 50 and 1000),
    home_latitude double precision check (home_latitude between -90 and 90),
    home_longitude double precision check (home_longitude between -180 and 180),
    campus_latitude double precision check (campus_latitude between -90 and 90),
    campus_longitude double precision check (campus_longitude between -180 and 180),
    campus_radius_meters real not null default 100
        check (campus_radius_meters between 50 and 1000),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.members (
    id uuid primary key,
    household_id uuid not null references public.households (id) on delete cascade,
    auth_user_id uuid not null unique references auth.users (id) on delete cascade,
    name text not null check (char_length(btrim(name)) between 1 and 60),
    role text,
    status text not null default 'AWAY'
        check (status in ('HOME', 'CAMPUS', 'AWAY')),
    fcm_token text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.bills (
    id uuid primary key,
    household_id uuid not null references public.households (id) on delete cascade,
    title text not null check (char_length(btrim(title)) between 1 and 120),
    merchant text,
    total_cents bigint not null check (total_cents > 0),
    paid_by_member_id uuid not null references public.members (id) on delete restrict,
    split_among_member_ids uuid[] not null default '{}',
    split_type text not null default 'EQUAL' check (split_type in ('EQUAL')),
    due_at timestamptz,
    created_at timestamptz not null default now(),
    receipt_uri text,
    ocr_confidence integer check (ocr_confidence between 0 and 100),
    is_settled boolean not null default false,
    is_deleted boolean not null default false,
    created_by uuid references auth.users (id) on delete set null,
    updated_by uuid references auth.users (id) on delete set null,
    updated_at timestamptz not null default now()
);

create table if not exists public.chores (
    id uuid primary key,
    household_id uuid not null references public.households (id) on delete cascade,
    title text not null check (char_length(btrim(title)) between 1 and 120),
    assigned_member_id uuid references public.members (id) on delete set null,
    due_at timestamptz,
    is_completed boolean not null default false,
    completed_at timestamptz,
    is_deleted boolean not null default false,
    created_by uuid references auth.users (id) on delete set null,
    updated_by uuid references auth.users (id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists members_household_id_idx on public.members (household_id);
create index if not exists bills_household_id_idx on public.bills (household_id);
create index if not exists bills_due_at_idx on public.bills (due_at);
create index if not exists chores_household_id_idx on public.chores (household_id);
create index if not exists chores_due_at_idx on public.chores (due_at);

comment on table public.households is
    'A shared student household. The 6-character join_code is the invite secret.';
comment on table public.members is
    'A person in a household. id equals auth.uid() so RLS can resolve the caller.';
comment on column public.members.fcm_token is
    'Latest FCM registration token for push notifications.';
comment on column public.bills.split_among_member_ids is
    'Member ids that share the bill. EQUAL split is computed on-device.';
