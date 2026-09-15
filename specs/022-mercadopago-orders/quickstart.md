# Quickstart: validar a migração Orders

Este guia cobre a validação da implementação (código já escrito em `app/` e `supabase/`). Nenhuma
função foi publicada em homologação/produção, nenhuma migration foi aplicada em banco real e
nenhuma credencial foi validada nesta sessão — só verificações locais de código.

## Status de verificação local (2026-09-15, sessão de implementação)

- `deno check` de todas as `mp-*` Edge Functions alteradas/criadas: sem erros de tipo.
- `./gradlew :app:assembleDebug :app:lintDebug`: BUILD SUCCESSFUL, 0 erros de lint (115 avisos
  pré-existentes no projeto, nenhum nos arquivos de `features/payment`).
- `supabase db lint --local` e `supabase migration up --local`: **não executados** — Docker
  continua ausente do PATH deste ambiente (`ECONNREFUSED 127.0.0.1:54322`), então o Postgres local
  nunca inicia. Isso é uma limitação de ambiente, não uma falha das migrations; permanece um
  impedimento real registrado, não uma validação marcada como concluída.
- Homologação (projeto Supabase dedicado, credenciais Mercado Pago, deploy das functions, matriz
  manual de aceitação, SC-006/SC-007) **não foi executada** — exige projeto/credenciais explícitos
  do operador, que não foram fornecidos nesta sessão. `MP_ORDERS_ENABLED` deve permanecer desligado
  até essa validação ocorrer.

## Pré-requisitos

Verificação local de 2026-09-15: Java 21.0.10 e Android SDK disponíveis; Deno 2.9.6 e Supabase CLI 2.117.0 acessíveis por `npx --yes deno` e `npx --yes supabase`, sem upgrade das dependências do app. Docker e psql ausentes do PATH; banco local e homologação ainda não validados. Projeto de homologação e credenciais não foram selecionados nesta sessão. A preparação/documentação não habilita Orders em produção.

