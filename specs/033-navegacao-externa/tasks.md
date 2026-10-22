# Tasks: Navegação externa e indicação de localização

**Input**: Documentos de `specs/033-navegacao-externa/`.
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contrato](contracts/external-navigation.md) e [quickstart.md](quickstart.md).
**Branch Git**: `feature/rota-externa`.
**Validação**: Compilação e aceite manual. Não criar testes automatizados, mocks ou fixtures. Ajustar o consumidor visual simulado já existente somente para manter o ícone correto.
**Organization**: Histórias com incrementos verificáveis; nenhuma tarefa está concluída apenas por ter sido planejada.

## Format: `[ID] [P?] [Story] Description`

- `[P]` identifica trabalho em arquivos distintos que pode ocorrer em paralelo quando os pré-requisitos indicados estiverem concluídos.
- `[US1]`, `[US2]` e `[US3]` correspondem às histórias da especificação. Setup, fundação e acabamento não possuem etiqueta de história.
- Os caminhos são relativos à raiz do repositório. Arquivos novos estão identificados nas tarefas.

## Phase 1: Setup — preparação e recursos compartilhados

**Purpose**: Confirmar o ponto de partida e preparar visibilidade e textos, mantendo as dependências atuais.

- [X] T001 Conferir a branch e os pontos de integração em `specs/033-navegacao-externa/plan.md`, `app/src/main/AndroidManifest.xml` e `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt`; registrar em `specs/033-navegacao-externa/quickstart.md` as condições disponíveis para validação, sem declarar cenários executados ou criar novos módulos, serviços e dependências.
- [X] T002 [P] Adicionar consultas restritas aos pacotes `com.waze` e `com.google.android.apps.maps` em `app/src/main/AndroidManifest.xml`, sem `QUERY_ALL_PACKAGES` ou novas permissões para abertura de navegadores; depende de T001.
- [X] T003 [P] Adicionar textos para “Abrir navegação”, opções, cancelamento, preparação, destino ausente, mudança de contexto, falha de revalidação e aplicativo indisponível em `app/src/main/res/values/strings.xml`, com nomes compreensíveis por leitor de tela; depende de T001.

## Phase 2: Foundational — integração e modelos comuns

**Purpose**: Entregar os elementos reutilizados pelas duas histórias de navegação antes de integrá-las às telas. Reutilizar os modelos existentes de mapa para US3.

