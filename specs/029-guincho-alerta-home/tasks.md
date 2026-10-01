# Tasks: Alerta e Home do guincheiro

**Input**: Design documents from `specs/029-guincho-alerta-home/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md) e [contrato](contracts/guincho-home-alert.md).

**Tests**: Não criar testes automatizados, mocks ou fixtures. O usuário executará a validação funcional manual; compilação e lint fazem parte da implementação. Não instalar APKs ou efetuar pagamentos por causa destas tarefas.

**Organization**: Tarefas por história; caminhos relativos à raiz do projeto. `[P]` indica arquivos distintos, depois das dependências indicadas. Os novos arquivos abaixo só serão criados na implementação.

## Phase 1: Setup

**Purpose**: Confirmar os pontos de integração, sem inicializar outro projeto ou dependência.

- [X] T001 Conferir estado do repositório e registrar no plano os pontos atuais de navegação, pedidos, localização e notificação em `specs/029-guincho-alerta-home/plan.md`, usando os arquivos referenciados no plano; preservar alterações externas à feature.
- [X] T002 Confirmar o dado existente que identifica prestador MOBILE de Guincho em `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/model/ProviderDashboardModels.kt` e seu carregamento, documentando o critério em `specs/029-guincho-alerta-home/contracts/guincho-home-alert.md`; não inferir elegibilidade pelo nome traduzido nem criar novo modo de cadastro.

## Phase 2: Foundational

**Purpose**: Fixar o escopo autenticado e os contratos compartilhados antes das histórias.

- [X] T003 Definir modelos imutáveis de posição Loading/Current/Stale/PermissionDenied/LocationDisabled/Unavailable e câmera em `app/src/main/java/com/trustarx/pitgo/features/guincho/model/GuinchoProviderHomeModels.kt`, reutilizando DeviceLocationFix e modelos de mapa existentes, sem booleans contraditórios.
- [X] T004 Definir e integrar a seleção do perfil de guincheiro e o escopo por providerId para Home/card em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`, usando o critério confirmado em T002 e preservando as rotas dos demais perfis; sem navegação nova até a implementação das histórias.

**Checkpoint**: Modelos e escopo disponíveis; US1 e núcleo de US2 podem avançar, serializando mudanças no NavHost.

## Phase 3: User Story 1 — Perceber e responder à solicitação (P1, MVP)

**Goal**: Mostrar card único sobre qualquer tela do guincheiro e notificar fora do aplicativo somente para pedido pago liberado.

**Independent Test (usuário)**: Liberar pedido pago com guincheiro na Home, Perfil e Histórico; verificar dados, prazo e resposta. Repetir em segundo plano/bloqueado com serviço de acompanhamento ativo e com pagamento pendente: não deve haver alerta operacional antes da aprovação.

