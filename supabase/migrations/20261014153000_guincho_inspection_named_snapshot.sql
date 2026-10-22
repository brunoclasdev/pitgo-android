begin;
create or replace function public.get_guincho_inspection_v2(p_appointment_id uuid) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare a public.appointments%rowtype; v jsonb; pending jsonb; required_photos jsonb; named_photos jsonb;
begin
 if not private.inspection_participant(p_appointment_id) then raise exception using errcode='42501',message='PARTICIPANT_REQUIRED'; end if;
 select * into a from public.appointments where id=p_appointment_id;
 select to_jsonb(x) into v from public.guincho_inspection_versions x where appointment_id=a.id order by version_number desc limit 1;
 select coalesce(jsonb_agg(to_jsonb(f) order by f.photo_order),'[]') into pending from public.guincho_inspection_photos f
 where f.appointment_id=a.id and f.state='RESERVED' and f.abandoned_at is null and f.reserved_for_version_id=(v->>'id')::uuid;
 select coalesce(jsonb_agg(to_jsonb(f) order by f.photo_order),'[]') into required_photos
 from public.guincho_inspection_photos f join public.guincho_inspection_version_photos m on m.photo_id=f.id
 where m.version_id=(v->>'id')::uuid and m.selected and m.point in ('FRONT','REAR','LEFT','RIGHT');
 select coalesce(jsonb_agg(to_jsonb(f) order by f.photo_order),'[]') into named_photos
 from public.guincho_inspection_photos f join public.guincho_inspection_version_photos m on m.photo_id=f.id
 where m.version_id=(v->>'id')::uuid and m.selected and (m.point in ('FRONT','REAR','LEFT','RIGHT') or m.detail_point is not null);
 return jsonb_build_object('named_photos',named_photos,'required_photos',required_photos,'appointment_id',a.id,'revision',a.revision,'summary',private.inspection_summary(a.id),'current_version',v,
 'vehicle',a.guincho_trip_snapshot->'vehicle','is_provider',exists(select 1 from public.providers where id=a.provider_id and account_id=auth.uid()),
 'closed',a.status not in ('ACCEPTED'),'pending_photos',pending);
end $$;
create or replace function public.get_guincho_inspection(p_appointment_id uuid) returns jsonb language sql stable security definer set search_path='' as $$
 select (d-'named_photos') || jsonb_build_object(
 'required_photos',(select coalesce(jsonb_agg(x-array['detail_point','thumbnail_object_path','thumbnail_byte_size']),'[]') from jsonb_array_elements(d->'required_photos') x),
 'pending_photos',(select coalesce(jsonb_agg(x-array['detail_point','thumbnail_object_path','thumbnail_byte_size']),'[]') from jsonb_array_elements(d->'pending_photos') x))
 from (select public.get_guincho_inspection_v2(p_appointment_id) d) q;
$$;
notify pgrst,'reload schema';
commit;
