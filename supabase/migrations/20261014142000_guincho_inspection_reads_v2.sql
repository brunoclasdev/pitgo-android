begin;
create or replace function public.get_guincho_inspection_v2(p_appointment_id uuid) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare a public.appointments%rowtype; v jsonb; pending jsonb; required_photos jsonb;
begin
 if not private.inspection_participant(p_appointment_id) then raise exception using errcode='42501',message='PARTICIPANT_REQUIRED'; end if;
 select * into a from public.appointments where id=p_appointment_id;
 select to_jsonb(x) into v from public.guincho_inspection_versions x where appointment_id=a.id order by version_number desc limit 1;
 select coalesce(jsonb_agg(to_jsonb(f) order by f.photo_order),'[]') into pending from public.guincho_inspection_photos f
 where f.appointment_id=a.id and f.state='RESERVED' and f.abandoned_at is null and f.reserved_for_version_id=(v->>'id')::uuid;
 select coalesce(jsonb_agg(to_jsonb(f) order by f.photo_order),'[]') into required_photos
 from public.guincho_inspection_photos f join public.guincho_inspection_version_photos m on m.photo_id=f.id
 where m.version_id=(v->>'id')::uuid and m.selected and m.point in ('FRONT','REAR','LEFT','RIGHT');
 return jsonb_build_object('required_photos',required_photos,'appointment_id',a.id,'revision',a.revision,'summary',private.inspection_summary(a.id),'current_version',v,
 'vehicle',a.guincho_trip_snapshot->'vehicle','is_provider',exists(select 1 from public.providers where id=a.provider_id and account_id=auth.uid()),
 'closed',a.status not in ('ACCEPTED'),'pending_photos',pending);
end $$;
create or replace function public.list_guincho_inspection_photos_v2(p_appointment_id uuid,p_version_id uuid default null,p_after_order bigint default null,p_after_id uuid default null,p_limit integer default 20) returns jsonb language plpgsql stable security definer set search_path='' as $$
begin
 if not private.inspection_participant(p_appointment_id) then raise exception using errcode='42501',message='PARTICIPANT_REQUIRED'; end if;
 return private.inspection_photos_page(p_appointment_id,p_version_id,p_after_order,p_after_id,p_limit);
end $$;

create or replace function public.get_guincho_inspection(p_appointment_id uuid) returns jsonb language sql stable security definer set search_path='' as $$
 select d || jsonb_build_object(
 'required_photos',(select coalesce(jsonb_agg(x-array['detail_point','thumbnail_object_path','thumbnail_byte_size']),'[]') from jsonb_array_elements(d->'required_photos') x),
 'pending_photos',(select coalesce(jsonb_agg(x-array['detail_point','thumbnail_object_path','thumbnail_byte_size']),'[]') from jsonb_array_elements(d->'pending_photos') x))
 from (select public.get_guincho_inspection_v2(p_appointment_id) d) q;
$$;
create or replace function public.list_guincho_inspection_photos(p_appointment_id uuid,p_version_id uuid default null,p_after_order bigint default null,p_after_id uuid default null,p_limit integer default 20)
returns jsonb language sql stable security definer set search_path='' as $$
 select d||jsonb_build_object('items',(select coalesce(jsonb_agg(x-array['detail_point','thumbnail_object_path','thumbnail_byte_size']),'[]') from jsonb_array_elements(d->'items') x))
 from (select public.list_guincho_inspection_photos_v2(p_appointment_id,p_version_id,p_after_order,p_after_id,p_limit) d) q;
$$;
revoke all on function public.get_guincho_inspection_v2(uuid),public.list_guincho_inspection_photos_v2(uuid,uuid,bigint,uuid,integer) from public,anon;
grant execute on function public.get_guincho_inspection_v2(uuid),public.list_guincho_inspection_photos_v2(uuid,uuid,bigint,uuid,integer) to authenticated;

create or replace function private.inspection_summary(p_id uuid) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare a public.appointments%rowtype; v public.guincho_inspection_versions%rowtype; actions jsonb:='[]'; ready_count integer; total integer; provider boolean;
begin
 select * into a from public.appointments where id=p_id;
 select * into v from public.guincho_inspection_versions where appointment_id=p_id order by version_number desc limit 1;
 select p.account_id=auth.uid() into provider from public.providers p where p.id=a.provider_id;
 select count(*) filter(where point in ('FRONT','REAR','LEFT','RIGHT')),count(*) into ready_count,total from public.guincho_inspection_version_photos where version_id=v.id and selected;
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
notify pgrst,'reload schema';
commit;
