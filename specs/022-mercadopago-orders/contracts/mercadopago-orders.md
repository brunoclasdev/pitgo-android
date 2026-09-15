# Contrato externo: Checkout API Orders

Referências e decisões em [research.md](../research.md). Base HTTPS: https://api.mercadopago.com. Bearer MP_ACCESS_TOKEN somente no servidor.

## Criação

POST /v1/orders, Content-Type application/json, X-Idempotency-Key = UUID persistido da tentativa.

```json
{
  "type": "online",
  "processing_mode": "automatic",
  "external_reference": "UUID-DA-TENTATIVA",
  "total_amount": "125.00",
  "payer": { "email": "comprador-de-teste@example.com" },
  "transactions": {
    "payments": [{
      "amount": "125.00",
      "expiration_time": "PT30M",
      "payment_method": { "id": "pix", "type": "bank_transfer" }
    }]
  }
}
```

Para cartão, substituir payment_method por id/tipo resolvidos do cartão, token novo e installments=1; omitir expiration_time Pix e incluir payer.customer_id. Dados adicionais obrigatórios do pagador vêm do perfil validado conforme regras documentadas do meio. Não registrar payload em logs.

Guardar id da order separadamente de transactions.payments[0].id e reference_id. QR é lido de transactions.payments[0].payment_method.{qr_code,qr_code_base64,ticket_url}. expires_at é derivado da data de criação retornada e duração aceita quando não houver data absoluta na resposta; nunca da hora do retry.

201: ordem criada. 402: pode haver ordem/transação recusada, persistir identidade e resultado. 409/423/timeouts/5xx após envio: VERIFYING, não chave nova. 400/422 só liberam tentativa quando resposta comprova rejeição sem cobrança; erros de schema não são automaticamente CARD_REJECTED.

## Consulta e recuperação

- GET /v1/orders/{order_id}.
- GET /v1/orders?begin_date=<RFC3339>&end_date=<RFC3339>&external_reference=<UUID>&type=online; seguir paging da resposta data/paging.
- Janela de busca cobre horário original da tentativa, com margem de relógio de 5 minutos; dividir janelas se necessário pelos limites documentados e continuar paginação.
- Uma correspondência: conferir referência, valor exato, meio, tipo online e vínculo da conta recebedora antes de associar. Credencial é da conta proprietária configurada.
- Zero correspondências: não é prova de ausência; manter verificação. Mais de uma: registrar identidades, não liberar duas vezes, encaminhar compensação de recebimentos excedentes.
- Comparar valores em centavos inteiros ou decimal exato, nunca tolerância de ponto flutuante.

## Estados

| Order / detalhe | Tratamento |
|---|---|
| processed / accredited | Aprovação somente com quitação integral da transação e contexto local válido |
| created, processing, action_required | Sem aprovação; waiting_transfer gera tela Pix quando QR disponível |
| failed | Recusa somente se transação não liquidada |
| canceled, expired | Terminal sem recebimento quando confirmado |
| refunded | Confirmar valor integral antes de REFUNDED |
| processed / partially_refunded | Verificação/suporte; não tratar como nova aprovação |
| charged_back, desconhecido ou inconsistência | Conservar fatos e bloquear liberação; suporte conforme modelo |

O estado externo não sobrepõe intenção de cancelamento, reembolso em andamento ou revisão local mais nova.

## Cancelamento e reembolso

- POST /v1/orders/{id}/cancel, chave de cancelamento persistida. created/action_required são elegíveis; corrida com aprovação muda para devolução.
- POST /v1/orders/{id}/refund, chave de reembolso persistida. Reembolso integral sem amount/body parcial. Não introduzir reembolso parcial.
- HTTP 201/2xx é interpretado pelo conteúdo e confirmação; não basta para afirmar crédito final.
- Conflitos already_canceled/already_refunded/refund_already_in_process/idempotency_key_already_used: consultar e reconciliar, sem rotacionar chave.
- Saldo insuficiente/prazo excedido: registrar falha e suporte, sem fabricar sucesso. Timeout: observar operação antes de reenviar.

## Webhook

POST mp-webhook; manter type=payment para legado e adicionar type=order. Evento de Orders não é orders_v2 de outro produto.

1. Validar método, tamanho máximo de 64 KiB e tipos.
2. Ler data.id da URL para manifesto; conferir body.data.id quando presente. Para notificações sem campos opcionais, seguir regras de omissão da documentação; exigir identificadores necessários para autenticação. Não trocar silenciosamente URL assinada por outro recurso do body.
3. Manifesto: id:<data.id em minúsculas>;request-id:<x-request-id>;ts:<ts>;. HMAC-SHA256 com MP_WEBHOOK_SECRET, comparar v1 em tempo constante. Normalização apenas no manifesto.
4. Persistir inbox mínima/deduplicada antes de 200/201. Falha no banco retorna 5xx; assinatura inválida 401. Duplicata já persistida retorna 200.
5. Executor consulta recurso, valida e aplica. ACK não significa pagamento aprovado. Timestamp assinado não é relógio do negócio; reentregas legítimas não são descartadas só por atraso.

## Legado Payments

GET /v1/payments/{mp_payment_id}; PUT /v1/payments/{id} com status=cancelled quando elegível; POST /v1/payments/{id}/refunds para devolução. Preservar external_reference original=request_id. Consulta deve validar currency_id, transaction_amount e vínculo, não só moeda local. Mapear approved/rejected/cancelled/refunded e estados pendentes; devolver total confirmado em operações de reembolso existentes. Nunca passar ORD/PAY nesses endpoints.

A homologação deve verificar os contratos legados e Orders com credenciais da mesma conta. Referência: [cancelamento Payments](https://www.mercadopago.com.br/developers/pt/reference/online-payments/checkout-api-payments/create-cancellation/put). Nenhum endpoint remoto é chamado por este documento.
