begin;
create or replace function public.store_guincho_quote(p_client_id uuid,p_draft_id uuid,p_input_revision bigint,
 p_provider_id uuid,p_distance_meters numeric) returns jsonb
language plpgsql security definer set search_path='' as $$
declare d public.guincho_trip_drafts%rowtype; r public.guincho_pricing_rules%rowtype;
 o public.provider_services%rowtype; q public.guincho_quotes%rowtype; provider_name text; pv public.provider_vehicles%rowtype; sp public.guincho_size_prices%rowtype; cat uuid;
 distance_charge numeric(10,2); remuneration numeric(10,2); markup numeric(10,2); fee numeric(10,2);
begin
 perform pg_advisory_xact_lock(hashtextextended(p_client_id::text,2801));
 select * into d from public.guincho_trip_drafts where id=p_draft_id and client_id=p_client_id for update;
 if d.id is null then raise exception using errcode='42501',message='DRAFT_UNAVAILABLE'; end if;
 perform private.lock_guincho_transport(d.id,p_provider_id);
 if not private.guincho_transport_eligible(d.id,p_provider_id) then raise exception using errcode='22023',message='INCOMPATIBLE_VEHICLE'; end if;
 if d.revision is distinct from p_input_revision then raise exception using errcode='40001',message='QUOTE_CONFLICT'; end if;
 if p_distance_meters is null or not (p_distance_meters between 0 and 2000000) then
 raise exception using errcode='22023',message='ROUTE_UNAVAILABLE'; end if;
 select ps.* into o from public.provider_services ps join public.providers p on p.id=ps.provider_id
 join public.services s on s.id=ps.service_id join public.service_categories c on c.id=s.category_id
 join public.provider_discovery_locations l on l.provider_id=p.id
 where p.id=p_provider_id and p.active and p.available and p.location_mode='MOBILE'
 and ps.active and s.active and c.active and s.dispatch_kind='MOBILE_SEQUENTIAL'
 and l.published_at>=now()-interval '30 seconds'
 and extensions.st_dwithin(l.position,extensions.st_setsrid(extensions.st_makepoint(d.pickup_longitude,d.pickup_latitude),4326)::extensions.geography,25000)
 and not exists(select 1 from public.appointments a where a.provider_id=p.id
 and a.status in ('PENDING','AWAITING_PAYMENT','AWAITING_CLIENT_CONFIRMATION','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION'))
 order by ps.id limit 1;
 if o.id is null then raise exception using errcode='22023',message='PROVIDER_UNAVAILABLE'; end if;
 select * into r from public.guincho_pricing_rules where active and effective_from<=now()
 and (effective_until is null or effective_until>now()) order by version desc limit 1;
 if r.id is null or r.duration_minutes is null then raise exception using errcode='22023',message='PRICING_UNAVAILABLE'; end if;
 select v.* into pv from public.provider_vehicles v join public.providers p on p.dispatch_vehicle_id=v.id and p.id=v.provider_id where p.id=p_provider_id for share of v;
 select transport_category_id into cat from public.vehicles where id=d.vehicle_id and user_id=p_client_id;
 select * into sp from public.guincho_size_prices where size_id=pv.guincho_size_id and active and effective_from<=now() and (effective_until is null or effective_until>now()) for share;
 if sp.id is null then raise exception using errcode='22023',message='PRICING_UNAVAILABLE'; end if;
 distance_charge:=round(p_distance_meters/1000*r.distance_rate_per_km,2);
 remuneration:=sp.base_amount+distance_charge;
 markup:=round(remuneration*r.markup_percent/100,2);
 fee:=round(remuneration*r.fee_percent/100+r.fee_fixed_amount,2);
 insert into public.guincho_quotes(draft_id,client_id,provider_id,provider_service_id,input_revision,
 pricing_rule_id,pricing_rule_version,base_amount,distance_meters,distance_rate_per_km,distance_charge_amount,
 provider_amount,markup_amount,fee_amount,customer_total_amount,duration_minutes,dispatch_vehicle_id,guincho_size_id,transport_category_id,size_price_id,size_price_version)
 values(d.id,d.client_id,p_provider_id,o.id,d.revision,r.id,r.version,sp.base_amount,p_distance_meters,
 r.distance_rate_per_km,distance_charge,remuneration,markup,fee,remuneration+markup+fee,r.duration_minutes,pv.id,pv.guincho_size_id,cat,sp.id,sp.version) returning * into q;
 update public.guincho_trip_drafts set vehicle_snapshot=vehicle_snapshot||jsonb_build_object('transport_category_id',cat),route_distance_meters=p_distance_meters,route_input_revision=d.revision,
 route_calculated_at=now() where id=d.id;
 select name into provider_name from public.providers where id=p_provider_id;
 return jsonb_build_object('quote_id',q.id,'version',q.version,'provider_id',q.provider_id,
 'provider_name',provider_name,'dispatch_vehicle_id',pv.id,'size_id',pv.guincho_size_id,'size_name',(select name from public.guincho_sizes where id=pv.guincho_size_id),'size_price_version',sp.version,'customer_total_amount',q.customer_total_amount::text,
 'duration_minutes',q.duration_minutes,'pricing_rule_version',q.pricing_rule_version,'currency',q.currency,'expires_at',q.expires_at);
end; $$;

do $$ declare definition text; begin
 definition:=pg_get_functiondef('private.discover_guincho_providers_before_ratings(uuid,bigint,integer)'::regprocedure);
 definition:=replace(definition,'where private.guincho_transport_eligible(d.id,p.id)', 'where private.guincho_transport_eligible(d.id,p.id) and exists(select 1 from public.provider_vehicles vehicle join public.guincho_size_prices price on price.size_id=vehicle.guincho_size_id and price.active and price.effective_from<=now() and (price.effective_until is null or price.effective_until>now()) where vehicle.id=p.dispatch_vehicle_id)');
 execute definition;
end $$;
commit;
