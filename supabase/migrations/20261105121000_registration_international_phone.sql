begin;
-- Keep legacy Brazilian profiles valid while accepting international registration numbers.
create or replace function private.is_valid_registration_phone(value text)
returns boolean language sql immutable set search_path='' as $$
 select case when value like '+%' then value ~ '^\+[1-9][0-9]{6,14}$'
 else private.is_valid_brazilian_phone(value) end;
$$;
revoke all on function private.is_valid_registration_phone(text) from public, anon;
grant execute on function private.is_valid_registration_phone(text) to authenticated;
alter table public.profiles drop constraint if exists profiles_phone_normalized;
alter table public.profiles add constraint profiles_phone_normalized
 check (private.is_valid_registration_phone(phone));
create or replace function private.handle_verified_registration_signup() returns trigger language plpgsql security definer set search_path='' as $$
declare mode text:=new.raw_user_meta_data->>'account_type'; f text:=btrim(new.raw_user_meta_data->>'first_name');
 l text:=btrim(new.raw_user_meta_data->>'last_name'); phone text:=btrim(new.raw_user_meta_data->>'phone');
 cpf text:=nullif(regexp_replace(new.raw_user_meta_data->>'cpf','[^0-9]','','g'),''); cnpj text:=nullif(regexp_replace(upper(new.raw_user_meta_data->>'cnpj'),'[^0-9A-Z]','','g'),''); cid uuid; sid uuid;
begin
 if new.raw_app_meta_data->>'dashboard_staff'='true' then return new; end if;
 if mode is null or mode not in ('CLIENT','PROVIDER') or coalesce(f,'')='' or coalesce(l,'')='' or
 (mode='PROVIDER' and (cpf is not null or not coalesce(private.is_valid_cnpj(cnpj),false))) or
 (mode='CLIENT' and (num_nonnulls(cpf,cnpj)<>1 or not (coalesce(private.is_valid_cpf(cpf),false) or coalesce(private.is_valid_cnpj(cnpj),false)))) or not coalesce(private.is_valid_registration_phone(phone),false) then
 raise exception using errcode='22023',message='INVALID_REGISTRATION'; end if;
 insert into private.registration_identities(user_id,first_name,last_name,email,phone,cpf,cnpj)
 values(new.id,f,l,lower(new.email),phone,cpf,cnpj);
 insert into public.account_roles(user_id,account_type) values(new.id,mode);
 if mode='CLIENT' then
 insert into public.profiles(id,name,email,phone,cpf,cnpj,active) values(new.id,f||' '||l,lower(new.email),phone,cpf,cnpj,true);
 end if;
 insert into private.registration_cases(user_id,account_type) values(new.id,mode) returning id into cid;
 insert into private.registration_submissions(case_id,version) values(cid,1) returning id into sid;
 update private.registration_cases set current_submission_id=sid where id=cid;
 return new;
end $$;

commit;
