# Tarefas: Avaliação de guincheiros e PitTecs

**Input**: Artefatos de design em `specs/034-avaliacao-guincheiros-pittecs/`

**Pré-requisitos**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/service-rating-rpcs.md`

**Testes**: Não foram solicitados testes automatizados; a constituição do projeto orienta não gerá-los automaticamente. Os critérios independentes abaixo serão validados manualmente conforme `quickstart.md`.

## Formato

Cada tarefa usa checkbox, identificador sequencial, marcador [P] somente quando pode ser executada em paralelo, tag de história nas fases de história e caminho do arquivo-alvo.

## Fase 1: Preparação

**Objetivo**: Confirmar os pontos de integração reais antes de alterar código compartilhado.

- [X] T001 Conferir o identificador canônico do provider PitTec, os snapshots/listagens existentes e as rotas disponíveis nos DTOs `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/data/MobileDispatchDtos.kt` e na navegação `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`; registrar essas referências para a migration criada em T002

---

## Fase 2: Base compartilhada

**Objetivo**: Criar persistência e contratos seguros necessários às duas histórias, sem alterar a conclusão dos atendimentos.

- [X] T002 Criar tabela, constraints de nota e unicidade por atendimento, revogar acesso direto de clientes para INSERT/UPDATE/DELETE e configurar grants/políticas de leitura privada no migration `supabase/migrations/20261022100000_service_ratings.sql`
- [X] T003 Implementar RPCs de submissão, resumo agregado e leitura privada no migration `supabase/migrations/20261022100000_service_ratings.sql`; validar cliente, provider, modalidade e estado concluído no servidor, permitir apenas inserção definitiva e retornar `already_submitted` sem alterar a avaliação existente
- [X] T004 Adicionar estados de domínio e DTOs de avaliação alinhados aos RPCs em `app/src/main/java/com/trustarx/pitgo/features/rating/model/ServiceRating.kt` e `app/src/main/java/com/trustarx/pitgo/features/rating/data/ServiceRatingDtos.kt`
- [X] T005 Integrar chamadas Supabase para enviar avaliação, consultar estado privado e obter resumo em `app/src/main/java/com/trustarx/pitgo/features/rating/repository/ServiceRatingRepository.kt`

**Checkpoint**: A persistência rejeita notas inválidas e atendimentos inelegíveis, impede duplicidade e não expõe comentários pelo resumo público.

---

## Fase 3: História 1 — Avaliar atendimento concluído (Prioridade: P1) 🎯 MVP

**Objetivo**: Oferecer a avaliação opcional de Guincho ou PitTec após a conclusão e mostrar somente média/quantidade em dados públicos do prestador.

**Teste independente**: Concluir um atendimento de cada modalidade, enviar notas distintas com e sem comentário e confirmar vínculo correto, ausência de duplicatas e resumo agregado sem texto ou identidade.

### Implementação

- [X] T006 [US1] Criar estado imutável da tela e ViewModel para nota não selecionada, envio, sucesso, erro recuperável e avaliação já registrada em `app/src/main/java/com/trustarx/pitgo/features/rating/viewmodel/ServiceRatingViewModel.kt`
- [X] T007 [P] [US1] Criar formulário Compose de 1 a 5 estrelas sem seleção inicial e comentário opcional limitado a 500 caracteres em `app/src/main/java/com/trustarx/pitgo/features/rating/ui/ServiceRatingScreen.kt`, com textos compartilhados em `app/src/main/res/values/strings.xml`
- [X] T008 [US1] Validar nota obrigatória e limite do comentário, preservar formulário em falha e tratar resposta repetida como avaliação já salva em `app/src/main/java/com/trustarx/pitgo/features/rating/viewmodel/ServiceRatingViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/rating/repository/ServiceRatingRepository.kt`
- [X] T009 [US1] Registrar a rota centralizada da tela de avaliação e fornecer dependências do ViewModel pelo container em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` e `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`
- [X] T010 [US1] Integrar a oferta dispensável de avaliação após conclusão, usando a rota registrada em T009, sem bloquear ou modificar o fluxo de atendimento em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt`
- [X] T011 [P] [US1] Exibir média e quantidade do prestador sem carregar comentários nos dados/perfis públicos de Guincho; quando não houver avaliações, indicar que o prestador ainda não tem notas em `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoDtos.kt`, `app/src/main/java/com/trustarx/pitgo/features/guincho/model/GuinchoModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/guincho/repository/GuinchoRepository.kt`, `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoMapScreen.kt`
- [X] T012 [P] [US1] Exibir média e quantidade do prestador sem carregar comentários nos dados/perfis públicos de PitTec; quando não houver avaliações, indicar que o prestador ainda não tem notas em `app/src/main/java/com/trustarx/pitgo/features/pittec/data/PitTecDtos.kt`, `app/src/main/java/com/trustarx/pitgo/features/pittec/model/PitTecModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/pittec/repository/PitTecRepository.kt`, `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecDispatchComponents.kt`

**Checkpoint**: Guincho e PitTec podem ser avaliados após conclusão; dispensar fecha a oferta, e nenhuma avaliação ou comentário público vaza dados privados.

---

## Fase 4: História 2 — Dispensar e avaliar pelo histórico (Prioridade: P1)

**Objetivo**: Permitir que o cliente dispense a avaliação e retorne ao atendimento concluído pelo histórico, sem alterar o status do serviço.

**Teste independente**: Dispensar avaliação de um atendimento concluído, sair do acompanhamento, abrir o histórico, enviar a avaliação pendente e confirmar que um atendimento já avaliado aparece como definitivo e sem ação para uma segunda avaliação.

### Implementação

- [X] T013 [US2] Incluir no snapshot/listagem de Guincho estado avaliado e elegibilidade para o cliente, sem incluir comentário em payloads públicos, na migration `supabase/migrations/20261022100000_service_ratings.sql` e no DTO `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoDtos.kt`
- [X] T014 [US2] Incluir estado avaliado e elegibilidade nos snapshots/listagens de PitTec, confirmando modalidade e provider canônico na migration `supabase/migrations/20261022100000_service_ratings.sql` e em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/data/MobileDispatchDtos.kt`
- [X] T015 [US2] Adicionar entrada da avaliação pendente e indicação definitiva de avaliação concluída nos cartões de histórico Guincho em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoHistoryScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoHistoryViewModel.kt`
- [X] T016 [US2] Confirmar se existe histórico PitTec reutilizável; se não houver, implementar listagem de atendimentos concluídos usando `app/src/main/java/com/trustarx/pitgo/features/pittec/repository/PitTecRepository.kt`, criar tela e ViewModel com ação para avaliação pendente/estado definitivo e registrar a rota em `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecHistoryScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecHistoryViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`

**Checkpoint**: Pular a avaliação não muda o atendimento concluído; os históricos de ambas as modalidades permitem avaliar pendências e identificam registros finais sem permitir edição ou substituição.

---

## Fase 5: Revisão e validação transversal

**Objetivo**: Confirmar integração, segurança, compilação e cenários de aceitação das duas modalidades.

- [X] T017 Executar `./gradlew :app:assembleHomolDebug` e corrigir falhas de integração Kotlin/Compose nos arquivos alterados em `app/src/main/java/com/trustarx/pitgo/features/rating/`
- [X] T018 Percorrer cenários de ambas as histórias, retry após resposta ambígua e permissões de leitura conforme `specs/034-avaliacao-guincheiros-pittecs/quickstart.md`

---

## Dependências e ordem

### Dependências de fases

- Preparação (Fase 1) precede a base compartilhada.
- Base compartilhada (Fase 2) bloqueia as histórias porque define persistência, contratos e chamadas Supabase.
- História 1 e História 2 dependem da base compartilhada. A História 2 também reutiliza a tela/ViewModel da História 1 para enviar a avaliação do histórico.
- Revisão transversal depende das duas histórias concluídas.

### Dependências entre histórias

- **US1 (P1)**: começa após a Fase 2; entrega o envio opcional após conclusão, mais resumo agregado.
- **US2 (P1)**: começa após US1; depende do formulário e do estado de avaliação criado pela US1, e adiciona recuperação pelo histórico.
- Recomenda-se executar sequencialmente, pois as duas histórias alteram o fluxo compartilhado de avaliação e a ordem maximiza um MVP utilizável.

### Oportunidades de paralelismo

- T007 pode ocorrer em paralelo com T006, pois a tela Compose e o estado/ViewModel usam arquivos diferentes; integrar após ambos.
- T011 e T012 podem ocorrer em paralelo após o contrato do resumo estar disponível, pois afetam features e arquivos distintos.
- T013 e T014 precisam alterar o mesmo arquivo SQL e devem ser executadas em sequência; os DTOs correspondentes podem ser preparados em paralelo antes da integração.
- Evitar paralelizar alterações no mesmo arquivo de navegação, container ou migration.

## Estratégia de implementação

### MVP

1. Concluir preparação e base compartilhada.
2. Implementar US1 para submissão, retry, oferta pós-conclusão e resumo agregado.
3. Parar no checkpoint e validar envio Guincho/PitTec, segurança do comentário e conclusão não bloqueada.
4. Implementar US2 para retornar à avaliação pelo histórico e confirmar estado definitivo.

### Entrega incremental

- Base comum mais US1 oferece o MVP: avaliação imediata opcional e reputação pública agregada.
- US2 acrescenta a recuperação de avaliações dispensadas.
- Finalizar após build e roteiro manual de `quickstart.md`.

## Notas

- [P] indica tarefas em arquivos distintos que podem avançar sem dependência incompleta.
- Cada tarefa de história está marcada com [US1] ou [US2].
- Não foram incluídas tarefas de testes automatizados, conforme a spec e a constituição do projeto.

## Registro de implementação

- T001/T013–T016: ambas as modalidades usam `appointments.provider_id` / `providers.id`. A tela e o ViewModel de histórico existentes foram reutilizados com o RPC compartilhado `list_my_service_appointments`, evitando novos arquivos de histórico PitTec.
- T017: a variante configurada de homologação foi compilada com `./gradlew :app:assembleHomolDebug` (sucesso).
- T018: consultas transacionais de Guincho e PitTec validaram nota/texto, comentário opcional, limite Unicode, reenvio sem substituição, histórico, resumo e acesso restrito de cliente/prestador/suporte. A UI do ASUS foi conferida com envio desabilitado sem nota inicial e estado de avaliação registrada sem edição.

## Complemento — Perfil do guincheiro

- [X] T019 Implementar listagem privada paginada das avaliações do prestador autenticado em `supabase/migrations/20261022103000_provider_rating_comments.sql` e no repository `app/src/main/java/com/trustarx/pitgo/features/rating/repository/ServiceRatingRepository.kt`.
- [X] T020 Exibir resumo das notas e botão Ver comentários no perfil, com tela de avaliações recebidas e estados de carregamento/erro/vazio em `app/src/main/java/com/trustarx/pitgo/features/rating/ui/ProviderRatingsScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/rating/viewmodel/ProviderRatingsViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/ui/ProviderDashboardScreen.kt`.
- [X] T021 Registrar a rota de comentários e preservar a aba Perfil ao retornar em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`, `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoProviderHomeScreen.kt`.
- [X] T022 Validar build, permissões e navegação do perfil/comentários conforme `specs/034-avaliacao-guincheiros-pittecs/quickstart.md`.
