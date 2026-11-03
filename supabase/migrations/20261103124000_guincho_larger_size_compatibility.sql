begin;

-- A tow truck may transport vehicles of its own size or any smaller size.
with size_order(code, capacity) as (
 values ('LEVE',1),('MEDIO',2),('PESADO',3),('EXTRAPESADO',4),('ESPECIALIZADO',5)
)
insert into public.guincho_size_compatibility(size_id,vehicle_category_id)
select s.id,c.id
from public.guincho_sizes s
join size_order truck on truck.code=s.code
join public.vehicle_transport_categories c on c.active
join size_order vehicle on vehicle.code=c.code and vehicle.capacity<=truck.capacity
where s.active
on conflict do nothing;

commit;
