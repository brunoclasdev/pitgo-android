# Implementation Plan: PitTec sob demanda

**Branch**: `015-pittec-on-demand` | **Date**: 2026-09-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/015-pittec-on-demand/spec.md`

## Summary

Adicionar o PitTec como terceira modalidade de prestador e como serviço sob
demanda. O cliente verá PitTecs próximos em posições aproximadas, iniciará uma
busca com um toque e o backend oferecerá o chamado, por proximidade, a um
prestador por vez durante 60 segundos, sem repetir recusas. Após o aceite, os
dois participantes verão posições exatas, distância e percurso; o prestador
confirmará manualmente a chegada e a conclusão.

A implementação reutiliza o modelo transacional, os canais privados e o
serviço de localização do despacho móvel existente, mas introduz um tipo de
despacho próprio para não alterar o fluxo manual do Guincho. Uma migração
Supabase adicionará a modalidade, os RPCs atômicos, a descoberta anonimizada e
o snapshot corrente de rota. O app Android ganhará um fluxo Compose em
`features/pittec`, extensões nos fluxos compartilhados do prestador e um proxy
Edge Function para um provedor OSRM compatível.

## Technical Context

**Language/Version**: Kotlin 2.2.10, Java 11 bytecode; TypeScript/Deno para a Edge Function; PostgreSQL/PLpgSQL nas migrações Supabase

**Primary Dependencies**: Jetpack Compose BOM 2026.06.01, Navigation Compose 2.9.8, Lifecycle 2.10.0, Kotlin Coroutines/StateFlow, Supabase Kotlin 3.2.6, Ktor 3.3.1 e MapLibre Native Android 13.5.0

**Storage**: PostgreSQL gerenciado pelo Supabase com PostGIS, RLS e Realtime; DataStore apenas para preferências locais já existentes

**Testing**: validação manual orientada por cenários, `supabase db lint`, build Android debug/release e `lintDebug`; a constituição não exige testes automatizados e o projeto não possui infraestrutura de testes configurada

**Target Platform**: Android API 24+, compile SDK 37, target SDK 36; Supabase PostgreSQL e Edge Functions Deno

**Project Type**: aplicativo Android de módulo único com backend Supabase versionado no mesmo repositório

**Performance Goals**: atualizar o estado de despacho em até 5 s quando Realtime falhar; selecionar a próxima oferta em uma transação; exibir a atualização de percurso até 15 s após movimento relevante; manter interação do mapa a 60 fps em aparelhos suportados

**Constraints**: timeout de 60 s por prestador; raios progressivos de 10/25/50 km; no máximo 200 marcadores aproximados; uma busca PitTec ativa por cliente; posições exatas acessíveis somente aos participantes após aceite; sem chave do provedor de rotas no APK; sem permissão `ACCESS_BACKGROUND_LOCATION`

**Scale/Scope**: um novo fluxo cliente, extensões em cadastro/painel/atendimento do prestador, uma migração forward-only, uma Edge Function e três contratos técnicos; preparação para múltiplos clientes e prestadores concorrentes por região

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Verificação pré-pesquisa | Verificação pós-design |
|---|---|---|
| Kotlin e Android nativo | PASS — UI e integração permanecem em Kotlin/Compose no módulo `:app`. | PASS — somente a função server-side usa TypeScript, conforme a plataforma Supabase. |
| Arquitetura feature-first e MVVM | PASS — novo fluxo isolado em `features/pittec` e dependências compartilhadas em `core`. | PASS — Composables recebem estado/eventos; ViewModels expõem `StateFlow`; repositórios isolam Supabase e rota. |
| Coroutines, StateFlow e Navigation Compose | PASS — são os mecanismos já adotados no projeto. | PASS — nenhuma pilha paralela de estado ou navegação foi introduzida. |
| Segurança, RLS e privacidade | PASS — seleção e transições críticas serão RPCs atômicos autenticados. | PASS — descoberta é anonimizada; coordenadas exatas e rotas ficam limitadas aos participantes; segredos ficam na Edge Function. |
| Estados explícitos e recuperação | PASS — estados existentes serão reutilizados com semântica documentada. | PASS — busca, oferta, aceite, chegada, conclusão, cancelamento, timeout e retomada possuem transições contratuais. |
| KISS/YAGNI | PASS — infraestrutura móvel, Realtime e FGS existentes serão estendidos. | PASS — FCM, histórico de rotas, chat, pagamento e novo estado persistido `ARRIVED` ficam fora do escopo. |
| Qualidade e verificação | PASS — o plano inclui lint, builds e cenários ponta a ponta. | PASS — nenhuma suíte de testes foi criada sem solicitação; o quickstart define a matriz manual necessária. |
| Configuração e segredos | PASS — nenhum segredo será versionado. | PASS — estilo de mapa permanece em propriedades locais e credenciais de rota em Supabase Secrets. |

Não há violação constitucional a justificar.

## Design Decisions

1. `PITTEC_AUTO_SEQUENTIAL` será um novo `dispatch_kind`; o despacho manual
   `MOBILE_SEQUENTIAL` continua inalterado para não mudar a experiência do
   Guincho.
2. `PITTEC` é modalidade de prestador, não um novo papel de conta. Mudanças de
   modalidade durante atendimento ficam pendentes até seu encerramento.
3. O backend faz descoberta, ordenação, exclusão de recusados e criação da
   consulta/oferta em uma única transação, com bloqueios, revisão e
   idempotência.
4. `ACCEPTED` significa “a caminho” e `IN_PROGRESS` significa “chegou” para
   PitTec. A ação “Cheguei” efetua essa transição; concluir só é permitido
   depois dela.
5. Antes do aceite, o mapa recebe pontos estáveis aproximados em grade de cerca
   de 500 m e identificadores opacos. Após o aceite, somente os participantes
   acessam as tabelas exatas do atendimento.
6. Enquanto disponível como PitTec, o FGS existente observa o canal privado de
   ofertas e publica alerta local de alta prioridade, com som e deep link. A
   disponibilidade exige permissão e canal habilitados. FCM não faz parte desta
   entrega; portanto, reinício do aparelho e force-stop não têm garantia.
7. Uma Edge Function autenticada obtém as coordenadas privadas, consulta um
   serviço HTTPS OSRM compatível e grava apenas o snapshot de rota mais recente.
   Em indisponibilidade, a UI mostra distância em linha reta e percurso tracejado.

Detalhes e alternativas estão registrados em [research.md](research.md).

## Project Structure

### Documentation (this feature)

```text
specs/015-pittec-on-demand/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── pittec-rpcs.md
│   ├── pittec-realtime-notifications.md
│   └── pittec-routing-ui.md
└── tasks.md                         # criado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/
├── AndroidManifest.xml
├── java/com/trustarx/pitgo/
│   ├── MainActivity.kt
│   ├── core/
│   │   ├── account/model/AccountContext.kt
│   │   ├── app/PitGoAppContainer.kt
│   │   ├── catalog/
│   │   ├── location/
│   │   ├── map/model/MapSurfaceModels.kt
│   │   ├── navigation/
│   │   ├── network/
│   │   └── routing/                 # cliente/modelo/repositório de rota
│   └── features/
│       ├── pittec/                  # descoberta, solicitação, mapa e ViewModel cliente
│       ├── mobiledispatch/          # transporte/estados compartilhados e oferta prestador
│       ├── appointmenttracking/     # participantes, percurso e ações do atendimento
│       ├── providertracking/        # FGS, localização e alerta de oferta
│       ├── providerregistration/    # opção PITTEC
│       ├── providerdashboard/       # disponibilidade e modalidade pendente
│       └── home/                    # card e navegação PitTec
└── res/                             # textos, ícones e configuração do canal

