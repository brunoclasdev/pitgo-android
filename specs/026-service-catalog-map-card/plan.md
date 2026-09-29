# Implementation Plan: Catálogo de serviços por categoria e mapa PitTec

**Branch**: `026-service-catalog-map-card` | **Date**: 2026-09-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/026-service-catalog-map-card/spec.md`

## Summary

O cadastro de prestador fixo passará a trabalhar com uma categoria principal e vários serviços
específicos dessa categoria. O catálogo inicial será carregado por migration com as sete categorias
e listas definidas na spec; serviços posteriores serão gerenciados por RPCs protegidas para equipe
autorizada. No acompanhamento PitTec, o mapa continuará ocupando a área principal e o painel de
status será compacto por padrão, expansível para exibir cancelamento e detalhes.

## Technical Context

**Language/Version**: Kotlin 2.2.10, SQL/PostgreSQL para Supabase

**Primary Dependencies**: Jetpack Compose/Material 3, ViewModel, StateFlow, Coroutines, Supabase Kotlin 3.2.6 e MapLibre Android já adotados pelo projeto

**Storage**: PostgreSQL/Supabase; tabelas existentes `service_categories`, `services`, `provider_categories`, `provider_services` e estados existentes de appointments

**Testing**: verificação SQL/migration, `./gradlew assembleDebug`, inspeção funcional em dispositivo Android; testes automatizados somente se necessários pela regra vigente do projeto

**Target Platform**: Android API 24+, target SDK 36; backend Supabase/PostgreSQL

**Project Type**: aplicativo móvel Android nativo com backend Supabase e migrations versionadas

**Performance Goals**: evitar consultas redundantes ao selecionar categoria; verificar no dispositivo de validação que atualizações de localização não recriam o mapa nem interrompem pan/zoom. Registrar dispositivo, cenário e evidência observada.

**Constraints**: preservar as transições PitTec e o rastreamento existentes; não expor edição de catálogo a prestadores/clientes; não cobrir marcadores ou rota com o painel compacto; respeitar offline, timeout, sessão expirada e catálogo alterado

**Scale/Scope**: sete categorias iniciais, até 15 serviços iniciais por categoria, expansão posterior autorizada; uma categoria principal e várias ofertas por prestador fixo; um painel por acompanhamento PitTec

O limite inicial conta especialidades selecionáveis por prestadores fixos e exclui os serviços
implícitos Guincho/PitTec. Especialidades dessas categorias usam `BROWSE` e o fluxo de serviços
fixos para clientes; apenas os dois serviços implícitos mantêm despacho automático.

## Constitution Check

*GATE: Must pass before Phase 0 research and after Phase 1 design.*

- **I. Simplicidade**: PASS. Reutiliza o catálogo, cadastro, RPC de registro e componentes de mapa existentes; adiciona apenas seed, validação, RPCs de gestão e estado de expansão necessários.
- **II. Android/MVVM**: PASS. Mudanças de UI ficam em `providerregistration` e `appointmenttracking`/`pittec`; StateFlow/ViewModel continuam controlando estado e Repository concentra dados.
- **III. Estado previsível e UX**: PASS. Categoria selecionada, serviços carregados e painel expandido serão estados explícitos; o mapa mantém viewport manual e o painel possui ação acessível.
- **IV. Dados e segurança**: PASS. RLS e RPCs com `private.dashboard_access_at_least('MANAGER')` protegem gestão; o cadastro revalida serviços no servidor; dados de localização seguem contratos existentes.
- **V. Qualidade proporcional**: PASS. Não adiciona dependências nem refatora o fluxo inteiro; valida build, migration, cadastro, estados PitTec e acessibilidade manualmente.

## Project Structure

```text
specs/026-service-catalog-map-card/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    ├── catalog-management.md
    ├── provider-service-selection.md
    └── pittec-map-summary.md

app/src/main/java/com/trustarx/pitgo/
├── core/catalog/{model,repository}/
├── features/providerregistration/{model,repository,ui,viewmodel}/
├── features/appointmenttracking/{model,ui,viewmodel}/
└── features/pittec/{model,ui,viewmodel}/

supabase/migrations/
└── 20260930000000_service_catalog_and_pittec_map_card.sql
```

**Structure Decision**: manter a organização por feature já existente. O catálogo compartilhado
continua em `core/catalog`; a seleção pertence ao cadastro de prestador; o painel pertence ao fluxo
de acompanhamento do PitTec. O backend recebe uma migration forward-only para seed e contratos de
gestão, sem criar uma nova camada ou módulo.

## Phase 0: Research

As decisões técnicas e de produto estavam suficientemente definidas pela spec e pela inspeção do
código existente. Os pontos de integração, falhas e alternativas estão registrados em
[research.md](research.md); não restaram `NEEDS CLARIFICATION`.

## Phase 1: Design

- [data-model.md](data-model.md) define categoria, serviço, oferta, permissões e estado de painel.
- [contracts/catalog-management.md](contracts/catalog-management.md) define as operações protegidas de catálogo.
- [contracts/provider-service-selection.md](contracts/provider-service-selection.md) define o contrato de cadastro e validação.
- [contracts/pittec-map-summary.md](contracts/pittec-map-summary.md) define o comportamento do mapa e do painel compacto/expandido.
- [quickstart.md](quickstart.md) define a validação end-to-end e os checks de build/migration.

## Constitution Check — Post-Design

- **Simplicidade**: PASS — nenhuma dependência ou arquitetura nova.
- **MVVM e estado**: PASS — o painel expandido, carregamento de catálogo e falha de catálogo são estados de tela; acesso a dados permanece em Repository/RPC.
- **Segurança**: PASS — somente staff autorizado altera catálogo; auditoria usa o mecanismo existente; o app recebe apenas leitura pública autenticada.
- **Estados e localização**: PASS — `ACCEPTED`, `IN_PROGRESS` e estados terminais reutilizam o modelo existente; atualização do mapa não sobrescreve viewport manual.
- **Validação**: PASS — migration, build e cenários do quickstart cobrem o risco principal.

## Complexity Tracking

Nenhuma violação da constituição exige justificativa.
