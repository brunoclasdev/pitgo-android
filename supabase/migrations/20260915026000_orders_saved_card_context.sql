begin;
alter table public.saved_cards add column payment_method_id text,
  add column payment_type_id text check (payment_type_id in ('credit_card','debit_card'));
create function public.get_saved_card_tokenization_context(p_saved_card_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare result jsonb;
begin
  select jsonb_build_object('saved_card_id',c.id,'card_id',c.mp_card_id,
    'payment_method_id',c.payment_method_id,'payment_type_id',c.payment_type_id,
    'holder_name',p.name,'document_number',p.cpf,'document_type','CPF') into result
  from public.saved_cards c join public.profiles p on p.id=c.client_id
  where c.id=p_saved_card_id and c.client_id=auth.uid();
  if result is null then raise exception using errcode='P0002',message='CARD_NOT_FOUND'; end if;
  return result;
end;
$$;
revoke all on function public.get_saved_card_tokenization_context(uuid) from public,anon,authenticated;
grant execute on function public.get_saved_card_tokenization_context(uuid) to authenticated;
commit;
