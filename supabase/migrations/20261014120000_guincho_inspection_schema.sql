begin;
alter table public.appointments add column guincho_inspection_required boolean not null default false;
create table public.guincho_inspection_versions (
 id uuid primary key default gen_random_uuid(), appointment_id uuid not null references public.appointments(id),
 version_number integer not null check(version_number>0), parent_version_id uuid unique,
 state text not null default 'DRAFT' check(state in ('DRAFT','SUBMITTED','CORRECTION_REQUESTED','APPROVED')),
 created_by uuid not null references auth.users(id), created_at timestamptz not null default now(),
 submitted_at timestamptz, submission_operation_id uuid, decided_by uuid references auth.users(id),
 decided_at timestamptz, decided_by_name text, decision_operation_id uuid, correction_reason text,
 unique(appointment_id,version_number), unique(id,appointment_id),
 foreign key(parent_version_id,appointment_id) references public.guincho_inspection_versions(id,appointment_id),
 check ((state='DRAFT' and submitted_at is null and submission_operation_id is null and decided_at is null and decided_by is null and decision_operation_id is null and correction_reason is null)
 or (state='SUBMITTED' and submitted_at is not null and submission_operation_id is not null and decided_at is null and decided_by is null and decision_operation_id is null and correction_reason is null)
 or (state in ('APPROVED','CORRECTION_REQUESTED') and submitted_at is not null and submission_operation_id is not null and decided_at is not null and decided_by is not null and decision_operation_id is not null
 and ((state='APPROVED' and correction_reason is null) or (state='CORRECTION_REQUESTED' and correction_reason is not null and char_length(btrim(correction_reason)) between 1 and 1000))))
);
create unique index guincho_inspection_open_version on public.guincho_inspection_versions(appointment_id) where state in ('DRAFT','SUBMITTED');
create unique index guincho_inspection_submit_operation on public.guincho_inspection_versions(appointment_id,submission_operation_id) where submission_operation_id is not null;
create unique index guincho_inspection_decide_operation on public.guincho_inspection_versions(appointment_id,decision_operation_id) where decision_operation_id is not null;
create index guincho_inspection_version_history on public.guincho_inspection_versions(appointment_id,version_number desc);
create table public.guincho_inspection_photos (
 id uuid primary key default gen_random_uuid(), appointment_id uuid not null references public.appointments(id),
 reserved_for_version_id uuid not null, photo_order bigint generated always as identity,
 point text not null check(point in ('FRONT','REAR','LEFT','RIGHT','DETAIL')),
 caption text check(char_length(caption)<=1000), captured_by uuid not null references auth.users(id), captured_by_name text not null, captured_at timestamptz not null,
 reserved_at timestamptz not null default now(), saved_at timestamptz, abandoned_at timestamptz,
 state text not null default 'RESERVED' check(state in ('RESERVED','READY')), object_path text not null unique,
 content_type text not null default 'image/jpeg' check(content_type='image/jpeg'), byte_size bigint not null check(byte_size between 1 and 5242880),
 reserve_operation_id uuid not null, confirm_operation_id uuid, discard_operation_id uuid, replaces_photo_id uuid,
 unique(id,appointment_id), unique(appointment_id,reserve_operation_id),
 foreign key(reserved_for_version_id,appointment_id) references public.guincho_inspection_versions(id,appointment_id),
 foreign key(replaces_photo_id,appointment_id) references public.guincho_inspection_photos(id,appointment_id),
 check(object_path=appointment_id::text||'/'||id::text||'.jpg'),
 check((state='READY' and saved_at is not null and confirm_operation_id is not null and abandoned_at is null and discard_operation_id is null)
 or (state='RESERVED' and saved_at is null and confirm_operation_id is null and ((abandoned_at is null and discard_operation_id is null) or (abandoned_at is not null and discard_operation_id is not null))))
);
create unique index guincho_inspection_confirm_operation on public.guincho_inspection_photos(appointment_id,confirm_operation_id) where confirm_operation_id is not null;
create unique index guincho_inspection_discard_operation on public.guincho_inspection_photos(appointment_id,discard_operation_id) where discard_operation_id is not null;
create unique index guincho_inspection_point_reservation on public.guincho_inspection_photos(reserved_for_version_id,point) where state='RESERVED' and abandoned_at is null and point<>'DETAIL';
create index guincho_inspection_photo_history on public.guincho_inspection_photos(appointment_id,photo_order,id);
create table public.guincho_inspection_version_photos (
 appointment_id uuid not null, version_id uuid not null, photo_id uuid not null,
 point text not null check(point in ('FRONT','REAR','LEFT','RIGHT','DETAIL')), display_order bigint not null,
 selected boolean not null default true, unselect_operation_id uuid, primary key(version_id,photo_id),
 foreign key(version_id,appointment_id) references public.guincho_inspection_versions(id,appointment_id),
 foreign key(photo_id,appointment_id) references public.guincho_inspection_photos(id,appointment_id)
);
create unique index guincho_inspection_selected_point on public.guincho_inspection_version_photos(version_id,point) where selected and point<>'DETAIL';
create unique index guincho_inspection_unselect_operation on public.guincho_inspection_version_photos(appointment_id,unselect_operation_id) where unselect_operation_id is not null;

