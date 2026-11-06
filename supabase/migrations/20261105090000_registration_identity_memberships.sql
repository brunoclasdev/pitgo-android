-- Ensure canonical validators exist even on deployments with historical schema drift.
do $install$ begin if to_regprocedure('private.is_valid_brazilian_phone(text)') is null then execute $definition$create or replace function private.is_valid_brazilian_phone(value text)
returns boolean
language sql
immutable
strict
set search_path = ''
as $$
  select value ~ '^[0-9]{10,11}$'
    and substring(value from 1 for 2) = any (array[
      '11','12','13','14','15','16','17','18','19',
      '21','22','24','27','28',
      '31','32','33','34','35','37','38',
      '41','42','43','44','45','46','47','48','49',
      '51','53','54','55',
      '61','62','63','64','65','66','67','68','69',
      '71','73','74','75','77','79',
      '81','82','83','84','85','86','87','88','89',
      '91','92','93','94','95','96','97','98','99'
    ]);
$$;$definition$; end if; end $install$;
do $install$ begin if to_regprocedure('private.is_valid_cpf(text)') is null then execute $definition$create or replace function private.is_valid_cpf(value text)
returns boolean
language plpgsql
immutable
strict
set search_path = ''
as $$
declare
  first_digit integer;
  second_digit integer;
  total integer := 0;
  index integer;
begin
  if value !~ '^[0-9]{11}$' or value ~ '^([0-9])\1{10}$' then
    return false;
  end if;

  for index in 1..9 loop
    total := total + substring(value from index for 1)::integer * (11 - index);
  end loop;
  first_digit := case when total % 11 < 2 then 0 else 11 - total % 11 end;

  total := 0;
  for index in 1..10 loop
    total := total + substring(value from index for 1)::integer * (12 - index);
  end loop;
  second_digit := case when total % 11 < 2 then 0 else 11 - total % 11 end;

  return first_digit = substring(value from 10 for 1)::integer
    and second_digit = substring(value from 11 for 1)::integer;
end;
$$;$definition$; end if; end $install$;
do $install$ begin if to_regprocedure('private.is_valid_cnpj(text)') is null then execute $definition$create or replace function private.is_valid_cnpj(value text)
returns boolean
language plpgsql
immutable
strict
set search_path = ''
as $$
declare
  first_weights integer[] := array[5,4,3,2,9,8,7,6,5,4,3,2];
  second_weights integer[] := array[6,5,4,3,2,9,8,7,6,5,4,3,2];
  first_digit integer;
  second_digit integer;
  total integer := 0;
  index integer;
begin
  if value !~ '^[0-9A-Z]{12}[0-9]{2}$'
    or value ~ '^([0-9])\1{13}$'
  then
    return false;
  end if;

  for index in 1..12 loop
    total := total + (ascii(substring(value from index for 1)) - 48) * first_weights[index];
  end loop;
  first_digit := case when total % 11 < 2 then 0 else 11 - total % 11 end;

  total := 0;
  for index in 1..12 loop
    total := total + (ascii(substring(value from index for 1)) - 48) * second_weights[index];
  end loop;
  total := total + first_digit * second_weights[13];
  second_digit := case when total % 11 < 2 then 0 else 11 - total % 11 end;

  return first_digit = substring(value from 13 for 1)::integer
    and second_digit = substring(value from 14 for 1)::integer;
end;
$$;$definition$; end if; end $install$;
-- Prepare coexistence without changing current authorization until the atomic cutover.
create table private.registration_runtime(singleton boolean primary key default true check(singleton), enabled boolean not null default false);
insert into private.registration_runtime values(true,false);
create table private.registration_identities(
 user_id uuid primary key references auth.users(id), first_name text, last_name text,
 email text not null unique, phone text, cpf text unique, created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(), check(cpf is null or private.is_valid_cpf(cpf))
);
alter table public.account_roles add column if not exists registration_primary boolean not null default true;
alter table public.account_roles drop constraint account_roles_pkey;
alter table public.account_roles add primary key(user_id,account_type);
create unique index account_roles_one_primary on public.account_roles(user_id) where registration_primary;
revoke all on private.registration_runtime,private.registration_identities from public,anon,authenticated;
