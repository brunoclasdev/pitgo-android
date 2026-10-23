begin;
create function private.snapshot_central_pittec_pricing() returns trigger
language plpgsql security definer set search_path='' as $$
declare t public.pittec_service_pricing%rowtype; r public.pittec_pricing_rules%rowtype; d numeric; base numeric; markup numeric; fee numeric;
begin
 if not private.is_pittec_service(new.service_id) then return new; end if;
 if new.service_pricing_id is not null then select * into t from public.pittec_service_pricing where id=new.service_pricing_id;
 else select * into t from public.pittec_service_pricing where service_id=new.service_id and active and effective_from<=now(); end if;
 select * into r from public.pittec_pricing_rules where active and effective_from<=now() and (effective_until is null or effective_until>now()) order by version desc limit 1;
 if t.id is null or r.id is null then raise exception using errcode='22023',message='PITTEC_PRICING_UNAVAILABLE'; end if;
 select distance_meters into d from private.dispatch_candidates where id=new.dispatch_candidate_id;
 if d is null or d<0 then raise exception using errcode='22023',message='INVALID_LOCATION'; end if;
 base:=t.price+round(d/1000*r.distance_rate_per_km,2); markup:=round(base*r.markup_percent/100,2); fee:=round(base*r.fee_percent/100+r.fee_fixed_amount,2);
 new.service_pricing_id:=t.id; new.service_duration_minutes:=t.duration_minutes;
 new.service_amount:=t.price; new.distance_meters:=d; new.distance_rate_per_km:=r.distance_rate_per_km;
 new.distance_charge_amount:=round(d/1000*r.distance_rate_per_km,2); new.provider_amount:=base;
 new.quoted_price:=base; new.is_free:=false; new.markup_amount:=markup; new.customer_fee_amount:=fee;
 new.customer_total_amount:=base+markup+fee; new.payment_amount:=base+markup+fee; new.payment_test_mode:=false;
 new.pricing_currency:='BRL'; new.pricing_rule_id:=r.id; new.pricing_rule_version:=r.version;
 new.quote_expires_at:=now()+interval '15 minutes';
 return new;
end $$;
revoke all on function private.snapshot_central_pittec_pricing() from public,anon,authenticated;
create trigger snapshot_central_pittec_pricing before insert on public.appointments
 for each row execute function private.snapshot_central_pittec_pricing();
