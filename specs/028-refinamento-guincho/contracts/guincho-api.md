# Interface Contracts: Guincho

Interfaces propostas, ainda não implementadas. Comandos autenticados recebem operation_id UUID/expected_revision; resultado `{outcome, snapshot?, server_now}`. Dinheiro string decimal BRL, timestamps UTC. Conflito devolve estado atual; sessão ausente/propriedade inválida não permite operação. Locks em ordem consistente, sem I/O externo sob lock.

## Descoberta/cotação/contratação

| Interface | Entrada | Resultado/garantia |
| --- | --- | --- |
| save_guincho_trip_draft | draft_id opcional/revision, vehicle_id, pickup/destination address e coordenadas | draft_id/revision, veículo próprio, invalidação de rota ao editar. |
| discover_guincho_providers | draft_id/revision, center/radius, limit ≤200 | providers MOBILE com oferta Guincho ativa, disponíveis, posição/nome; sem total fictício. |
| POST guincho-quote | draft_id/input_revision, provider_id | quote_id/version, provider_name, customer_total_amount, currency/expires_at. |
| create_guincho_request | quote_id, operation_id | request_id/appointment_id/quote_version/total, AWAITING_PAYMENT; reserva única, valida oferta/regra/cotação, repetição recupera mesmo pedido. |

Edge calcula distância retirada→destino via configuração OSRM-compatible; cache vinculado à revisão do draft, não ao marcador. Body não aceita distância/tarifa/total autoritativos. RPC store_guincho_quote exclusiva service-role valida cliente/revisão e grava cotação. QUOTE_CONFLICT/QUOTE_EXPIRED, ROUTE_UNAVAILABLE, PRICING_UNAVAILABLE e PROVIDER_UNAVAILABLE são erros recuperáveis. Sem rota não permite pagar. Validade 15 min.

## Payments

mp-create-payment/reserva SQL devem aceitar GUINCHO_DIRECT com request_id, appointment_id, quote_version e expected amount comparado ao backend. Reusar Pix/cartão, intenção durável, chave de idempotência, recuperar/status/cancel/refund e reconciliação. Não criar chave nova a cada retry.

APPROVED valida moeda/amount/quote/pedido/reserva e converte AWAITING_PAYMENT → PENDING uma vez; inicia prazo de aceite existente e libera oferta exclusiva e dados ao escolhido. Aprovação tardia de cancelado solicita compensação/refund. Recusa/expiração não chama advance_mobile_dispatch nem contrata outro prestador. Reembolso integral = payment.amount, enfileirado pelo pipeline existente.

## Atendimento/contestação

| RPC | Quem e entrada | Resultado |
| --- | --- | --- |
| get_my_active_guincho_request | participante | ACTIVE/snapshot ou NONE; exclui DISPUTED. |
| get_guincho_appointment_snapshot | participante, appointment_id | Estado atual inclusive terminal/disputa; projeção por ator. |
| respond_guincho_request | escolhido, appointment_id/revision/op, accept | PENDING pago → ACCEPTED ou cancel/refund. |
| arrive_guincho_appointment | prestador, id/revision/op | ACCEPTED + arrived_at, idempotente. |
| start_guincho_transport | prestador, id/revision/op | Exige chegada → IN_PROGRESS; sem confirmação do cliente. |
| request_guincho_completion | prestador, id/revision/op | IN_PROGRESS → espera confirmação. |
| confirm_guincho_completion | cliente, id/revision/op | Espera sem disputa → COMPLETED. |
| dispute_guincho_completion | cliente, id/revision/op, reason | Espera → DISPUTED, uma contestação, motivo 1–1000 chars, libera operação. |
| list_my_guincho_appointments | participante, cursor/limit | Histórico paginado/disputas, separado de restore. |
| dashboard_list_guincho_disputes | MANAGER+, status/limit/offset | Fila paginada. |
| dashboard_get_guincho_dispute | MANAGER+, dispute_id | Detalhe e contexto autorizado. |
| dashboard_resolve_guincho_dispute | MANAGER+, dispute_id/revision/op, decision/justification | COMPLETE ou CANCEL_FULL_REFUND; justificativa 1–1000 chars, revisão/lock/audit; refund idempotente. |

Cliente recebe apenas total; prestador apenas provider_amount e dados operacionais após aprovação; nunca markup/taxas/customer_total. Suporte tem projeção própria. Datas opcionais = JSON null. Realtime privado + polling 5 s no pedido visível; disputa via histórico. Refund pending/failed é estado financeiro separado; resolução velha não interrompe outro atendimento. Auditoria registra ator/decisão/data/justificativa sem logs públicos sensíveis.
