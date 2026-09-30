begin;

-- Request modality is independent from catalog modality: old mobile orders keep their contract.
alter table public.service_searches add column dispatch_kind text;
update public.service_searches s set dispatch_kind=v.dispatch_kind
from public.services v where v.id=s.service_id;
alter table public.service_searches alter column dispatch_kind set not null;
alter table public.service_searches alter column dispatch_kind set default 'MOBILE_SEQUENTIAL';
alter table public.service_searches add constraint service_searches_dispatch_kind_check
check (dispatch_kind in ('MOBILE_SEQUENTIAL','PITTEC_AUTO_SEQUENTIAL','GUINCHO_DIRECT'));

create function private.set_search_dispatch_kind() returns trigger
language plpgsql security definer set search_path='' as $$
begin
  if new.dispatch_kind <> 'GUINCHO_DIRECT' then
    select dispatch_kind into new.dispatch_kind from public.services where id=new.service_id;
  end if;
  return new;
end; $$;
create trigger set_search_dispatch_kind before insert on public.service_searches
for each row execute function private.set_search_dispatch_kind();
revoke all on function private.set_search_dispatch_kind() from public,anon,authenticated;

create table public.guincho_pricing_rules (
  id uuid primary key default gen_random_uuid(),
  version bigint generated always as identity unique,
  distance_rate_per_km numeric(10,2) not null default 3,
  markup_percent numeric(7,4) not null default 10,
  fee_percent numeric(7,4) not null,
  fee_fixed_amount numeric(10,2) not null,
  currency text not null default 'BRL' check(currency='BRL'),
  effective_from timestamptz not null default now(),
  effective_until timestamptz,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  check(distance_rate_per_km>=0 and markup_percent=10 and fee_percent>=0 and fee_fixed_amount>=0),
  check(effective_until is null or effective_until>effective_from)
);
create unique index guincho_pricing_rules_one_active on public.guincho_pricing_rules(active) where active;

create table public.guincho_trip_drafts (
  id uuid primary key default gen_random_uuid(),
  client_id uuid not null references public.profiles(id),
  vehicle_id uuid not null references public.vehicles(id),
  vehicle_snapshot jsonb not null,
  pickup_address text not null check(char_length(btrim(pickup_address)) between 1 and 500),
  destination_address text not null check(char_length(btrim(destination_address)) between 1 and 500),
  pickup_latitude numeric(9,6) not null check(pickup_latitude between -90 and 90),
  pickup_longitude numeric(10,6) not null check(pickup_longitude between -180 and 180),
  destination_latitude numeric(9,6) not null check(destination_latitude between -90 and 90),
  destination_longitude numeric(10,6) not null check(destination_longitude between -180 and 180),
  revision bigint not null default 1 check(revision>0),
  route_distance_meters numeric(12,3) check(route_distance_meters between 0 and 2000000),
  route_input_revision bigint,
  route_calculated_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check((route_distance_meters is null and route_input_revision is null and route_calculated_at is null)
    or (route_distance_meters is not null and route_input_revision=revision and route_calculated_at is not null))
);
create index guincho_trip_drafts_client on public.guincho_trip_drafts(client_id,updated_at desc);

create table public.guincho_quotes (
  id uuid primary key default gen_random_uuid(),
  version bigint generated always as identity unique,
  draft_id uuid not null references public.guincho_trip_drafts(id),
  client_id uuid not null references public.profiles(id),
  provider_id uuid not null references public.providers(id),
  provider_service_id uuid not null references public.provider_services(id),
  input_revision bigint not null,
  pricing_rule_id uuid not null references public.guincho_pricing_rules(id),
  pricing_rule_version bigint not null,
  base_amount numeric(10,2) not null check(base_amount>=0),
  distance_meters numeric(12,3) not null check(distance_meters between 0 and 2000000),
  distance_rate_per_km numeric(10,2) not null check(distance_rate_per_km>=0),
  distance_charge_amount numeric(10,2) not null check(distance_charge_amount>=0),
  provider_amount numeric(10,2) not null check(provider_amount>=0),
  markup_amount numeric(10,2) not null check(markup_amount>=0),
  fee_amount numeric(10,2) not null check(fee_amount>=0),
  customer_total_amount numeric(10,2) not null check(customer_total_amount>0),
  currency text not null default 'BRL' check(currency='BRL'),
  expires_at timestamptz not null default now()+interval '15 minutes',
  created_at timestamptz not null default now(),
  check(provider_amount=base_amount+distance_charge_amount),
  check(customer_total_amount=provider_amount+markup_amount+fee_amount)
);
create index guincho_quotes_client on public.guincho_quotes(client_id,created_at desc);
alter table public.appointments add column guincho_quote_id uuid references public.guincho_quotes(id);
alter table public.appointments add column guincho_trip_snapshot jsonb;
alter table public.appointments add column guincho_operation_id uuid;
create unique index guincho_appointment_operation on public.appointments(client_id,guincho_operation_id)
where guincho_operation_id is not null;
create unique index guincho_quote_one_appointment on public.appointments(guincho_quote_id)
where guincho_quote_id is not null;
alter table public.appointments add constraint guincho_direct_context check (
 guincho_quote_id is null or (dispatch_candidate_id is null and dispatch_search_id is not null
 and guincho_trip_snapshot is not null and guincho_operation_id is not null));

create table public.appointment_disputes (
  id uuid primary key default gen_random_uuid(),
  appointment_id uuid not null unique references public.appointments(id),
  client_id uuid not null references public.profiles(id),
  reason text not null check(reason=btrim(reason) and char_length(reason) between 1 and 1000),
  status text not null default 'OPEN' check(status in ('OPEN','RESOLVED')),
  revision bigint not null default 1 check(revision>0),
  opened_at timestamptz not null default now(),
  resolution text check(resolution in ('COMPLETE','CANCEL_FULL_REFUND')),
  justification text check(justification=btrim(justification) and char_length(justification) between 1 and 1000),
  resolved_by uuid references public.profiles(id),
  resolved_at timestamptz,
  check((status='OPEN' and resolution is null and justification is null and resolved_by is null and resolved_at is null)
    or (status='RESOLVED' and resolution is not null and justification is not null and resolved_by is not null and resolved_at is not null))
);
create index appointment_disputes_queue on public.appointment_disputes(status,opened_at desc,id);
commit;
