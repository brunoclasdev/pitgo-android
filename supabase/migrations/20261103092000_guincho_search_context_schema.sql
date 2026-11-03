begin;
create table public.guincho_search_contexts (
 id uuid primary key default gen_random_uuid(), client_id uuid not null references public.profiles(id),
 draft_id uuid not null references public.guincho_trip_drafts(id), generation bigint not null default 1 check(generation>0),
 state text not null default 'DISCOVERY' check(state in ('DISCOVERY','REQUESTED','CLOSED')),
 appointment_id uuid references public.appointments(id), revision bigint not null default 1, updated_at timestamptz not null default now()
);
create unique index guincho_open_context_client on public.guincho_search_contexts(client_id) where state<>'CLOSED';
create index guincho_context_appointment on public.guincho_search_contexts(appointment_id);
create table public.guincho_search_exclusions (
 context_id uuid not null references public.guincho_search_contexts(id), generation bigint not null,
 provider_id uuid not null references public.providers(id), source_appointment_id uuid not null references public.appointments(id),
 created_at timestamptz not null default now(), primary key(context_id,generation,provider_id)
);
alter table public.guincho_search_contexts enable row level security;
alter table public.guincho_search_exclusions enable row level security;
revoke all on public.guincho_search_contexts,public.guincho_search_exclusions from public,anon,authenticated;
grant select on public.guincho_search_contexts,public.guincho_search_exclusions to authenticated;
create policy own_guincho_context on public.guincho_search_contexts for select to authenticated using(client_id=auth.uid());
create policy own_guincho_exclusion on public.guincho_search_exclusions for select to authenticated using(exists(select 1 from public.guincho_search_contexts c where c.id=context_id and c.client_id=auth.uid()));
commit;
