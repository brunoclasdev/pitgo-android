create or replace function public.create_vehicle(
  p_brand text,
  p_model text,
  p_version text,
  p_manufacture_year integer,
  p_model_year integer,
  p_plate text,
  p_renavam text,
  p_chassis text,
  p_color text,
  p_fuel_type text,
  p_transmission text,
  p_engine text,
  p_mileage integer,
  p_make_primary boolean
)
returns setof public.vehicles
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := auth.uid();
  normalized_brand text := pg_catalog.btrim(p_brand);
  normalized_model text := pg_catalog.btrim(p_model);
  normalized_plate text := nullif(
    pg_catalog.regexp_replace(
      pg_catalog.upper(pg_catalog.btrim(p_plate)),
      '[^A-Z0-9]',
      '',
      'g'
    ),
    ''
  );
  normalized_renavam text := nullif(pg_catalog.btrim(p_renavam), '');
  normalized_chassis text := nullif(pg_catalog.upper(pg_catalog.btrim(p_chassis)), '');
  maximum_year integer := extract(year from current_date)::integer + 1;
  should_be_primary boolean;
  inserted_vehicle public.vehicles%rowtype;
  violated_constraint text;
begin
  if current_user_id is null or not private.has_account_type('CLIENT') then
    raise exception using errcode = '42501', message = 'vehicle operation unavailable';
  end if;

  if not exists (
    select 1
    from public.profiles profile
    where profile.id = current_user_id and profile.active = true
  ) then
    raise exception using errcode = '42501', message = 'vehicle operation unavailable';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(current_user_id::text, 12012)
  );

  if normalized_brand is null or normalized_brand = '' then
    raise exception using errcode = 'P2001', message = 'invalid vehicle field';
  end if;
  if normalized_model is null or normalized_model = '' then
    raise exception using errcode = 'P2002', message = 'invalid vehicle field';
  end if;
  if p_plate is not null and pg_catalog.btrim(p_plate) <> '' and (
    normalized_plate is null or (
      normalized_plate !~ '^[A-Z]{3}[0-9]{4}$' and
      normalized_plate !~ '^[A-Z]{3}[0-9][A-Z][0-9]{2}$'
    )
  ) then
    raise exception using errcode = 'P2003', message = 'invalid vehicle field';
  end if;
  if normalized_renavam is not null and normalized_renavam !~ '^[0-9]{11}$' then
    raise exception using errcode = 'P2004', message = 'invalid vehicle field';
  end if;
  if normalized_chassis is not null and normalized_chassis !~ '^[A-HJ-NPR-Z0-9]{17}$' then
    raise exception using errcode = 'P2005', message = 'invalid vehicle field';
  end if;
  if p_manufacture_year is not null and
    (p_manufacture_year < 1900 or p_manufacture_year > maximum_year)
  then
    raise exception using errcode = 'P2006', message = 'invalid vehicle field';
  end if;
  if p_model_year is not null and (p_model_year < 1900 or p_model_year > maximum_year) then
    raise exception using errcode = 'P2007', message = 'invalid vehicle field';
  end if;
  if p_mileage is not null and p_mileage < 0 then
    raise exception using errcode = 'P2008', message = 'invalid vehicle field';
  end if;

  should_be_primary := pg_catalog.coalesce(p_make_primary, false) or not exists (
    select 1
    from public.vehicles vehicle
    where vehicle.user_id = current_user_id and vehicle.active = true
  ) or not exists (
    select 1
    from public.vehicles vehicle
    where vehicle.user_id = current_user_id
      and vehicle.active = true
      and vehicle.is_primary = true
  );

  if should_be_primary then
    update public.vehicles
    set is_primary = false, updated_at = pg_catalog.now()
    where user_id = current_user_id and is_primary = true;
  end if;

  insert into public.vehicles (
    user_id,
    brand,
    model,
    version,
    manufacture_year,
    model_year,
    plate,
    renavam,
    chassis,
    color,
    fuel_type,
    transmission,
    engine,
    mileage,
    is_primary,
    active
  ) values (
    current_user_id,
    normalized_brand,
    normalized_model,
    nullif(pg_catalog.btrim(p_version), ''),
    p_manufacture_year,
    p_model_year,
    normalized_plate,
    normalized_renavam,
    normalized_chassis,
    nullif(pg_catalog.btrim(p_color), ''),
    nullif(pg_catalog.btrim(p_fuel_type), ''),
    nullif(pg_catalog.btrim(p_transmission), ''),
    nullif(pg_catalog.btrim(p_engine), ''),
    p_mileage,
    should_be_primary,
    true
  )
  returning * into inserted_vehicle;

  return next inserted_vehicle;
exception
  when unique_violation then
    get stacked diagnostics violated_constraint = constraint_name;
    case violated_constraint
      when 'vehicles_plate_unique' then
        raise exception using errcode = 'P2011', message = 'vehicle field unavailable';
      when 'vehicles_renavam_unique' then
        raise exception using errcode = 'P2012', message = 'vehicle field unavailable';
      when 'vehicles_chassis_unique' then
        raise exception using errcode = 'P2013', message = 'vehicle field unavailable';
      else
        raise exception using errcode = 'P2099', message = 'vehicle operation unavailable';
    end case;
end;
$$;

create or replace function public.set_primary_vehicle(p_vehicle_id uuid)
returns setof public.vehicles
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := auth.uid();
  selected_vehicle public.vehicles%rowtype;
begin
  if current_user_id is null or not private.has_account_type('CLIENT') then
    raise exception using errcode = '42501', message = 'vehicle operation unavailable';
  end if;

  if not exists (
    select 1
    from public.profiles profile
    where profile.id = current_user_id and profile.active = true
  ) then
    raise exception using errcode = '42501', message = 'vehicle operation unavailable';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(current_user_id::text, 12012)
  );

  select vehicle.*
  into selected_vehicle
  from public.vehicles vehicle
  where vehicle.id = p_vehicle_id
    and vehicle.user_id = current_user_id
    and vehicle.active = true
  for update;

  if not found then
    raise exception using errcode = 'P0002', message = 'vehicle unavailable';
  end if;

  update public.vehicles
  set is_primary = false, updated_at = pg_catalog.now()
  where user_id = current_user_id and is_primary = true and id <> p_vehicle_id;

  update public.vehicles
  set is_primary = true, updated_at = pg_catalog.now()
  where id = p_vehicle_id and user_id = current_user_id and active = true
  returning * into selected_vehicle;

  return next selected_vehicle;
end;
$$;

revoke all on function public.create_vehicle(
  text, text, text, integer, integer, text, text, text, text, text, text, text, integer, boolean
) from public, anon, authenticated;
revoke all on function public.set_primary_vehicle(uuid) from public, anon, authenticated;

grant execute on function public.create_vehicle(
  text, text, text, integer, integer, text, text, text, text, text, text, text, integer, boolean
) to authenticated;
grant execute on function public.set_primary_vehicle(uuid) to authenticated;

notify pgrst, 'reload schema';
