begin;
create function private.guincho_transport_eligible(p_draft_id uuid,p_provider_id uuid) returns boolean
language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.guincho_trip_drafts d
 join public.vehicles v on v.id=d.vehicle_id and v.user_id=d.client_id and v.active
 join public.vehicle_transport_categories cat on cat.id=v.transport_category_id and cat.active
 join public.providers p on p.id=p_provider_id and p.provider_kind='GUINCHO'
 join public.provider_vehicles pv on pv.id=p.dispatch_vehicle_id and pv.provider_id=p.id
 join public.guincho_sizes sz on sz.id=pv.guincho_size_id and sz.active
 join public.guincho_size_compatibility cmp on cmp.size_id=sz.id and cmp.vehicle_category_id=cat.id
 where d.id=p_draft_id and not exists(select 1 from public.guincho_search_contexts ctx
 join public.guincho_search_exclusions ex on ex.context_id=ctx.id and ex.generation=ctx.generation
 where ctx.client_id=d.client_id and ctx.draft_id=d.id and ctx.state<>'CLOSED' and ex.provider_id=p.id));
$$;
revoke all on function private.guincho_transport_eligible(uuid,uuid) from public,anon,authenticated;
create function private.lock_guincho_transport(p_draft_id uuid,p_provider_id uuid) returns void
language plpgsql security definer set search_path='' as $$
begin
 -- Hold the advertised vehicle and classification stable through quote/reservation commit.
 perform 1 from public.providers where id=p_provider_id for share;
 perform 1 from public.provider_vehicles v join public.providers p on p.dispatch_vehicle_id=v.id and p.id=v.provider_id where p.id=p_provider_id for share of v;
 perform 1 from public.vehicles v join public.guincho_trip_drafts d on d.vehicle_id=v.id where d.id=p_draft_id and v.user_id=d.client_id for share of v;
 perform 1 from public.guincho_sizes s join public.provider_vehicles v on v.guincho_size_id=s.id join public.providers p on p.dispatch_vehicle_id=v.id where p.id=p_provider_id for share of s;
 perform 1 from public.vehicle_transport_categories c join public.vehicles v on v.transport_category_id=c.id join public.guincho_trip_drafts d on d.vehicle_id=v.id where d.id=p_draft_id for share of c;
end $$;
revoke all on function private.lock_guincho_transport(uuid,uuid) from public,anon,authenticated;

do $$ declare definition text; begin
 definition:=pg_get_functiondef('private.discover_guincho_providers_before_ratings(uuid,bigint,integer)'::regprocedure);
 if position('where not exists(select 1 from public.guincho_search_contexts' in definition)=0 then raise exception 'Unexpected discovery exclusion contract'; end if;
 definition:=replace(definition,'where not exists(select 1 from public.guincho_search_contexts','where private.guincho_transport_eligible(d.id,p.id) and not exists(select 1 from public.guincho_search_contexts');
 definition:=replace(definition,'if d.revision is distinct', 'if not exists(select 1 from public.vehicles vehicle join public.vehicle_transport_categories category on category.id=vehicle.transport_category_id and category.active where vehicle.id=d.vehicle_id and vehicle.user_id=d.client_id and vehicle.active) then raise exception using errcode=''22023'',message=''VEHICLE_CATEGORY_REQUIRED''; end if; if d.revision is distinct');
 execute definition;
end $$;
commit;
