# Contract: RPCs do PitTec ajustadas (compartilhadas com o iOS)

## Status: migration nova, aditiva, compartilhada

Estas três funções já existem no projeto Supabase compartilhado (`015`/`018`); esta feature altera
o corpo de todas as três via `CREATE OR REPLACE FUNCTION` numa única migration forward-only. Nenhuma
tabela, coluna ou RPC nova. **Mudança de backend compartilhado** — o app iOS usa as mesmas funções e
deve ser atualizado separadamente para acompanhar (fora do escopo desta spec, que é Android-only).

## `private.pittec_dispatch_snapshot(p_search_id uuid)` — adiciona `candidate`

Mesma assinatura e mesmo corpo de hoje, com um bloco novo no `jsonb_build_object` final:

```sql
'candidate', case
  when caller_is_client and appointment_row.status = 'PENDING' then jsonb_build_object(
    'provider_name', (select provider.name from public.providers as provider
                       where provider.id = appointment_row.provider_id),
    'approximate_distance_meters', round(candidate_row.distance_meters / 500) * 500
  ) else null
end,
```

Inserido ao lado do bloco `'offer'` já existente (mesma fonte de dados, papel do chamador
diferente). `jsonb_strip_nulls` (já usado na função) garante que o campo simplesmente não aparece
quando `null`, sem quebrar nenhum consumidor existente que ainda não conhece `candidate`.

## `public.start_pittec_dispatch(...)` — remove a guarda `PAYMENT_REQUIRED`

Remove exatamente este bloco (hoje logo após a validação de `p_service_id`/coordenadas):

```sql
if not exists (
  select 1 from public.payments
   where request_id = p_request_id
     and client_id = auth.uid()
     and status = 'APPROVED'
) then
  raise exception using errcode = 'P0001', message = 'PAYMENT_REQUIRED';
end if;
```

Nenhuma outra linha da função muda — a busca passa a iniciar normalmente sem esse pré-requisito.

## `public.arrive_pittec_appointment(p_request_id, p_appointment_id, p_expected_revision)` — adiciona a guarda

Adiciona, logo depois de confirmar `appointment_row.status = 'ACCEPTED'` (antes do `update`):

```sql
if not exists (
  select 1 from public.payments
   where request_id = appointment_row.dispatch_search_id
     and status = 'APPROVED'
) then
  raise exception using errcode = 'P0001', message = 'PAYMENT_REQUIRED';
end if;
```

**Efeito**: se o prestador tentar marcar "cheguei" antes de o pagamento do cliente ter sido
aprovado, a transição falha com `PAYMENT_REQUIRED` (o app do prestador trata isso como uma falha
genérica de rede/estado — não é esperado ocorrer no fluxo feliz, já que o pagamento roda em segundos
depois do aceite, muito antes de o prestador chegar fisicamente).

## Client: `mp-create-payment` (`018`, sem mudança de contrato)

Continua recebendo `request_id` (= `search_id`), `dispatch_kind = "PITTEC_AUTO_SEQUENTIAL"`, e
`amount` = a mesma tarifa fixa de plataforma já usada em `018`. A única mudança é **quem chama e
quando**: hoje é chamado a partir da tela de Home antes de `start_pittec_dispatch`; a partir desta
spec é chamado a partir de `PitTecViewModel`, no momento em que o snapshot chega a
`ConfirmingValues` (ver `data-model.md`), reaproveitando o mesmo `PaymentRepository`/
`PaymentCheckoutViewModel` já existentes.

## Navegação (Android) — remove o salto por `PitGoRoutes.Payment`

`HomeRoute`, ao tocar num serviço com `dispatchKind == PITTEC_AUTO_SEQUENTIAL`, passa a navegar
direto para `PitGoRoutes.pitTec(serviceId)` (sem `requestId`), em vez de
`PitGoRoutes.payment(serviceId, "PITTEC_AUTO_SEQUENTIAL")`. `PitTecViewModel` deixa de receber
`initialRequestId` vindo de navegação — o `requestId` volta a ser sempre gerado internamente
(`UUID.randomUUID()`) no momento de `requestPitTec()`, como já acontece hoje para qualquer chamada
que não venha de um pagamento prévio. `MOBILE_SEQUENTIAL` (guincho móvel) **não muda** — continua
passando por `PitGoRoutes.Payment` antes do despacho, como já implementado em `018`.
