begin;
alter table public.guincho_inspection_photos
 add column detail_point text,
 add column thumbnail_object_path text,
 add column thumbnail_byte_size bigint,
 add constraint inspection_detail_point check(detail_point is null or (point='DETAIL' and detail_point in ('TRUNK_INTERIOR','INSTRUMENT_PANEL','FRONT_SEATS','REAR_SEATS','FULL_DASHBOARD','TOOL_KIT','SPARE_TIRE','FRONT_LEFT_TIRE','FRONT_RIGHT_TIRE','REAR_RIGHT_TIRE','REAR_LEFT_TIRE','ENGINE','BATTERY'))),
 add constraint inspection_thumbnail_asset check(
  (thumbnail_object_path is null and thumbnail_byte_size is null) or
  (thumbnail_object_path is not null and thumbnail_byte_size is not null and thumbnail_byte_size between 1 and 262144
   and thumbnail_object_path=appointment_id::text||'/'||id::text||'.thumb.jpg'));
alter table public.guincho_inspection_version_photos add column detail_point text,
 add constraint inspection_manifest_detail check(detail_point is null or (point='DETAIL' and detail_point in ('TRUNK_INTERIOR','INSTRUMENT_PANEL','FRONT_SEATS','REAR_SEATS','FULL_DASHBOARD','TOOL_KIT','SPARE_TIRE','FRONT_LEFT_TIRE','FRONT_RIGHT_TIRE','REAR_RIGHT_TIRE','REAR_LEFT_TIRE','ENGINE','BATTERY')));
create unique index inspection_named_reservation on public.guincho_inspection_photos(reserved_for_version_id,detail_point)
 where state='RESERVED' and abandoned_at is null and detail_point is not null;
create unique index inspection_named_selection on public.guincho_inspection_version_photos(version_id,detail_point)
 where selected and detail_point is not null;
create unique index inspection_replacement_reservation on public.guincho_inspection_photos(reserved_for_version_id,replaces_photo_id)
 where state='RESERVED' and abandoned_at is null and replaces_photo_id is not null;
notify pgrst,'reload schema';
commit;
