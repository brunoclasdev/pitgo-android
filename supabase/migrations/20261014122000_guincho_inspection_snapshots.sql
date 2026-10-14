begin;
create function private.inspection_summary(p_id uuid) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare a public.appointments%rowtype; v public.guincho_inspection_versions%rowtype; actions jsonb:='[]'; ready_count integer; total integer; provider boolean;
begin
 select * into a from public.appointments where id=p_id;
 select * into v from public.guincho_inspection_versions where appointment_id=p_id order by version_number desc limit 1;
 select p.account_id=auth.uid() into provider from public.providers p where p.id=a.provider_id;
 select count(*) filter(where point<>'DETAIL'),count(*) into ready_count,total from public.guincho_inspection_version_photos where version_id=v.id and selected;
 if v.id is not null then actions:='["OPEN_INSPECTION"]'; end if;
 if private.inspection_writable(p_id) then
  if provider and v.state='DRAFT' then actions:=actions||'["CAPTURE","SUBMIT"]'::jsonb;
  elsif provider and v.state='CORRECTION_REQUESTED' then actions:=actions||'["BEGIN_CORRECTION"]'::jsonb;
  elsif not provider and a.client_id=auth.uid() and v.state='SUBMITTED' then actions:=actions||'["APPROVE","REQUEST_CORRECTION"]'::jsonb;
  end if;
 end if;
 return jsonb_build_object('required',a.guincho_inspection_required,'state',v.state,'current_version_id',v.id,'current_version_number',v.version_number,
 'required_points_ready',ready_count,'photo_count',total,'approved_at',case when v.state='APPROVED' then v.decided_at end,
 'correction_reason',coalesce(v.correction_reason,(select correction_reason from public.guincho_inspection_versions where id=v.parent_version_id)),
 'allowed_actions',actions);
end $$;
create function private.inspection_enrich(p_id uuid,p_snapshot jsonb) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare summary jsonb; actions jsonb;
begin
 summary:=private.inspection_summary(p_id); actions:=p_snapshot->'allowed_actions';
 if (summary->>'required')::boolean and summary->>'state' is distinct from 'APPROVED' then actions:=actions-'START'-'COMPLETE'; end if;
 return p_snapshot||jsonb_build_object('inspection',summary,'allowed_actions',actions);
end $$;
do $$
declare definition text; suffix text:='''version'',a.quote_version,''expires_at'',a.quote_expires_at) end));';
begin
 definition:=pg_get_functiondef('private.guincho_snapshot(uuid)'::regprocedure);
 if position('return jsonb_build_object(''search_id'',s.id' in definition)=0 or position(suffix in definition)=0 then raise exception 'Unexpected Guincho snapshot contract'; end if;
 definition:=replace(definition,'return jsonb_build_object(''search_id'',s.id','return private.inspection_enrich(a.id,jsonb_build_object(''search_id'',s.id');
 definition:=replace(definition,suffix,'''version'',a.quote_version,''expires_at'',a.quote_expires_at) end)));');
 execute definition;
end $$;
create function public.get_guincho_inspection(p_appointment_id uuid) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare a public.appointments%rowtype; v jsonb; pending jsonb; required_photos jsonb;
begin
 if not private.inspection_participant(p_appointment_id) then raise exception using errcode='42501',message='PARTICIPANT_REQUIRED'; end if;
 select * into a from public.appointments where id=p_appointment_id;
 select to_jsonb(x) into v from public.guincho_inspection_versions x where appointment_id=a.id order by version_number desc limit 1;
 select coalesce(jsonb_agg(to_jsonb(f) order by f.photo_order),'[]') into pending from public.guincho_inspection_photos f
 where f.appointment_id=a.id and f.state='RESERVED' and f.abandoned_at is null and f.reserved_for_version_id=(v->>'id')::uuid;
 select coalesce(jsonb_agg(to_jsonb(f) order by f.photo_order),'[]') into required_photos
 from public.guincho_inspection_photos f join public.guincho_inspection_version_photos m on m.photo_id=f.id
 where m.version_id=(v->>'id')::uuid and m.selected and m.point<>'DETAIL';
 return jsonb_build_object('required_photos',required_photos,'appointment_id',a.id,'revision',a.revision,'summary',private.inspection_summary(a.id),'current_version',v,
 'vehicle',a.guincho_trip_snapshot->'vehicle','is_provider',exists(select 1 from public.providers where id=a.provider_id and account_id=auth.uid()),
 'closed',a.status not in ('ACCEPTED'),'pending_photos',pending);
