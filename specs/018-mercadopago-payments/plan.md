# Implementation Plan: Pagamento de serviços (Pix e cartão)

**Branch**: `018-mercadopago-payments` | **Date**: 2026-09-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/018-mercadopago-payments/spec.md`

## Summary

Adicionar pagamento via Pix e cartão salvo (Mercado Pago) como etapa obrigatória antes do
despacho, tanto no fluxo padrão de solicitação de serviço quanto na chamada PitTec sob demanda. O
cliente cadastra cartões de forma totalmente opcional numa área dedicada de formas de pagamento
(Perfil); a etapa de pagamento da solicitação só oferece Pix e cartões já cadastrados, nunca
cadastro inline. O Access Token do Mercado Pago fica só em Supabase Secrets, nunca no app; o
tokenizador de cartão roda no app via SDK nativo do Mercado Pago, e a cobrança/estorno passam por
novas Edge Functions que chamam a API do Mercado Pago e mantêm o estado em novas tabelas
Postgres. RPCs de despacho existentes (`start_mobile_dispatch`, `start_pittec_dispatch`) passam a
exigir pagamento aprovado; cancelamento pré-conclusão e busca PitTec exaurida disparam estorno
automático com retry e fallback para suporte em caso de falha persistente.

## Technical Context

**Language/Version**: Kotlin 2.2.10, Java 11 bytecode; TypeScript/Deno nas Edge Functions;
PostgreSQL/PL-pgSQL nas migrações Supabase

**Primary Dependencies**: Jetpack Compose BOM 2026.06.01, Navigation Compose 2.9.8, Lifecycle
2.10.0, Coroutines/StateFlow, Supabase Kotlin 3.2.6 (Postgrest, Functions, Realtime), Ktor 3.3.1;
uma dependência nova e justificada: **Mercado Pago Android SDK**, para tokenização de cartão
isolada do escopo PCI, sem alternativa nativa/Jetpack. Nenhuma biblioteca de QR Code é necessária —
a API de Pagamentos do Mercado Pago já devolve o QR Pix pronto em Base64 (ver research.md §3)

**Storage**: PostgreSQL gerenciado pelo Supabase com RLS e Realtime; duas tabelas novas
(`payments`, `saved_cards`); nenhum dado sensível de cartão é persistido, apenas identificadores
reutilizáveis retornados pelo Mercado Pago

**Testing**: validação manual orientada por cenários (sandbox do Mercado Pago com cartões e
usuários de teste), `supabase db lint`, build Android debug/release e `lintDebug`; a constituição
não exige testes automatizados e o projeto não possui infraestrutura de testes configurada

**Target Platform**: Android API 24+, compileSdk 37, targetSdk 36; Supabase PostgreSQL e Edge
Functions Deno

**Project Type**: aplicativo Android de módulo único com backend Supabase versionado no mesmo
repositório

**Performance Goals**: confirmação de pagamento (Pix ou cartão) refletida na UI em poucos segundos
(SC-005); fluxo Pix completo em até 2 minutos (SC-001); estorno automático concluído ou escalado
para suporte sem exigir ação do cliente (SC-007)

**Constraints**: Access Token e demais segredos do Mercado Pago só em Supabase Secrets, nunca no
APK; nenhuma cobrança duplicada por solicitação (idempotência por `request_id`); nenhum número
completo de cartão, CVV ou validade persistido; despacho (padrão e PitTec) bloqueado enquanto o
pagamento não estiver aprovado; cadastro de cartão restrito à área dedicada de formas de pagamento,
sem cadastro inline no checkout (FR-019); webhook público deve validar a origem da notificação e
sempre reconsultar o pagamento na API do Mercado Pago antes de confiar no status

**Scale/Scope**: 2 tabelas novas, 5 Edge Functions novas (`mp-create-payment`, `mp-save-card`,
`mp-remove-card`, `mp-webhook`, `mp-refund-payment`), guarda de pagamento adicionada a 2 RPCs de
despacho existentes, 2 features Android novas (`paymentmethods`, `payment`) e extensões pontuais em
`requestservice`, `pittec`, `mobiledispatch` e `profile`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Verificação pré-pesquisa | Verificação pós-design |
|---|---|---|
| Kotlin e Android nativo | PASS — UI e integração seguem em Kotlin/Compose no módulo `:app`. | PASS — só as Edge Functions usam TypeScript, como já ocorre no projeto. |
| Arquitetura feature-first e MVVM | PASS — novas features `payment` e `paymentmethods` isoladas; extensões pontuais nas features existentes. | PASS — Composables recebem estado/eventos; ViewModels expõem `StateFlow`; Repository isola Supabase/SDK do Mercado Pago. |
| Coroutines, StateFlow e Navigation Compose | PASS — mecanismos já adotados. | PASS — nenhuma pilha paralela de estado ou navegação introduzida. |
| Segurança, RLS e privacidade | PASS — cobrança/estorno ficam em RPCs e Edge Functions autenticadas; Access Token nunca sai do backend. | PASS — `saved_cards` guarda só bandeira/últimos dígitos/id reutilizável; RLS restringe cada tabela ao cliente dono. |
| Estados explícitos e recuperação | PASS — `Pagamento` reutiliza o padrão de status explícito já usado em despacho/atendimento. | PASS — status de pagamento (pendente/aprovado/recusado/expirado/cancelado/reembolsado) e do estorno têm transições contratuais. |
| KISS/YAGNI | PASS — sem split de pagamento a prestadores, sem boleto, sem cadastro inline no checkout. | PASS — nenhuma infraestrutura especulativa (ex.: carteira multi-moeda, assinaturas) foi introduzida. |
| Qualidade e verificação | PASS — plano inclui `supabase db lint`, builds e cenários ponta a ponta via sandbox do Mercado Pago. | PASS — nenhuma suíte de testes automatizada criada sem solicitação; quickstart cobre a matriz manual. |
| Configuração e segredos | PASS — Access Token, client secret e chave de webhook só em Supabase Secrets. | PASS — nenhuma credencial versionada; app usa apenas a Public Key do Mercado Pago via `BuildConfig`. |

Não há violação constitucional a justificar.

## Design Decisions

1. **Gate de pagamento nos RPCs de despacho existentes**: `start_mobile_dispatch` e
   `start_pittec_dispatch` passam a exigir um `payments` aprovado para o mesmo `request_id`, em vez
   de criar um novo fluxo de despacho paralelo — menor superfície de mudança e reaproveita o
   contrato transacional já existente (spec 013/015).
2. **Tokenização de cartão só no cliente**: o SDK nativo do Mercado Pago gera o token do cartão
   dentro do app; o app nunca envia PAN/CVV ao backend do PitGo, só o token de curta duração.
3. **Cartão salvo via Customer + Customer Card do Mercado Pago**: a Edge Function `mp-save-card`
   cria (ou reaproveita) um "Customer" do Mercado Pago vinculado ao `user_id` do PitGo e salva o
   token como "Customer Card", retornando um `card_id` reutilizável — é o mecanismo suportado pelo
   próprio Mercado Pago para cobranças futuras sem redigitar dados; reter o `card_token` bruto para
   reuso foi descartado por ser de curta duração/uso único (ver research.md).
4. **Cobrança e estorno concentrados em Edge Functions dedicadas**: `mp-create-payment` e
   `mp-refund-payment` são os únicos pontos que guardam/usam o Access Token; RPCs Postgres só leem
   e escrevem o resultado já processado, mantendo o segredo fora do banco.
5. **Webhook nunca confia no corpo recebido**: `mp-webhook` sempre busca o pagamento pelo `id` na
   API do Mercado Pago antes de atualizar `payments`, e usa `request_id` (armazenado como
   `external_reference`) para religar ao despacho correspondente.
6. **Idempotência por solicitação**: `mp-create-payment` usa o `request_id` como chave de
   idempotência ao chamar a API do Mercado Pago, atendendo FR-011/SC-006 sem lógica extra no app.
7. **Estorno automático dirigido por evento, não por polling**: os próprios RPCs de cancelamento
   (`cancel_mobile_appointment`, cancelamento/exaurida do PitTec) enfileiram o estorno quando o
   pagamento já estava aprovado e o atendimento não foi concluído (FR-015/FR-017); `mp-refund-payment`
   aplica até 3 tentativas automáticas antes de marcar `REFUND_FAILED` para acompanhamento manual
   (FR-018).
8. **Sem cadastro inline no checkout**: a etapa de pagamento da solicitação lê apenas os cartões já
   salvos; o fluxo de cadastro fica isolado em `features/paymentmethods`, reduzindo estados e telas
   do checkout (decisão do usuário, ver Clarifications em spec.md).
9. **Correção de escopo pós-investigação**: `RequestService` (`onSubmit` no-op em
   `PitGoNavHost.kt:836`) é a tela do fluxo BROWSE (empresas fixas, provider já escolhido
   manualmente) — uma lacuna pré-existente e não relacionada a pagamento; não é tocada por esta
   feature. O "fluxo padrão de solicitação de serviço" de FR-014 é, na verdade, o despacho
   automático MOBILE_SEQUENTIAL, acionado direto do Home
   (`ServiceDispatchKind.MOBILE_SEQUENTIAL → PitGoRoutes.mobileDispatchNew(service.id)`) via
   `ClientMobileDispatchViewModel.startWithPreciseLocation()` — o par exato do
   `PitTecViewModel.requestPitTec()` já identificado para o PitTec. O gate de pagamento se conecta
   nesses dois pontos, não em `RequestService`.
10. **Nova rota de pagamento compartilhada**: uma tela `features/payment` própria (rota
    `payment/{serviceId}/{dispatchKind}`) fica entre o toque no card do Home e a tela de despacho.
    O Home gera o `request_id` e passa `serviceId`/`dispatchKind`/valor (do catálogo,
    `HomeService.minimumPrice` — mesma estimativa já exibida hoje, com a mesma ressalva de que o
    valor final pode variar); ao aprovar, ela navega para a rota de despacho já existente
    (`mobile-dispatch/new/{serviceId}` ou `pittec/{serviceId}`) carregando o `request_id` já pago,
    e o ViewModel de destino passa a iniciar a busca automaticamente com esse id em vez de esperar
    um toque manual em "Solicitar"/"Chamar PitTec" — a cobrança substitui esse toque como a ação
    que efetivamente dispara a busca.

Detalhes e alternativas estão registrados em [research.md](research.md).

## Project Structure

### Documentation (this feature)

```text
specs/018-mercadopago-payments/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── mercadopago-edge-functions.md
│   ├── payment-rpcs.md
│   └── payment-realtime-ui.md
└── tasks.md                         # criado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/
├── java/com/trustarx/pitgo/
│   ├── core/
│   │   ├── account/model/           # AccountSnapshot.profile.cpf reaproveitado como payer.cpf
│   │   └── network/                 # SupabaseClientProvider (Functions já instalado)
│   └── features/
│       ├── payment/                 # NOVO: checkout (Pix + cartão salvo) da solicitação
│       │   ├── data/
│       │   ├── model/
│       │   ├── repository/
│       │   ├── ui/
│       │   └── viewmodel/
│       ├── paymentmethods/          # NOVO: cadastro/lista/remoção de cartões (área dedicada)
│       │   ├── data/
│       │   ├── model/
│       │   ├── repository/
│       │   ├── ui/
│       │   └── viewmodel/
│       ├── requestservice/          # liga onSubmit → payment → mobiledispatch
│       ├── pittec/                  # liga requestPitTec() → payment → repository.start(...)
│       ├── mobiledispatch/          # sem mudança de contrato Kotlin; RPCs ganham a guarda
│       ├── appointmenttracking/     # conclusão dispara liberação definitiva do pagamento
│       └── profile/                 # novo card "Formas de pagamento" abrindo paymentmethods
└── res/                             # textos e ícones novos (Pix, cartão, formas de pagamento)

