-- Installer only: invoked under locks in the final atomic migration.
create function private.install_registration_accounts() returns void language plpgsql security definer set search_path='' as $install$
begin
 insert into public.account_roles(user_id,account_type,registration_primary)
 select account_id,'PROVIDER',false from public.providers on conflict(user_id,account_type) do nothing;
 insert into private.registration_identities(user_id,first_name,email,phone,cpf)
 select p.id,p.name,p.email,p.phone,p.cpf from public.profiles p on conflict(user_id) do nothing;
 insert into private.registration_cases(user_id,account_type,verification_required,legacy_exempt_at,status)
 select r.user_id,r.account_type,false,now(),null from public.account_roles r
 on conflict(user_id,account_type) do nothing;
end $install$;
create function private.handle_verified_registration_signup() returns trigger language plpgsql security definer set search_path='' as $$
declare mode text:=new.raw_user_meta_data->>'account_type'; f text:=btrim(new.raw_user_meta_data->>'first_name');
 l text:=btrim(new.raw_user_meta_data->>'last_name'); phone text:=regexp_replace(new.raw_user_meta_data->>'phone','[^0-9]','','g');
 cpf text:=regexp_replace(new.raw_user_meta_data->>'cpf','[^0-9]','','g'); cid uuid; sid uuid;
begin
 if new.raw_app_meta_data->>'dashboard_staff'='true' then return new; end if;
 if mode is null or mode not in ('CLIENT','PROVIDER') or coalesce(f,'')='' or coalesce(l,'')='' or
 not coalesce(private.is_valid_cpf(cpf),false) or not coalesce(private.is_valid_brazilian_phone(phone),false) then
 raise exception using errcode='22023',message='INVALID_REGISTRATION'; end if;
 insert into private.registration_identities(user_id,first_name,last_name,email,phone,cpf)
 values(new.id,f,l,lower(new.email),phone,cpf);
 insert into public.account_roles(user_id,account_type) values(new.id,mode);
 if mode='CLIENT' then
 insert into public.profiles(id,name,email,phone,cpf,active) values(new.id,f||' '||l,lower(new.email),phone,cpf,true);
 end if;
 insert into private.registration_cases(user_id,account_type) values(new.id,mode) returning id into cid;
 insert into private.registration_submissions(case_id,version) values(cid,1) returning id into sid;
 update private.registration_cases set current_submission_id=sid where id=cid;
 return new;
end $$;
revoke all on function private.install_registration_accounts(),private.handle_verified_registration_signup() from public,anon,authenticated;
