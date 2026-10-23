begin;
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
 values('store-product-images','store-product-images',false,5242880,array['image/jpeg','image/png','image/webp']);
create policy store_image_owner_insert on storage.objects for insert to authenticated with check(
 bucket_id='store-product-images' and exists(select 1 from public.providers p where p.id::text=(storage.foldername(storage.objects.name))[1]
 and p.account_id=auth.uid() and p.provider_kind='STORE'));
create policy store_image_owner_update on storage.objects for update to authenticated using(
 bucket_id='store-product-images' and exists(select 1 from public.providers p where p.id::text=(storage.foldername(storage.objects.name))[1]
 and p.account_id=auth.uid() and p.provider_kind='STORE')) with check(
 bucket_id='store-product-images' and exists(select 1 from public.providers p where p.id::text=(storage.foldername(storage.objects.name))[1]
 and p.account_id=auth.uid() and p.provider_kind='STORE'));
create policy store_image_owner_delete on storage.objects for delete to authenticated using(
 bucket_id='store-product-images' and exists(select 1 from public.providers p where p.id::text=(storage.foldername(storage.objects.name))[1]
 and p.account_id=auth.uid() and p.provider_kind='STORE'));
create policy store_image_read on storage.objects for select to authenticated using(
 bucket_id='store-product-images' and (exists(select 1 from public.providers p where p.id::text=(storage.foldername(storage.objects.name))[1] and p.account_id=auth.uid() and p.provider_kind='STORE')
 or exists(select 1 from public.store_products product join public.providers p on p.id=product.store_provider_id
 where product.image_path=storage.objects.name and product.active and p.active and p.provider_kind='STORE')));
commit;