supabase/
├── migrations/
│   └── <timestamp>_mercadopago_payments.sql   # tabelas, RLS, guarda nos RPCs de despacho
└── functions/
    ├── mp-create-payment/index.ts
    ├── mp-save-card/index.ts
    ├── mp-remove-card/index.ts
    ├── mp-webhook/index.ts
    └── mp-refund-payment/index.ts
```

**Structure Decision**: mantém o módulo Android único e a organização feature-first já adotada.
`features/payment` concentra o checkout da solicitação; `features/paymentmethods` concentra o
cadastro opcional de cartão como área independente (decisão do usuário). Toda mudança de banco é
uma nova migração forward-only; migrações aplicadas não são editadas.

## Delivery Phases

### Phase 0 — Research

- Confirmar SDK Android do Mercado Pago recomendado atualmente para tokenização e sua integração
  com Customers/Customer Cards.
- Confirmar formato de geração/validade do código Pix via API de Pagamentos do Mercado Pago.
- Definir validação de assinatura do webhook do Mercado Pago.
- Definir política de retry do estorno automático (backoff, limite de tentativas).
- Registrar decisões, alternativas e riscos em `research.md`.

### Phase 1 — Data and contracts

- Especificar `payments` e `saved_cards` (campos, estados, RLS, índices) em `data-model.md`.
- Especificar as 5 Edge Functions (entrada/saída/erros) em
  `contracts/mercadopago-edge-functions.md`.
- Especificar os RPCs Postgres novos e a guarda adicionada aos RPCs de despacho existentes em
  `contracts/payment-rpcs.md`.
- Especificar canal Realtime de status de pagamento e estados de UI do checkout/cadastro de cartão
  em `contracts/payment-realtime-ui.md`.
- Documentar preparação de sandbox e validação ponta a ponta em `quickstart.md`.

### Phase 2 — Execution preparation

- Executar `$speckit-tasks` para transformar este desenho em tarefas pequenas, ordenadas por
  dependência e pelos cenários da especificação.
- Publicar migração e Edge Functions antes de liberar o APK que passa a exigir pagamento aprovado
  para despachar.

## Implementation Sequence

1. **Credenciais e segredos**: registrar Public Key, Access Token e segredo de verificação do
   webhook como Supabase Secrets; app recebe só a Public Key via `BuildConfig`.
2. **Banco**: migração com `payments`, `saved_cards`, RLS, e a guarda de pagamento aprovado
   adicionada a `start_mobile_dispatch`/`start_pittec_dispatch`, além dos RPCs de leitura/estorno.
3. **Edge Functions**: `mp-create-payment`, `mp-save-card`, `mp-remove-card`, `mp-webhook`,
   `mp-refund-payment`, cada uma validando sessão/assinatura antes de tocar no Access Token.
4. **Android — modelos/repositórios**: enums e contratos serializáveis de `payment` e
   `paymentmethods`, tratamento de erros recuperáveis (offline, timeout, sessão expirada).
5. **Android — cadastro de cartão**: tela em `features/paymentmethods`, acessível pelo novo card em
   Profile; tokenização via SDK, listagem e remoção.
6. **Android — checkout**: tela em `features/payment` com Pix (QR/copia-e-cola) e cartões salvos;
   observa status via Realtime; só then chama `MobileDispatchRepository.start(...)` /
   `PitTecRepository.start(...)`.
7. **Integração**: liga `RequestService.onSubmit` e `PitTecViewModel.requestPitTec()` à nova etapa
   de pagamento; ajusta cancelamento para refletir estorno em andamento.

## Rollout and rollback

- Registrar os segredos e publicar as Edge Functions e a migração antes de liberar o APK que exige
  pagamento aprovado para despachar — caso contrário, nenhuma solicitação despacharia.
- A migração é forward-only e falha cedo se encontrar dados incompatíveis; nenhuma migração
  aplicada é reescrita.
- Em falha do Mercado Pago (Pix ou cartão), o cliente vê o erro e pode tentar novamente ou trocar de
  forma de pagamento; nenhum prestador é acionado enquanto isso.
- Em falha do webhook (indisponibilidade momentânea), o app pode consultar o status atual via RPC de
  leitura como fallback ao Realtime, sem travar o cliente numa tela de espera indefinida.
- Rollback do APK não remove dados; qualquer correção de banco é uma nova migração.

## Complexity Tracking

Nenhuma violação: as Edge Functions são necessárias para proteger o Access Token e os dados de
cartão; as duas features novas seguem os limites de camada já impostos pela constituição, e o
número de Edge Functions reflete as operações mínimas exigidas pela spec (cobrar, salvar cartão,
remover cartão, receber notificação, estornar) — nenhuma foi criada por especulação.
