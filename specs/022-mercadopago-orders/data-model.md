# Data Model: Mercado Pago Orders

## Estratégia

Expandir public.payments (uma linha por tentativa), sem tabela redundante de orders: esta feature cria uma ordem com uma transação. Preservar saved_cards e IDs legados. Novas migrações aditivas; nunca editar migrations aplicadas. Dados operacionais sensíveis permanecem sem acesso direto de anon/authenticated.

## public.payments

| Campo | Tipo / regra |
|---|---|
| id, request_id, client_id, dispatch_kind | Existentes; identidade local, contexto e titular |
| service_id | UUID de serviço, obrigatório em novas tentativas; backfill somente quando comprovável |
| appointment_id, quote_version | Existentes; obrigatórios para PitTec, versão positiva confirmada |
| amount, currency | numeric(10,2) positivo e BRL; obtidos no servidor, nunca por confiança no app |
| method, saved_card_id, card_brand, card_last_four_digits | Existentes; snapshot não sensível sobrevive à remoção do cartão |
| provider_api | PAYMENTS ou ORDERS, obrigatório; backfill PAYMENTS |
| mp_payment_id | Texto legado preservado; não receber ORD/PAY em lugar do ID antigo |
| mp_order_id, mp_transaction_id, mp_reference_id | Textos nullable; identidades externas distintas, sem normalização destrutiva |
| external_reference | UUID da tentativa para Orders, referência original para legado |
| idempotency_key | Chave de criação estável/única, já existente |
| status | Estados financeiros existentes: PENDING, APPROVED, REJECTED, EXPIRED, CANCELLED, REFUND_PENDING, REFUNDED, REFUND_FAILED |
| provider_status, provider_status_detail, transaction_status, transaction_status_detail | Snapshot externo mínimo; nunca corpo completo |
| sync_state | CREATING, VERIFYING, CURRENT; incerteza não equivale a recusa |
| pix_qr_code, pix_qr_code_base64, pix_expires_at | Nullable; acesso só do titular via snapshot; limite de tamanho, sem logs; limpar após resolução/expiração quando não necessário |
| cancel_requested_at, cancel_idempotency_key | Intenção durável; chave própria da operação |
| refund_idempotency_key, mp_refund_id, refunded_amount | Operação integral única por tentativa; valor confirmado, não inferido de HTTP 2xx |
| settled_amount | numeric(10,2), montante confirmado de quitação; comparar com amount e devoluções antes de liberar atendimento |
| refund_attempts | Existente; 0..3 envios, incremento transacional antes do envio; consultas não contam |
| next_action_at, work_action | Próximo trabalho: RECONCILE, CANCEL ou REFUND; nullable quando sem trabalho |
| lease_token, lease_until | Reserva temporária do executor; lease de 90 s |
| revision | bigint crescente; aplicação condicionada à versão e lease impede escrita obsoleta |
| last_checked_at, last_error_code, support_required_at | Observabilidade sanitizada; suporte não impede receber confirmação tardia |
| created_at, updated_at, approved_at | Existentes; approved_at preservado após primeira aprovação |

Campos de tokens/PAN/CVV não são criados. Nunca persistir request completo de criação. Token novo vive apenas na operação em memória.

### Identidade e bloqueios

- Lock transacional por request_id serializa criação e cancelamento. Validar vínculo de dono ao reutilizar UUID; não permitir que outro usuário se aproprie de contexto existente.
- Manter uma tentativa financeira bloqueadora por request_id: CREATING/VERIFYING ou status PENDING/APPROVED/REFUND_PENDING/REFUND_FAILED. REFUND_FAILED bloqueia nova cobrança enquanto não resolvido; não usar pagamento falho de reembolso como autorização para tentar novamente.
- Índice parcial substitui payments_one_live_per_request para cobrir incerteza. Atualizações que precisam registrar cobrança excedente não violam o índice: marcar diretamente a tentativa excedente como REFUND_PENDING com indicador de compensação e excluir somente essas compensações do índice de obrigação ativa; elas não liberam atendimento.
- Adicionar campo is_compensation boolean default false. Apenas operação interna validada pode marcá-lo. Impedir nova tentativa normal enquanto qualquer compensação do request estiver não resolvida.
- Índices únicos externos por origem/ID (onde não nulo), chave de criação e chave de reembolso. Índice de trabalho por next_action_at para itens com trabalho; sem varrer histórico em cada ciclo.
- Novas tentativas só após resultado terminal comprovado e contexto ainda elegível. Nova tentativa tem novo UUID; retry de transporte não é nova tentativa.

### Preço e elegibilidade

