# Payment Environment Contract

## Common gateway behavior

O checkout e o PitTec chamam `PaymentGateway` e continuam recebendo os modelos/estados de pagamento existentes. A UI não conhece `BuildConfig`, Supabase ou Mercado Pago.

### Simulated gateway (`dev`)

- Não chama `SupabaseClient.functions` para `mp-create-payment`, `mp-cancel-payment` ou `mp-refund-payment`.
- Não chama RPC financeiro nem canal Realtime financeiro externo.
- Mantém contexto local da tentativa e emite os estados `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED` e `CANCELLED` por ações de teste determinísticas.
- Retorna identificadores marcados como simulação para impedir confusão com pagamentos reais.
- Falhas de rede não impedem a execução dos cenários locais.
- Após aprovação, `features/pittec/repository/SimulatedPitTecFlowRepository.kt` fornece estado local para deslocamento, atendimento e conclusão, reutilizado pelo ViewModel e UI. Cenários locais permitem iniciar sem atendimento remoto. Não há envio de pagamento simulado nem mutação de atendimento no servidor; homol/prod preservam a continuação integrada.

### Integrated gateway (`homol`/`prod`)

- Usa o `PaymentRepository` atual e o cliente Supabase criado com a configuração do flavor.
- Chama as Edge Functions/RPCs somente do backend correspondente.
- Inicializa o SDK Mercado Pago somente com a public key correspondente quando o fluxo integrado exigir.
- Mantém o tratamento atual de aprovação, falha, expiração, cancelamento, reembolso e recuperação.
- Não faz fallback silencioso para simulação se a configuração estiver ausente ou inválida.

## Isolation rules

`PaymentMode` é derivado do flavor e não pode ser alterado por tela, parâmetro remoto ou preferência. O container deve construir exatamente uma implementação do gateway por processo. O fingerprint do ambiente deve ser validado antes de restaurar uma tentativa ou sessão.

## Error contract

Falha de configuração deve ocorrer antes da operação financeira e ser apresentada como erro acionável por ambiente. Logs podem registrar ambiente, operação e código sanitizado, mas nunca tokens, secrets, dados de cartão ou payloads financeiros completos.
