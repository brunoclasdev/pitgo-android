# Quickstart: validar o fluxo de pagamento do PitTec

Este guia valida o comportamento descrito em [spec.md](spec.md). O contrato detalhado está em
[contracts/pittec-payment-flow.md](contracts/pittec-payment-flow.md) e o modelo está em
[data-model.md](data-model.md).

## Pré-requisitos

- Branch 023-fluxo-pagamento-pittec.
- Android Studio/SDK, Java 21 e dispositivo ou emulador com dois perfis autenticados:
  cliente e prestador PitTec.
- Projeto Supabase de homologação vinculado, migrations aplicadas e cron de expiração ativo.
- Credenciais de teste Mercado Pago da mesma conta usada nas Edge Functions.
- Pelo menos um serviço PitTec ativo e um prestador com provider_services.price positivo.
- Uma regra pittec_pricing_rules ativa, com markup_percent=40.00, BRL e taxa de teste conhecida.
- Pelo menos um cartão de teste salvo; manter também dados para cadastrar um cartão novo.
- Não usar conta de produção nem dados reais de cartão.

## Verificações estáticas

Na raiz:

    ./gradlew :app:assembleDebug :app:lintDebug
    deno check supabase/functions/mp-create-payment/index.ts
    deno check supabase/functions/mp-webhook/index.ts
    deno check supabase/functions/mp-reconcile-payments/index.ts
    supabase db lint --local

Quando Docker não estiver disponível, registrar essa limitação sem marcar a validação SQL como
passada. Não executar reset em banco com dados a preservar.

## Cenário 1 — busca antes do pagamento

1. No cliente, solicitar um PitTec com localização válida.
2. Confirmar que a busca inicia e nenhum registro de cobrança é criado para a solicitação.
3. No prestador, aceitar a solicitação.
4. Confirmar que o prestador recebe somente provider_amount.
5. Confirmar que o cliente recebe a proposta com markup, taxas, total, versão e expiração.

Esperado:

- O cliente não vê provider_amount.
- O prestador não vê markup, taxas ou customer_total_amount.
- Nenhuma cobrança é enviada antes do aceite.

## Cenário 2 — fórmula e confirmação

1. Configurar valor base conhecido e taxa conhecida na regra ativa.
2. Aceitar como prestador.
3. Conferir: markup = base × 40%; total = base + markup + taxas.
4. Tentar abrir o pagamento sem confirmar a proposta.
5. Confirmar a proposta dentro de 15 minutos.

Esperado:

- O pagamento permanece bloqueado antes da confirmação.
- A confirmação aceita somente a quote_version/revision atuais.
- A etapa de pagamento mostra exatamente o total confirmado.
- Alterar a regra ou o preço depois do aceite não altera a proposta congelada.

## Cenário 3 — cartão salvo e cartão novo

1. Na proposta confirmada, selecionar um cartão salvo e informar o código de segurança pelo
   componente seguro.
2. Repetir o fluxo em uma nova proposta escolhendo cadastrar cartão.
3. Concluir a tokenização, conferir marca/últimos quatro dígitos e selecionar o novo cartão.

Esperado:

- Nenhum PAN, CVV ou token aparece em logs, payloads persistidos ou telas do outro usuário.
- O pagamento usa o customer_total_amount, nunca provider_amount.
- Falha no cadastro deixa a proposta em AWAITING_PAYMENT sem cobrança criada.

## Cenário 4 — aprovação e início

1. Concluir o pagamento com cartão de teste aprovado.
2. Fechar e reabrir o app do cliente antes de a tela atualizar.
3. Observar o snapshot/realtime e a tela do prestador.
4. Tentar marcar chegada antes e depois da aprovação.

Esperado:

- PENDING/VERIFYING não inicia o atendimento.
- APPROVED válido muda o atendimento para ACCEPTED e libera o próximo passo.
- Cliente e prestador visualizam o estado atualizado em até 10 segundos em conexão normal.
- A tentativa anterior é retomada; nenhuma cobrança duplicada é criada.

## Cenário 5 — recusa, erro e repetição

1. Usar cartão de teste recusado.
2. Tentar novamente com outro cartão na mesma proposta.
3. Repetir o toque de pagar e simular dois dispositivos.

Esperado:

- O serviço não começa com pagamento recusado ou pendente.
- A troca de cartão é possível sem duplicar uma tentativa ativa.
- A mesma proposta produz no máximo uma cobrança que possa liberar o atendimento.
- A mensagem ao usuário é acionável e não expõe detalhes do provedor.

## Cenário 6 — expiração

1. Aceitar uma proposta e não confirmar/pagar.
2. Aguardar a janela de 15 minutos ou executar o job de expiração apenas em homologação.
3. Reabrir os dois aplicativos e tentar confirmar/pagar.

Esperado:

- A proposta fica inválida após o prazo.
- O prestador é liberado e a solicitação é encerrada.
- O pagamento tardio é rejeitado.
- O cliente precisa iniciar nova solicitação para buscar outro prestador.

## Cenário 7 — timeout e recuperação

1. Em homologação, interromper a resposta após o envio da criação Orders.
2. Fechar o app do cliente.
3. Reabrir o app e aguardar polling/realtime/executor.

Esperado:

- A tentativa permanece VERIFYING sem novo POST de criação.
- A reconciliação associa uma Order existente ou mantém pendência.
- Uma aprovação tardia só libera a proposta se contexto, valor, versão e intenção ainda forem
  válidos; caso contrário, segue para cancelamento/devolução conforme regra financeira.

## Auditoria de privacidade

Para cada cenário, inspecionar somente logs sanitizados e snapshots de cada papel:

- cliente nunca recebe provider_amount;
- prestador nunca recebe customer_total_amount ou customer_fee_amount;
- nenhum log contém Access Token, PAN, CVV, token de cartão ou payload completo do provedor;
- mudanças de preço e regra mostram a versão congelada, sem recalcular uma proposta antiga.

## Evidências

Registrar status HTTP/resultado sanitizado, request_id local, quote_version, estados e timestamps.
Não registrar valores de cartão, credenciais, QR privado, payloads integrais ou identificadores
externos que não sejam necessários para correlação operacional.
## Implementação local

- `./gradlew :app:assembleDebug :app:lintDebug`: passou em 2026-09-18.
- `./gradlew :app:compileDebugKotlin`: passou em 2026-09-18.
- `deno check`: não executado; o binário `deno` não está instalado no ambiente.
- `supabase db lint --local`: não executado; o CLI/ambiente local do Supabase não está disponível.
- Os cenários de cliente/prestador, expiração de 15 minutos e pagamento aprovado ainda precisam ser executados em homologação com duas contas autenticadas.
