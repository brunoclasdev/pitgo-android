# Contract: Edge Functions do Mercado Pago

Todas seguem o padrão já usado por `geocode-provider-address`/`pittec-route`: verificam a sessão do
usuário com o client anon + `Authorization` repassado, usam um client com a service role só para as
gravações privilegiadas, e mantêm os segredos do Mercado Pago exclusivamente em
`Deno.env.get(...)` (Supabase Secrets). Nenhuma delas aceita o Access Token vindo do app.

**Segredos esperados**: `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET` (produção e sandbox como segredos
separados, trocados por ambiente de deploy).

## `mp-create-payment`

**Request** (autenticado, `POST`):

```json
{
  "request_id": "uuid",
  "dispatch_kind": "MOBILE_SEQUENTIAL | PITTEC_AUTO_SEQUENTIAL",
  "amount": 129.90,
  "method": "PIX | SAVED_CARD",
  "saved_card_id": "uuid (obrigatório quando method = SAVED_CARD)",
  "card_security_code_token": "string (token curto do SDK p/ CVV na hora da cobrança, quando exigido)"
}
```

**Comportamento**:
1. Valida sessão; rejeita se `amount <= 0` ou dados inconsistentes com `400 INVALID_INPUT`.
2. Verifica via RPC se já existe `payments` `PENDING`/`APPROVED` para o `request_id`; se existir,
   devolve o registro existente em vez de criar outro (idempotência, ver research.md §6).
3. Monta a chamada ao Mercado Pago (`POST /v1/payments`) com `X-Idempotency-Key: request_id`,
   `external_reference = request_id`, `payer.identification = { type: "CPF", number: <cpf> }`.
4. Grava/atualiza a linha em `payments` com o `mp_payment_id` e o `status` inicial devolvido.
5. Responde ao app.

**Response** (`200`):

```json
{
  "payment_id": "uuid (interno)",
  "status": "PENDING | APPROVED | REJECTED",
  "pix": { "qr_code": "copia-e-cola", "qr_code_base64": "...", "expires_at": "ISO-8601" }
}
```
`pix` só aparece quando `method = PIX`.

**Erros**: `401 SESSION_REQUIRED`, `400 INVALID_INPUT`, `409 DUPLICATE_REQUEST` (corrida rara entre
duas chamadas simultâneas para o mesmo `request_id`), `502 PROVIDER_UNAVAILABLE` (Mercado Pago fora
do ar — app orienta tentar novamente).

## `mp-save-card`

**Request** (autenticado, `POST`): `{ "card_token": "token gerado pelo SDK no app" }`

**Comportamento**: garante um `mp_customer_id` para o `client_id` (cria se ainda não existir),
associa o `card_token` como Customer Card, grava `saved_cards` com bandeira/últimos 4 dígitos.

**Response** (`200`): `{ "saved_card_id": "uuid", "brand": "visa", "last_four_digits": "4242" }`

**Erros**: `401 SESSION_REQUIRED`, `422 CARD_REJECTED` (cartão recusado/token inválido — mensagem
compreensível ao cliente, FR-004/US2 cenário 2), `502 PROVIDER_UNAVAILABLE`.

## `mp-remove-card`

**Request** (autenticado, `POST`): `{ "saved_card_id": "uuid" }`

**Comportamento**: confirma posse (`client_id = auth.uid()`), remove o Customer Card no Mercado
Pago e a linha em `saved_cards`. Não afeta `payments` já `APPROVED` feitos com esse cartão
(FR-007, edge case de remoção).

**Response**: `204`. **Erros**: `401 SESSION_REQUIRED`, `404 CARD_NOT_FOUND`.

## `mp-webhook`

**Request** (público, `POST`, chamado pelo Mercado Pago): payload padrão de notificação
(`{ "type": "payment", "data": { "id": "..." } }`).

**Comportamento**:
1. Valida `x-signature`/`x-request-id` com `MP_WEBHOOK_SECRET` (HMAC-SHA256); assinatura inválida →
   `401` sem tocar no banco.
2. Busca o pagamento por `id` via `GET /v1/payments/{id}` (nunca confia no corpo da notificação).
3. Localiza a linha em `payments` pelo `mp_payment_id` (ou pelo `external_reference` no primeiro
   recebimento) e atualiza `status` conforme a máquina de estados em `data-model.md`.
4. Quando o novo `status = APPROVED`, publica no canal Realtime da solicitação (ver
   `payment-realtime-ui.md`) para o app avançar ao despacho.

**Response**: sempre `200` para notificações processadas (mesmo se o pagamento já estava no estado
final) — evita reentrega agressiva do Mercado Pago; `401` só para assinatura inválida.

## `mp-refund-payment`

**Request** (chamada internamente por RPC via `pg_net`/trigger, ou reexecutável manualmente pelo
suporte): `{ "payment_id": "uuid interno" }`

**Comportamento**: chama `POST /v1/payments/{mp_payment_id}/refunds`; em sucesso marca
`payments.status = REFUNDED`; em falha incrementa `refund_attempts` e, se `< 3`, reagenda uma nova
tentativa (backoff descrito em research.md §7); ao atingir 3 tentativas falhas, marca
`REFUND_FAILED`.

**Response**: `{ "status": "REFUNDED | RETRY_SCHEDULED | REFUND_FAILED" }`.
