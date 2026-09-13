# Phase 1 Data Model: Pagamento de serviços (Pix e cartão)

Duas tabelas novas no schema `public`, seguindo o padrão já usado por `mobile_dispatch_searches` /
`pittec_*` (RLS por dono, `SECURITY DEFINER` RPCs para as transições sensíveis, sem acesso direto de
escrita do cliente às colunas de status).

## `payments` (Pagamento)

| Coluna | Tipo | Notas |
|---|---|---|
| `id` | `uuid`, PK, `default gen_random_uuid()` | Identificador interno do pagamento. |
| `request_id` | `uuid`, not null, unique por tentativa ativa | Mesmo `request_id`/`search_id` usado por `start_mobile_dispatch`/`start_pittec_dispatch`; liga o pagamento à solicitação. |
| `client_id` | `uuid`, not null, FK `auth.users(id)` | Dono do pagamento; usado pela política de RLS. |
| `dispatch_kind` | `enum MOBILE_SEQUENTIAL \| PITTEC_AUTO_SEQUENTIAL` | Qual fluxo originou a cobrança (FR-014). |
| `method` | `enum PIX \| SAVED_CARD` | Forma de pagamento escolhida. |
| `saved_card_id` | `uuid`, nullable, FK `saved_cards(id)` | Preenchido só quando `method = SAVED_CARD`. |
| `amount` | `numeric(10,2)`, not null, `> 0` | Valor cobrado, no mesmo cálculo já usado pelo serviço solicitado. |
| `status` | `enum PENDING \| APPROVED \| REJECTED \| EXPIRED \| CANCELLED \| REFUNDED \| REFUND_FAILED` | Ver máquina de estados abaixo. |
| `mp_payment_id` | `text`, nullable, unique quando presente | Id do pagamento no Mercado Pago; chave usada pelo webhook. |
| `idempotency_key` | `text`, not null, unique | `request_id` (+ sufixo de tentativa); evita cobrança duplicada (FR-011). |
| `refund_attempts` | `smallint`, not null, default `0` | Incrementado a cada tentativa de `mp-refund-payment`; limite de 3 (research.md §7). |
| `created_at` / `updated_at` | `timestamptz`, not null, default `now()` | Auditoria simples. |
| `approved_at` | `timestamptz`, nullable | Preenchido quando `status = APPROVED`. |

**RLS**: `client_id = auth.uid()` para `SELECT`; nenhum `INSERT`/`UPDATE`/`DELETE` direto do cliente
— tudo passa pelas Edge Functions (com service role) ou pelos RPCs `SECURITY DEFINER` descritos em
`contracts/payment-rpcs.md`.

**Máquina de estados**:

```text
PENDING --(Mercado Pago aprova)--> APPROVED --(cancelamento pré-conclusão OU busca exaurida)--> REFUNDED
PENDING --(Mercado Pago recusa)--> REJECTED
PENDING --(prazo do Pix expira)--> EXPIRED
PENDING --(cliente cancela antes da aprovação)--> CANCELLED
APPROVED --(estorno falha 3x)--> REFUND_FAILED  (suporte assume manualmente)
```

Nenhuma outra transição é válida; os RPCs que gravam `status` rejeitam transições fora deste
conjunto (mesma disciplina de "transições válidas" já exigida pela constituição para o marketplace).

## `saved_cards` (Cartão salvo)

| Coluna | Tipo | Notas |
|---|---|---|
| `id` | `uuid`, PK, `default gen_random_uuid()` | Identificador interno. |
| `client_id` | `uuid`, not null, FK `auth.users(id)` | Dono do cartão; base da política de RLS. |
| `mp_customer_id` | `text`, not null | Customer do Mercado Pago vinculado a este cliente (um por cliente, reaproveitado entre cartões). |
| `mp_card_id` | `text`, not null, unique | Customer Card do Mercado Pago; usado como `token` em `POST /v1/payments`. |
| `brand` | `text`, not null | Bandeira retornada pelo Mercado Pago (ex.: `visa`, `mastercard`). |
| `last_four_digits` | `char(4)`, not null | Últimos 4 dígitos retornados pelo Mercado Pago — nunca o PAN completo. |
| `created_at` | `timestamptz`, not null, default `now()` | Data de cadastro. |

Nenhuma coluna guarda PAN completo, CVV ou data de validade (FR-006) — esses dados existem apenas
em trânsito entre o SDK do Mercado Pago e a API do Mercado Pago, nunca no banco do PitGo.

**RLS**: `client_id = auth.uid()` para `SELECT`; `INSERT`/`DELETE` só via `SECURITY DEFINER` RPC que
confere que o `mp_card_id` pertence ao `mp_customer_id` daquele cliente (a Edge Function
`mp-save-card`/`mp-remove-card` chama o RPC autenticado, nunca escreve direto via service role sem
essa checagem).

## Relação com entidades existentes

- `payments.request_id` referencia o mesmo identificador que `start_mobile_dispatch`/
  `start_pittec_dispatch` recebem como `p_search_id`/`p_request_id` — a guarda de pagamento nesses
  RPCs faz `EXISTS (SELECT 1 FROM payments WHERE request_id = p_search_id AND status = 'APPROVED')`.
- Cancelamento (`cancel_mobile_appointment` e o equivalente PitTec) e a rotina de busca exaurida do
  PitTec passam a, dentro da mesma transação, marcar `payments.status = 'REFUNDED'`/enfileirar
  `mp-refund-payment` quando havia um pagamento `APPROVED` associado e o atendimento não chegou a
  `COMPLETED` (FR-015/FR-017).
- Conclusão do atendimento (`complete_mobile_appointment` e equivalente PitTec) não altera
  `payments` — o valor já cobrado fica como `APPROVED` definitivo (FR-016).