- [X] T004 Criar `app/src/main/java/com/trustarx/pitgo/core/navigation/ExternalNavigationLauncher.kt` com `ExternalNavigator`, resultado de abertura e operações de construção, resolução e lançamento dos intents do contrato: Waze `waze://?ll=latitude,longitude&navigate=yes` e Maps `https://www.google.com/maps/dir/?api=1&destination=latitude,longitude&travelmode=driving&dir_action=navigate`; fixar o pacote escolhido, usar `Uri.Builder` e decimais independentes do idioma, consultar o intent exato e tratar `ActivityNotFoundException`/`SecurityException` sem fallback automático. Receber contexto somente durante a chamada, enviar apenas coordenadas/parâmetros fixos e não importar modelos da feature; depende de T002.
- [X] T005 Acrescentar destino, etapa, pedido com identificador e estado exclusivo `Closed`/`Choosing`/`Preparing`/`ReadyToLaunch`/`Failure` em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/model/AppointmentTrackingModels.kt`, conforme `specs/033-navegacao-externa/data-model.md`; reutilizar `MapCoordinate`, validar finitude e intervalos, não fabricar ponto padrão e não persistir a escolha. Referenciar o navegador definido no limite compartilhado de T004 sem duplicar o enum; depende de T004.
- [X] T006 Criar `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/ExternalNavigationChooser.kt` como diálogo Material que recebe estado e callbacks, mostra Waze/Google Maps com disponibilidade textual, permite cancelar e tentar outra opção em falha, bloqueia repetição durante preparação e informa quando nenhuma opção pode abrir; respeitar tema, fonte ampliada e semântica acessível, sem regra de atendimento ou acesso a repositório na UI; depende de T003, T004 e T005.

**Checkpoint**: Adaptador Android, modelos e seletor definidos; nenhum backend, SDK ou mecanismo novo de rastreamento necessário.

## Phase 3: User Story 1 — Guincheiro segue a rota no aplicativo escolhido (Priority: P1) — MVP

**Goal**: Abrir Waze ou Google Maps na retirada e no destino final conforme a etapa autorizada, sem alterar o atendimento.

**Independent Test**: Em atendimento de guincho autorizado, conferir as quatro combinações de dois destinos e dois navegadores, abrir sem redigitar endereço ou aguardar geometria interna, voltar ao acompanhamento e verificar preservação da vistoria e continuidade da localização pelo cliente.

### Implementation

- [X] T007 [US1] Implementar em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt` a elegibilidade do guincheiro e resolução de destino: prestador, tela sem histórico, modalidade `GUINCHO_DIRECT`, `trackingAuthorized`, `ACCEPTED → guinchoTrip.pickup` e `IN_PROGRESS → guinchoTrip.destination`; revalidar com `appointmentSnapshot()` no repositório atual antes de preparar abertura, comparar identificador/revisão, usar etapa/destino vigentes e recusar contexto sem confirmação, cancelado ou sem coordenada válida. A regra não depende da origem ou da rota interna; depende da Phase 2.
- [X] T008 [US1] Implementar eventos de escolha, preparação, falha recuperável, cancelamento e consumo único em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt`: serializar tentativas, identificar operações, ignorar respostas de geração descartada, rejeitar revisão antiga, seguir o fluxo existente de sessão expirada e permitir nova escolha sem guardar `Context`/`Activity`; depende de T007.
- [X] T009 [US1] Integrar o seletor e o efeito Android em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt`: configurar `historical` no ViewModel antes de observar a tela, consultar disponibilidade pelo adaptador ao abrir opções, consumir o pedido aprovado antes de lançar enquanto a rota estiver visível e comunicar falhas para nova escolha; encaminhar estado/callbacks necessários ao conteúdo guincho e manter os efeitos comuns antes do retorno antecipado dessa modalidade; depende de T008.
- [X] T010 [P] [US1] Adicionar a ação compacta de navegação em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoTrackingContent.kt`, usando a elegibilidade do ViewModel e callbacks de T009, acessível com resumo recolhido e sem cobrir controles, avisos ou ações da vistoria; preservar a função de recentralização “Ver rota” e permitir abrir o seletor mesmo com percurso interno indisponível; depende de T009.
- [X] T011 [US1] Ajustar o ciclo de vida dos novos pedidos em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt`: `onHidden()` invalida operações e limpa pedidos transitórios, `onVisible()` recarrega o snapshot sem repetir lançamento, e nenhuma ação de navegação chama chegada, início, vistoria, pagamento, conclusão ou parada do serviço. Conferir a independência já existente de `app/src/main/java/com/trustarx/pitgo/features/providertracking/service/ProviderLocationTrackingService.kt`, preservando intervalos e permissões; depende de T008 e T009, pode ocorrer junto de T010 após essas dependências.
- [ ] T012 [US1] Compilar com `./gradlew :app:assembleHomolDebug` e executar manualmente os cenários US1 de `specs/033-navegacao-externa/quickstart.md`, incluindo A32 quando disponível, retirada/final × dois navegadores, destino completo confirmado, ausência de origem/rota interna, histórico/pagamento pendente/cancelamento, mudança de etapa com seletor aberto, retorno e compartilhamento em segundo plano; registrar nesse mesmo arquivo os resultados reais e condições não executadas; depende de T010 e T011.

**Checkpoint**: US1 utilizável e validável sem depender da navegação PitTec ou da mudança visual dos marcadores. Este é o MVP sugerido, não o encerramento da feature completa.

## Phase 4: User Story 2 — PitTec navega até o cliente (Priority: P1)

**Goal**: Reutilizar a escolha de navegador para o deslocamento PitTec, com o ponto vigente do atendimento e bloqueio após chegada.

**Independent Test**: Em atendimento PitTec autorizado antes da chegada, conferir destino nos dois aplicativos, repetir com somente um/nenhum disponível, retornar sem mudança operacional e observar compartilhamento pelo cliente. Após chegada ou início do serviço, conferir ausência de navegação habilitada para essa etapa.