- [X] T005 [US1] Compartilhar uma instância de ProviderIncomingRequestViewModel por sessão de guincheiro acima das rotas em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`; remover somente o redirecionamento automático de PENDING GUINCHO_DIRECT para ProviderRequest e impedir card/tela duplicados, preservando PitTec e despacho legado.
- [X] T006 [US1] Ajustar refresh, elegibilidade GUINCHO_DIRECT/PENDING autorizado, prazo original e ações únicas em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/viewmodel/ProviderIncomingRequestViewModel.kt`; serializar refresh/resposta, revalidar após erro/reconexão e impedir envio depois do prazo sem alterar regras dos demais dispatch kinds.
- [X] T007 [US1] Criar card stateless rolável semelhante ao PitTec em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoIncomingRequestCard.kt`, com nome/veículo/retirada/destino/remuneração do prestador, contagem original, Aceitar/Recusar e feedback de envio/falha; consumir estado e callbacks sem acesso direto ao repository.
- [X] T008 [US1] Inserir card acima do NavHost em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` sem mudar a rota na chegada; aceite confirmado abre tracking uma vez, recusa confirmada retorna à Home, cancelamento/expiração remove o card sem executar resposta antiga.
- [X] T009 [P] [US1] Implementar persistência mínima e serializada da primeira postagem autorizada por providerId/appointmentId em `app/src/main/java/com/trustarx/pitgo/features/providertracking/notification/ProviderIncomingAlertStore.kt`, reutilizando DataStore existente; não armazenar dados pessoais, não marcar postagem bloqueada e remover marcadores somente após encerramento ou expiração; preservar o marcador mínimo por conta/pedido ao sair ou trocar de conta, sem manter dados pessoais ou notificações visíveis.
- [X] T010 [US1] Adaptar `app/src/main/java/com/trustarx/pitgo/features/providertracking/notification/ProviderIncomingRequestNotifier.kt` para ID estável, atualizações silenciosas, timeout pelo deadline e conteúdo genérico inclusive versão pública; usar T009 para não repetir som após recriação, respeitando canal/permissões e mantendo suporte PitTec.
- [X] T011 [US1] Integrar T009/T010 ao polling privado já existente em `app/src/main/java/com/trustarx/pitgo/features/providertracking/service/ProviderLocationTrackingService.kt` e construção em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`; remover notificação ao responder/cancelar/expirar, evitar alertas duplicados entre foreground/background e não depender da posição da Home para consultar oferta já liberada.
- [X] T012 [US1] Adicionar contexto mínimo id/conta ao toque de notificação em `app/src/main/java/com/trustarx/pitgo/features/providertracking/notification/ProviderIncomingRequestNotifier.kt` e tratar Intent inicial/onNewIntent em `app/src/main/java/com/trustarx/pitgo/MainActivity.kt`, propagando ao host existente; consultar estado autorizado antes de apresentar pedido, informar toque antigo sem ações e não abrir dados de outra conta.

**Checkpoint**: Alerta e resposta utilizáveis sobre as telas existentes, mesmo antes da Home com mapa. Nenhum teste manual declarado executado.

## Phase 4: User Story 2 — Visualizar posição atual na Home (P2)

**Goal**: Mapa próprio na Home, com acompanhamento, Recentralizar e recuperação de localização sem bloquear outras funcionalidades.

**Independent Test (usuário)**: Abrir Home disponível/indisponível, deslocar mapa e recentralizar; negar localização e conferir recuperação. Quando US1 estiver integrado, verificar marcador e Recentralizar com card visível.

- [X] T013 [P] [US2] Implementar aquisição precisa com timeout de 10 segundos, preciseUpdates apenas com Home visível e transição para Stale após 35 segundos em `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoProviderHomeViewModel.kt`; validar coordenadas/precisão com regra existente, cancelar jobs ao ocultar/sair e não usar cache antigo do cliente como posição atual nem alterar disponibilidade/publicação operacional.
- [X] T014 [P] [US2] Acrescentar callback opcional de gesto real em `app/src/main/java/com/trustarx/pitgo/core/map/ui/MapLibreMapHost.kt` quando necessário, mantendo assinatura compatível com chamadas existentes; distinguir gesto de animação/comando de câmera para não suspender acompanhamento programaticamente.
- [X] T015 [US2] Concluir followEnabled, comandos revisionados, posição no centro da área livre e Recentralizar em `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoProviderHomeViewModel.kt`, usando T014; gesto suspende apenas câmera, novas posições atualizam marcador, Current recentraliza e Stale/erro oferece recuperação.
- [X] T016 [US2] Criar Home/Route em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoProviderHomeScreen.kt` com MapLibreMapHost e estilo de participante existentes, marcador próprio, Recentralizar, obtenção/permissão/GPS/indisponibilidade e falha de recursos do mapa; manter perfil/disponibilidade/histórico acessíveis e não exibir marcador fictício.
- [X] T017 [US2] Integrar Home e factory ao ramo elegível em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` e dependências em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`; reutilizar disponibilidade/histórico existentes, manter inicialização de serviço por suas regras e pausar localização ao sair da Home.
- [X] T018 [US2] Integrar área livre entre mapa e card em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoProviderHomeScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoIncomingRequestCard.kt`; sobre a Home com mapa, fonte ampliada/card extenso usa rolagem mantendo marcador próprio e Recentralizar visíveis e utilizáveis, sem recenter forçado após gesto manual.

**Checkpoint**: Home operacional sem oferta e integração com alerta pronta. Sem regressão das telas dos outros perfis.

## Phase 5: User Story 3 — Retomar estado correto (P3)

**Goal**: Retomar pedido com prazo original ou atendimento no estágio correto e retornar à Home após conclusão.

**Independent Test (usuário)**: Retomar em PENDING, ACCEPTED, IN_PROGRESS e AWAITING_CLIENT_COMPLETION_CONFIRMATION; confirmar conclusão pelo cliente e conferir Home sem alerta residual.

- [X] T019 [US3] Unificar retomada de GUINCHO_DIRECT em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`: PENDING mantém rota/card e prazo, ACCEPTED/IN_PROGRESS/AWAITING_CLIENT_COMPLETION_CONFIRMATION abre tracking sem passagem pela oferta; evitar efeitos concorrentes e não redirecionar indevidamente consulta histórica.
- [X] T020 [US3] Integrar término e confirmação bilateral ao retorno para Home do guincheiro em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`, preservando regra existente de contestação/histórico e não interrompendo atendimento novo por atualização de pedido antigo.
- [X] T021 [US3] Limpar jobs, posição, estado de pedido e notificação na mudança de sessão em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` e `app/src/main/java/com/trustarx/pitgo/features/providertracking/service/ProviderLocationTrackingService.kt`; preservar somente o marcador mínimo do store até encerramento/expiração, evitando novo som ao retornar à mesma conta, e impedir callbacks tardios da conta anterior.

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T022 Completar textos e acessibilidade em `app/src/main/res/values/strings.xml`, `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoProviderHomeScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoIncomingRequestCard.kt`; tema claro/escuro, labels, fonte ampliada, áreas de toque e ação de habilitar notificações sem depender só de cor.
- [X] T023 Revisar no código rastreabilidade FR-001 a FR-016, ausência de dados pessoais em logs, isolamento de sessão, observers/requisições redundantes e preservação de clientes/fixos/PitTec nos arquivos alterados; registrar resultado e limitações em `specs/029-guincho-alerta-home/quickstart.md`, sem executar validação manual ou pagamentos.
- [X] T024 Executar `./gradlew :app:assembleDevDebug :app:assembleHomolDebug :app:assembleProdDebug` e `./gradlew :app:lintHomolDebug`, corrigir falhas relacionadas e registrar resultados em `specs/029-guincho-alerta-home/quickstart.md`; não adicionar suítes de teste, não instalar APKs nesta tarefa.
- [X] T025 Entregar os cenários e medições SC-001 a SC-006 ao usuário em `specs/029-guincho-alerta-home/quickstart.md`, distinguindo verificações de build/revisão concluídas dos testes funcionais/20 amostras ainda não executados, e atualizar o progresso em `specs/029-guincho-alerta-home/tasks.md` sem marcar validação manual como realizada.

