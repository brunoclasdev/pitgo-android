begin;
create table public.store_products (
 id uuid primary key default gen_random_uuid(), store_provider_id uuid not null references public.providers(id) on delete cascade,
 name text not null check(length(btrim(name)) between 1 and 150),
 description text not null check(length(btrim(description)) between 1 and 2000),
 price numeric(12,2) not null check(price>0), image_path text,
 active boolean not null default true, created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 check(image_path is null or image_path like store_provider_id::text||'/'||id::text||'/%')
);
create index store_products_store on public.store_products(store_provider_id,created_at desc);
alter table public.store_products enable row level security;
revoke all on public.store_products from public,anon,authenticated;
grant select on public.store_products to authenticated;
create policy store_product_read on public.store_products for select to authenticated using (
 exists(select 1 from public.providers p where p.id=store_provider_id and p.provider_kind='STORE'
 and (p.account_id=auth.uid() or (store_products.active and p.active))));

create function public.create_store_registration(p_provider jsonb,p_address jsonb,p_offers jsonb default '[]'::jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
begin
 return public.create_provider_registration(p_provider||jsonb_build_object('provider_kind','STORE','location_mode','FIXED'),p_address,'[]'::jsonb);
end $$;
create function public.list_stores() returns jsonb language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
 return coalesce((select jsonb_agg(jsonb_build_object('id',p.id,'name',p.name,'description',p.description,'city',a.city) order by p.name)
 from public.providers p join public.addresses a on a.provider_id=p.id where p.provider_kind='STORE' and p.active),'[]'::jsonb);
end $$;
create function public.list_store_products(p_store_id uuid) returns jsonb language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception using errcode='42501',message='SESSION_REQUIRED'; end if;
 return coalesce((select jsonb_agg(to_jsonb(product) order by product.created_at desc)
 from public.store_products product join public.providers p on p.id=product.store_provider_id
 where p.id=p_store_id and p.provider_kind='STORE' and (p.account_id=auth.uid() or (p.active and product.active))),'[]'::jsonb);
end $$;
create function public.save_store_product(p_product jsonb) returns jsonb language plpgsql security definer set search_path='' as $$
declare owned uuid; result public.store_products%rowtype; product_id uuid:=(p_product->>'id')::uuid;
begin
 select id into owned from public.providers where account_id=auth.uid() and provider_kind='STORE' and active;
 if owned is null then raise exception using errcode='42501',message='STORE_OWNER_REQUIRED'; end if;
 if product_id is null or length(btrim(coalesce(p_product->>'name',''))) not between 1 and 150
 or length(btrim(coalesce(p_product->>'description',''))) not between 1 and 2000 or (p_product->>'price') is null
 or (p_product->>'price')::numeric<=0 then raise exception using errcode='22023',message='INVALID_PRODUCT'; end if;
 insert into public.store_products(id,store_provider_id,name,description,price,image_path,active)
 values(product_id,owned,btrim(p_product->>'name'),btrim(p_product->>'description'),(p_product->>'price')::numeric,
 nullif(p_product->>'image_path',''),coalesce((p_product->>'active')::boolean,true))
 on conflict(id) do update set name=excluded.name,description=excluded.description,price=excluded.price,
 image_path=excluded.image_path,active=excluded.active,updated_at=now() where store_products.store_provider_id=owned
 returning * into result;
 if result.id is null then raise exception using errcode='42501',message='STORE_OWNER_REQUIRED'; end if;
 return to_jsonb(result);
end $$;
create function public.delete_store_product(p_product_id uuid) returns void language plpgsql security definer set search_path='' as $$
begin
 delete from public.store_products product using public.providers p
 where product.id=p_product_id and product.store_provider_id=p.id and p.account_id=auth.uid() and p.provider_kind='STORE';
 if not found then raise exception using errcode='42501',message='STORE_OWNER_REQUIRED'; end if;
end $$;
revoke all on function public.create_store_registration(jsonb,jsonb,jsonb),public.list_stores(),public.list_store_products(uuid),public.save_store_product(jsonb),public.delete_store_product(uuid) from public,anon;
grant execute on function public.create_store_registration(jsonb,jsonb,jsonb),public.list_stores(),public.list_store_products(uuid),public.save_store_product(jsonb),public.delete_store_product(uuid) to authenticated;
commit;