create function private.inspection_participant(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.appointments a join public.providers p on p.id=a.provider_id
 where a.id=p_id and a.guincho_quote_id is not null and (a.client_id=auth.uid() or p.account_id=auth.uid()));
$$;
create function private.inspection_writable(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.appointments a where a.id=p_id and a.guincho_inspection_required and a.status='ACCEPTED'
 and a.arrived_at is not null and a.service_started_at is null and (a.service_deadline_at is null or a.service_deadline_at>now())
 and exists(select 1 from public.payments p where p.request_id=a.dispatch_search_id and p.appointment_id=a.id
 and p.quote_version=a.quote_version and p.amount=coalesce(a.payment_amount,a.customer_total_amount) and private.payment_can_release(p,a.service_id)));
$$;
create function private.inspection_lock(p_id uuid,p_role text) returns public.appointments language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype; sid uuid;
begin
 if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
 select x.dispatch_search_id into sid from public.appointments x join public.providers p on p.id=x.provider_id where x.id=p_id and x.guincho_quote_id is not null
 and ((p_role='PROVIDER' and p.account_id=auth.uid()) or (p_role='CLIENT' and x.client_id=auth.uid()));
 if sid is null then raise exception using errcode='42501',message='PARTICIPANT_REQUIRED'; end if;
 perform 1 from public.service_searches where id=sid for update;
 select * into strict a from public.appointments where id=p_id for update;
 return a;
end $$;
create function private.inspection_bump(p_id uuid) returns void language plpgsql security definer set search_path='' as $$
begin
 update public.appointments set revision=revision+1,updated_at=now() where id=p_id;
 update public.service_searches set revision=revision+1,updated_at=now() where id=(select dispatch_search_id from public.appointments where id=p_id);
end $$;
create function private.inspection_immutable() returns trigger language plpgsql set search_path='' as $$
begin
 if tg_op='DELETE' then raise exception using errcode='22023',message='INSPECTION_IMMUTABLE'; end if;
 if tg_table_name='guincho_inspection_versions' then
  if old.state in ('APPROVED','CORRECTION_REQUESTED') or (old.state='SUBMITTED' and (new.state not in ('APPROVED','CORRECTION_REQUESTED')
   or (to_jsonb(new)-array['state','decided_by','decided_by_name','decided_at','decision_operation_id','correction_reason'])<>(to_jsonb(old)-array['state','decided_by','decided_by_name','decided_at','decision_operation_id','correction_reason']))) then
   raise exception using errcode='22023',message='INSPECTION_IMMUTABLE'; end if;
 elsif tg_table_name='guincho_inspection_photos' then
  if old.state='READY' or old.abandoned_at is not null then raise exception using errcode='22023',message='INSPECTION_IMMUTABLE'; end if;
 end if;
 return new;
end $$;
create function private.inspection_manifest_guard() returns trigger language plpgsql set search_path='' as $$
begin
 if tg_op='DELETE' then raise exception using errcode='22023',message='INSPECTION_IMMUTABLE'; end if;
 if not exists(select 1 from public.guincho_inspection_versions v join public.guincho_inspection_photos p on p.id=new.photo_id and p.appointment_id=v.appointment_id
 where v.id=new.version_id and v.appointment_id=new.appointment_id and v.state='DRAFT' and p.state='READY' and p.point=new.point) then
  raise exception using errcode='22023',message='INSPECTION_IMMUTABLE'; end if;
 return new;
end $$;
create trigger inspection_version_immutable before update or delete on public.guincho_inspection_versions for each row execute function private.inspection_immutable();
create trigger inspection_photo_immutable before update or delete on public.guincho_inspection_photos for each row execute function private.inspection_immutable();
create trigger inspection_manifest_immutable before insert or update or delete on public.guincho_inspection_version_photos for each row execute function private.inspection_manifest_guard();
alter table public.guincho_inspection_versions enable row level security;
alter table public.guincho_inspection_photos enable row level security;
alter table public.guincho_inspection_version_photos enable row level security;
revoke all on public.guincho_inspection_versions,public.guincho_inspection_photos,public.guincho_inspection_version_photos from public,anon,authenticated;
revoke all on function private.inspection_participant(uuid),private.inspection_writable(uuid),private.inspection_lock(uuid,text),private.inspection_bump(uuid),private.inspection_immutable(),private.inspection_manifest_guard() from public,anon,authenticated;
commit;
