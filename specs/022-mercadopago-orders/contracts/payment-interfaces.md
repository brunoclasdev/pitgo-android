# Contratos internos: pagamentos

Base: /functions/v1 para Edge Functions e RPCs PostgREST existentes. Nomes abaixo são interfaces planejadas, ainda não implementadas.

## Autorização

mp-create-payment, mp-refund-payment e mp-cancel-payment exigem JWT validado e titularidade no servidor. mp-save-card/mp-remove-card preservam contratos atuais. Executor aceita somente segredo interno dedicado (MP_RECONCILE_SECRET, comparação segura), verify_jwt=false e rejeição explícita sem segredo; JWT de cliente ou chave pública não autoriza execução. Webhook é público com HMAC, não JWT.

## POST mp-create-payment

Entrada comum: request_id UUID estável, dispatch_kind MOBILE_SEQUENTIAL ou PITTEC_AUTO_SEQUENTIAL, method PIX ou SAVED_CARD.

- Móvel: service_id obrigatório; amount opcional para comparação com preço do servidor, nunca autoridade de cobrança.
- PitTec: appointment_id e quote_version obrigatórios; serviço/valor vêm da proposta confirmada.
- Cartão: saved_card_id e card_security_code_token novo obrigatórios. O backend resolve customer, método/tipo do cartão do titular; nunca usa ID como token.

Reserva antes de HTTP. Repetição do mesmo contexto retorna snapshot existente, mesmo se o método enviado diferir; não realiza nova cobrança enquanto houver pendência. Contexto conflitante retorna 409.

Resposta 200/202:
```json
{
  "payment_id": "UUID",
  "request_id": "UUID",
  "status": "PENDING",
  "sync_state": "VERIFYING",
  "revision": 1,
  "method": "PIX",
  "amount": "125.00",
  "currency": "BRL",
  "pix": null
}
```

pix, quando disponível: qr_code, qr_code_base64, expires_at RFC3339. Campos atuais permanecem; adicionais são opcionais para leitura antiga. 202 significa processamento/verificação, não recusa. Se o provedor criou ordem e recusou transação (inclusive HTTP 402), retornar snapshot REJECTED quando confirmado, mantendo IDs.

Erros: 400 INVALID_INPUT; 401 SESSION_REQUIRED; 404 CARD_NOT_FOUND/SERVICE_NOT_FOUND; 409 INVALID_PAYMENT_CONTEXT/QUOTE_CONFLICT/PRICE_CHANGED; 422 PAYER_DOCUMENT_REQUIRED/CARD_TOKEN_REQUIRED; 429 RATE_LIMITED; 503 SERVICE_UNAVAILABLE. Falha após possível envio gera snapshot VERIFYING se a reserva existe. Mensagem ao usuário traduzida no app, sem payload técnico.

## POST mp-cancel-payment

Entrada: payment_id UUID. Verificar titular e elegibilidade, persistir cancel_requested_at e trabalho na mesma transação de cancelamento do contexto aplicável. Checkout móvel ainda sem busca registra cancelamento na própria tentativa. Se não houve pagamento, agendar cancelamento; se já houve, REFUND_PENDING conforme política. Repetição é idempotente. Resposta 200/202 snapshot; nunca prometer ausência de cobrança enquanto cancelamento externo estiver incerto.

As RPCs existentes de cancelamento de atendimento também precisam gravar a intenção/trabalho: chamar esta função pelo app não é requisito para reembolso.

## POST mp-refund-payment

Entrada preservada: payment_id. Validar elegibilidade já registrada no domínio; não permitir estorno arbitrário só por titularidade. Persistir/agendar operação e retornar 202 com status REFUND_PENDING; se resolvido, 200 com estado atual. Repetição não zera contador nem troca chave. Remover sleeps/retries síncronos atuais. O executor processa mesmo sem esta chamada.

## RPCs e Realtime

- get_payment_status(p_request_id UUID): FOUND/NOT_FOUND, payment_id/status/method atuais e snapshot adicional (sync_state, revision, amount, currency, pix, contexto). Somente titular; não chama Mercado Pago.
- get_recoverable_payment(): devolve a pendência mais recente do titular (ou NOT_FOUND), incluindo request_id/service_id/dispatch_kind/appointment_id/quote_version. Inclui cobrança aprovada cujo fluxo móvel ainda não iniciou e reembolso não encerrado. Não ocultar outras pendências no histórico autenticado.
- get_saved_card_tokenization_context(p_saved_card_id UUID): card_id externo do titular, payment_method_id/payment_type_id e metadados mínimos necessários; sem credenciais privadas.
- Canal privado payment:<request_id>, evento CHANGED: request_id, payment_id, status, revision e sync_state. Cliente refaz snapshot ao receber evento; ignora revisão antiga.
- get_payment_history(p_before timestamptz, p_before_id UUID, p_limit integer default 20): paginação estável por created_at/id, limite máximo 50, somente titular; retorna valores, meio mascarado, datas e status/reembolso. Sem IDs de segredo ou acesso direto à tabela. Necessário para FR-013 e múltiplas pendências; reutilizar a apresentação existente quando houver.
- Retorno ao app consulta estado antes de oferecer nova cobrança. Persistir request_id em SavedStateHandle e usar servidor para recuperar após logout/process death. Fallback de 10 s somente com tela ativa; financeiro não depende dele.

## POST mp-reconcile-payments (interno)

Corpo {}. Sem IDs arbitrários vindos de cliente. Claim de até 20 itens vencidos, concorrência 2 e orçamento 40 s. Lease 90 s; reexecução recupera expirados. Ao atingir orçamento, não iniciar mais HTTP e liberar/reagendar itens ainda não processados, com proteção de lease; crash é recuperado pela expiração. Credencial MP e service role apenas no backend. Resposta operacional 200 com claimed/processed/deferred/failed; sem dados pessoais.

1. Inbox: GET autenticado e associação validada, persistir resultado; retry se não resolvido.
2. Tentativa sem ID: buscar external_reference com intervalo/paginação. Sem resultado, manter VERIFYING; múltiplos resultados exigem compensação de excedentes e suporte.
3. Com ID: GET e aplicar resultado atômico.
4. Cancelamento: consultar antes de mutação; cancelar elegível, ou reembolsar aprovação tardia.
5. Reembolso: registrar contador/chave antes de envio; resultado incerto só consulta. Até três envios quando falha conhecida e retry seguro; após falha definitiva/limite marcar suporte.
6. 429 respeita Retry-After; falhas transitórias usam backoff/jitter. Nunca reexecutar criação de cartão sem token novo e evidência conclusiva de elegibilidade.

Logs: somente UUID local, operação, status HTTP/código sanitizado, duração e contadores. Indicadores: idade da pendência mais antiga, falhas de assinatura, atraso do cron, lease expirado, compensações, REFUND_FAILED e support_required_at. Suporte acompanha consulta operacional restrita; esta feature não cria portal administrativo ou envia mensagens externas.

## Compatibilidade e ativação

MP_ORDERS_ENABLED controla novas criações; usar modo suspenso para incidentes, sem fallback silencioso a Payments. Após ativação, novos contextos incompletos são rejeitados com orientação para atualizar app. IDs PAYMENTS existentes continuam com rota legada. Nunca misturar namespaces externos ou emitir cobrança nova para resolver pendência antiga.