supabase/
├── migrations/
│   └── 20260902000000_pittec_on_demand.sql
└── functions/
    └── pittec-route/index.ts
```

**Structure Decision**: manter o módulo Android único e a organização
feature-first já adotada. `features/pittec` concentra a experiência específica;
componentes realmente transversais ficam em `core`, enquanto despacho,
localização e atendimento existentes recebem extensões pequenas. Toda alteração
de banco é feita por nova migração forward-only, sem editar migrações aplicadas.

## Delivery Phases

### Phase 0 — Research

- Confirmar a fronteira entre o despacho PitTec automático e o Guincho manual.
- Definir privacidade da localização antes/depois do aceite.
- Escolher mecanismo de alerta compatível com a disponibilidade em background.
- Definir provedor/contrato de rotas e política de atualização/fallback.
- Registrar decisões, alternativas e riscos em `research.md`.

### Phase 1 — Data and contracts

- Especificar entidades, campos, estados, índices, políticas e invariantes em
  `data-model.md`.
- Especificar RPCs transacionais e respostas de erro em
  `contracts/pittec-rpcs.md`.
- Especificar eventos privados, deduplicação e notificação local em
  `contracts/pittec-realtime-notifications.md`.
- Especificar a Edge Function, snapshot de rota e estados da UI em
  `contracts/pittec-routing-ui.md`.
- Documentar preparação e validação ponta a ponta em `quickstart.md`.

### Phase 2 — Execution preparation

- Executar `$speckit-tasks` para transformar este desenho em tarefas pequenas,
  ordenadas por dependência e pelos cenários da especificação.
- Implantar backend/migração e função antes de publicar o APK que reconhece o
  novo `dispatch_kind`.

## Implementation Sequence

1. Banco: constraints aditivas, seed PitTec, modalidade pendente, privacidade,
   RPCs de descoberta/despacho/transição e snapshot de rota.
2. Edge Function: autorização por atendimento, chamada ao provedor de rotas,
   validação de geometria e persistência do único snapshot corrente.
3. Modelos/repositórios Android: enums, contratos serializáveis, tratamento de
   versões antigas e erros recuperáveis.
4. Prestador: cadastro PitTec, regra de disponibilidade, FGS, oferta sonora,
   aceite/recusa, “Cheguei”, concluir e cancelar.
5. Cliente: card, mapa aproximado, iniciar/cancelar busca, retomada, mapa aceito,
   rota/distância e estados finais.
6. Integração: navegação/deep link, telemetria sem coordenadas e verificação de
   concorrência, segurança, acessibilidade e APIs 24/36.

## Rollout and rollback

- Aplicar primeiro a migração e publicar `pittec-route`; somente depois liberar
  o app que envia/consome `PITTEC_AUTO_SEQUENTIAL`.
- O seed é idempotente e a migração deve falhar cedo se encontrar valores ou
  estados incompatíveis, sem reescrever histórico.
- Em falha do provedor de rotas, manter o atendimento funcional usando o
  fallback direto. Em falha do novo despacho, desativar o serviço PitTec no
  catálogo sem afetar `MOBILE_SEQUENTIAL`.
- Rollback do APK não remove dados. Alterações de banco são corrigidas por nova
  migração; migrações aplicadas nunca são revertidas/editadas em produção.

## Complexity Tracking

Nenhuma violação: a Edge Function é necessária para proteger credenciais e
coordenadas; a nova feature e o repositório de rota seguem os limites de camada
já impostos pela constituição.
