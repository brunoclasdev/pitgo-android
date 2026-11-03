# Implementation Plan: Guincho por porte e retomada da busca

**Branch**: `feature/ajuste-cadastro-layout` | **Date**: 2026-11-03 | **Spec**: [spec.md](spec.md)

**Input**: `specs/036-guincho-porte-busca/spec.md` (inclui duas decisões de esclarecimento).

## Summary

Estender o cadastro de veículos e guinchos com classificação administrada no dashboard, aplicar compatibilidade e preço por porte no servidor e retomar a busca após recusa sem perder o trajeto. Persistir a busca e os guincheiros excluídos no Supabase, com recuperação após encerramento do aplicativo. Posicionar destino e status no topo e aplicar bordas roxas nas duas telas abrangidas.

Preservar o fluxo atual de aceite antes do pagamento, inclusive o endpoint `create_guincho_request_before_payment`; manter compatibilidade com pedidos pagos legados e seus reembolsos. Não mudar a ordem de pagamento por causa dos cenários de recusa paga da especificação.

O repositório contém Android e Supabase, sem frontend web do dashboard. Aqui serão implementados catálogo, permissões, auditoria e RPCs administrativos. A integração visual no dashboard externo é parte necessária da entrega do produto e depende de seu repositório; não considerar o requisito administrativo entregue apenas com RPCs.

## Technical Context

**Language/Version**: Kotlin/Compose compiler e serialization 2.2.10 conforme catálogo local; JVM 11; PostgreSQL/PLpgSQL; TypeScript no Edge Function existente.

**Primary Dependencies**: Compose BOM 2026.06.01, Material3, Navigation Compose 2.9.8, ViewModel/StateFlow/Coroutines, Supabase Kotlin 3.2.6, Ktor 3.3.1, MapLibre 13.5.0. Versões observadas em `gradle/libs.versions.toml`, sem atualização ou nova biblioteca proposta; não representam verificação de versões mais recentes.

**Storage**: Supabase PostgreSQL com RLS, RPCs transacionais e snapshots imutáveis. Reutilizar `vehicles`, `provider_vehicles`, `guincho_trip_drafts`, `guincho_quotes`, `guincho_pricing_rules`, `appointments` e o fluxo financeiro existente. Adicionar apenas catálogo de portes, compatibilidade, preço versionado por porte e estado persistente da busca/exclusões.

**Testing**: Constituição proíbe gerar testes sem solicitação. Compilação `./gradlew :app:assembleHomolDebug`, revisão de migrações e permissões, execução manual dos contratos em ambiente isolado e walkthrough em [quickstart.md](quickstart.md). Não criar suítes, fixtures nem utilitários de teste.

**Target Platform**: Android API 24+, compile SDK 37 e target SDK 36 conforme configuração atual; Supabase backend; dashboard administrativo externo.

**Project Type**: Aplicativo Android nativo com backend Supabase e integração administrativa externa.

**Performance Goals**: Retorno à busca em até 3 segundos após recebida a confirmação da recusa, com conexão ativa; descoberta continua limitada a 200 resultados conforme contrato atual. Resolver compatibilidade/preço em consultas agrupadas, sem chamada por marcador. Não aumentar polling para cumprir a meta: reagir ao evento recebido e reconciliar no retorno à atividade.

**Constraints**: PostgreSQL é autoritativo; dono do veículo e do rascunho verificado; preços e capacidades administrados por MANAGER; sem chaves privilegiadas no Android; regras monetárias e snapshots preservados; exclusões sobrevivem a atualização e reabertura; nenhuma inferência de capacidade por nome/ordenação; migrações aditivas sem atribuir porte a registros antigos por suposição.

**Scale/Scope**: Cadastro/edição de veículo do cliente, cadastro/edição de guincho, busca/cotação/reserva, tracking/restauração de navegação e home do guincheiro, mais catálogo administrativo. Não amplia despacho, pagamentos, infraestrutura ou outras modalidades.

## Constitution Check

*GATE pré-pesquisa e reavaliação pós-design.*

| Princípio | Pré | Pós | Evidência |
|---|---|---|---|
| I. Simplicidade e requisito atual | PASS | PASS | Estender features e funções existentes; sem novo backend, motor genérico de regras ou aplicativo administrativo. |
| II. Android nativo e MVVM | PASS | PASS | UI emite eventos, ViewModel expõe estado imutável, Repository faz I/O; sem Use Case de simples repasse. |
| III. Estado e acessibilidade | PASS | PASS | Estados explícitos de recuperação, vazio e classificação pendente; borda roxa não substitui rótulos; fonte/teclado previstos. |
| IV. Dados e segurança | PASS | PASS | RLS de proprietário, RPCs administrativos auditados, capacidade validada em descoberta/cotação/reserva, recusa idempotente e snapshots financeiros. |
| V. Qualidade proporcional | PASS | PASS | Sem novas dependências ou testes gerados; compilação e validação manual dirigidas ao risco. |

