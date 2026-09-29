-- Consolidate legacy category names into the seven supported service categories.
-- Existing category/service/provider IDs and provider offers are preserved.
begin;

do $$
declare
  old_count integer;
begin
  select count(*) into old_count
  from public.service_categories
  where name in (
    'Ar-condicionado', 'Elétrica', 'Bateria', 'Chaveiro automotivo',
    'Diagnóstico', 'Estética automotiva', 'Freios', 'Funilaria',
    'Higienização', 'Pequenos reparos', 'Suspensão', 'Troca de óleo', 'Vidros'
  );
  if old_count <> 13 then
    raise exception 'LEGACY_CATEGORY_SET_CHANGED';
  end if;
end;
$$;

-- Move the implicit mobile service to the canonical Guincho category and the
-- legacy battery offer to Auto elétrica. IDs remain unchanged.
update public.services
set category_id = (select id from public.service_categories where name = 'Guincho')
where dispatch_kind = 'MOBILE_SEQUENTIAL';

update public.services
set category_id = (select id from public.service_categories where name = 'Auto elétrica')
where category_id = (select id from public.service_categories where name = 'Bateria')
  and dispatch_kind = 'BROWSE';

-- Rebuild the provider category projection from the provider's actual offers.
insert into public.provider_categories(provider_id, category_id)
select distinct offer.provider_id, service.category_id
from public.provider_services offer
join public.services service on service.id = offer.service_id
on conflict (provider_id, category_id) do nothing;

delete from public.provider_categories
where category_id in (
  select id from public.service_categories where name in (
    'Ar-condicionado', 'Elétrica', 'Bateria', 'Chaveiro automotivo',
    'Diagnóstico', 'Estética automotiva', 'Freios', 'Funilaria',
    'Higienização', 'Pequenos reparos', 'Suspensão', 'Troca de óleo', 'Vidros'
  )
);

update public.service_categories
set active = false, updated_at = now()
where name in (
  'Ar-condicionado', 'Elétrica', 'Bateria', 'Chaveiro automotivo',
  'Diagnóstico', 'Estética automotiva', 'Freios', 'Funilaria',
  'Higienização', 'Pequenos reparos', 'Suspensão', 'Troca de óleo', 'Vidros'
);

commit;