Móvel: app informa service_id; servidor consulta a mesma origem de preço do catálogo, valida serviço ativo e dispatch_kind e congela amount. amount do cliente, quando presente, serve apenas para detectar preço alterado (409). start_mobile_dispatch valida service_id e cobrança aprovados do mesmo titular; não recalcula preço depois de cobrança já aprovada.

PitTec: ler appointment/search sob lock; ambos em AWAITING_PAYMENT, cliente correto, quoted_price positivo, quote_version = client_confirmed_quote_version. Proposta alterada ou atendimento cancelado não é aprovado por pagamento antigo.

Legado sem service_id: preservar operação existente e rastrear apenas por associações comprovadas; não inventar backfill de serviço. O reforço de contexto vale para novas tentativas; a rota de conclusão legada deve manter a regra anterior explicitamente delimitada por provider_api=PAYMENTS.

## Estados e aplicação de resultado

| Evidência externa validada | Resultado local / ação |
|---|---|
| Reserva pré-HTTP | PENDING + CREATING; nova cobrança bloqueada |
| Timeout, 409/423 ambíguo, estado desconhecido | VERIFYING; consultar, não repetir criação com outra chave |
| created, processing ou action_required sem quitação | PENDING; sem liberar atendimento |
| processed/accredited + transação quitada integral | APPROVED apenas se contexto vigente e não cancelado |
| failed com transação recusada e nenhuma quitação | REJECTED |
| expired/canceled confirmados sem recebimento | EXPIRED/CANCELLED |
| Intenção de cancelamento + quitação tardia | REFUND_PENDING; nunca reativar atendimento |
| Reembolso aceito/em processamento | REFUND_PENDING, continuar consulta |
| Evidência de devolução integral | REFUNDED, refunded_amount = amount |
| Três envios sem sucesso ou recusa definitiva | REFUND_FAILED + suporte; continuar consulta se resultado incerto |
| Partial refund/chargeback externo | Preservar fatos, VERIFYING + suporte, sem liberação/reaprovação |

Todas as entradas passam pela mesma operação SQL de aplicação. Lock/revision impede que consulta iniciada antes de outra conciliação sobrescreva o resultado. Reembolso/intenção de cancelamento prevalecem sobre aprovação antiga. Reaplicação do mesmo estado é no-op financeiro. Guards de liberação exigem APPROVED, sync_state=CURRENT e quitação integral líquida, sem cancelamento, devolução, compensação ou conflito financeiro pendente; não basta verificar status isolado. Dados legados de quitação são preenchidos somente por evidência confiável, nunca supondo que campos ausentes significam zero devoluções.

private.reconcile_pittec_payment_change deve atuar somente na primeira transição para APPROVED; atualizações de metadados e duplicatas não produzem novo aceite nem reembolso. Broadcast deve ocorrer somente quando snapshot visível mudar, não a cada lease/consulta.

## private.payment_webhook_inbox

| Campo | Regra |
|---|---|
| id | UUID local |
| provider_api, resource_id, event_type | Protocolo e recurso autenticados |
| dedupe_key | Única: identificador de evento quando confiável; caso contrário hash dos identificadores assinados e versão/action |
| provider_version | Opcional, apenas metadado; GET continua autoritativo |
| received_at, next_action_at | Datas de recebimento e reprocessamento |
| lease_token, lease_until, attempts | Claim atômico e recuperação após crash |
| processed_at, last_error_code | Resultado operacional sanitizado |

Não guardar corpo bruto, documento, email, token ou assinatura completa. Evento sem associação permanece pendente; consultar recurso e localizar tentativa por external_reference validada. Se criação externa retornou antes de gravar IDs, a reserva local já existe. Conservar eventos processados por 7 dias e pendentes enquanto necessários; limpeza diária limitada, reutilizando manutenção existente quando apropriado.

## saved_cards

Preservar id local, client_id, mp_customer_id/mp_card_id e metadados. Acrescentar payment_method_id/payment_type_id apenas se necessários para evitar adivinhar bandeira/tipo; popular consultando cartão do mesmo customer. Nenhum PAN/CVV. get_saved_card_tokenization_context retorna ao titular somente card_id, dados públicos do método e identificação necessária à tokenização.

## Operações SQL planejadas

- Reserva de tentativa, claim de trabalho e aplicação de resultado: somente service_role, search_path vazio, validar todos os vínculos.
- get_payment_status(p_request_id) preserva assinatura e amplia snapshot; get_recoverable_payment() retorna pendência do titular mais recente com contexto suficiente para retomada.
- get_saved_card_tokenization_context(p_saved_card_id): authenticated, filtro auth.uid().
- RPCs atuais de cancelamento gravam cancel_requested_at e agendamento na mesma transação, inclusive pagamentos ainda pendentes.
- Nenhum acesso direto de clientes às tabelas financeiras ou inbox. Segurança não depende do conhecimento de UUID.
