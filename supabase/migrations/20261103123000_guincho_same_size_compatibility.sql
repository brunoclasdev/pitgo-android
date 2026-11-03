begin;
-- Permit transport only for an explicitly matching size; larger-size capacity
-- must be configured separately by the administrator.
insert into public.guincho_size_compatibility(size_id,vehicle_category_id)
select s.id,c.id from public.guincho_sizes s
join public.vehicle_transport_categories c on c.code=s.code
where s.active and c.active
on conflict do nothing;
commit;