### Implementation

- [X] T013 [P] [US2] Expor leitura pontual da posição vigente do cliente em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/repository/AppointmentTrackingRepository.kt`, reutilizando `get_appointment_client_location` e a validação existente, com resultado que distinga ponto ausente/inválido, falha de leitura e sessão expirada; não engolir falha e não substituir o destino por posição local padrão ou extremidade de geometria. Não criar RPC ou Repository novo; depende da Phase 2 e pode antecipar a integração US2.
- [X] T014 [US2] Ampliar `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt` para `PITTEC_AUTO_SEQUENTIAL`, exigindo prestador, ausência de histórico, autorização, `ACCEPTED` e `arrivedAt == null`; revalidar snapshot e a leitura de T013, usar a posição vigente do cliente e reaplicar as guardas após a leitura assíncrona para impedir abertura se houver chegada/cancelamento ou resposta antiga. Reutilizar estado e consumo único de US1, sem criar segunda viagem ou alterar regras financeiras; depende de T011 e T013.
- [X] T015 [US2] Integrar a ação PitTec em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt`, usando estado/callbacks comuns, sem expansão obrigatória do resumo ou dependência de cálculo interno, com o destino e estados de erro de T014; cliente, histórico, outras modalidades e etapa após chegada não recebem ação operacional habilitada; depende de T009 e T014.
- [ ] T016 [US2] Compilar a integração Homol e executar manualmente US2 em `specs/033-navegacao-externa/quickstart.md`: os dois navegadores no ponto correto, um/nenhum aplicativo disponível, indisponibilidade após escolha, ausência de ponto, falha de revalidação, chegada durante leitura, cancelamento da escolha, retorno e compartilhamento em segundo plano; registrar resultados reais nesse mesmo arquivo, sem produzir dados ou testes simulados; depende de T015.

**Checkpoint**: Navegação externa disponível aos dois perfis, com destinos e restrições próprios e infraestrutura comum.

## Phase 5: User Story 3 — Marcadores padrão sem relógio de localização (Priority: P2)

**Goal**: Retirar o relógio nos mapas do atendimento, mantendo identidade do participante, última posição útil e aviso textual coerente.

**Independent Test**: Nas visões de cliente e prestador, conferir guincho e PitTec com posição atual, antiga, offline/falha com última posição e sem ponto; ícones padrão permanecem sem relógio, texto comunica a condição e nova posição retira o aviso correspondente.

### Implementation

