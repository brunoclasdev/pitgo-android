-- Forward fix for the canonical address column reconciled by migration 009.
-- Keep the applied 20260823020000 migration immutable.
create or replace function private.advance_mobile_dispatch(p_search_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare s public.service_searches%rowtype;
declare chosen record;
declare next_sequence integer;
declare candidate_id uuid;
declare radius integer;
begin
  select * into s from public.service_searches where id=p_search_id for update;
  if s.id is null then raise exception using errcode='P0002',message='Search unavailable'; end if;
  if s.search_point is null then raise exception using errcode='22023',message='Search location unavailable'; end if;
  radius:=s.current_radius_m;
  loop
    select p.id provider_id,ps.id provider_service_id,p.name provider_name,
      concat_ws(' - ',nullif(btrim(ad.neighborhood),''),nullif(btrim(ad.city),''),nullif(btrim(ad.state),'')) locality,
      extensions.st_distance(l.position,s.search_point) distance_meters,ps.price,ps.is_free
    into chosen
    from public.provider_services ps
    join public.providers p on p.id=ps.provider_id
    join public.account_roles ar on ar.user_id=p.account_id and ar.account_type='PROVIDER'
    join public.provider_discovery_locations l on l.provider_id=p.id
    left join public.addresses ad on ad.provider_id=p.id
    where ps.service_id=s.service_id and ps.active and p.active and p.available
      and p.location_mode='MOBILE' and l.published_at>=now()-interval '30 seconds'
      and extensions.st_dwithin(l.position,s.search_point,radius)
      and not exists(select 1 from private.dispatch_candidates dc where dc.search_id=s.id and dc.provider_id=p.id)
      and not exists(select 1 from public.appointments ap where ap.provider_id=p.id and ap.status in ('PENDING','ACCEPTED','IN_PROGRESS'))
    order by extensions.st_distance(l.position,s.search_point),p.id limit 1;
    exit when chosen.provider_id is not null or radius=50000;
    radius:=case radius when 10000 then 25000 else 50000 end;
  end loop;
  if chosen.provider_id is null then
    update public.service_searches set status='EXHAUSTED',current_radius_m=50000,current_candidate_id=null,
      search_latitude=null,search_longitude=null,search_point=null,search_accuracy_m=null,
      revision=revision+1,updated_at=now(),ended_at=now() where id=s.id;
    return jsonb_build_object('outcome','EXHAUSTED');
  end if;
  select coalesce(max(sequence),0)+1 into next_sequence from private.dispatch_candidates where search_id=s.id;
  insert into private.dispatch_candidates(search_id,provider_id,provider_service_id,sequence,
    provider_name,locality,distance_meters,quoted_price,is_free)
  values(s.id,chosen.provider_id,chosen.provider_service_id,next_sequence,chosen.provider_name,
    coalesce(nullif(chosen.locality,''),'Localidade não informada'),chosen.distance_meters,
    chosen.price,chosen.is_free) returning id into candidate_id;
  update public.service_searches set status='OPTION_FOUND',current_radius_m=radius,
    current_candidate_id=candidate_id,revision=revision+1,updated_at=now() where id=s.id;
  return jsonb_build_object('outcome','ADVANCED');
end;
$$;

revoke all on function private.advance_mobile_dispatch(uuid) from public,anon,authenticated;
notify pgrst,'reload schema';
