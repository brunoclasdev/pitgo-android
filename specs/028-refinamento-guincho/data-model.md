# Data Model: Guincho

## Entidades

Reutilizar providers, provider_services, vehicles, appointments, service_searches e payments. Acrescentar GUINCHO_DIRECT às modalidades fechadas preservando as antigas. Pedidos dirigidos não usam fila de private.dispatch_candidates; ajustar nulabilidade de referências legadas apenas nesse ramo, sem candidato artificial que ative avanço automático.

| Entidade | Campos | Validação e acesso |
| --- | --- | --- |
| guincho_pricing_rules | id/version, active, effective_from/until, distance_rate_per_km, markup_percent, fee_percent/fixed_amount, currency | Tarifa inicial 3.00; markup 10.00; taxas da política existente; BRL, valores não negativos; escrita staff autorizada. |
| guincho_trip_drafts | id/client_id, vehicle_id/snapshot, pickup/destination address/geography, revision, route_distance_meters/calculated_at/input_revision | Veículo do cliente; coordenadas válidas; mudar entrada invalida rota/cotação; somente cliente dono lê. |
| guincho_quotes | id/draft_id/client_id/provider_id/provider_service_id, input_revision, base_amount, distance_meters/rate/charge, provider_amount, markup/fee/customer_total, pricing_rule_id/version, expires_at | Distância confiável de backend; oferta ativa; dinheiro numeric; só funções confiáveis escrevem; cotação paga imutável. |
| appointment_disputes | id, appointment_id UNIQUE, client_id, reason, status/revision, opened_at, resolution/justification/resolved_by/resolved_at | Motivo e justificativa trimmed 1–1000 chars; OPEN → RESOLVED; decisão COMPLETE ou CANCEL_FULL_REFUND; leitura participantes/staff. |

Snapshots de veículo/endereço preservam dados pagos se cadastro mudar. Não apagar cotação referenciada por pagamento. Retenção segue política existente. Índices por cliente, provider, revisão e fila de disputa status/opened_at; consultas paginadas.

## Campos existentes e preço

Appointments associam guincho_quote_id/draft_id e reutilizam campos monetários, arrived_at, service_started_at, completion_requested_at e client_completion_confirmed_at. request_id referencia service_search; appointment/payment/quote pertencem ao mesmo pedido. Reserva é derivada de appointment operacional; response_deadline começa só após aprovação.

```text
distance_charge = round(distance_meters / 1000 × rate, 2)
provider_amount = base_amount + distance_charge
markup_amount = round(provider_amount × 0.10, 2)
fee_amount = round(provider_amount × fee_percent / 100 + fee_fixed_amount, 2)
customer_total_amount = provider_amount + markup_amount + fee_amount
payment_amount = customer_total_amount
```

Numeric/decimal exato, half-up, sem float para dinheiro. Distância fracionária finita/não negativa. Base 100, distância 10000 m, rate 3 e fee 0%+5 → remuneração 130 e total 148. A ausência de regra/preço/rota bloqueia cotação. quote_version obrigatório no pagamento; paid quote não muda com preço/regra posterior. Guincho não usa pagamento reduzido de teste do PitTec.

## Transições

```text
draft → quote → AWAITING_PAYMENT (reserva)
APPROVED → PENDING (search AWAITING_PROVIDER, oferta exclusiva)
aceite → ACCEPTED (a caminho)
Cheguei → ACCEPTED + arrived_at
Iniciar transporte → IN_PROGRESS
Concluir → AWAITING_CLIENT_COMPLETION_CONFIRMATION
confirmar → COMPLETED
informar problema → DISPUTED + dispute OPEN
suporte COMPLETE → COMPLETED + RESOLVED
suporte CANCEL_FULL_REFUND → CANCELLED + RESOLVED + refund pipeline
```

Recusa/expiração da oferta paga cancela e solicita refund; pagamento falho/expirado libera reserva sem oferta. Aprovação tardia de pedido cancelado compensa/refunda sem ressuscitar. Repetições usam operation_id/revisão e timestamps imutáveis.

Índice único por provider inclui AWAITING_PAYMENT/PENDING/ACCEPTED/IN_PROGRESS/AWAITING_CLIENT_COMPLETION_CONFIRMATION; exclui DISPUTED/terminais. Ajustar também índice cliente/serviço. Não expirar espera por confirmação/disputa nos workers. Deadline existente de serviço de 4 h segue ACCEPTED/IN_PROGRESS. Contestação libera reserva e localização; espera normal permanece operacional. Novo pedido tem prioridade de restore; resolver disputa velha não modifica outro pedido.

## Segurança

Cliente lê seus rascunhos/cotações/pedidos; prestador destinatário recebe pedido apenas após APPROVED e só remuneração, cliente, veículo e endereços operacionais. Staff ativo MANAGER+ consulta/resolve com audit log. RLS e RPCs validam propriedade/papel; revogar updates diretos de preço/estado/decisão. Service-role persiste rota somente após Edge autenticar autoria/revisão. Realtime transmite ID/revisão, dados por leitura privada. JSON null para datas opcionais, nunca transformar snapshot em SQL NULL.
