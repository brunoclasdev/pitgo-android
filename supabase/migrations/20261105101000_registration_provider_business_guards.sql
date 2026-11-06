alter table public.providers add column registration_document_hold boolean not null default false;
create function private.registration_provider_hold() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if not private.registration_enabled() then return new; end if;
 if (tg_op='INSERT' or (tg_op='UPDATE' and new.account_id is distinct from old.account_id)) and not exists(select 1 from private.registration_cases where user_id=new.account_id and account_type='PROVIDER' and (status='APPROVED' or not verification_required)) then
 new.active:=false; new.available:=false; new.registration_document_hold:=true;
 elsif tg_op='UPDATE' and private.registration_staff() and new.active=false then
 -- An explicit operational disablement must survive documentary approval.
 new.registration_document_hold:=false;
 end if;
 if not new.active then new.available:=false; end if;
 if new.available and not private.can_use_account_mode(new.account_id,'PROVIDER') then raise exception using errcode='42501',message='REGISTRATION_REQUIRED'; end if;
 return new;
end $$;
create trigger registration_provider_hold before insert or update on public.providers for each row execute function private.registration_provider_hold();
create function private.registration_table_access(p_owner uuid default null) returns boolean language sql stable security definer set search_path='' as $$
 select not private.registration_enabled() or auth.role()='service_role' or private.registration_staff()
 or (auth.uid() is not null and (p_owner=auth.uid() or private.can_use_account_mode(auth.uid(),'CLIENT') or private.can_use_account_mode(auth.uid(),'PROVIDER')));
$$;
revoke all on function private.registration_table_access(uuid) from public;
grant execute on function private.registration_table_access(uuid) to anon,authenticated;
create policy registration_provider_boundary on public.providers as restrictive for select to public using(private.registration_table_access(account_id));
create policy registration_address_boundary on public.addresses as restrictive for select to public using(private.registration_table_access((select p.account_id from public.providers p where p.id=provider_id)));
create policy registration_offer_boundary on public.provider_services as restrictive for select to public using(private.registration_table_access((select p.account_id from public.providers p where p.id=provider_id)));
do $$ declare t text; begin
 foreach t in array array['appointments','provider_current_locations','provider_discovery_locations','service_searches','client_current_locations','appointment_route_snapshots','saved_cards','payments','guincho_trip_drafts','guincho_quotes','appointment_disputes','guincho_inspection_versions','guincho_inspection_photos','guincho_inspection_version_photos','guincho_route_snapshots','service_ratings'] loop
 if to_regclass('public.'||t) is not null then
 execute format('create policy registration_business_boundary on public.%I as restrictive for all to public using(private.registration_table_access(null)) with check(private.registration_table_access(null))',t);
 end if;
 end loop;
end $$;