- [X] T017 [P] [US3] Alterar os marcadores de `AppointmentTrackingUiState` em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/model/AppointmentTrackingModels.kt` para escolher `PITTEC_LIVE` ou `LIVE` pelo perfil, independentemente de `TrackingDataState`, preservando `CLIENT_LOCATION`, validação de coordenadas e descrições de última posição conhecida; não converter estado antigo em `Live`; depende de T005.
- [X] T018 [P] [US3] Ajustar os marcadores construídos em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoTrackingContent.kt` para usar o ícone padrão do guincho na última posição válida, sem emitir `MapMarkerKind.STALE`, sem modificar retirada/destino ou fabricar marcador para posição ausente; executar sem concorrer com T010 no mesmo arquivo.
- [X] T019 [P] [US3] Ajustar o consumidor visual existente em `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecMockTrackingContent.kt` para manter `PITTEC_LIVE` nas posições válidas antigas e preservar a indicação textual existente, sem gerar novos mocks, fixtures ou simuladores; manter compatibilidade com `app/src/main/java/com/trustarx/pitgo/core/map/ui/ParticipantMapStyleController.kt`, cuja camada legada `STALE` ainda atende a descoberta do guincheiro; depende da Phase 2.
- [X] T020 [P] [US3] Adicionar ou reutilizar avisos de posição desatualizada, última posição conhecida e localização indisponível em `app/src/main/res/values/strings.xml`, distinguindo offline/falha com ponto retido de ausência de ponto, sem mudar o limite de atualização existente; depende de T003.
- [X] T021 [US3] Integrar os avisos acessíveis em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoTrackingContent.kt`, reutilizando `TrackingStatus` quando adequado, preservando informação de posição antiga/sem horário do cliente e garantindo aviso de última posição conhecida em offline/falha. Manter o limiar de 30 segundos e retirar somente o aviso correspondente após atualização válida; depende de T017, T018 e T020; serializar com T009, T010 e T015 nesses arquivos.
- [ ] T022 [US3] Executar a matriz manual US3 de `specs/033-navegacao-externa/quickstart.md` para as duas modalidades e visões, verificando posição atual/antiga/ausente, recuperação, offline/falha, ausência do relógio, identidade PitTec e ausência de pontos artificiais; conferir também a descoberta do guincheiro como consumidor compartilhado e registrar resultados reais nesse arquivo; depende de T019 e T021.

**Checkpoint**: Mapas do atendimento sem relógio, com identidade dos participantes e qualidade da informação preservadas.

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Consolidar integração, privacidade, acessibilidade e evidências de conclusão.

- [X] T023 Revisar e corrigir eventuais desvios de privacidade/abertura em `app/src/main/java/com/trustarx/pitgo/core/navigation/ExternalNavigationLauncher.kt` e `app/src/main/AndroidManifest.xml`: somente dois pacotes consultados, intents sem identificação do atendimento/dados pessoais/origem inventada, nenhuma URI ou coordenada nos logs, exceções recuperáveis e ausência de fallback silencioso; depende das histórias de navegação.
- [ ] T024 Conferir e corrigir o layout e a acessibilidade em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/ExternalNavigationChooser.kt`, `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoTrackingContent.kt`, usando fonte ampliada, TalkBack e temas claro/escuro nos aparelhos disponíveis; garantir dois toques sem expandir resumo e ausência de cortes/sobreposição com controles e ações operacionais; depende das três histórias.
- [X] T025 Executar na raiz a compilação final `./gradlew :app:assembleHomolDebug`, definida em `app/build.gradle.kts`, conferir o APK `app/build/outputs/apk/homol/debug/app-homol-debug.apk` e registrar o resultado em `specs/033-navegacao-externa/quickstart.md`; repetir validações afetadas caso T023/T024 tenham exigido correções, sem gerar novos testes; depende de T023 e T024.
- [ ] T026 Medir manualmente SC-002 conforme `specs/033-navegacao-externa/quickstart.md`, distribuindo 20 tentativas pelos seis casos e contando desde a escolha, inclusive revalidação: pelo menos 19 devem abrir o aplicativo configurado em até 5 segundos; registrar versões, condições e duração nesse arquivo, sem dados pessoais; depende de T025 e dos navegadores configurados.
- [ ] T027 Consolidar em `specs/033-navegacao-externa/quickstart.md` a evidência dos seis critérios de sucesso, cenários de falha, retorno, compartilhamento e modelos de aparelho efetivamente validados; revisar a conformidade com `specs/033-navegacao-externa/plan.md` e distinguir aprovação, falha e impossibilidade de execução, sem marcar aceite manual como concluído apenas pela compilação; depende de T012, T016, T022, T025 e T026.

## Dependencies & Execution Order

### Dependências das fases

```text
Setup: T001 → (T002 || T003)
Fundação: T002 → T004 → T005
          T003 + T004 + T005 → T006
US1: T006 → T007 → T008 → T009 → (T010 || T011) → T012
US2: T006 → T013; T011 + T013 → T014 → T015 → T016
US3: T005 → T017; T006 → T018/T019; T003 → T020
     T017 + T018 + T020 → T021; T019 + T021 → T022
Final: histórias → T023 → T024 → T025 → T026 → T027
```

`||` indica execução simultânea possível em arquivos diferentes. T005 espera T004 porque utiliza o enum compartilhado; as tarefas da fundação são sequenciais.

### Dependências entre histórias