end $$;
create function private.inspection_versions_page(p_id uuid,p_before integer,p_limit integer) returns jsonb language sql stable security definer set search_path='' as $$
 with rows as (select v.*,row_number() over(order by version_number desc) as rn from public.guincho_inspection_versions v
 where appointment_id=p_id and (p_before is null or version_number<p_before) order by version_number desc limit greatest(1,least(coalesce(p_limit,10),50))+1)
 select jsonb_build_object('items',coalesce(jsonb_agg(to_jsonb(rows)-'rn' order by version_number desc) filter(where rn<=greatest(1,least(coalesce(p_limit,10),50))),'[]'),
 'has_more',count(*)>greatest(1,least(coalesce(p_limit,10),50)), 'next_version',min(version_number) filter(where rn<=greatest(1,least(coalesce(p_limit,10),50)))) from rows;
$$;
create function private.inspection_photos_page(p_id uuid,p_version uuid,p_after bigint,p_after_id uuid,p_limit integer) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare result jsonb;
begin
 if p_version is not null and not exists(select 1 from public.guincho_inspection_versions where id=p_version and appointment_id=p_id) then raise exception using errcode='22023',message='VERSION_CONFLICT'; end if;
 with rows as (select f.*,f.photo_order as display_order,
 exists(select 1 from public.guincho_inspection_version_photos m join public.guincho_inspection_versions v on v.id=m.version_id
 where m.photo_id=f.id and m.selected and v.state<>'DRAFT') as sent,
 row_number() over(order by f.photo_order,f.id) as rn
 from public.guincho_inspection_photos f where f.appointment_id=p_id and f.state='READY'
 and (p_version is null or exists(select 1 from public.guincho_inspection_version_photos m where m.version_id=p_version and m.photo_id=f.id and m.selected))
 and (p_after is null or (f.photo_order,f.id)>(p_after,coalesce(p_after_id,'00000000-0000-0000-0000-000000000000'::uuid)))
 order by f.photo_order,f.id limit greatest(1,least(coalesce(p_limit,20),100))+1)
 select jsonb_build_object('items',coalesce(jsonb_agg(to_jsonb(rows)-'rn' order by photo_order,id) filter(where rn<=greatest(1,least(coalesce(p_limit,20),100))),'[]'),
 'has_more',count(*)>greatest(1,least(coalesce(p_limit,20),100)),
 'next_order',max(photo_order) filter(where rn<=greatest(1,least(coalesce(p_limit,20),100))),
 'next_id',(array_agg(id order by photo_order desc,id desc) filter(where rn<=greatest(1,least(coalesce(p_limit,20),100))))[1]) into result from rows;
 return result;
end $$;
create function public.list_guincho_inspection_versions(p_appointment_id uuid,p_before_version integer default null,p_limit integer default 10) returns jsonb language plpgsql stable security definer set search_path='' as $$
begin
 if not private.inspection_participant(p_appointment_id) then raise exception using errcode='42501',message='PARTICIPANT_REQUIRED'; end if;
 return private.inspection_versions_page(p_appointment_id,p_before_version,p_limit);
end $$;
create function public.list_guincho_inspection_photos(p_appointment_id uuid,p_version_id uuid default null,p_after_order bigint default null,p_after_id uuid default null,p_limit integer default 20) returns jsonb language plpgsql stable security definer set search_path='' as $$
begin
 if not private.inspection_participant(p_appointment_id) then raise exception using errcode='42501',message='PARTICIPANT_REQUIRED'; end if;
 return private.inspection_photos_page(p_appointment_id,p_version_id,p_after_order,p_after_id,p_limit);
end $$;
create function private.inspection_result(p_id uuid,p_outcome text) returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object('outcome',p_outcome,'snapshot',private.guincho_snapshot(p_id),'inspection',public.get_guincho_inspection(p_id),'server_now',now());
$$;
revoke all on function private.inspection_summary(uuid),private.inspection_enrich(uuid,jsonb),private.inspection_versions_page(uuid,integer,integer),private.inspection_photos_page(uuid,uuid,bigint,uuid,integer),private.inspection_result(uuid,text) from public,anon,authenticated;
revoke all on function public.get_guincho_inspection(uuid),public.list_guincho_inspection_versions(uuid,integer,integer),public.list_guincho_inspection_photos(uuid,uuid,bigint,uuid,integer) from public,anon;
grant execute on function public.get_guincho_inspection(uuid),public.list_guincho_inspection_versions(uuid,integer,integer),public.list_guincho_inspection_photos(uuid,uuid,bigint,uuid,integer) to authenticated;
commit;
