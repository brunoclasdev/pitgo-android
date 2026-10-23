begin;
create table public.pittec_service_pricing (
 id uuid primary key default gen_random_uuid(), service_id uuid not null references public.services(id),
 version bigint generated always as identity unique, request_id uuid not null unique,
 price numeric(12,2) not null check(price>0), duration_minutes integer not null check(duration_minutes between 1 and 10080),
 currency text not null default 'BRL' check(currency='BRL'), active boolean not null default true,
 effective_from timestamptz not null default now(), created_at timestamptz not null default now()
);
create unique index pittec_service_pricing_one_active on public.pittec_service_pricing(service_id) where active;
alter table public.pittec_service_pricing enable row level security;
revoke all on public.pittec_service_pricing from public,anon,authenticated;
alter table public.guincho_pricing_rules add column base_amount numeric(12,2) check(base_amount>=0),
 add column duration_minutes integer check(duration_minutes between 1 and 10080),
 add column guincheiro_price numeric(12,2) check(guincheiro_price>=0), add column request_id uuid unique;
do $$ declare c record; begin
 for c in select conname from pg_constraint where conrelid='public.guincho_pricing_rules'::regclass
   and contype='c' and pg_get_constraintdef(oid) like '%markup_percent%' loop
   execute format('alter table public.guincho_pricing_rules drop constraint %I',c.conname);
 end loop;
end $$;
alter table public.guincho_pricing_rules add constraint guincho_central_amounts_valid check(
 distance_rate_per_km>=0 and markup_percent between 0 and 100 and fee_percent between 0 and 100 and fee_fixed_amount>=0);
alter table public.appointments add column service_pricing_id uuid references public.pittec_service_pricing(id),
 add column service_duration_minutes integer check(service_duration_minutes between 1 and 10080);
alter table public.guincho_quotes add column duration_minutes integer check(duration_minutes between 1 and 10080);

create function public.set_dashboard_pittec_pricing(p_service_id uuid,p_request_id uuid,p_price numeric,p_duration_minutes integer)
returns jsonb language plpgsql security definer set search_path='' as $$
declare r public.pittec_service_pricing%rowtype;
begin
 if not coalesce(private.dashboard_access_at_least('MANAGER'),false) then raise exception using errcode='42501',message='STAFF_REQUIRED'; end if;
 if p_request_id is null or p_price is null or p_price<=0 or p_price>9999999999.99 or p_duration_minutes is null or p_duration_minutes not between 1 and 10080 then
 raise exception using errcode='22023',message='INVALID_PRICING'; end if;
 if not exists(select 1 from public.services where id=p_service_id and active and dispatch_kind='PITTEC_AUTO_SEQUENTIAL') then
 raise exception using errcode='P0002',message='SERVICE_NOT_FOUND'; end if;
 perform pg_advisory_xact_lock(hashtextextended('pittec-price:'||p_service_id::text,3510));
 select * into r from public.pittec_service_pricing where request_id=p_request_id;
 if r.id is not null then
  if r.service_id<>p_service_id or r.price<>round(p_price,2) or r.duration_minutes<>p_duration_minutes then raise exception using errcode='22023',message='IDEMPOTENCY_CONFLICT'; end if;
  return to_jsonb(r);
 end if;
 update public.pittec_service_pricing set active=false where service_id=p_service_id and active;
 insert into public.pittec_service_pricing(service_id,request_id,price,duration_minutes)
 values(p_service_id,p_request_id,round(p_price,2),p_duration_minutes) returning * into r;
 perform public.record_dashboard_action('SET_PRICING','PITTEC_SERVICE_PRICING',r.id);
 return to_jsonb(r);