## Dependencies & Execution Order

- Setup: T001 → T002. Foundation: T003 → T004, após Setup.
- US1: T005 → T006 → T007 → T008. T009 pode executar em paralelo com T005–T008; T010 depende de T009, T011 de T010, T012 de T008/T011.
- US2: T013 e T014 em paralelo após Foundation; T015 após ambos; T016 → T017 após T015; T018 após T008 e T017.
- US3: T019 após T012/T017; T020 após T019; T021 após T020.
- Polish: T022 → T023 → T024 → T025, após US1/US2/US3.
- Arquivos compartilhados NavHost, AppContainer, notifier, serviço e card devem ser editados sequencialmente. Núcleo de US2 pode avançar com US1, mas a integração do layout T018 requer ambos.

```text
Setup → Foundation → US1 (MVP) ─────┐
                   → US2 (núcleo) → integração US1/US2 → US3 → Polish
```

## Parallel Examples

- **US1**: executar T009 (store) junto com T005–T008 (observador/card/host); notifier/serviço aguardam store.
- **US2**: executar T013 (ViewModel) e T014 (gestos no host de mapa) simultaneamente; câmera completa aguarda ambos.
- **US3**: sem tarefas internas paralelas por dependência e arquivos compartilhados; executar sequencialmente T019–T021. Não dividir retomada e efeitos de sessão entre agentes concorrentes.

## Implementation Strategy

1. Entregar foundation e US1 como MVP: card pago sobre as telas atuais e notificação com resposta válida. A Home com mapa não bloqueia esse primeiro incremento.
2. Entregar US2 com mapa, gesto e recuperação; integrar card sem encobrir controles.
3. Concluir US3 com retomada operacional e encerramento/limpeza; revisar e compilar os três flavors.
4. Transferir validação funcional ao usuário pelo quickstart. Checkpoints não são pedidos de autorização adicionais nem motivo para parar a implementação autorizada.

## Coverage & Notes

| Requisitos | Tarefas principais |
|---|---|
| FR-001/002/006/007 | T005–T008 |
| FR-003/004/005 | T009–T012 |
| FR-008/009/010/011 | T013–T017 |
| FR-012/013 | T017–T018, T022 |
| FR-014/015 | T019–T021 |
| FR-016 | T004–T005, T017, T023 |
| SC-001 a SC-006 | T023–T025; medições manuais a cargo do usuário |

- Sem migrations, novas Edge Functions, alteração de pagamentos ou novas dependências.
- Notificação em segundo plano usa o serviço ativo existente; force-stop fica fora da garantia, conforme research/contrato.
- Hooks before_tasks/after_tasks ausentes: `.specify/extensions.yml` não existe.