create or replace function private.advance_pittec_dispatch(p_search_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  search_row public.service_searches%rowtype;
  chosen record;
  radius integer;
  next_sequence integer;
  candidate_uuid uuid;
  appointment_uuid uuid;
begin
  select search.* into search_row
  from public.service_searches as search where search.id = p_search_id for update;
  if search_row.id is null or not private.is_pittec_service(search_row.service_id) then
    raise exception using errcode = 'P0002', message = 'PITTEC_SEARCH_UNAVAILABLE';
  end if;
  if search_row.status <> 'SEARCHING' then
    return jsonb_build_object('outcome','UNCHANGED');
  end if;
  if search_row.search_point is null then
    raise exception using errcode = '22023', message = 'INVALID_LOCATION';
  end if;

  if not exists(select 1 from public.pittec_service_pricing where service_id=search_row.service_id and active and effective_from<=now()) then
    raise exception using errcode='22023',message='PITTEC_PRICING_UNAVAILABLE';
  end if;
  radius := greatest(10000, search_row.current_radius_m);
  loop
    select
      provider.id as provider_id,
      offer.id as provider_service_id,
      provider.name as provider_name,
      coalesce(
        nullif(concat_ws(' - ',nullif(btrim(client_address.city),''),nullif(btrim(client_address.state),'')),''),
        case radius when 10000 then 'Região de até 10 km'
          when 25000 then 'Região de até 25 km' else 'Região de até 50 km' end
      ) as locality,
      extensions.st_distance(location.position, search_row.search_point) as distance_meters,
      tariff.price, false as is_free, tariff.id as tariff_id
    into chosen
    from public.provider_services as offer
    join public.pittec_service_pricing tariff on tariff.service_id=offer.service_id and tariff.active and tariff.effective_from<=now()
    join public.providers as provider on provider.id = offer.provider_id
    join public.account_roles as role
      on role.user_id = provider.account_id and role.account_type = 'PROVIDER'
    join public.provider_discovery_locations as location on location.provider_id = provider.id
    left join public.client_addresses as client_address on client_address.client_id = search_row.client_id
    where offer.service_id = search_row.service_id and offer.active
      and provider.active and provider.available and provider.location_mode = 'PITTEC'
      and location.published_at >= now() - interval '30 seconds'
      and extensions.st_dwithin(location.position, search_row.search_point, radius)
      and not exists (
        select 1 from private.dispatch_candidates as prior
        where prior.search_id = search_row.id and prior.provider_id = provider.id
      )
      and not exists (
        select 1 from public.appointments as active_appointment
        where active_appointment.provider_id = provider.id
          and active_appointment.status in (
            'PENDING','AWAITING_CLIENT_CONFIRMATION','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS'
          )
      )
    order by extensions.st_distance(location.position, search_row.search_point), provider.id
    limit 1;

    if chosen.provider_id is null then
      if radius = 10000 then radius := 25000; continue; end if;
      if radius = 25000 then radius := 50000; continue; end if;
      update public.service_searches
      set status = 'SEARCHING', current_radius_m = 50000,
          current_candidate_id = null, current_appointment_id = null,
          next_dispatch_at = now() + interval '5 seconds',
          last_event = 'SEARCH_CONTINUES', last_event_at = now(),
          revision = revision + 1, updated_at = now(), ended_at = null
      where id = search_row.id;
      return jsonb_build_object('outcome','SEARCH_CONTINUES');
    end if;

    select coalesce(max(candidate.sequence),0) + 1 into next_sequence
    from private.dispatch_candidates as candidate where candidate.search_id = search_row.id;
    begin
      insert into private.dispatch_candidates(
        search_id,provider_id,provider_service_id,sequence,status,provider_name,
        locality,distance_meters,quoted_price,is_free,decided_at
      ) values (
        search_row.id,chosen.provider_id,chosen.provider_service_id,next_sequence,'REQUESTED',
        chosen.provider_name,chosen.locality,chosen.distance_meters,chosen.price,chosen.is_free,now()
      ) returning id into candidate_uuid;

      insert into public.appointments(
        client_id,provider_id,status,dispatch_search_id,dispatch_candidate_id,
        service_id,provider_service_id,quoted_price,is_free,response_deadline,revision,quote_version,service_pricing_id
      ) values (
        search_row.client_id,chosen.provider_id,'PENDING',search_row.id,candidate_uuid,
        search_row.service_id,chosen.provider_service_id,chosen.price,chosen.is_free,
        now() + interval '60 seconds',1,1,chosen.tariff_id
      ) returning id into appointment_uuid;
    exception when unique_violation then
      update private.dispatch_candidates
      set status = 'INVALIDATED', decided_at = now()
      where id = candidate_uuid;
      continue;
    end;

    update public.service_searches
    set status = 'AWAITING_PROVIDER', current_radius_m = radius,
        current_candidate_id = candidate_uuid, current_appointment_id = appointment_uuid,
        next_dispatch_at = null, revision = revision + 1, updated_at = now()
    where id = search_row.id;
    return jsonb_build_object('outcome','OFFERED','appointment_id',appointment_uuid);
  end loop;
end;
$$;
create or replace function public.respond_to_mobile_dispatch(p_appointment_id uuid,p_accept boolean,p_expected_revision bigint)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare a public.appointments%rowtype; s public.service_searches%rowtype; owned uuid; kind text; p jsonb; service_price numeric; distance numeric;
begin
  if not private.dispatch_is_provider() then raise exception using errcode='42501',message='PROVIDER_REQUIRED'; end if;
  select id into owned from public.providers where account_id=auth.uid();
  select * into a from public.appointments where id=p_appointment_id and provider_id=owned for update;
  if a.id is null then raise exception using errcode='P0002',message='REQUEST_UNAVAILABLE'; end if;
  select * into s from public.service_searches where id=a.dispatch_search_id for update;
  select dispatch_kind into kind from public.services where id=a.service_id;
  if a.status='PENDING' and a.response_deadline<=now() then perform private.expire_mobile_dispatch_appointment(a.id); return jsonb_build_object('outcome','OFFER_EXPIRED'); end if;
  if a.revision<>p_expected_revision then return jsonb_build_object('outcome','REVISION_CONFLICT','snapshot',case when kind='PITTEC_AUTO_SEQUENTIAL' then private.pittec_dispatch_snapshot(s.id) else private.dispatch_snapshot(s.id) end); end if;
  if a.status<>'PENDING' or s.current_appointment_id is distinct from a.id then return jsonb_build_object('outcome','OFFER_UNAVAILABLE'); end if;
  if p_accept and kind='PITTEC_AUTO_SEQUENTIAL' then
    select price into service_price from public.provider_services where id=a.provider_service_id and active;
    select distance_meters into distance from private.dispatch_candidates where id=a.dispatch_candidate_id;
    if a.service_pricing_id is null then
      p := private.pittec_calculate_pricing(service_price,distance);
    else
      p := to_jsonb(a) || jsonb_build_object('currency',a.pricing_currency,'rule_id',a.pricing_rule_id,'rule_version',a.pricing_rule_version);
    end if;
    update public.appointments set service_amount=(p->>'service_amount')::numeric,distance_meters=(p->>'distance_meters')::numeric,distance_rate_per_km=(p->>'distance_rate_per_km')::numeric,distance_charge_amount=(p->>'distance_charge_amount')::numeric,payment_amount=(p->>'payment_amount')::numeric,payment_test_mode=(p->>'payment_test_mode')::boolean,quoted_price=(p->>'provider_amount')::numeric,provider_amount=(p->>'provider_amount')::numeric,markup_amount=(p->>'markup_amount')::numeric,customer_fee_amount=(p->>'customer_fee_amount')::numeric,customer_total_amount=(p->>'customer_total_amount')::numeric,pricing_currency=p->>'currency',pricing_rule_id=(p->>'rule_id')::uuid,pricing_rule_version=(p->>'rule_version')::bigint,quote_expires_at=(p->>'quote_expires_at')::timestamptz,status='AWAITING_CLIENT_CONFIRMATION',accepted_at=now(),client_confirmation_deadline=(p->>'quote_expires_at')::timestamptz,revision=revision+1,updated_at=now() where id=a.id;
    update private.dispatch_candidates set status='ACCEPTED',decided_at=now() where id=a.dispatch_candidate_id;
    update public.service_searches set status='AWAITING_CLIENT_CONFIRMATION',next_dispatch_at=null,revision=revision+1,updated_at=now() where id=s.id;
    return private.pittec_dispatch_snapshot(s.id);
  end if;
  if p_accept then update public.appointments set status='ACCEPTED',accepted_at=now(),service_deadline_at=now()+interval '4 hours',revision=revision+1,updated_at=now() where id=a.id; update public.service_searches set status='ACCEPTED',revision=revision+1,updated_at=now() where id=s.id; update private.dispatch_candidates set status='ACCEPTED',decided_at=now() where id=a.dispatch_candidate_id; return case when kind='PITTEC_AUTO_SEQUENTIAL' then private.pittec_dispatch_snapshot(s.id) else private.dispatch_snapshot(s.id) end; end if;
  update public.appointments set status='PROVIDER_REJECTED',ended_at=now(),revision=revision+1,updated_at=now() where id=a.id; perform private.apply_pending_provider_mode(a.provider_id); update private.dispatch_candidates set status='PROVIDER_REJECTED',decided_at=now() where id=a.dispatch_candidate_id; update public.service_searches set status='SEARCHING',current_appointment_id=null,current_candidate_id=null,next_dispatch_at=case when kind='PITTEC_AUTO_SEQUENTIAL' then now() else null end,revision=revision+1,updated_at=now() where id=s.id; if kind='PITTEC_AUTO_SEQUENTIAL' then perform private.advance_pittec_dispatch(s.id); return jsonb_build_object('outcome','DECLINED'); end if; perform private.advance_mobile_dispatch(s.id); return jsonb_build_object('outcome','NONE');
end; $$;
alter function private.pittec_dispatch_snapshot(uuid) rename to pittec_dispatch_snapshot_before_central_pricing;
create function private.pittec_dispatch_snapshot(p_search_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare result jsonb; a public.appointments%rowtype; v bigint;
begin
 result:=private.pittec_dispatch_snapshot_before_central_pricing(p_search_id);
 select ap.* into a from public.appointments ap join public.service_searches ss on ss.current_appointment_id=ap.id where ss.id=p_search_id;
 select version into v from public.pittec_service_pricing where id=a.service_pricing_id;
 if result#>'{appointment,quote}' is not null and result#>'{appointment,quote}'<>'null'::jsonb then
  result:=jsonb_set(result,'{appointment,quote}',(result#>'{appointment,quote}')||jsonb_strip_nulls(jsonb_build_object('duration_minutes',a.service_duration_minutes,'service_pricing_version',v)));
 end if;
 return result;
end $$;
revoke all on function private.pittec_dispatch_snapshot(uuid),private.pittec_dispatch_snapshot_before_central_pricing(uuid) from public,anon,authenticated;
create or replace function public.list_home_services()
returns table(
  service_id uuid,
  name text,
  description text,
  category_name text,
  minimum_price numeric,
  dispatch_kind text
) language sql stable security definer set search_path = '' as $$
  select service.id, service.name, service.description, category.name,
    case service.dispatch_kind
      when 'PITTEC_AUTO_SEQUENTIAL' then (select tariff.price from public.pittec_service_pricing tariff where tariff.service_id=service.id and tariff.active and tariff.effective_from<=now())
      when 'MOBILE_SEQUENTIAL' then (select rule.base_amount from public.guincho_pricing_rules rule where rule.active and rule.effective_from<=now() and (rule.effective_until is null or rule.effective_until>now()))
      else min(offer.price) filter (where offer.active) end, service.dispatch_kind
  from public.services as service
  join public.service_categories as category on category.id = service.category_id
  join public.provider_services as offer on offer.service_id = service.id and offer.active
  join public.providers as provider on provider.id = offer.provider_id and provider.active
  where service.active and category.active
    and auth.uid() is not null and private.has_account_type('CLIENT')
  group by service.id, service.name, service.description, category.name, service.dispatch_kind
  order by service.name;
$$;
commit;