end $$;
create function public.set_dashboard_guincho_pricing(p_request_id uuid,p_config jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare r public.guincho_pricing_rules%rowtype; b numeric; d integer; rate numeric; m numeric; f numeric; fixed numeric; driver numeric;
begin
 if not coalesce(private.dashboard_access_at_least('MANAGER'),false) then raise exception using errcode='42501',message='STAFF_REQUIRED'; end if;
 b:=(p_config->>'base_amount')::numeric; d:=(p_config->>'duration_minutes')::integer;
 rate:=(p_config->>'distance_rate_per_km')::numeric; m:=(p_config->>'markup_percent')::numeric;
 f:=(p_config->>'fee_percent')::numeric; fixed:=(p_config->>'fee_fixed_amount')::numeric; driver:=(p_config->>'guincheiro_price')::numeric;
 if p_request_id is null or b is null or b<0 or b>9999999999.99 or d is null or d not between 1 and 10080
 or rate is null or rate not between 0 and 99999999.99 or m is null or m not between 0 and 100 or f is null or f not between 0 and 100
 or fixed is null or fixed not between 0 and 99999999.99 or driver is null or driver not between 0 and 9999999999.99 then raise exception using errcode='22023',message='INVALID_PRICING'; end if;
 perform pg_advisory_xact_lock(hashtextextended('guincho-price',3510));
 select * into r from public.guincho_pricing_rules where request_id=p_request_id;
 if r.id is not null then
  if (r.base_amount,r.duration_minutes,r.distance_rate_per_km,r.markup_percent,r.fee_percent,r.fee_fixed_amount,r.guincheiro_price)
   is distinct from (round(b,2),d,round(rate,2),round(m,4),round(f,4),round(fixed,2),round(driver,2)) then raise exception using errcode='22023',message='IDEMPOTENCY_CONFLICT'; end if;
  return to_jsonb(r);
 end if;
 update public.guincho_pricing_rules set active=false where active;
 insert into public.guincho_pricing_rules(request_id,base_amount,duration_minutes,distance_rate_per_km,markup_percent,fee_percent,fee_fixed_amount,guincheiro_price)
 values(p_request_id,b,d,rate,m,f,fixed,driver) returning * into r;
 perform public.record_dashboard_action('SET_PRICING','GUINCHO_PRICING',r.id);
 return to_jsonb(r);
end $$;
create function public.get_current_service_pricing(p_service_id uuid default null) returns jsonb
language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
 return jsonb_build_object('pittec',coalesce((select jsonb_agg(jsonb_build_object('id',r.id,'service_id',r.service_id,'version',r.version,'price',r.price,'duration_minutes',r.duration_minutes,'currency',r.currency)) from public.pittec_service_pricing r
 where active and effective_from<=now() and (p_service_id is null or service_id=p_service_id)),'[]'::jsonb),
 'guincho',(select jsonb_build_object('id',r.id,'version',r.version,'base_amount',r.base_amount,'duration_minutes',r.duration_minutes,'distance_rate_per_km',r.distance_rate_per_km,'currency',r.currency) from public.guincho_pricing_rules r where active and base_amount is not null
 and duration_minutes is not null and effective_from<=now() and (effective_until is null or effective_until>now()) limit 1));
end $$;
revoke all on function public.set_dashboard_pittec_pricing(uuid,uuid,numeric,integer),public.set_dashboard_guincho_pricing(uuid,jsonb),public.get_current_service_pricing(uuid) from public,anon;
grant execute on function public.set_dashboard_pittec_pricing(uuid,uuid,numeric,integer),public.set_dashboard_guincho_pricing(uuid,jsonb),public.get_current_service_pricing(uuid) to authenticated;
create function public.get_dashboard_provider_pricing(p_service_id uuid default null) returns jsonb
language plpgsql stable security definer set search_path='' as $$
begin
 if not coalesce(private.dashboard_access_at_least('MANAGER'),false) then raise exception using errcode='42501',message='STAFF_REQUIRED'; end if;
 return jsonb_build_object('pittec',coalesce((select jsonb_agg(to_jsonb(r)) from
  (select * from public.pittec_service_pricing where p_service_id is null or service_id=p_service_id order by version desc limit 100) r),'[]'::jsonb),
 'guincho',coalesce((select jsonb_agg(to_jsonb(r)) from (select * from public.guincho_pricing_rules order by version desc limit 100) r),'[]'::jsonb));
end $$;
revoke all on function public.get_dashboard_provider_pricing(uuid) from public,anon;
grant execute on function public.get_dashboard_provider_pricing(uuid) to authenticated;
commit;