Nenhuma exceção constitucional necessária. A ausência do frontend do dashboard é dependência de integração, não justificativa para omitir a administração do produto.

## Project Structure

### Documentation (this feature)

```text
specs/036-guincho-porte-busca/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── guincho-service.md
│   └── dashboard-catalog.md
└── checklists/requirements.md
```

`tasks.md` será produzido somente por `$speckit-tasks`.

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/
├── core/vehicle/{model,data,repository}/
├── core/navigation/                         # PitGoNavHost, PitGoRoutes
├── core/ui/AddressEntryMapCard.kt
├── core/design/theme/
├── features/garage/{ui,viewmodel}/
├── features/onboarding/{ui,viewmodel}/
├── features/providerregistration/{model,data,validation,repository,viewmodel,ui}/
├── features/providerdashboard/{repository,viewmodel,ui}/
├── features/guincho/{model,data,repository,viewmodel,ui}/
├── features/mobiledispatch/{model,data,repository,viewmodel}/
└── features/appointmenttracking/{model,repository,viewmodel,ui}/
app/src/main/res/values/strings.xml
supabase/migrations/
supabase/functions/guincho-quote/index.ts
```

**Structure Decision**: Reutilizar organização por feature. Um repositório de catálogo compartilhado em `core/vehicle` só deve surgir para o uso real comum por cadastro de cliente e guincho; DTOs e regras de transporte continuam em guincho. Não inserir consultas em Composables. Não criar frontend de dashboard dentro do aplicativo.

## Phase 0 — Research

Decisões e evidências em [research.md](research.md): classificação explícita, veículo de atendimento selecionado, preço por porte associado à regra global, busca persistente e restauração orientada à causa da recusa. Todos os pontos técnicos necessários ao design foram resolvidos por inspeção do código.

## Phase 1 — Design & Contracts

1. Migrações de catálogo/compatibilidade e preço por porte, com RLS, RPCs MANAGER e auditoria. FKs inicialmente opcionais para legados; leitura e gravação compatíveis para demais modalidades.
2. Campos de categoria em veículo de cliente e porte em veículo de guincho; edição acessível aos cadastros antigos. Selecionar o guincho de atendimento da frota explicitamente no perfil/home; não escolher o maior automaticamente.
3. Aplicar o mesmo predicado autoritativo de elegibilidade em descoberta, cotação e ambos os caminhos de reserva. Copiar veículo/porte, categoria e versões de preço para snapshots. Revisar também `reserve_order_payment` e a simulação homol para que validem as referências novas sem depender de base_amount global. Atualização de tarifas não invalida uma cotação vigente; mudança de capacidade incompatível impede nova reserva.
4. Persistir contexto da busca e recusas acumuladas atomicamente com a recusa. Para pedido anterior à implantação sem contexto, criá-lo idempotentemente a partir de seu rascunho/cotação com propriedade validada, sem substituir viagem mais recente. Cancelamento voluntário, expiração e encerramento definitivo diferente de recusa fecham apenas o contexto do próprio pedido, preservando regras existentes, sem exclusão nem retorno por recusa. Expor recuperação autenticada; navegação retorna à descoberta apenas para recusa do pedido corrente e fora do histórico. Atendimento operacional novo prevalece sobre evento antigo.
5. Android consome novos contratos com erros explícitos, reconcilia no resume/reconexão e limpa seleção/cotação/checkout antigos ao retomar. Não depende de `SavedStateHandle` para sobreviver a encerramento do processo.
6. Reposicionar `AddressEntryMapCard` no topo de `GuinchoMapScreen`; mover status/disponibilidade para o primeiro card em `GuinchoProviderHomeScreen`; aplicar bordas nos cards das duas telas, incluindo solicitações embutidas, sem alterar telas de outras modalidades.
7. Validar conforme [quickstart.md](quickstart.md). Identificar repositório acessível, caminhos reais e responsável antes de executar a integração T031 do dashboard externo; manter T031/T033 pendentes até integração/validação efetivas, permitindo continuidade local independente. Entregar os contratos ao fluxo de integração do dashboard externo; a UI externa deve cadastrar/editar porte, categorias compatíveis e valor, apresentar erros e conflitos.

Modelo: [data-model.md](data-model.md). Contratos: [serviço](contracts/guincho-service.md) e [dashboard](contracts/dashboard-catalog.md).

## Complexity Tracking

Sem violações ou exceções. Persistência adicional é necessária para a exclusão e restauração após reabertura explicitamente solicitadas.
