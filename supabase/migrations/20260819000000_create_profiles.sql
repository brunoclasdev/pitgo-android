create schema if not exists private;

revoke all on schema private from public, anon, authenticated;

create or replace function private.is_valid_brazilian_phone(value text)
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
$$;

create or replace function private.is_valid_cpf(value text)
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
$$;

create or replace function private.is_valid_cnpj(value text)
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
$$;

revoke all on function private.is_valid_brazilian_phone(text) from public, anon, authenticated;
revoke all on function private.is_valid_cpf(text) from public, anon, authenticated;
revoke all on function private.is_valid_cnpj(text) from public, anon, authenticated;

grant usage on schema private to authenticated;
grant execute on function private.is_valid_brazilian_phone(text) to authenticated;
grant execute on function private.is_valid_cpf(text) to authenticated;
grant execute on function private.is_valid_cnpj(text) to authenticated;

create table public.profiles (
  id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  email text not null,
  phone text not null,
  cpf text,
  cnpj text,
  active boolean not null default true,
  constraint profiles_pkey primary key (id),
  constraint profiles_name_normalized check (name = btrim(name) and name <> ''),
  constraint profiles_email_normalized check (
    email = lower(btrim(email)) and email <> '' and email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'
  ),
  constraint profiles_phone_normalized check (
    phone ~ '^[0-9]{10,11}$' and private.is_valid_brazilian_phone(phone)
  ),
  constraint profiles_exactly_one_document check (num_nonnulls(cpf, cnpj) = 1),
  constraint profiles_cpf_normalized check (cpf is null or cpf ~ '^[0-9]{11}$'),
  constraint profiles_cpf_valid check (cpf is null or private.is_valid_cpf(cpf)),
  constraint profiles_cnpj_normalized check (
    cnpj is null or (cnpj = upper(cnpj) and cnpj ~ '^[0-9A-Z]{12}[0-9]{2}$')
  ),
  constraint profiles_cnpj_valid check (cnpj is null or private.is_valid_cnpj(cnpj)),
  constraint profiles_email_unique unique (email),
  constraint profiles_cpf_unique unique (cpf),
  constraint profiles_cnpj_unique unique (cnpj)
);

alter table public.profiles enable row level security;

revoke all on table public.profiles from anon, authenticated;
grant select, insert on table public.profiles to authenticated;

create policy profiles_select_own
on public.profiles
for select
to authenticated
using ((select auth.uid()) = id);

create policy profiles_insert_own
on public.profiles
for insert
to authenticated
with check (
  (select auth.uid()) = id
  and email = lower(coalesce((select auth.jwt() ->> 'email'), ''))
  and active = true
);

create or replace function private.handle_new_user_profile()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  normalized_cpf text;
  normalized_cnpj text;
begin
  normalized_cpf := nullif(
    regexp_replace(coalesce(new.raw_user_meta_data ->> 'cpf', ''), '[^0-9]', '', 'g'),
    ''
  );
  normalized_cnpj := nullif(
    regexp_replace(upper(coalesce(new.raw_user_meta_data ->> 'cnpj', '')), '[^0-9A-Z]', '', 'g'),
    ''
  );

  insert into public.profiles (id, name, email, phone, cpf, cnpj)
  values (
    new.id,
    btrim(coalesce(new.raw_user_meta_data ->> 'name', '')),
    lower(btrim(coalesce(new.email, ''))),
    regexp_replace(coalesce(new.raw_user_meta_data ->> 'phone', ''), '[^0-9]', '', 'g'),
    normalized_cpf,
    normalized_cnpj
  );

  return new;
end;
$$;

revoke all on function private.handle_new_user_profile() from public, anon, authenticated;

create trigger on_auth_user_created_create_profile
after insert on auth.users
for each row execute function private.handle_new_user_profile();
