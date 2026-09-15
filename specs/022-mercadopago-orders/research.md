# Research: Mercado Pago Orders

Data: 2026-09-15. Fontes oficiais consultadas; versões do app são as do repositório. Nenhuma cobrança ou configuração remota foi executada.

## 1. Produto, criação e Pix

**Decision**: Checkout API Orders, type=online, processing_mode=automatic, uma transação por tentativa; valores em strings decimais de duas casas. Pix com expiration_time=PT30M.
**Rationale**: Produto cobre o checkout atual. Pix tem mínimo de 30 minutos, preservando a duração existente. HTTP 402 pode acompanhar ordem criada; examinar a resposta antes de tratar como ausência de cobrança.
**Alternatives considered**: Checkout Pro, Merchant Orders, assinaturas e processamento manual acrescentam escopo.

Fontes: [criação](https://www.mercadopago.com.br/developers/en/reference/online-payments/checkout-api/create-order/post), [Pix](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-integration/pix), [cartões](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-integration/cards).

## 2. Cartão salvo e SDK

**Decision**: Preservar mp_customer_id/mp_card_id e usar payer.customer_id com token novo. No SDK 1.0.0: generateCardToken(cardId, securityCodeState, expirationDateState = null, buyerIdentification); sucesso fornece data.token. Capturar CVV com SecurityCodeTextField/PCIFieldState. Repository obtém cardId somente após validação de titularidade; UI não consulta backend diretamente.
**Rationale**: Guia Orders reutiliza Customers/Cards. A assinatura foi confirmada no código oficial version_1.0.0 e nas fontes do artefato 1.0.0 presentes no cache Gradle. Não confundir com o tag antigo v.1.0.0.
**Alternatives considered**: Recadastro obrigatório viola FR-004; usar ID de cartão como token está errado; Card-on-File sem CVV é outro produto; não é necessário WebView ou upgrade geral.

Fontes: [cartões salvos](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-management/improve-payment-approval/saved-cards), [SDK CoreMethods](https://github.com/mercadopago/sdk-android/blob/version_1.0.0/core-methods/src/main/java/com/mercadopago/sdk/android/coremethods/domain/interactor/CoreMethods.kt), [modelo CardToken](https://github.com/mercadopago/sdk-android/blob/version_1.0.0/core-methods/src/main/java/com/mercadopago/sdk/android/coremethods/domain/model/CardToken.kt).

## 3. Idempotência e recuperação

**Decision**: Reservar tentativa com UUID estável como chave/referência antes de HTTP. Recuperar por ID ou GET /v1/orders com external_reference e intervalo de datas; percorrer paginação. Nunca girar chave só para contornar timeout/409.
**Rationale**: Erros 409 de chave usada e 423 de recurso bloqueado não garantem replay com ID original. A referência específica de busca usa /v1/orders, apesar da divergência /search no overview. Busca vazia não prova ausência; manter verificação e encaminhar suporte após 24 h sem resolução, continuando consulta horária.
**Alternatives considered**: Timestamp novo a cada retry e inserção apenas após resposta externa permitem duplicação/perda. Não persistir token de cartão para replay: recuperação autônoma usa consulta/notificação. Crash antes de envio não autoriza inferir ausência de cobrança.

Fontes: [buscar Orders](https://www.mercadopago.com.br/developers/pt/reference/online-payments/checkout-api/search-order/get), [criação/erros](https://www.mercadopago.com.br/developers/en/reference/online-payments/checkout-api/create-order/post).

## 4. Estados

**Decision**: Aprovar apenas processed/accredited com transação e valor integral coerentes. Preservar status/details externos. Estado financeiro e andamento da sincronização são separados. Desconhecidos, divergência, reembolso parcial externo e chargeback exigem verificação/suporte, sem nova liberação.
**Rationale**: created, processing e action_required não são aprovação; processed/partially_refunded não representa pagamento novo. Aplicar resultado com lock/revisão impede regressão por respostas concorrentes.
**Alternatives considered**: Mapear desconhecidos como pendente esconde falha; reaplicar APPROVED no trigger PitTec atual pode provocar reembolso incorreto.

Fonte: [estados](https://www.mercadopago.com.br/developers/en/docs/checkout-api-orders/payment-management/status/order-status).

## 5. Webhook

**Decision**: Inscrever Order (Mercado Pago), type=order, mantendo Payment para legado. Validar HMAC antes de persistir metadados mínimos na inbox; ACK só após commit. Reconsultar recurso com credencial do vendedor para obter resultado.
**Rationale**: O serviço espera confirmação em até 22 s e reenvia falhas. Persistir primeiro evita perda quando consulta externa falha. Manifesto usa data.id da URL, x-request-id e ts; lowercase só na canonicalização, não no ID persistido.
**Alternatives considered**: ACK após falha de persistência perde evento; depender do corpo para preço/status não comprova cobrança; consulta externa longa antes de ACK aumenta reentregas.

Fontes: [notificações](https://www.mercadopago.com.br/developers/en/docs/checkout-api-orders/notifications), [manifesto manual oficial](https://omega.mercadopago.com.br/developers/es/docs/checkout-api-orders/notifications).

## 6. Cancelamento/reembolso

**Decision**: /cancel para ordens elegíveis, /refund (singular) para devolução integral; chave persistente por operação. Intenção primeiro; consultar antes de repetir operação incerta. Três envios no máximo, consultas não contam.
**Rationale**: Cancelamento vale para created/action_required; corrida com aprovação exige reembolso. Há limites de prazo (180 dias documentados) e saldo disponível. Aceite da operação não prova devolução integral.
**Alternatives considered**: Todo 2xx como reembolsado e chave nova após timeout são inseguros. Retries dormindo na chamada do app não atendem à clarificação.

Fontes: [cancelar](https://www.mercadopago.com.br/developers/pt/reference/online-payments/checkout-api/cancel-order/post), [reembolsar](https://www.mercadopago.com.br/developers/pt/reference/online-payments/checkout-api/refund-order/post), [regras](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-management/refunds-cancellations).

## 7. Trabalho durável e carga

**Decision**: Um cron de minuto verifica índice de vencimentos e chama executor apenas com trabalho. Vault/Edge secrets guardam segredo dedicado. Lotes de 20, concorrência 2, lease de 90 s, orçamento 40 s; sem transação aberta durante HTTP. Backoff de consulta: 1, 5, 15 minutos e horário na verificação prolongada; respeitar Retry-After e jitter. Reembolso tenta no primeiro ciclo, depois após 1 e 5 minutos, somente se resultado anterior conhecido e permitir retry.
**Rationale**: Escolha local atende FR-016 e respeita mitigação de sobrecarga existente. pg_cron+pg_net pode invocar funções. waitUntil tem limites de vida e não substitui persistência.
**Alternatives considered**: Polling do app, timers em memória ou fila externa não são necessários para esta solução.

Fontes: [agendamento](https://supabase.com/docs/guides/functions/schedule-functions), [background](https://supabase.com/docs/guides/functions/background-tasks).

## 8. Código atual e transição

**Decision**: Pequeno adapter por origem PAYMENTS/ORDERS; schema aditivo, criação Orders e operações legadas preservadas. Snapshot autenticado recupera tentativa após reinício. Fluxo móvel passa service_id, servidor calcula preço do catálogo e reserva; PitTec mantém valor/versão confirmados.
**Rationale**: Hoje criação insere depois de HTTP, fluxo móvel aceita amount sem serviço, checkout não passa token novo, reembolso depende do app e get_payment_status não recupera Pix. Trigger PitTec precisa ignorar reaplicação de APPROVED.
**Alternatives considered**: Converter IDs legados ou apagar histórico viola FR-010. Reescrever regras de despacho não é necessário; busca móvel continua a exigir localização do cliente ao retornar.

Referências locais: supabase/functions/mp-create-payment/index.ts, mp-webhook/index.ts, mp-refund-payment/index.ts; migrations 20260912000000_mercadopago_payments.sql, 20260913010000_pittec_call_tracking.sql, 20260915010000_mitigate_supabase_overload.sql; features/payment/viewmodel/PaymentCheckoutViewModel.kt.

## Resultado

Decisões de design resolvidas. Homologação deve comprovar contratos na conta utilizada, incluindo cartão legado, busca Orders e estorno; não é promessa de compatibilidade verificada ao vivo.
