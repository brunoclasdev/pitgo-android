---
description: "Tarefas ordenadas para catálogo por categoria e resumo PitTec"
---

# Tasks: Catálogo de serviços por categoria e mapa PitTec

**Input**: Design documents from `specs/026-service-catalog-map-card/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ e quickstart.md.

**Tests**: Não gerar novas suítes automatizadas; executar validação funcional, SQL e build conforme a constituição. Não aplicar migrations a produção como parte da validação.

**Organization**: Tarefas por história; [P] indica arquivos independentes, após pré-requisitos. Caminhos relativos à raiz do projeto.

## Phase 1: Setup

Preparar a execução usando os contratos existentes.

- [X] T001 Revisar o RPC vigente de cadastro, políticas de catálogo e rota real de acompanhamento; registrar pontos de integração e preservar as migrations históricas em specs/026-service-catalog-map-card/research.md.

## Phase 2: Foundational

Resolver os detalhes comuns antes das histórias.

- [X] T002 Consolidar em specs/026-service-catalog-map-card/contracts/catalog-management.md as assinaturas, retornos e erros das operações de categoria/serviço, discriminador de entidade na atualização e proteção dos serviços implícitos; usar permissões e auditoria já existentes.
- [X] T003 Registrar em specs/026-service-catalog-map-card/contracts/provider-service-selection.md a troca de categoria durante o rascunho (limpar ofertas incompatíveis após confirmação), compatibilidade de cadastros antigos e distinção entre serviços de catálogo e serviços implícitos únicos de Móvel/PitTec.

## Phase 3: User Story 1 — Seleção de serviços (P1, MVP)

Uma categoria principal com várias ofertas escolhidas. Validação independente: cadastrar Borracharia com dois serviços, rejeitar duplicado e categoria incompatível, confirmar somente duas ofertas persistidas.

T008 inclui adicionar os textos necessários ao cadastro em app/src/main/res/values/strings.xml,
antes de T010. Validar também ofertas FIXED das categorias Guincho/PitTec no fluxo de serviços fixos.

- [X] T004 [US1] Adicionar categoria principal ao rascunho e campo de validação correspondente em app/src/main/java/com/trustarx/pitgo/features/providerregistration/model/ProviderRegistrationModels.kt, preservando modelos de Móvel/PitTec.
- [X] T005 [P] [US1] Filtrar categorias/serviços ativos e elegíveis para FIXED, mantendo resolução implícita separada e erros de rede existentes em app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ProviderRegistrationRepository.kt.
- [X] T006 [US1] Validar categoria única, serviço pertencente à categoria, duplicidade, seleção obrigatória, preço e duração em app/src/main/java/com/trustarx/pitgo/features/providerregistration/validation/ProviderRegistrationValidation.kt.
- [X] T007 [US1] Implementar seleção de categoria principal, carga sem consultas redundantes, descarte de respostas de categoria anterior, adição/remoção de serviços e revalidação antes do envio em app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt.
- [X] T008 [US1] Renderizar categoria única e lista selecionável de serviços com ofertas individuais, confirmação de troca de categoria, loading/erro/vazio e retry em app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt.
- [X] T009 [US1] Acrescentar revalidação transacional de categoria única e serviços ativos ao RPC vigente, preservar registros existentes e serviços implícitos e gravar somente ofertas escolhidas em supabase/migrations/20260930000000_service_catalog_and_pittec_map_card.sql.
- [ ] T010 [US1] Executar os cenários de cadastro, serviço desativado antes do envio, duplicação, troca de categoria e regressão Móvel/PitTec; registrar resultados reais em specs/026-service-catalog-map-card/validation.md.

## Phase 4: User Story 2 — Catálogo inicial e gestão (P1)

Carga inicial e operações autorizadas. Validação independente: conferir listas, adicionar um 16º serviço com staff e negar escrita a cliente/prestador.

T011 e T014 contam até 15 especialidades BROWSE selecionáveis por FIXED em cada categoria,
excluindo os serviços implícitos Guincho/PitTec. Esses serviços implícitos preservam IDs e despacho
automático. Especialidades Guincho/PitTec são ofertas FIXED e usam descoberta/contratação fixa.

- [X] T011 [US2] Inserir as sete categorias e listas da spec com correspondência por nome normalizado, preservando IDs/ofertas existentes e a unicidade dos serviços implícitos em supabase/migrations/20260930000000_service_catalog_and_pittec_map_card.sql; especialidades de Guincho/PitTec não recebem dispatch automático nem são atribuídas automaticamente.
- [X] T012 [US2] Implementar criação, edição, ativação/desativação de categorias e serviços conforme contrato, sem limite permanente de 15, com autorização staff no servidor, grants mínimos e auditoria na mesma transação em supabase/migrations/20260930000000_service_catalog_and_pittec_map_card.sql.
- [X] T013 [P] [US2] Garantir leitura autenticada ordenada, filtro ativo de categoria/serviço e paginação para expansão do catálogo em app/src/main/java/com/trustarx/pitgo/core/catalog/repository/ServiceCatalogRepository.kt, reutilizando leitura existente quando cumprir list_service_catalog.
- [X] T014 [US2] Validar migration em ambiente descartável, contagem da carga inicial, integridade de IDs, expansão além de 15, unicidade implícita, desativação sem exclusão histórica e negação de mutações não autorizadas; registrar evidências em specs/026-service-catalog-map-card/validation.md.

## Phase 5: User Story 3 — Mapa PitTec desobstruído (P1)

Resumo compacto por padrão, cancelamento na expansão. Validação independente: pagamento aprovado, mapa visível, expansão/recolhimento e 20 atualizações sem mover viewport manual.

T017 inclui adicionar textos e descrições de expandir/recolher em app/src/main/res/values/strings.xml
antes de T019. T018 enquadra localizações disponíveis e rota na área livre acima da altura medida
do resumo ao abrir ou recentralizar; pan/zoom manual pode tirar marcadores da tela e deve prevalecer.
T019 registra dispositivo e verifica que as atualizações não recriam o mapa nem interrompem pan/zoom.

- [X] T015 [P] [US3] Adicionar estado summaryExpanded inicial falso por atendimento e eventos de apresentação em app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/model/AppointmentTrackingModels.kt, distinguindo apresentação e autorização de cancelar.
- [X] T016 [US3] Controlar expansão/recolhimento, reset por atendimento/estado terminal e preservar viewport manual durante atualização de localização/rota em app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt; reenquadrar somente na entrada inicial ou por ação explícita.
- [X] T017 [US3] Substituir o painel PitTec que ocupa o mapa por resumo compacto inferior com detalhes expansíveis e cancelar somente na expansão em app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt; preservar ações autorizadas, confirmação de cancelamento e comportamento de Guincho.
- [X] T018 [US3] Ajustar enquadramento e área útil conforme altura medida do resumo, controles e attribution, sem reenquadramentos a cada atualização em app/src/main/java/com/trustarx/pitgo/core/map/ui/MapLibreMapHost.kt; modificar o host compartilhado somente se o contrato atual não comportar o padding necessário.
- [ ] T019 [US3] Validar ACCEPTED após pagamento, chegada, IN_PROGRESS, estados terminais, rota estimada, GPS antigo, offline e preservação do pan/zoom por 20 atualizações; registrar resultados em specs/026-service-catalog-map-card/validation.md.

## Phase 6: Polish & Cross-Cutting Concerns

Finalizar acessibilidade e validação integrada.

- [ ] T020 Consolidar textos de categoria única, catálogo indisponível, expandir/recolher e descrições acessíveis em app/src/main/res/values/strings.xml; integrar referências nas telas e verificar fonte ampliada, temas, orientação e toque mínimo.
- [ ] T021 Executar ./gradlew assembleDebug e git diff --check, realizar os cenários integrados de specs/026-service-catalog-map-card/quickstart.md e registrar comandos, resultados e limitações em specs/026-service-catalog-map-card/validation.md.
- [X] T022 Conferir cobertura de FR-001 a FR-016 e SC-001 a SC-007 em specs/026-service-catalog-map-card/tasks.md, marcando tarefas somente após execução e documentando evidências pendentes em specs/026-service-catalog-map-card/validation.md.

## Dependencies & Execution Order

```text
T001 → T002–T003 → Foundation ready
  ├─ US1: T004–T009 → validação T010
  ├─ US2: T011–T013 → validação T014
  └─ US3: T015–T018 → validação T019
