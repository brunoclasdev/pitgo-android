# Quickstart: validar cobrança e acompanhamento PitTec

## Pré-requisitos

- Projeto Android configurado com o SDK local.
- Projeto Supabase vinculado ao ambiente de desenvolvimento.
- Um usuário cliente e um usuário PitTec autenticados.
- Um serviço PitTec ativo com preço positivo em `provider_services`.
- Uma regra ativa em `pittec_pricing_rules` com tarifa de R$ 2,00/km (R$ 10,00 a cada 5 km), 40% e taxas definidas.

## Validar a regra no banco

Verifique a regra vigente:

```sql
select id, version, markup_percent, distance_rate_per_km,
       fee_percent, fee_fixed_amount, currency, active,
       effective_from, effective_until
from public.pittec_pricing_rules
where active = true
order by effective_from desc, version desc;
```

Para uma solicitação concluída, confira os componentes congelados:

```sql
select a.id as appointment_id,
       a.service_amount,
       a.distance_meters,
       a.distance_rate_per_km,
       a.distance_charge_amount,
       a.provider_amount,
       a.markup_amount,
       a.customer_fee_amount,
       a.customer_total_amount,
       p.amount as payment_amount,
       p.status as payment_status
from public.appointments a
left join public.payments p on p.appointment_id = a.id
where a.id = '<APPOINTMENT_ID>';
```

## Cenário cliente

1. Cliente solicita um serviço PitTec.
2. PitTec aceita a oferta.
3. Confirme que a tela do cliente mostra somente `customer_total_amount`.
4. Pague via Pix ou cartão salvo.
5. Confirme que `appointments.customer_total_amount` mantém o total real e `payments.amount = appointments.payment_amount` está entre `1.00` e `2.00`, em centavos, durante os testes.
6. Após aprovação, confirme a abertura do mapa e a atualização do status.

## Cenário PitTec

1. Abra a solicitação recebida no usuário PitTec.
2. Confirme que a tela mostra serviço, adicional de quilometragem e `provider_amount`.
3. Confirme que markup, taxas e total do cliente não aparecem.
4. Após o pagamento aprovado, marque a chegada e confirme o início do serviço.

## Casos de falha

- Em ambiente isolado ou transação com rollback, desative temporariamente a regra vigente: o aceite deve retornar `PITTEC_PRICING_UNAVAILABLE` e não deve criar uma cobrança.
- Envie um valor diferente do total congelado: a reserva deve retornar `PRICE_CHANGED`.
- Expire a proposta: a cobrança deve retornar `QUOTE_EXPIRED`.
- Reabra a aplicação após pagamento aprovado: cliente e PitTec devem retomar o estado correto sem duplicar transições.

## Validação local

```bash
./gradlew :app:assembleDebug
```

Depois da compilação, instalar o APK gerado nos quatro dispositivos Android conectados e executar os dois cenários acima.

## Estado da validação desta implementação

Consulte [validation.md](validation.md) para evidências e pendências. A faixa de teste entre R$ 1,00 e R$ 2,00 deve ser desativada antes de cobrança real.

Após publicar, repetir chegada e início separadamente: chegada deve conservar ACCEPTED; iniciar deve produzir IN_PROGRESS. Repetir cada ação para confirmar idempotência. O reconhecimento do cliente deve persistir ao reabrir a aplicação.
