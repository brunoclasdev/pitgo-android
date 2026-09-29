-- The remote legacy schema has only category name uniqueness by exact spelling,
-- and no service-name uniqueness. Enforce the catalog RPC's normalized-name contract.
-- Existing IDs, category associations and offers remain unchanged.
begin;
create unique index if not exists service_categories_name_normalized_unique
  on public.service_categories(lower(btrim(name)));
create unique index if not exists services_category_name_normalized_unique
  on public.services(category_id, lower(btrim(name)));
commit;
