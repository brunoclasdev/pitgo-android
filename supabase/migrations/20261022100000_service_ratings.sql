begin;

create table public.service_ratings (
  id uuid primary key default gen_random_uuid(),
  appointment_id uuid not null unique references public.appointments(id) on delete restrict,
  client_id uuid not null references public.profiles(id) on delete restrict,
  provider_id uuid not null references public.providers(id) on delete restrict,
  rating integer not null check (rating between 1 and 5),
  comment text check (char_length(comment) <= 500),
  created_at timestamptz not null default now()
);
create index service_ratings_provider_idx on public.service_ratings(provider_id);
alter table public.service_ratings enable row level security;
-- All access is mediated by the RPCs below; no direct reads can reveal comments/authors.
revoke all on table public.service_ratings from public, anon, authenticated;

create function private.prevent_service_rating_change() returns trigger
language plpgsql set search_path = '' as $$
begin
  raise exception using errcode='22023', message='RATING_IS_FINAL';
end $$;
create trigger service_ratings_immutable before update or delete on public.service_ratings
for each row execute function private.prevent_service_rating_change();
revoke all on function private.prevent_service_rating_change() from public, anon, authenticated;

create function private.provider_rating_summary(p_provider_id uuid) returns jsonb
language sql stable security definer set search_path='' as $$
  select jsonb_build_object('count',count(*),'average',avg(rating))
  from public.service_ratings where provider_id=p_provider_id;
$$;
revoke all on function private.provider_rating_summary(uuid) from public, anon, authenticated;