- **US1** fornece a primeira integração completa de seleção, revalidação e consumo único. Não depende de US2 ou US3.
- **US2** reutiliza o fluxo comum de US1; sua leitura de dados T013 pode ser antecipada. O aceite PitTec é independente dos resultados do guincho, mas a implementação T014 depende da infraestrutura de T011.
- **US3** não depende funcionalmente da navegação. Pode começar após a fundação, respeitando exclusão de edição nos arquivos compartilhados. T018 espera T010 caso US1 esteja sendo integrado primeiro; T021 espera T015 se US2 estiver alterando a mesma tela.
- Ordem simples com um executor: T001 até T027. Essa ordem respeita os pré-requisitos e evita conflitos.

### Restrições de edição e execução

- Serializar alterações em `AppointmentTrackingModels.kt` (T005/T017), `AppointmentTrackingViewModel.kt` (T007/T008/T011/T014), `AppointmentTrackingScreen.kt` (T009/T015/T021/T024), `GuinchoTrackingContent.kt` (T010/T018/T021/T024) e `strings.xml` (T003/T020).
- Registros de validação no mesmo `quickstart.md` e operações nos mesmos aparelhos são sequenciais. Os exemplos abaixo são oportunidades de trabalho, não autorização para iniciar agentes ou manipular atendimentos reais fora do fluxo normal.
- Após cada implementação que altere o aplicativo, compilar antes do aceite manual correspondente. Se faltar sessão/dispositivo/estado necessário, registrar a condição e manter o cenário pendente; não fabricar aprovação.

## Parallel Example: User Story 1

Após T009 e a fundação, T010 pode ocorrer junto de T011 (UI guincho e ViewModel em arquivos distintos). Outra combinação segura é T010 com T019 (ícone do consumidor PitTec), sem abrir validações simultâneas no mesmo aparelho.

## Parallel Example: User Story 2

Após a fundação, T013 (leitura no repositório PitTec) pode ocorrer durante T010/T011 de US1. T014 aguarda T011/T013, e T015 é sequencial para integrar o resultado na tela compartilhada.

## Parallel Example: User Story 3

T017, T018 e T019 podem ocorrer em paralelo após seus pré-requisitos, pois alteram modelos, conteúdo guincho e consumidor PitTec, respectivamente. T020 pode acompanhar esse grupo depois de T003. T021 integra os textos após o grupo e sem concorrer com outras alterações das mesmas telas.

## Cobertura dos requisitos

| Requisitos | Tarefas principais |
|---|---|
| FR-001, FR-002: ação e escolha explícita | T002–T006, T009–T010, T015 |
| FR-003: retirada/destino do guincho | T007, T012 |
| FR-004: local/etapa PitTec | T013–T016 |
| FR-005, FR-006: caminho preparado e origem externa | T004, T007, T012, T014, T016 |
| FR-007, FR-008: destino/app indisponíveis e recuperação | T003–T004, T006–T009, T013–T016 |
| FR-009: contexto vigente e bloqueios | T007–T009, T011–T016 |
| FR-010, FR-011: preservação e compartilhamento | T008–T012, T014–T016, T027 |
| FR-012, FR-013: marcadores e mensagens | T017–T022 |
| FR-014: dados mínimos | T004, T023 |
| FR-015: acessibilidade | T003, T006, T010, T015, T020–T024 |
| SC-001, SC-003, SC-005, SC-006 | T012, T016, T027 |
| SC-002 | T010, T015, T024, T026 |
| SC-004 | T017–T022, T027 |

## Implementation Strategy

### MVP e entrega incremental

1. Concluir Setup e fundação, preservando arquitetura e dependências atuais.
2. Implementar e validar US1: quatro combinações do guincho, retorno e localização compartilhada. Esse é o primeiro incremento demonstrável.
3. Reutilizar o fluxo em US2 e validar os dois casos PitTec e suas restrições.
4. Concluir US3 e validar os mapas sem relógio, incluindo falha/recuperação e avisos.
5. Executar acabamento, compilação final, amostra de tempo e consolidação. O marco MVP não interrompe uma implementação autorizada da feature completa.

### Limites de escopo

Não criar migrations, Edge Functions, deploy de backend, preferência de navegador, outros navegadores, reprodução da polilinha interna ou outro serviço de localização. Não remover o suporte legado de `STALE` usado fora do acompanhamento. Este comando somente gera tarefas; instalação e validação descritas serão executadas na implementação.
