begin;
-- Mobile callers can distinguish per-size pricing from the retired global base.
create or replace function public.get_current_service_pricing(p_service_id uuid default null) returns jsonb language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
 return jsonb_build_object('pittec',coalesce((select jsonb_agg(jsonb_build_object('id',r.id,'service_id',r.service_id,'version',r.version,'price',r.price,'duration_minutes',r.duration_minutes,'currency',r.currency)) from public.pittec_service_pricing r where active and effective_from<=now() and (p_service_id is null or service_id=p_service_id)),'[]'::jsonb),
 'guincho',null,'guincho_pricing_mode','PER_SIZE','guincho_sizes',coalesce((select jsonb_agg(jsonb_build_object('size_id',p.size_id,'version',p.version,'base_amount',p.base_amount,'currency',p.currency)) from public.guincho_size_prices p join public.guincho_sizes s on s.id=p.size_id and s.active where p.active and p.effective_from<=now() and (p.effective_until is null or p.effective_until>now())),'[]'::jsonb));
end $$;
drop function public.discover_guincho_providers(uuid,bigint,integer);
create function public.discover_guincho_providers(p_draft_id uuid,p_expected_revision bigint,p_limit integer default 200)
returns table(provider_id uuid,provider_service_id uuid,display_name text,latitude double precision,longitude double precision,distance_meters double precision,rating_summary jsonb,dispatch_vehicle_id uuid,size_id uuid,size_name text)
language sql stable security definer set search_path='' as $$
 select candidate.*,private.provider_rating_summary(candidate.provider_id),vehicle.id,size.id,size.name
 from private.discover_guincho_providers_before_ratings(p_draft_id,p_expected_revision,p_limit) candidate
 join public.providers provider on provider.id=candidate.provider_id
 join public.provider_vehicles vehicle on vehicle.id=provider.dispatch_vehicle_id and vehicle.provider_id=provider.id
 join public.guincho_sizes size on size.id=vehicle.guincho_size_id;
$$;
revoke all on function public.discover_guincho_providers(uuid,bigint,integer) from public,anon;
grant execute on function public.discover_guincho_providers(uuid,bigint,integer) to authenticated;
create or replace function public.get_dashboard_provider_pricing(p_service_id uuid default null) returns jsonb
language plpgsql stable security definer set search_path='' as $$
begin
 if not coalesce(private.dashboard_access_at_least('MANAGER'),false) then raise exception using errcode='42501',message='STAFF_REQUIRED'; end if;
 return jsonb_build_object('pittec',coalesce((select jsonb_agg(to_jsonb(r)) from
  (select * from public.pittec_service_pricing where p_service_id is null or service_id=p_service_id order by version desc limit 100) r),'[]'::jsonb),
 'guincho',coalesce((select jsonb_agg(to_jsonb(r)) from (select * from public.guincho_pricing_rules order by version desc limit 100) r),'[]'::jsonb),'guincho_size_prices',coalesce((select jsonb_agg(to_jsonb(price)) from (select * from public.guincho_size_prices order by version desc limit 100) price),'[]'::jsonb));
end $$;

notify pgrst,'reload schema';
commit;
