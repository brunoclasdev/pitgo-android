# Contrato: fluxo financeiro do PitTec

## Princípios

- A busca PitTec é gratuita até o aceite do prestador.
- O servidor é a autoridade para preço, fórmula, validade, confirmação e pagamento.
- O snapshot é filtrado pelo papel antes de sair do servidor.
- O pagamento usa o total do cliente, nunca o valor do prestador.
- APPROVED é o único estado financeiro que libera o atendimento.

## Snapshot PitTec

As RPCs existentes de restauração, início, observação e resposta continuam retornando o envelope
de dispatch. O objeto appointment.quote será ajustado conforme o chamador.

### Cliente em AWAITING_CLIENT_CONFIRMATION

    {
      "status": "AWAITING_CLIENT_CONFIRMATION",
      "appointment": {
        "id": "UUID",
        "status": "AWAITING_CLIENT_CONFIRMATION",
        "revision": 4,
        "quote": {
          "markup_amount": "20.00",
          "customer_fee_amount": "3.50",
          "customer_total_amount": "73.50",
          "currency": "BRL",
          "version": 2,
          "expires_at": "2026-09-18T15:15:00Z"
        }
      },
      "allowed_actions": ["CONFIRM_QUOTE", "DECLINE_QUOTE", "CANCEL"]
    }

O cliente não recebe provider_amount. O exemplo usa valores ilustrativos e não define taxa de
produção.

### Prestador em proposta aceita

    {
      "status": "AWAITING_CLIENT_CONFIRMATION",
      "appointment": {
        "id": "UUID",
        "status": "AWAITING_CLIENT_CONFIRMATION",
        "revision": 4,
        "quote": {
          "provider_amount": "50.00",
          "currency": "BRL",
          "version": 2,
          "expires_at": "2026-09-18T15:15:00Z"
        }
      },
      "allowed_actions": ["CANCEL"]
    }

O prestador não recebe markup, taxas ou customer_total_amount.

## Confirmação da proposta

### respond_to_pittec_quote

Entrada autenticada:

    {
      "p_request_id": "UUID",
      "p_appointment_id": "UUID",
      "p_quote_version": 2,
      "p_accept": true,
      "p_expected_revision": 4
    }

Regras:

- Somente o cliente da solicitação pode confirmar ou recusar.
- appointment, request, client, quote_version e revision devem coincidir.
- O servidor rejeita confirmação após quote_expires_at ou quando a proposta não está em
  AWAITING_CLIENT_CONFIRMATION.
- p_accept=true grava client_confirmed_at e client_confirmed_quote_version e muda busca/
  appointment para AWAITING_PAYMENT.
- p_accept=false encerra a proposta sem criar pagamento.
- Repetição da mesma confirmação devolve snapshot atual; revisão diferente retorna
  REVISION_CONFLICT.

Resultados:

- CONFIRMED
- DECLINED
- CONFIRMATION_EXPIRED
- REVISION_CONFLICT
- INVALID_TRANSITION
- NOT_FOUND

## Criação do pagamento

### mp-create-payment

O contrato de autenticação e recuperação existente permanece. Para PitTec:

    {
      "request_id": "UUID",
      "dispatch_kind": "PITTEC_AUTO_SEQUENTIAL",
      "appointment_id": "UUID",
      "quote_version": 2,
      "method": "SAVED_CARD",
      "saved_card_id": "UUID",
      "card_security_code_token": "token novo do SDK",
      "amount": "73.50"
    }

Regras server-side:

- request_id e appointment_id devem pertencer ao cliente autenticado.
- appointment deve estar em AWAITING_PAYMENT e client_confirmed_quote_version deve coincidir.
- amount do cliente é somente comparação; a cobrança usa customer_total_amount armazenado.
- Uma tentativa ativa para o mesmo request/appointment/version é devolvida, sem novo POST externo.
- Para cartão salvo, o backend resolve customer/card/tokenização; PAN, CVV e token não são
  persistidos ou registrados.
- O Access Token do Mercado Pago fica apenas na Edge Function.

Resposta:

    {
      "payment_id": "UUID",
      "request_id": "UUID",
      "appointment_id": "UUID",
      "quote_version": 2,
      "status": "PENDING",
      "sync_state": "VERIFYING",
      "revision": 1,
      "method": "SAVED_CARD",
      "amount": "73.50",
      "currency": "BRL"
    }

Erros relevantes: INVALID_PAYMENT_CONTEXT, QUOTE_EXPIRED, QUOTE_CONFLICT,
CARD_NOT_FOUND, CARD_REJECTED, PAYER_DOCUMENT_REQUIRED, RATE_LIMITED e SERVICE_UNAVAILABLE.

## Pagamento e início do atendimento

- Webhook, polling e executor atualizam payments sem depender do app aberto.
- A aplicação de APPROVED valida amount=customer_total_amount, BRL, request, appointment,
  quote_version e confirmação do cliente.
- A transição financeira aprovada muda search/appointment para ACCEPTED de forma atômica.
- O prestador não pode marcar chegada antes dessa transição.
- O app só navega para tracking após snapshot APPROVED/ACCEPTED.
- Pagamento pendente, rejeitado, expirado ou erro mantém o atendimento sem início.
- Expiração de proposta sem aprovação libera o prestador, encerra a busca e torna a tentativa
  inelegível para pagamento.

## Cartão novo durante o checkout

1. O cliente solicita a lista de cartões salvos.
2. Se escolher novo cartão, o SDK nativo tokeniza os dados sensíveis.
3. O app chama mp-save-card e recebe somente marca/últimos quatro dígitos/ID interno.
4. A lista é atualizada e o cliente seleciona o cartão recém-cadastrado.
5. O checkout solicita o código de segurança pelo componente seguro e cria a tentativa Orders.

Falha no cadastro mantém a proposta em AWAITING_PAYMENT e não cria cobrança.

## Eventos e recuperação

Canal privado do pagamento continua emitindo request_id, payment_id, status, revision e sync_state.
O canal PitTec continua emitindo snapshot sem valores do papel oposto. Ao receber evento, cada app
consulta seu snapshot; eventos antigos são ignorados pela revision.

Ao reabrir:

- cliente recupera a proposta pela solicitação ativa e payment status pelo request_id;
- prestador recupera somente seu valor e o estado do atendimento;
- nenhum cliente recebe payment IDs externos, provider_amount ou dados internos da regra.
