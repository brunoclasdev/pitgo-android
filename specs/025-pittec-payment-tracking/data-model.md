# Data Model: Acompanhamento pós-pagamento e cobrança PitTec

## Entidades existentes alteradas

### `provider_services`

Origem do preço base do serviço. O preço vigente do serviço deve ser positivo e ser capturado no momento em que o PitTec aceita a solicitação.

### `private.dispatch_candidates`

Origem da distância usada no orçamento do candidato escolhido.

- `distance_meters`: distância registrada entre PitTec e cliente, não negativa.
- `provider_service_id`: identifica o preço base do serviço aceito.

### `pittec_pricing_rules`

Regra comercial versionada, protegida por RLS e consultada somente pelo backend.

| Campo | Regra |
|---|---|
| `id`, `version` | Identidade e versão monotônica da regra |
| `markup_percent` | 40.00 por padrão; aplicado sobre serviço + quilometragem |
| `distance_rate_per_km` | Tarifa vigente por quilômetro em BRL |
| `fee_percent` | Percentual de taxas do cliente |
| `fee_fixed_amount` | Parcela fixa das taxas do cliente |
| `currency` | BRL |
| `effective_from`, `effective_until` | Janela de vigência |
| `active` | No máximo uma regra vigente deve ser selecionável |
| `payment_test_mode` | Mantém a ordem do Mercado Pago em valor de teste |
| `test_payment_min_amount`, `test_payment_max_amount` | Faixa da ordem durante os testes; atualmente R$ 1,00 a R$ 2,00 |

Valores negativos e moeda diferente de BRL são inválidos. Alterações devem gerar ou ativar uma nova versão sem alterar propostas já congeladas.

### `appointments`

Armazena a proposta aceita e seus valores congelados:

- `service_amount`: preço base de `provider_services`.
- `distance_meters`: distância usada na proposta.
- `distance_rate_per_km`: tarifa copiada da regra vigente.
- `distance_charge_amount`: adicional de quilometragem.
- `provider_amount`: `service_amount + distance_charge_amount`.
- `markup_amount`: 40% de `provider_amount`.
- `customer_fee_amount`: taxas percentuais e fixas calculadas.
- `customer_total_amount`: `provider_amount + markup_amount + customer_fee_amount`.
- `payment_amount`: valor reservado para Pix/cartão; atualmente sorteado em centavos entre R$ 1,00 e R$ 2,00 em modo de testes.
- `payment_test_mode`: modo de teste congelado na proposta.
- `pricing_rule_id`, `pricing_rule_version`, `pricing_currency` e `quote_expires_at`.

Todos os valores monetários são arredondados a centavos antes de serem persistidos. A proposta não pode ser recalculada depois de confirmada pelo cliente.

### `payments`

Para PitTec, `amount` deve ser exatamente `appointments.payment_amount`, com BRL, `appointment_id` e `quote_version` correspondentes. O total comercial permanece em `customer_total_amount` e a aprovação só libera o atendimento quando todos os vínculos forem válidos.

## Projeções por papel

### PitTec

Pode receber `service_amount`, `distance_meters`, `distance_rate_per_km`, `distance_charge_amount`, `provider_amount`, moeda, versão e prazo. Não recebe `markup_amount`, `customer_fee_amount` ou `customer_total_amount`.

### Cliente

Pode receber `customer_total_amount`, moeda, versão e prazo. Não recebe `service_amount`, `distance_meters`, `distance_rate_per_km`, `distance_charge_amount`, `provider_amount`, `markup_amount` ou `customer_fee_amount`.

## Fórmula

```text
distance_charge_amount = distance_meters / 1000 * distance_rate_per_km
provider_amount = service_amount + distance_charge_amount
markup_amount = provider_amount * 40%
customer_total_amount = provider_amount + markup_amount + customer_fee_amount
```

O valor comercial é `customer_total_amount`. O valor usado pelo Mercado Pago é `payment_amount`; durante os testes, ele é sorteado entre R$ 1,00 e R$ 2,00, inclusive, e congelado no aceite.

## Ciclo operacional implementado

| Estado | Campos | Próxima ação |
|---|---|---|
| `AWAITING_PAYMENT` | proposta confirmada e pagamento pendente | aguardar aprovação |
| `ACCEPTED` | pagamento válido; `arrived_at` nulo | PitTec confirma chegada |
| `ACCEPTED` | `arrived_at` preenchido | PitTec confirma início |
| `IN_PROGRESS` | `service_started_at` preenchido | cliente reconhece; PitTec conclui |
| `COMPLETED` / `CANCELLED` | encerramento registrado | encerrar mapa e compartilhamento |

`client_start_acknowledged_at` é opcional. Confirmações repetidas preservam o registro inicial. `revision` controla concorrência e a leitura periódica recupera o estado atual. O snapshot financeiro sempre respeita o papel; campos internos não são enviados ao cliente.

A tarifa configurada é R$ 2,00/km (R$ 10,00 a cada 5 km). `distance_rate_per_km` nulo exige configuração antes de novos aceites. Taxas percentuais incidem sobre serviço + quilometragem, mantendo a regra existente. Valores são arredondados em PostgreSQL `numeric` a duas casas (meio centavo para cima em valores positivos).
