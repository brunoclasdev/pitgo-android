create or replace function private.project_registration_approval(p_case uuid) returns void language plpgsql security definer set search_path='' as $$
declare c private.registration_cases; d jsonb; p jsonb; a jsonb; pid uuid;
begin
 select * into c from private.registration_cases where id=p_case;
 select snapshot into d from private.registration_submissions where id=c.approved_submission_id;
 a:=d->'address'; p:=d->'provider';
 if c.account_type='PROVIDER' then
 select id into pid from public.providers where account_id=c.user_id;
 if pid is null then raise exception using errcode='22023',message='INCOMPLETE_REGISTRATION'; end if;
 update public.providers set name=p->>'name',email=p->>'email',phone=p->>'phone',cnpj=p->>'cnpj',description=p->>'description',
 location_mode=p->>'location_mode',active=case when registration_document_hold then true else active end,registration_document_hold=false where id=pid;
 update public.addresses set street=a->>'street',number=a->>'number',neighborhood=a->>'neighborhood',zip_code=a->>'zip_code',city=a->>'city',state=a->>'state',complement=a->>'complement' where provider_id=pid;
 delete from public.provider_services o where o.provider_id=pid and not exists(select 1 from jsonb_array_elements(d->'offers') item where item->>'service_id'=o.service_id::text);
 insert into public.provider_services(provider_id,service_id,price,duration_minutes,description,is_free)
 select pid,(item->>'service_id')::uuid,(item->>'price')::numeric,(item->>'duration_minutes')::integer,item->>'description',coalesce((item->>'is_free')::boolean,false) from jsonb_array_elements(d->'offers') item
 on conflict(provider_id,service_id) do update set price=excluded.price,duration_minutes=excluded.duration_minutes,description=excluded.description,is_free=excluded.is_free;
 else
 if not exists(select 1 from public.profiles where id=c.user_id) then
 insert into public.profiles(id,name,email,phone,cpf,active)
 select user_id,first_name||' '||last_name,email,phone,cpf,true from private.registration_identities where user_id=c.user_id;
 end if;
 insert into public.client_addresses(client_id,street,number,neighborhood,zip_code,city,state,complement)
 values(c.user_id,a->>'street',a->>'number',a->>'neighborhood',a->>'zip_code',a->>'city',a->>'state',a->>'complement')
 on conflict(client_id) do update set street=excluded.street,number=excluded.number,neighborhood=excluded.neighborhood,zip_code=excluded.zip_code,city=excluded.city,state=excluded.state,complement=excluded.complement;
 end if;
end $$;

revoke all on function private.project_registration_approval(uuid) from public,anon,authenticated;