Todas as histórias → T020 → T021 → T022
```

US1 é demonstrável com catálogo existente ativo; US2 fornece as listas completas para a entrega final. US3 independe do catálogo. T009, T011 e T012 editam a mesma migration e devem ser sequenciais; validação US1 e US2 compartilha validation.md e deve ser consolidada sequencialmente. T005 pode ocorrer junto de T004; T013 pode ocorrer junto de T011; T015 pode ocorrer junto das tarefas de catálogo/cadastro. Modelos precedem validação/ViewModel, e ViewModel precede integração da tela.

T008 e T017 também editam strings.xml: executar essas edições sequencialmente ou consolidá-las
antes de validar as respectivas histórias. T020 faz a revisão transversal final dos textos já integrados.

## Parallel Examples

- **US1**: T004 (modelo) e T005 (repository) em paralelo; depois T006–T008. T009 pode ocorrer com T008, pois são arquivos distintos.
- **US2**: T011 (seed SQL) e T013 (leitura do catálogo) em paralelo; T012 após T011 para evitar conflito na migration.
- **US3**: T015 (modelo de acompanhamento) em paralelo com US1/US2; T018 (host do mapa) pode ocorrer com T017 após definir padding e eventos em T016.

## Implementation Strategy

1. Completar Setup e Foundational sem novas dependências.
2. Entregar US1 como MVP com catálogo já disponível e validar seu cadastro completo.
3. Entregar US2 para completar catálogo inicial e gestão autorizada; revalidar US1 com as novas listas.
4. Entregar US3 independentemente, verificando mapa, resumo e cancelamento.
5. Concluir acessibilidade, build e validação integrada; não afirmar metas de usabilidade ou performance sem evidência.

## Coverage

- US1: FR-001–FR-004, FR-007, FR-009; SC-001, SC-003, SC-004.
- US2: FR-005, FR-006, FR-008, FR-010; SC-002.
- US3: FR-011–FR-016; SC-005–SC-007.
- T020–T022 verificam requisitos transversais e evidências finais. Para SC-004, registrar número de participantes e taxa de conclusão; se não houver avaliação com participantes, informar a pendência.
