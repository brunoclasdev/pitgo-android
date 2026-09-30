begin;
create function public.store_guincho_quote(p_client_id uuid,p_draft_id uuid,p_input_revision bigint,
 p_provider_id uuid,p_distance_meters numeric) returns jsonb
language plpgsql security definer set search_path='' as $$
declare d public.guincho_trip_drafts%rowtype; r public.guincho_pricing_rules%rowtype;
 o public.provider_services%rowtype; q public.guincho_quotes%rowtype; provider_name text;
 distance_charge numeric(10,2); remuneration numeric(10,2); markup numeric(10,2); fee numeric(10,2);
begin
 select * into d from public.guincho_trip_drafts where id=p_draft_id and client_id=p_client_id for update;
 if d.id is null then raise exception using errcode='42501',message='DRAFT_UNAVAILABLE'; end if;
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
 if o.id is null or o.price is null or o.price<0 then raise exception using errcode='22023',message='PROVIDER_UNAVAILABLE'; end if;
 select * into r from public.guincho_pricing_rules where active and effective_from<=now()
 and (effective_until is null or effective_until>now()) order by version desc limit 1;
 if r.id is null then raise exception using errcode='22023',message='PRICING_UNAVAILABLE'; end if;
 distance_charge:=round(p_distance_meters/1000*r.distance_rate_per_km,2);
 remuneration:=o.price+distance_charge;
 markup:=round(remuneration*r.markup_percent/100,2);
 fee:=round(remuneration*r.fee_percent/100+r.fee_fixed_amount,2);
 insert into public.guincho_quotes(draft_id,client_id,provider_id,provider_service_id,input_revision,
 pricing_rule_id,pricing_rule_version,base_amount,distance_meters,distance_rate_per_km,distance_charge_amount,
 provider_amount,markup_amount,fee_amount,customer_total_amount)
 values(d.id,d.client_id,p_provider_id,o.id,d.revision,r.id,r.version,o.price,p_distance_meters,
 r.distance_rate_per_km,distance_charge,remuneration,markup,fee,remuneration+markup+fee) returning * into q;
 update public.guincho_trip_drafts set route_distance_meters=p_distance_meters,route_input_revision=d.revision,
 route_calculated_at=now() where id=d.id;
 select name into provider_name from public.providers where id=p_provider_id;
 return jsonb_build_object('quote_id',q.id,'version',q.version,'provider_id',q.provider_id,
 'provider_name',provider_name,'customer_total_amount',q.customer_total_amount::text,
 'currency',q.currency,'expires_at',q.expires_at);
end; $$;
revoke all on function public.store_guincho_quote(uuid,uuid,bigint,uuid,numeric) from public,anon,authenticated;
grant execute on function public.store_guincho_quote(uuid,uuid,bigint,uuid,numeric) to service_role;
commit;
