begin;
create function private.inspection_support_case(p_dispute uuid) returns uuid language plpgsql volatile security definer set search_path='' as $$
declare aid uuid;
begin
 if not coalesce(private.dashboard_access_at_least('MANAGER'),false) then raise exception using errcode='42501',message='STAFF_REQUIRED'; end if;
 select appointment_id into aid from public.appointment_disputes where id=p_dispute;
 if aid is null then raise exception using errcode='P0002',message='DISPUTE_NOT_FOUND'; end if;
 perform public.record_dashboard_action('GUINCHO_INSPECTION_VIEW','appointment_dispute',p_dispute);
 return aid;
end $$;
create function public.dashboard_get_guincho_inspection(p_dispute_id uuid) returns jsonb language plpgsql volatile security definer set search_path='' as $$
declare aid uuid; a public.appointments%rowtype;
begin
 aid:=private.inspection_support_case(p_dispute_id);
 select * into a from public.appointments where id=aid;
 return jsonb_build_object('appointment_id',a.id,'summary',private.inspection_summary(a.id),'vehicle',a.guincho_trip_snapshot->'vehicle');
end $$;
create function public.dashboard_list_guincho_inspection_versions(p_dispute_id uuid,p_before_version integer default null,p_limit integer default 10) returns jsonb language plpgsql volatile security definer set search_path='' as $$
begin return private.inspection_versions_page(private.inspection_support_case(p_dispute_id),p_before_version,p_limit); end $$;
create function public.dashboard_list_guincho_inspection_photos(p_dispute_id uuid,p_version_id uuid default null,p_after_order bigint default null,p_after_id uuid default null,p_limit integer default 20) returns jsonb language plpgsql volatile security definer set search_path='' as $$
begin return private.inspection_photos_page(private.inspection_support_case(p_dispute_id),p_version_id,p_after_order,p_after_id,p_limit); end $$;
create function public.dashboard_authorize_guincho_inspection_photo(p_dispute_id uuid,p_photo_id uuid) returns jsonb language plpgsql volatile security definer set search_path='' as $$
declare aid uuid; f public.guincho_inspection_photos%rowtype;
begin
 aid:=private.inspection_support_case(p_dispute_id);
 select * into f from public.guincho_inspection_photos where id=p_photo_id and appointment_id=aid and state='READY';
 if f.id is null then raise exception using errcode='42501',message='PHOTO_NOT_FOUND'; end if;
 perform public.record_dashboard_action('GUINCHO_INSPECTION_PHOTO_VIEW','guincho_inspection_photo',f.id);
 return jsonb_build_object('bucket','guincho-inspections','path',f.object_path,'content_type',f.content_type);
end $$;
revoke all on function private.inspection_support_case(uuid) from public,anon,authenticated;
revoke all on function public.dashboard_get_guincho_inspection(uuid),public.dashboard_list_guincho_inspection_versions(uuid,integer,integer),public.dashboard_list_guincho_inspection_photos(uuid,uuid,bigint,uuid,integer),public.dashboard_authorize_guincho_inspection_photo(uuid,uuid) from public,anon;
grant execute on function public.dashboard_get_guincho_inspection(uuid),public.dashboard_list_guincho_inspection_versions(uuid,integer,integer),public.dashboard_list_guincho_inspection_photos(uuid,uuid,bigint,uuid,integer),public.dashboard_authorize_guincho_inspection_photo(uuid,uuid) to authenticated;
notify pgrst,'reload schema';
commit;
