-- Realtime publication, private webhook configuration, and pg_net triggers.
-- The webhook secret is generated in the database, never committed to the repo.
-- The Edge Function reads the same row with the service role and compares it.

create extension if not exists pg_net;

alter table public.members replica identity full;

do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'households'
    ) then
        alter publication supabase_realtime add table public.households;
    end if;

    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'members'
    ) then
        alter publication supabase_realtime add table public.members;
    end if;

    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'bills'
    ) then
        alter publication supabase_realtime add table public.bills;
    end if;

    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'chores'
    ) then
        alter publication supabase_realtime add table public.chores;
    end if;
end
$$;

-- Private key/value store. RLS with no policies means the API cannot read it;
-- the Edge Function uses the service role, which bypasses RLS.
create table if not exists public.app_config (
    key text primary key,
    value text not null
);

alter table public.app_config enable row level security;
revoke all on public.app_config from anon, authenticated;

-- Each installation sets webhook_url to its own deployed function (see the
-- Supabase vault note). Until configured, the trigger safely skips delivery.

insert into public.app_config (key, value)
values (
    'webhook_secret',
    replace(gen_random_uuid()::text, '-', '') ||
    replace(gen_random_uuid()::text, '-', '')
)
on conflict (key) do nothing;

create or replace function public.enqueue_household_webhook()
returns trigger
language plpgsql
security definer
set search_path = public, net
as $$
declare
    v_secret text;
    v_url text;
    v_payload jsonb;
begin
    select value into v_secret from public.app_config where key = 'webhook_secret';
    select value into v_url from public.app_config where key = 'webhook_url';

    if v_secret is null or v_url is null then
        return new;
    end if;

    v_payload := jsonb_build_object(
        'table', tg_table_name,
        'record', to_jsonb(new),
        'old_record', case when tg_op = 'UPDATE' then to_jsonb(old) else null end
    );

    perform net.http_post(
        url := v_url,
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'x-webhook-secret', v_secret
        ),
        body := v_payload,
        timeout_milliseconds := 5000
    );

    return new;
end;
$$;

revoke all on function public.enqueue_household_webhook() from public, anon, authenticated;

drop trigger if exists bills_enqueue_webhook on public.bills;
create trigger bills_enqueue_webhook
    after insert or update on public.bills
    for each row execute function public.enqueue_household_webhook();

drop trigger if exists chores_enqueue_webhook on public.chores;
create trigger chores_enqueue_webhook
    after insert or update on public.chores
    for each row execute function public.enqueue_household_webhook();

drop trigger if exists members_enqueue_webhook on public.members;
create trigger members_enqueue_webhook
    after update on public.members
    for each row execute function public.enqueue_household_webhook();
