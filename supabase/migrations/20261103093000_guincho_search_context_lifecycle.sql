begin;
-- A business generation is independent of optimistic draft revisions and map GPS updates.
create function private.sync_guincho_trip_context() returns trigger language plpgsql security definer set search_path='' as $$
declare c public.guincho_search_contexts%rowtype; d public.guincho_trip_drafts%rowtype; changed boolean;
begin
 perform pg_advisory_xact_lock(hashtextextended(new.client_id::text,3601));
 select * into c from public.guincho_search_contexts where client_id=new.client_id and state<>'CLOSED' for update;
 if c.id is null then
  insert into public.guincho_search_contexts(client_id,draft_id) values(new.client_id,new.id);
 else
  if c.state='REQUESTED' then raise exception using errcode='22023',message='REQUEST_ALREADY_ACTIVE'; end if;
  if tg_op='UPDATE' and c.draft_id=new.id then d:=old;
  else select * into d from public.guincho_trip_drafts where id=c.draft_id; end if;
  changed:=(d.vehicle_id,btrim(d.pickup_address),d.pickup_latitude,d.pickup_longitude,btrim(d.destination_address),d.destination_latitude,d.destination_longitude)
   is distinct from (new.vehicle_id,btrim(new.pickup_address),new.pickup_latitude,new.pickup_longitude,btrim(new.destination_address),new.destination_latitude,new.destination_longitude);
  update public.guincho_search_contexts set draft_id=new.id,generation=generation+case when changed then 1 else 0 end,
   revision=revision+1,appointment_id=case when changed then null else appointment_id end,updated_at=now() where id=c.id;
 end if;
 return new;
end $$;
create trigger sync_guincho_trip_context after insert or update of vehicle_id,pickup_address,pickup_latitude,pickup_longitude,destination_address,destination_latitude,destination_longitude on public.guincho_trip_drafts for each row execute function private.sync_guincho_trip_context();
create function private.sync_guincho_appointment_context() returns trigger language plpgsql security definer set search_path='' as $$
declare q public.guincho_quotes%rowtype; c public.guincho_search_contexts%rowtype; d public.guincho_trip_drafts%rowtype;
begin
 if new.guincho_quote_id is null then return new; end if;
 select * into q from public.guincho_quotes where id=new.guincho_quote_id and client_id=new.client_id;
 if q.id is null then return new; end if;
 perform pg_advisory_xact_lock(hashtextextended(new.client_id::text,3601));
 select * into c from public.guincho_search_contexts where client_id=new.client_id and state<>'CLOSED' for update;
 if tg_op='INSERT' then
  if c.id is null then insert into public.guincho_search_contexts(client_id,draft_id,state,appointment_id) values(new.client_id,q.draft_id,'REQUESTED',new.id);
  elsif c.draft_id=q.draft_id and c.state='DISCOVERY' then update public.guincho_search_contexts set state='REQUESTED',appointment_id=new.id,revision=revision+1,updated_at=now() where id=c.id;
  else raise exception using errcode='22023',message='REQUEST_ALREADY_ACTIVE'; end if;
 elsif new.status in ('CANCELLED','COMPLETED','EXPIRED','PROVIDER_REJECTED') and (old.status is distinct from new.status) then
  if c.id is not null and c.appointment_id is distinct from new.id then return new; end if;
  if new.cancellation_reason='PROVIDER_DECLINED' then
   -- Lazy upgrade for pre-context appointments; don't replace a more recent trip.
   if c.id is null then
    select * into d from public.guincho_trip_drafts where id=q.draft_id and client_id=new.client_id;
    if d.id is null or exists(select 1 from public.guincho_trip_drafts where client_id=new.client_id and updated_at>d.updated_at and id<>d.id)
     or exists(select 1 from public.appointments where client_id=new.client_id and id<>new.id and status in ('PENDING','AWAITING_PAYMENT','ACCEPTED','IN_PROGRESS','AWAITING_CLIENT_COMPLETION_CONFIRMATION')) then return new; end if;
    insert into public.guincho_search_contexts(client_id,draft_id,state,appointment_id) values(new.client_id,d.id,'DISCOVERY',new.id) returning * into c;
   end if;
   insert into public.guincho_search_exclusions(context_id,generation,provider_id,source_appointment_id) values(c.id,c.generation,new.provider_id,new.id) on conflict do nothing;
   update public.guincho_search_contexts set state='DISCOVERY',revision=revision+1,updated_at=now() where id=c.id;
  elsif c.id is not null then
   update public.guincho_search_contexts set state='CLOSED',revision=revision+1,updated_at=now() where id=c.id and appointment_id=new.id;
  end if;
 end if;
 return new;
end $$;
create trigger sync_guincho_appointment_context after insert or update of status on public.appointments for each row execute function private.sync_guincho_appointment_context();
revoke all on function private.sync_guincho_trip_context(),private.sync_guincho_appointment_context() from public,anon,authenticated;
commit;
