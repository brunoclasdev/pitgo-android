# Contract: Pix e cancelamento

## Android -> `mp-create-payment`

A sessão autenticada acompanha a chamada. Para PitTec, o contexto da proposta é obrigatório e o valor efetivo é validado pelo backend.

```json
{
  "request_id": "uuid-da-solicitacao",
  "dispatch_kind": "PITTEC_AUTO_SEQUENTIAL",
  "appointment_id": "uuid-do-atendimento",
  "quote_version": 1,
  "method": "PIX",
  "amount": "1.00"
}
```

A resposta de sucesso deve conter um snapshot de pagamento. Para Pix pronto:

```json
{
  "outcome": "FOUND",
  "payment_id": "uuid-do-pagamento",
  "request_id": "uuid-da-solicitacao",
  "status": "PENDING",
  "method": "PIX",
  "amount": "1.00",
  "currency": "BRL",
  "sync_state": "CURRENT",
  "pix": {
    "qr_code": "codigo-copia-e-cola",
    "qr_code_base64": "base64-da-imagem",
    "expires_at": "2026-09-22T12:00:00Z"
  }
}
```

`pix: null`, ausência de campos ou valor diferente de `1.00` não pode ser tratado como Pix pronto.

## Android -> `mp-cancel-payment`

```json
{
  "payment_id": "uuid-do-pagamento"
}
```

### Antes da aprovação

Resposta esperada: snapshot com pagamento `CANCELLED` e solicitação persistida como `CANCELLED`.

### Depois da aprovação

Resposta inicial esperada: snapshot com `cancel_requested: true` e `status: REFUND_PENDING` ou estado equivalente de processamento. O backend conclui a operação como `REFUNDED` ou `REFUND_FAILED` sem apagar o pagamento aprovado.

## Android -> `mp-refund-payment`

Mantém o contrato atual para recuperação ou acionamento administrativo:

```json
{
  "payment_id": "uuid-do-pagamento"
}
```

## Regras de apresentação

- `qr_code_base64` é renderizado como imagem QR.
- `qr_code` aparece em área copiável e em ação explícita “Copiar código Pix”.
- O valor exibido é `R$ 1,00`.
- O cliente não recebe Access Token, credenciais ou dados internos do prestador.