create function public.get_provider_rating_summary(p_provider_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
begin
  if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
  return private.provider_rating_summary(p_provider_id);
end $$;

-- This small projection is only used after the surrounding snapshot has authorized its caller.
create function private.service_rating_state(p_appointment_id uuid) returns jsonb
language sql stable security definer set search_path='' as $$
  select jsonb_build_object('submitted',exists(select 1 from public.service_ratings r where r.appointment_id=a.id),
    'can_rate',coalesce(a.client_id=auth.uid(),false) and a.status='COMPLETED'
      and not exists(select 1 from public.appointment_disputes d where d.appointment_id=a.id)
      and not exists(select 1 from public.service_ratings r where r.appointment_id=a.id))
  from public.appointments a where a.id=p_appointment_id;
$$;
revoke all on function private.service_rating_state(uuid) from public, anon, authenticated;

create function public.get_service_rating(p_appointment_id uuid) returns jsonb
language plpgsql volatile security definer set search_path='' as $$
declare a public.appointments%rowtype; p public.providers%rowtype; staff boolean;
begin
  if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
  select * into a from public.appointments where id=p_appointment_id;
  select * into p from public.providers where id=a.provider_id;
  staff:=coalesce(private.dashboard_access_at_least('MANAGER'),false);
  if a.id is null or not (coalesce(a.client_id=auth.uid(),false) or coalesce(p.account_id=auth.uid(),false) or staff) then
    raise exception using errcode='42501',message='PARTICIPANT_REQUIRED';
  end if;
  if not exists(select 1 from public.service_searches s where s.id=a.dispatch_search_id
    and s.dispatch_kind in ('GUINCHO_DIRECT','PITTEC_AUTO_SEQUENTIAL')) then
    raise exception using errcode='22023',message='RATING_NOT_ELIGIBLE';
  end if;
  if staff then perform public.record_dashboard_action('SERVICE_RATING_VIEW','appointment',a.id); end if;
  return private.service_rating_state(a.id)||jsonb_build_object(
    'appointment_id',a.id,'provider_name',p.name,
    'rating',(select jsonb_build_object('id',r.id,'rating',r.rating,'comment',r.comment,'created_at',r.created_at)
      from public.service_ratings r where r.appointment_id=a.id));
end $$;

create function public.submit_service_rating(p_appointment_id uuid,p_rating integer,p_comment text default null)
returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.appointments%rowtype;
begin
  if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
  select * into a from public.appointments where id=p_appointment_id and client_id=auth.uid() for update;
  if a.id is null then raise exception using errcode='42501',message='CLIENT_REQUIRED'; end if;
  if not exists(select 1 from public.service_searches s where s.id=a.dispatch_search_id
    and s.dispatch_kind in ('GUINCHO_DIRECT','PITTEC_AUTO_SEQUENTIAL')) then
    raise exception using errcode='22023',message='RATING_NOT_ELIGIBLE';
  end if;
  -- Locking the appointment serializes first submission and retries, including lost responses.
  if exists(select 1 from public.service_ratings where appointment_id=a.id) then
    return jsonb_build_object('outcome','already_submitted','context',public.get_service_rating(a.id));
  end if;
  if a.status<>'COMPLETED' or exists(select 1 from public.appointment_disputes where appointment_id=a.id) then
    raise exception using errcode='22023',message='RATING_NOT_ELIGIBLE';
  end if;
  if p_rating is null or p_rating not between 1 and 5 or char_length(p_comment)>500 then
    raise exception using errcode='22023',message='INVALID_RATING';
  end if;
  insert into public.service_ratings(appointment_id,client_id,provider_id,rating,comment)
  values(a.id,a.client_id,a.provider_id,p_rating,nullif(btrim(p_comment),''));
  return jsonb_build_object('outcome','submitted','context',public.get_service_rating(a.id));
end $$;

-- Preserve the operational contracts, adding only private eligibility and a public-safe aggregate.
alter function private.guincho_snapshot(uuid) rename to guincho_snapshot_before_ratings;
create function private.guincho_snapshot(p_appointment_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare snapshot jsonb;
begin
  snapshot:=private.guincho_snapshot_before_ratings(p_appointment_id);
  if snapshot is null then return null; end if;
  return snapshot||jsonb_build_object('rating_state',private.service_rating_state(p_appointment_id));
end $$;
revoke all on function private.guincho_snapshot(uuid),private.guincho_snapshot_before_ratings(uuid) from public,anon,authenticated;

alter function private.pittec_dispatch_snapshot(uuid) rename to pittec_dispatch_snapshot_before_ratings;
create function private.pittec_dispatch_snapshot(p_search_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare snapshot jsonb; appointment_id uuid; provider_id uuid;
begin
  snapshot:=private.pittec_dispatch_snapshot_before_ratings(p_search_id);
  if snapshot is null then return null; end if;
  appointment_id:=(snapshot#>>'{appointment,appointment_id}')::uuid;
  provider_id:=(snapshot#>>'{provider,provider_id}')::uuid;
  if provider_id is not null then
    snapshot:=jsonb_set(snapshot,'{provider,rating_summary}',private.provider_rating_summary(provider_id));
  end if;
  return snapshot||jsonb_build_object('rating_state',private.service_rating_state(appointment_id));
end $$;
revoke all on function private.pittec_dispatch_snapshot(uuid),private.pittec_dispatch_snapshot_before_ratings(uuid) from public,anon,authenticated;

alter function public.discover_guincho_providers(uuid,bigint,integer) rename to discover_guincho_providers_before_ratings;
alter function public.discover_guincho_providers_before_ratings(uuid,bigint,integer) set schema private;
revoke all on function private.discover_guincho_providers_before_ratings(uuid,bigint,integer) from public,anon,authenticated;
create function public.discover_guincho_providers(p_draft_id uuid,p_expected_revision bigint,p_limit integer default 200)
returns table(provider_id uuid,provider_service_id uuid,display_name text,latitude double precision,
  longitude double precision,distance_meters double precision,rating_summary jsonb)
language sql stable security definer set search_path='' as $$
  select p.*,private.provider_rating_summary(p.provider_id)
  from private.discover_guincho_providers_before_ratings(p_draft_id,p_expected_revision,p_limit) p;
$$;

create function public.list_my_service_appointments(p_before timestamptz default null,p_before_id uuid default null,p_limit integer default 20)
returns setof jsonb language plpgsql stable security definer set search_path='' as $$
declare a public.appointments%rowtype; snapshot jsonb;
begin
  if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
  if (p_before is null) <> (p_before_id is null) then raise exception using errcode='22023',message='INVALID_CURSOR'; end if;
  for a in select appointment.* from public.appointments appointment
    join public.providers p on p.id=appointment.provider_id
    join public.service_searches s on s.id=appointment.dispatch_search_id
    where ((appointment.guincho_quote_id is not null and (appointment.client_id=auth.uid() or
      (p.account_id=auth.uid() and appointment.status<>'AWAITING_PAYMENT' and exists(
        select 1 from public.payments pay where pay.request_id=appointment.dispatch_search_id and not pay.is_compensation
        and pay.status in ('APPROVED','REFUND_PENDING','REFUND_FAILED','REFUNDED')))))
      or (s.dispatch_kind='PITTEC_AUTO_SEQUENTIAL' and appointment.status='COMPLETED'
        and s.current_appointment_id=appointment.id and (appointment.client_id=auth.uid() or p.account_id=auth.uid())))
      and (p_before is null or (appointment.created_at,appointment.id)<(p_before,p_before_id))
    order by appointment.created_at desc,appointment.id desc
    limit greatest(1,least(coalesce(p_limit,20),100))
  loop
    snapshot:=case when a.guincho_quote_id is not null then private.guincho_snapshot(a.id)
      else private.pittec_dispatch_snapshot(a.dispatch_search_id) end;
    return next snapshot||jsonb_build_object('created_at',a.created_at);
  end loop;
end $$;

revoke all on function public.submit_service_rating(uuid,integer,text),public.get_service_rating(uuid),
  public.get_provider_rating_summary(uuid),public.list_my_service_appointments(timestamptz,uuid,integer),
  public.discover_guincho_providers(uuid,bigint,integer) from public,anon;
grant execute on function public.submit_service_rating(uuid,integer,text),public.get_service_rating(uuid),
  public.get_provider_rating_summary(uuid),public.list_my_service_appointments(timestamptz,uuid,integer),
  public.discover_guincho_providers(uuid,bigint,integer) to authenticated;

notify pgrst,'reload schema';
commit;
