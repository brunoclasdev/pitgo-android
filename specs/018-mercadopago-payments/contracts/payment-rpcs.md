# Contract: RPCs Postgres de pagamento

Seguem o padrão já usado pelos RPCs de despacho (`SECURITY DEFINER`, `SELECT ... FOR UPDATE` onde
há corrida possível, revisão de `client_id = auth.uid()` antes de qualquer leitura/gravação).

## Novos RPCs

### `get_payment_status(p_request_id uuid) → payment_status_row`

Leitura pelo próprio cliente do estado atual do pagamento de uma solicitação. Usado como fallback
quando o Realtime não entrega a atualização (ver `payment-realtime-ui.md`). Retorna `status`,
`method`, `pix_qr_code` (se ainda pendente e for Pix) e `refund_status`.

### `list_saved_cards() → setof saved_card_row`

Lista os cartões do cliente autenticado (`brand`, `last_four_digits`, `id`, `created_at`); nunca
retorna `mp_card_id` bruto ao app além do necessário para seleção.

### `remove_saved_card(p_saved_card_id uuid) → void`

Chamado pela Edge Function `mp-remove-card` após remover no Mercado Pago; confere posse antes de
apagar a linha.

## Guarda adicionada aos RPCs de despacho existentes

`start_mobile_dispatch(p_search_id, ...)` e `start_pittec_dispatch(p_request_id, ...)` (specs
013/015) ganham, logo no início da transação, a checagem:

```sql
if not exists (
  select 1 from payments
  where request_id = p_search_id -- ou p_request_id, conforme o RPC
    and client_id = auth.uid()
    and status = 'APPROVED'
) then
  raise exception 'PAYMENT_REQUIRED' using errcode = 'P0001';
end if;
```

O app Kotlin mapeia o código `PAYMENT_REQUIRED` para um novo valor do enum de falha existente
(`MobileDispatchFailure`/`PitTecFailure`), reaproveitando o tratamento de erro já existente em vez
de criar um canal de erro paralelo.

## Guarda de estorno automático adicionada ao cancelamento e à exaustão

`cancel_mobile_appointment`, o cancelamento equivalente do PitTec e a rotina que marca uma busca
PitTec como exaurida (spec 015, "todos recusaram") passam a, dentro da mesma transação:

```sql
if exists (
  select 1 from payments
  where request_id = <search/request id do atendimento>
    and status = 'APPROVED'
) then
  update payments set status = 'REFUNDED' -- otimista; ajustado por mp-refund-payment se falhar
  where request_id = <...> and status = 'APPROVED';
  perform pg_notify('mp_refund_queue', <payment_id>); -- ou chamada http via pg_net, conforme research.md §7
end if;
```

Isso implementa FR-015/FR-017 sem introduzir uma tabela de fila nova: o próprio `payments` com
`status IN ('REFUNDED', 'REFUND_FAILED')` já é o registro de auditoria necessário nesta versão.