- Java 21/Android SDK configurados, dispositivo Android e contas cliente/prestador de homologação.
- CLI Supabase, Deno e Docker disponíveis para verificações locais (Supabase/Deno não estavam no PATH durante o planejamento).
- Projeto Supabase exclusivo de homologação e credenciais Mercado Pago correspondentes ao ambiente. Usar instruções de [contas de teste](https://www.mercadopago.com.br/developers/en/docs/checkout-api-orders/resources/test-accounts).
- MP_ACCESS_TOKEN, MP_WEBHOOK_SECRET e MP_RECONCILE_SECRET em arquivo de segredos ignorado pelo Git; chave pública no mecanismo de build existente. Não imprimir valores.
- No Vault, URL da função e mesmo segredo interno de reconciliação, acessíveis apenas pela função SQL restrita de agendamento.
- Manter um cartão e pagamentos de homologação criados com a integração anterior, cobrindo pendente/aprovado/reembolsável antes de ativar Orders.

## Verificações locais após implementar

Na raiz do projeto:

```sh
./gradlew :app:assembleDebug :app:lintDebug
deno check supabase/functions/mp-create-payment/index.ts supabase/functions/mp-webhook/index.ts supabase/functions/mp-refund-payment/index.ts supabase/functions/mp-cancel-payment/index.ts supabase/functions/mp-reconcile-payments/index.ts
supabase start
supabase migration up --local
supabase db lint --local
```

Executar em ambiente local de validação e aplicar apenas migrations revisadas. Não usar reset em banco que contenha dados a preservar. Migrações históricas que exigem extensões/agendamento precisam dos mesmos pré-requisitos do projeto; registrar falha de infraestrutura separadamente de falha da nova migration.

## Preparação da homologação

Os comandos seguintes alteram somente o projeto de homologação explicitamente selecionado pelo operador:

```sh
supabase link --project-ref <REF_HOMOLOGACAO>
supabase secrets set --env-file <ARQUIVO_LOCAL_DE_SEGREDOS>
supabase db push
supabase functions deploy mp-create-payment mp-save-card mp-remove-card mp-refund-payment mp-cancel-payment mp-webhook mp-reconcile-payments
./gradlew :app:installDebug
```

Configurar no painel Mercado Pago a URL HTTPS do mp-webhook para Order (Mercado Pago) e manter Payment para legado. Configurar Vault e cron conforme migration, sem literal de segredo em SQL versionado. Confirmar que MP_ORDERS_ENABLED só habilita novas cobranças após backend, schema e app compatíveis.

Com app fechado, verificar no painel Supabase que existe exatamente um novo cron de minuto e que ciclos sem trabalho não chamam a função externa. Não alterar a cadência dos jobs PitTec existentes.

## Matriz de aceitação

| Cenário / requisitos | Procedimento | Resultado esperado |
|---|---|---|
| Pix nos dois fluxos — FR-001/002/003/006/007 | Solicitação móvel e PitTec; neste último aceitar prestador e confirmar proposta antes de pagar. Usar os valores de teste oficiais para aprovação/recusa | QR/copia e cola, duração de 30 min, valor correto; avanço somente após aprovação |
| Cartão salvo — FR-004/014 | Pagar com cartão anterior à migração e outro cadastrado depois; preencher CVV nativo; repetir com recusa | Token novo, sem recadastro exigido pela migração; recusa acionável; nenhum PAN/CVV/token em logs |
| Repetição/concorrência — FR-008 | Repetir ação e mesma entrada em dois dispositivos com mesmo contexto | Uma tentativa bloqueadora e uma quitação; nenhuma liberação duplicada |
| Timeout de criação — FR-008/009/016 | Em homologação, interromper a resposta após envio; fechar app. Inspecionar tentativa e permitir executor consultar | VERIFYING; recuperar por referência sem POST de nova cobrança; retorno ao app mostra resultado |
| Busca vazia — FR-008 | Observar recuperação antes de recurso aparecer ou numa falha pré-envio | Não liberar nova cobrança só porque consulta não encontrou; backoff e suporte após prazo |
| Morte do processo — FR-009/016 | Gerar Pix e executar comando abaixo; confirmar pagamento no cenário de teste oficial; reabrir app | Mesmo request/payment; snapshot recupera QR/resultado sem criar outra order |
| Cancelamento pendente — FR-011 | Cancelar e induzir aprovação tardia em homologação | Nenhum atendimento reativado; reembolso integral agendado |
| Reembolso sem app — FR-011/012/016 | Cancelar pagamento aprovado antes da conclusão e fechar app imediatamente | Executor envia devolução; não depende de mp-refund-payment chamado pelo celular |
| Falha de reembolso — FR-012 | Induzir falha transitória controlada por proxy/debugger apenas em homologação, depois falha persistente | Até 3 envios, chave estável, consultas separadas; suporte após limite; sem falso REFUNDED |
| Crash do executor — FR-008/016 | Interromper execução após claim em ambiente de validação e aguardar lease expirar | Novo ciclo recupera após 90 s; nenhuma repetição financeira indevida |
| Webhook duplicado/antigo — FR-009/014 | Reentregar evento oficial pela ferramenta do provedor; testar assinatura inválida separadamente | Inbox deduplica; sem nova aprovação/reembolso; inválido não muda estado |
| Evento antes de persistir ID — FR-008 | Interromper resposta após criação externa com reserva já gravada | Reconciliar inbox por referência e anexar IDs; não perder confirmação |
| Legado — FR-010/013 | Consultar/confirmar/cancelar/reembolsar os registros anteriores à migração | Mesmo histórico/IDs; operações Payments, sem recriação Orders |
| Isolamento — FR-014 | Conta B tenta operar request/payment/card de A; chamar executor com JWT comum | Acesso negado, zero dados vazados |
| Contexto/preço — FR-007 | Alterar amount/service_id e versão de proposta enviada; testar proposta vencida | Servidor rejeita ou exige nova confirmação de preço; não cobra valor manipulado |
| Duplicata de aprovação PitTec — FR-006/008 | Receber a mesma aprovação após atendimento entrar em ACCEPTED | Sem novo aceite nem estorno indevido |
| Gratuito/pós-conclusão — FR-015 | Percorrer serviço gratuito e cancelamento após conclusão | Sem cobrança zero e sem novo direito a reembolso automático |
| Histórico/retomada — FR-005/013 | Remover cartão e reabrir histórico, incluindo mais de uma pendência | Meio mascarado e dados financeiros preservados; paginação e retomada corretas |
| Limitação externa — FR-016 | Simular 429 com Retry-After e 5xx em homologação | Backoff respeitado; sem loop intenso ou novas chaves de cobrança |

```sh
adb shell am force-stop com.trustarx.pitgo
adb shell monkey -p com.trustarx.pitgo 1
```

Referências oficiais de simulação: [Pix](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/integration-test/pix) e [introdução aos testes](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/integration-test/introduction). Não assumir que editar status local comprova confirmação pelo processador. Para cenários de falha, usar debugger/proxy controlado; não adicionar bypass de assinatura ou endpoint de aprovação ao produto.

## Consulta operacional restrita (REFUND_FAILED, compensações, suporte)

Estas consultas usam a role de serviço (`service_role`, nunca `anon`/`authenticated`) no editor
SQL do projeto de homologação/produção explicitamente selecionado; não criam um portal novo, apenas
leem colunas já persistidas em `public.payments` pelas migrations desta feature.

Pendências com reembolso esgotado ou aguardando análise manual:

```sql
select id, request_id, client_id, provider_api, mp_order_id, mp_payment_id, amount, currency,
  refund_attempts, refund_submission_state, cancel_submission_state, support_required_at,
  last_error_code, next_action_at
from public.payments
where support_required_at is not null
order by support_required_at asc;
```

Compensações abertas (recebimentos excedentes registrados por `mp_register_order_identity`, T034),
que bloqueiam nova cobrança normal do mesmo `request_id` enquanto não resolvidas:

```sql
select id, request_id, mp_order_id, external_reference, amount, status, refund_submission_state,
  created_at
from public.payments
where is_compensation and status <> 'REFUNDED'
order by created_at asc;
```

Idade da pendência mais antiga com trabalho vencido (indicador operacional de SC-006/FR-016):

```sql
select min(next_action_at) as oldest_due, count(*) as pending
from public.payments
where work_action is not null and next_action_at <= now();
```

- **Tentativas vs. consultas**: `refund_attempts` só incrementa em `mp_begin_operation`, imediatamente
  antes de um envio real de `/refund` (Orders) ou `/refunds` (Payments legado). Uma consulta
  autoritativa (`GET`), inclusive repetida pelo executor a cada ciclo, nunca incrementa esse
  contador. O limite de três aplica-se somente aos envios; a consulta pode continuar
  indefinidamente para confirmar um resultado, conforme `research.md`/§6.
- **Confirmação tardia após encaminhamento a suporte**: marcar `support_required_at` não interrompe
  a reconciliação — `work_action='RECONCILE'` e `next_action_at` continuam agendando consultas
  horárias (ver `mp_record_operation`). Se o processador confirmar a devolução depois do
  encaminhamento, a próxima consulta bem-sucedida aplica `REFUNDED` normalmente via
  `mp_apply_evidence`; o suporte humano só precisa agir enquanto a pendência permanecer
  `REFUND_FAILED`/`support_required_at` sem essa confirmação.
- Sem portal administrativo ou envio de mensagens externas: o suporte usa as consultas acima (ou o
  painel Supabase) e a resposta ao cliente segue os canais já existentes do produto, fora do escopo
  desta feature.

## Evidências e aprovação da entrega

Registrar IDs locais de homologação, horário e resultado por cenário; ocultar dados pessoais. Conferir:

- SC-001–005/008: todos os cenários correspondentes passam; sem cobrança líquida duplicada.
- SC-006: medir 20 ações de pagamento com conectividade, ao menos 19 com feedback em até 10 s; distinguir tempo do banco/pagador.
- SC-007: inspeção de logs, banco e UI sem PAN/CVV/token ou credenciais privadas.
- Inbox sem perda, pendências vencidas drenadas em ciclos limitados, sem ciclos subminuto; medir idade da pendência mais antiga.
- Limite de tentativas de reembolso, confirmação integral e acompanhamento manual visíveis.
- Compatibility gate: cartão salvo anterior realmente tokeniza/cobra na conta de homologação; busca Orders retorna recurso criado. Se não passar, suspender ativação e registrar evidência.
- Código de produção continua sem rotas de simulação. Em incidente, suspender novas criações e manter reconciliação/reembolso.

Detalhes técnicos não duplicados: [interfaces](contracts/payment-interfaces.md), [Mercado Pago](contracts/mercadopago-orders.md), [modelo](data-model.md).
