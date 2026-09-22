# Research: Pix e cancelamento do pagamento

## Decision 1: Reutilizar o fluxo atual da Orders API para Pix

- **Decision**: Manter a criação de cobrança no fluxo existente de `mp-create-payment` e consumir os campos `ticket_url`, `qr_code` e `qr_code_base64` já persistidos no snapshot de pagamento.
- **Rationale**: O projeto já usa Orders API, idempotência por tentativa e polling/reconciliação. A documentação do Mercado Pago define `qr_code` como o Pix Copia e Cola, `qr_code_base64` como a imagem do QR e `ticket_url` como a página de pagamento; o estado esperado antes do pagamento é `action_required / waiting_transfer`.
- **Alternatives considered**: Migrar para Payments API, adicionar outro SDK Pix ao Android ou abrir `ticket_url` em uma tela externa. Essas opções duplicariam o fluxo atual e não atendem à exigência de renderização na própria tela.
- **Reference**: https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-integration/pix

## Decision 2: Usar o estado persistido como fonte de verdade após cancelamento

- **Decision**: O aplicativo solicita o cancelamento uma vez, aguarda a resposta autoritativa do backend e retorna à tela inicial somente depois de registrar o resultado. O backend mantém reembolsos aprovados como `REFUND_PENDING`, `REFUNDED` ou `REFUND_FAILED`.
- **Rationale**: A Order API diferencia cancelamento de uma cobrança ainda não aprovada e reembolso de uma cobrança já capturada. A implementação existente já possui operações idempotentes e recuperação autônoma para reembolso.
- **Alternatives considered**: Apenas limpar a navegação local ou marcar tudo como cancelado no Android. Essas opções poderiam deixar a solicitação ativa no banco ou sobrescrever um pagamento aprovado.
- **Reference**: https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-management/refunds-cancellations

## Decision 3: Aplicar R$ 1,00 na fronteira autoritativa de cobrança

- **Decision**: Durante esta feature, o valor usado na proposta de pagamento, no Android e na cobrança enviada ao Mercado Pago será R$ 1,00. A reserva persistida deve rejeitar qualquer valor diferente.
- **Rationale**: O cliente não pode alterar o valor por meio do aplicativo. A validação no backend evita que uma chamada manual ou uma versão antiga do APK crie uma cobrança diferente.
- **Alternatives considered**: Alterar somente o texto da tela ou somente o valor enviado ao provedor. Essas alternativas permitiriam divergência entre o valor mostrado, o valor salvo e o valor cobrado.

## Decision 4: Reutilizar o contrato atual de snapshot

- **Decision**: Manter `pix` como objeto opcional no snapshot, contendo `qr_code`, `qr_code_base64` e `expires_at`; tratar ausência de qualquer campo como erro recuperável ou estado de verificação, nunca como Pix pronto.
- **Rationale**: O contrato Android já valida o snapshot completo e evita mostrar dados incompletos. Isso também impede carregamento infinito quando o provedor não devolve o QR.
- **Alternatives considered**: Aceitar somente `qr_code` e gerar a imagem no aparelho. O QR visual precisa do `qr_code_base64` retornado pelo provedor e não deve ser reconstruído localmente.
