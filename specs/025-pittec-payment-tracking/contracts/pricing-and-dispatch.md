# Contrato: preço e despacho PitTec

## Aceite do PitTec

**Operação**: `respond_to_mobile_dispatch`

Quando o PitTec aceita a oferta, o backend deve:

1. localizar o preço vigente do serviço;
2. localizar a distância do candidato escolhido;
3. localizar a regra de preço vigente;
4. calcular e congelar os componentes da proposta;
5. mudar a solicitação para `AWAITING_CLIENT_CONFIRMATION`;
6. devolver um snapshot com projeção compatível com o papel autenticado.

Se a regra ou o preço obrigatório não existir, o aceite deve falhar com erro acionável e não criar uma proposta incompleta.

## Snapshot do cliente

No objeto `appointment.quote`, quando o chamador for o cliente:

```json
{
  "customer_total_amount": 195.40,
  "currency": "BRL",
  "version": 3,
  "expires_at": "2026-09-23T15:00:00Z"
}
```

O campo `customer_total_amount` é o único componente financeiro retornado ao cliente. O aplicativo deve mostrar apenas esse total.

## Snapshot do PitTec

No objeto `appointment.quote`, quando o chamador for o PitTec:

```json
{
  "service_amount": 100.00,
  "distance_meters": 12000,
  "distance_rate_per_km": 2.00,
  "distance_charge_amount": 36.00,
  "provider_amount": 136.00,
  "currency": "BRL",
  "version": 3,
  "expires_at": "2026-09-23T15:00:00Z"
}
```

O snapshot do PitTec não deve conter percentual, taxas ou total do cliente.

## Reserva do pagamento

**Operação**: `reserve_order_payment`

Para `PITTEC_AUTO_SEQUENTIAL`, a função deve validar cliente, solicitação, atendimento, versão, expiração e confirmação da proposta. O valor comercial continua em `customer_total_amount`, mas o valor persistido em `payments.amount` deve ser lido de `appointments.payment_amount` (valor aleatório entre R$ 1,00 e R$ 2,00 em testes); um `p_expected_amount` diferente deve retornar `PRICE_CHANGED`.

## Regras de erro

| Código | Condição | Resultado |
|---|---|---|
| `PITTEC_PRICING_UNAVAILABLE` | Regra de preço não vigente | Não cria proposta |
| `PRICE_CHANGED` | Total enviado difere do total congelado | Não cria pagamento |
| `QUOTE_EXPIRED` | Proposta fora da validade | Não cria pagamento |
| `INVALID_PAYMENT_CONTEXT` | Vínculo ou versão inválida | Não libera o atendimento |

## Contrato da UI

- Cliente: exibir uma única linha de total final e o prazo da proposta.
- PitTec: exibir serviço, quilometragem, adicional por quilômetro e valor do PitTec.
- Nenhum papel deve inferir componentes financeiros a partir de outro papel ou de `quoted_price`.

## Contrato de chegada e início

Todas as operações exigem sessão do participante. Somente o PitTec do atendimento executa chegada/início; somente o cliente reconhece o início.

- `get_pittec_appointment_snapshot(p_appointment_id uuid)`: devolve o snapshot por papel, inclusive em estados terminais; usado na recuperação periódica do mapa.
- `arrive_pittec_appointment(p_request_id uuid, p_appointment_id uuid, p_expected_revision bigint)`: exige pagamento válido e estado ACCEPTED; grava `arrived_at` sem iniciar serviço; repetição é idempotente.
- `start_mobile_appointment(p_appointment_id uuid, p_expected_revision bigint)`: para PitTec exige chegada e pagamento válido; grava `service_started_at` e muda solicitação/atendimento para IN_PROGRESS.
- `acknowledge_pittec_start(p_appointment_id uuid, p_expected_revision bigint)`: registra `client_start_acknowledged_at` quando IN_PROGRESS; repetição não altera o registro inicial.
- Sem chegada: `ARRIVAL_REQUIRED`. Sem aprovação financeira válida: `PAYMENT_REQUIRED`. Outro participante: `PARTICIPANT_REQUIRED` ou `APPOINTMENT_UNAVAILABLE`.

O mapa consulta estado e localizações a cada cinco segundos enquanto visível, além dos avisos Realtime. O cliente publica posição enquanto o mapa está visível; o serviço de localização existente mantém a publicação do PitTec. Posições antigas são sinalizadas e estados terminais removem os marcadores.
