# Implementation Plan: Chamada e acompanhamento do PitTec

**Branch**: `020-pittec-call-tracking` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/020-pittec-call-tracking/spec.md`

## Summary

Evoluir o despacho PitTec existente para que uma única solicitação permaneça em busca até um
prestador aceitar, sem terminar por ausência momentânea de candidatos. O backend passa a controlar
explicitamente as etapas `AWAITING_CLIENT_CONFIRMATION` e `AWAITING_PAYMENT`: congela e mostra ao
PitTec o preço autoritativo antes do aceite, apresenta o mesmo preço ao cliente por dois minutos,
registra sua decisão de forma idempotente e só promove o atendimento ao estado `ACCEPTED` quando o
pagamento correspondente for aprovado. Preservar `ACCEPTED` como o marco que já autoriza a
infraestrutura de localização reduz a mudança e impede o compartilhamento exato antes do pagamento.

No Android, substituir o fluxo intermediário de candidato introduzido pela feature `019` por estados
imutáveis de busca, confirmação de valor e espera de pagamento; mostrar o preço real nos dois perfis;
restaurar cada etapa após reconexão; e reutilizar a tela compartilhada de acompanhamento e o serviço
de localização em primeiro plano já existentes. O mapa enquadra inicialmente o PitTec e o local fixo
do atendimento, move o marcador e recalcula a distância a cada localização válida, respeita a
interação manual e oferece uma ação explícita de reenquadramento. A Edge Function de pagamento passa
a resolver preço, cliente, atendimento e versão da proposta no servidor, sem confiar no valor enviado
pelo aplicativo.

## Technical Context

**Language/Version**: Kotlin 2.2.10 e Java 11 no Android; SQL/PLpgSQL nas migrations do Supabase;
TypeScript executado no Deno para Edge Functions.

**Primary Dependencies**: Android SDK (compileSdk 37, targetSdk 36, minSdk 24), Jetpack Compose BOM
2026.06.01, Material 3, Lifecycle/ViewModel 2.10.0, Navigation Compose 2.9.8, Coroutines, Supabase
Kotlin 3.2.6 (Auth, Postgrest, Realtime e Functions), Ktor 3.3.1, MapLibre 13.5.0, DataStore 1.2.1 e
Mercado Pago SDK BOM 1.0.0. Todas já existem; nenhuma dependência nova.

**Storage**: PostgreSQL/Supabase com PostGIS e pg_cron. Reutiliza `service_searches`,
`private.dispatch_candidates`, `appointments`, `payments`, `client_current_locations` e
`provider_current_locations`; uma migration forward-only acrescenta estados, metadados de
confirmação/proposta e vínculos de integridade do pagamento, sem criar uma nova entidade principal.

**Testing**: Nenhum teste automatizado novo, conforme a constituição e a ausência de solicitação
explícita. Verificação por `:app:assembleDebug`, `:app:lintDebug`, lint/reset somente no Supabase
local e roteiro manual concorrente e ponta a ponta em [quickstart.md](./quickstart.md).

**Target Platform**: Aplicativo Android nativo e backend hospedado no Supabase, com Edge Functions
do Mercado Pago.

**Project Type**: Aplicativo mobile único com backend compartilhado e integrações server-side.

**Performance Goals**: Uma localização válida do PitTec deve aparecer para o outro participante em
até 10 segundos em pelo menos 95% das condições normais; o serviço Android já coleta a cada 10
segundos, o Realtime continua como caminho principal e o polling de 5 segundos como recuperação.
Busca sem candidato é reagendada no servidor sem loop apertado; confirmação expira em 120 segundos
usando o relógio do banco.

**Constraints**: Uma chamada ativa por cliente e um aceite vencedor; local do atendimento imutável;
posição exata e deslocamento somente após pagamento aprovado; somente o PitTec envia localização em
segundo plano com notificação persistente; preservar seleção, prioridade, raios, meios e regras de
pagamento existentes; sem navegação curva a curva, ETA ou rastreamento de cliente em segundo plano;
sem secrets no APK e sem dependências novas.

**Scale/Scope**: Um fluxo PitTec por cliente, uma oferta ativa por solicitação e uma reserva ativa por
prestador. Reavaliação server-side em lotes pequenos com `FOR UPDATE SKIP LOCKED`; atualizações
Realtime são invalidações ordenadas por revisão e o snapshot do servidor permanece a fonte de
verdade. Mudanças limitadas à feature PitTec, ao acompanhamento compartilhado necessário, à migration
aditiva e às Edge Functions de pagamento já existentes.

## Constitution Check

*GATE: verificado antes da pesquisa e novamente após o design da Phase 1.*

- **I. Simplicidade/KISS/YAGNI — PASS**: reutiliza despacho, tabelas, Realtime, tela de tracking,
  foreground service e checkout atuais. Os novos estados e campos existem porque confirmação,
  timeout, restauração e autorização server-side não podem ser representados com segurança por um
  Boolean local. Nenhuma regra futura, rota ou ETA é adicionada.
- **II. Android nativo moderno com MVVM — PASS**: UI continua em Compose e apenas renderiza/emitirá
  eventos; ViewModels expõem `StateFlow` e chamam Repositories; Repositories concentram RPC,
  Realtime, pagamento e localização. Não há nova camada, framework ou dependência.
- **III. Estado previsível — PASS**: busca, confirmação do cliente, pagamento e deslocamento são
  fases mutuamente exclusivas no banco e em sealed types Kotlin. Revisões, prazo do servidor e ações
  permitidas tornam restauração e concorrência explícitas; nenhum conjunto de Booleans locais decide
  se o rastreamento pode começar.
- **IV. Dados, segurança e falhas explícitas — PASS**: preço e versão são resolvidos no servidor;
  RPCs derivam o ator de `auth.uid()`, usam locks/updates condicionais e rejeitam transições tardias;
  tópicos privados mantêm autorização; coordenadas exatas só existem no contexto compartilhado após
  o pagamento. Falhas de permissão, GPS, rede, posição desatualizada e pagamento têm estados
  acionáveis.
- **V. Qualidade proporcional — PASS**: validação combina build/lint e cenários manuais de alto risco,
  sem gerar testes não solicitados. A implementação remove apenas o estágio de candidato e os valores
  fictícios diretamente substituídos pelo fluxo real, preservando mobile dispatch, pós-chegada e
  comportamentos fora do escopo.
- **Restrições técnicas e produto — PASS**: Navigation Compose, Material, estrutura por feature e
  Gradle Kotlin DSL são preservados; não há armazenamento ou permissão de localização adicional.
- **Rechecagem pós-design — PASS**: `research.md`, `data-model.md` e os contratos mantêm uma migration
  forward-only, uma máquina de estados autoritativa e componentes existentes. Os vínculos adicionais
  de pagamento e o worker agendado são necessários para integridade e continuidade mesmo com o app
  fechado, não complexidade especulativa.

Nenhuma violação constitucional identificada.

## Project Structure

### Documentation (this feature)

```text
specs/020-pittec-call-tracking/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── pittec-call-rpcs.md
│   ├── pittec-call-realtime.md
│   └── pittec-call-ui-navigation.md
└── tasks.md                         # Phase 2 ($speckit-tasks, ainda não criado)
```

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/
├── core/
│   ├── map/
│   │   ├── MapLibreMapHost.kt
│   │   └── ParticipantMapStyleController.kt
│   └── navigation/
│       ├── PitGoRoutes.kt
│       └── PitGoNavHost.kt
└── features/
    ├── pittec/
    │   ├── data/PitTecDtos.kt
    │   ├── model/PitTecModels.kt
    │   ├── repository/PitTecRepository.kt
    │   ├── ui/
    │   │   ├── PitTecDispatchComponents.kt
    │   │   ├── PitTecProviderDashboardScreen.kt
    │   │   └── PitTecScreen.kt
    │   └── viewmodel/
    │       ├── PitTecProviderViewModel.kt
    │       └── PitTecViewModel.kt
    ├── mobiledispatch/
    │   ├── data/MobileDispatchDtos.kt
    │   ├── model/MobileDispatchModels.kt
    │   ├── repository/MobileDispatchRepository.kt
    │   └── viewmodel/ProviderIncomingRequestViewModel.kt
    ├── payment/
    │   ├── repository/PaymentRepository.kt
    │   └── viewmodel/PaymentCheckoutViewModel.kt
    ├── appointmenttracking/
    │   ├── model/AppointmentTrackingModels.kt
    │   ├── repository/AppointmentTrackingRepository.kt
    │   ├── ui/AppointmentTrackingScreen.kt
    │   └── viewmodel/AppointmentTrackingViewModel.kt
    └── providertracking/
        ├── repository/ProviderLocationRepository.kt
        └── service/ProviderLocationTrackingService.kt

supabase/
├── migrations/
│   └── <timestamp>_pittec_call_tracking.sql
└── functions/
    ├── mp-create-payment/index.ts
    └── mp-webhook/index.ts
```

**Structure Decision**: Manter a organização atual por feature. O fluxo de chamada pertence a
`features/pittec`; a oferta do prestador continua reutilizando `mobiledispatch`; pagamento e
acompanhamento são alterados apenas nos pontos compartilhados indispensáveis. Toda evolução de banco,
políticas, worker e funções RPC entra em uma única migration forward-only, enquanto as duas Edge
Functions existentes passam a usar a proposta persistida como autoridade. Não serão criados novos
pacotes genéricos, serviços Android ou tabelas de domínio.
