# Contract: Realtime de pagamento e estados de UI

## Canal Realtime

Reaproveita o padrão já usado por `MobileDispatchRepository`/`PitTecRepository`
(`RealtimeChannel` + `broadcastFlow`), com um tópico dedicado por solicitação:

- **Tópico**: `payment:<request_id>` (privado; só o `client_id` dono consegue se inscrever, mesma
  técnica de canal privado já usada em `mobile-dispatch:<searchId>`).
- **Evento**: publicado por `mp-webhook` sempre que `payments.status` muda; payload mínimo:
  `{ "request_id": "...", "status": "PENDING|APPROVED|REJECTED|EXPIRED|CANCELLED|REFUNDED|REFUND_FAILED" }`.
- **Fallback**: se o canal cair ou não emitir em até 10 s após uma ação do cliente (ex.: acabou de
  gerar o Pix), o app chama `get_payment_status` (RPC) para não deixar a tela presa sem retorno.

## Estados de UI — Checkout (`features/payment`)

| Estado | Gatilho | UI |
|---|---|---|
| `SelectingMethod` | Cliente chega à etapa de pagamento | Lista Pix + cartões salvos (sem opção de cadastrar, FR-019). |
| `CreatingPayment` | Cliente confirma o método | Loading curto enquanto `mp-create-payment` responde. |
| `AwaitingPix` | `method = PIX` e resposta `PENDING` | QR/copia-e-cola + contagem regressiva até `expires_at`. |
| `AwaitingCardConfirmation` | `method = SAVED_CARD` e resposta `PENDING` | Loading (cobrança em cartão costuma resolver em segundos). |
| `Approved` | Evento Realtime ou RPC retorna `APPROVED` | Confirmação breve, então navega para o despacho (`MobileDispatchRepository.start`/`PitTecRepository.start`). |
| `Declined` | `REJECTED` | Mensagem compreensível + ação "tentar outro método" (FR-010). |
| `Expired` | `EXPIRED` | Mensagem de prazo expirado + ação "gerar novo código" ou trocar de método (US1 cenário 3). |
| `Failure` | Erro de rede/serviço indisponível | Mensagem genérica + tentar novamente, sem cobrar de novo (idempotência já garante isso no backend). |

Nenhum destes estados aciona `MobileDispatchRepository.start`/`PitTecRepository.start` fora de
`Approved` — é o ponto único onde o gate de pagamento se conecta ao despacho (Design Decision 9 do
plan.md).

## Estados de UI — Formas de pagamento (`features/paymentmethods`)

| Estado | Gatilho | UI |
|---|---|---|
| `Loading` | Tela aberta | Spinner enquanto `list_saved_cards()` carrega. |
| `Empty` | Nenhum cartão salvo | Mensagem + botão "Cadastrar cartão", sem bloquear navegação (FR-005). |
| `Listing` | Um ou mais cartões | Cartões com bandeira/últimos 4 dígitos + ação remover (FR-007). |
| `Registering` | Cliente toca "Cadastrar cartão" | Formulário do SDK do Mercado Pago (dados do cartão). |
| `RegisterFailed` | `mp-save-card` retorna `422 CARD_REJECTED` | Mensagem compreensível + tentar novamente ou voltar sem cadastrar (US2 cenário 2). |
| `Removing` | Cliente confirma remoção | Loading curto; ao concluir volta para `Listing`/`Empty`. |

## Feedback de estorno

Quando `payments.status` chega a `REFUNDED` ou `REFUND_FAILED` (via o mesmo canal
`payment:<request_id>`), a tela de acompanhamento do atendimento (`appointmenttracking`) e/ou uma
notificação simples informam o cliente:
- `REFUNDED`: "valor estornado" (SC-007).
- `REFUND_FAILED`: "reembolso em andamento, nossa equipe já foi avisada" (FR-018) — sem exigir que o
  cliente abra chamado.
