# Tasks: Corrigir cadastro de veículos e erros por campo

**Input**: Design documents from `/specs/012-fix-vehicle-registration/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: Não gerar testes automatizados. A especificação solicita validação por Supabase local,
build, lint e fluxos manuais.

**Organization**: As tarefas estão agrupadas por história de usuário para que cada incremento
possa ser implementado e validado separadamente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode ser executada em paralelo porque usa arquivos diferentes e não depende de tarefa
  ainda incompleta na mesma fase
- **[Story]**: história de usuário correspondente (`US1`, `US2`, `US3`)
- Todas as tarefas indicam caminhos exatos

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: criar a correção incremental sem alterar migrations já aplicadas

- [X] T001 Criar a migration forward-only com as assinaturas exatas de `create_vehicle` e `set_primary_vehicle`, reset de `EXECUTE` e reload do schema PostgREST em `supabase/migrations/20260823000000_restore_vehicle_rpcs.sql`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: preparar os contratos Android compartilhados pelas três histórias

**⚠️ CRITICAL**: nenhuma história deve ser iniciada antes de esta fase terminar.

- [X] T002 [P] Ampliar os resultados e falhas tipados para `FieldFailure` e `RpcUnavailable`, preservando `VehicleFormOrigin` e a ordem de `VehicleField`, em `app/src/main/java/com/trustarx/pitgo/core/vehicle/model/VehicleModels.kt`
- [X] T003 [P] Extrair validação reutilizável por campo, normalização de opcionais e limite dinâmico de ano sem alterar a ordem da validação completa em `app/src/main/java/com/trustarx/pitgo/core/vehicle/validation/VehicleValidation.kt`

**Checkpoint**: contratos de domínio e validação prontos para o backend, Repository e formulário.

---

## Phase 3: User Story 1 - Cadastrar veículo com os dados mínimos (Priority: P1) 🎯 MVP

**Goal**: permitir que uma conta CLIENT salve um veículo apenas com Marca e Modelo, mantenha
exatamente um principal e conclua o fluxo no destino correspondente à origem.

**Independent Test**: com um cliente sem veículos, informar somente Marca e Modelo e confirmar
linha própria/principal + Home; pela Garagem, cadastrar um segundo não principal e outro principal,
confirmando retorno à Garagem e exatamente um principal.

### Implementation for User Story 1

- [X] T004 [US1] Implementar nas duas RPCs a autenticação CLIENT/profile ativo, ownership por `auth.uid()`, normalização, validação definitiva, lock por conta, criação do primeiro principal e troca atômica do principal em `supabase/migrations/20260823000000_restore_vehicle_rpcs.sql`
- [X] T005 [P] [US1] Adicionar atualização restrita de `AccountSnapshot.primaryVehicle` usando somente veículo retornado, próprio, ativo e principal em `app/src/main/java/com/trustarx/pitgo/core/auth/repository/SessionRepository.kt`
- [X] T006 [US1] Adaptar criação/seleção no Repository para validar o proprietário e a forma da resposta antes de retornar sucesso, sem serializar `user_id`, em `app/src/main/java/com/trustarx/pitgo/core/vehicle/repository/VehicleRepository.kt`
- [X] T007 [US1] Orquestrar submissão única, aplicação do principal autoritativo, resolução do onboarding somente após sucesso e estado terminal `Success` em `app/src/main/java/com/trustarx/pitgo/features/garage/viewmodel/VehicleFormViewModel.kt`
- [X] T008 [US1] Atualizar a projeção central do principal após `set_primary_vehicle` e manter a lista da Garagem coerente com a linha retornada em `app/src/main/java/com/trustarx/pitgo/features/garage/viewmodel/GarageViewModel.kt`
- [X] T009 [P] [US1] Separar “Cadastrar veículo” de “Pular por enquanto”, deixando somente o skip resolver a pendência antes da navegação, em `app/src/main/java/com/trustarx/pitgo/features/onboarding/viewmodel/VehicleOnboardingViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/onboarding/ui/VehicleOnboardingScreen.kt`
- [X] T010 [US1] Abrir o formulário do onboarding sem remover a tela anterior, navegar para Home como raiz após sucesso, retornar à Garagem por pop e ignorar pending legado quando o snapshot já possuir principal em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`
- [ ] T011 [US1] Executar os cenários mínimos, segundo veículo, troca e concorrência de principal, onboarding→Home e Garagem→Garagem descritos em `specs/012-fix-vehicle-registration/quickstart.md`

**Checkpoint**: o MVP salva veículos mínimos, mantém um principal e conclui a navegação correta.

---

## Phase 4: User Story 2 - Corrigir dados inválidos no campo certo (Priority: P2)

**Goal**: mostrar todos os erros conhecidos no controle correspondente, manter os erros até a
correção e tornar o primeiro campo inválido visível e focado.

**Independent Test**: submeter vários valores inválidos e duplicados; confirmar mensagens
específicas, foco no primeiro campo, preservação de valores/erros e remoção apenas após correção.

### Implementation for User Story 2

- [X] T012 [US2] Emitir SQLSTATEs `P2001..P2008`, traduzir apenas as três constraints únicas para `P2011..P2013` com `GET STACKED DIAGNOSTICS` e impedir detalhes sensíveis em `supabase/migrations/20260823000000_restore_vehicle_rpcs.sql`
- [X] T013 [US2] Mapear exclusivamente os SQLSTATEs permitidos para `FieldFailure(field, error)` sem interpretar mensagens, details ou hints em `app/src/main/java/com/trustarx/pitgo/core/vehicle/repository/VehicleRepository.kt`
- [X] T014 [US2] Revalidar somente campos que já possuem erro, manter/substituir o erro enquanto o novo valor continuar inválido, preservar erros alheios e repetir foco pelo `focusRequestId` em `app/src/main/java/com/trustarx/pitgo/features/garage/viewmodel/VehicleFormViewModel.kt`
- [X] T015 [P] [US2] Adicionar mensagens específicas de Marca, Modelo, Placa, Renavam, Chassi, anos, Quilometragem e identificador duplicado em `app/src/main/res/values/strings.xml`
- [X] T016 [US2] Renderizar mensagens por combinação campo/erro, adicionar `BringIntoViewRequester`, ações IME, semântica de erro e alvo único para o checkbox de principal em `app/src/main/java/com/trustarx/pitgo/features/garage/ui/VehicleFormScreen.kt`
- [ ] T017 [US2] Executar a matriz manual de erros simultâneos, formatos válidos, limites anuais, duplicidades, foco, fonte ampliada e TalkBack descrita em `specs/012-fix-vehicle-registration/quickstart.md`

**Checkpoint**: cada problema conhecido é acionável no campo correto e o restante do draft permanece intacto.

---

## Phase 5: User Story 3 - Receber falhas gerais sem indicação incorreta de campo (Priority: P3)

**Goal**: diferenciar falhas operacionais de erros de dados, preservar o formulário e não expor
detalhes internos.

**Independent Test**: simular offline, timeout, sessão expirada, 5xx, RPC ausente/incompatível e
toques repetidos; confirmar banner geral, nenhum campo marcado, draft preservado e uma chamada.

### Implementation for User Story 3

- [X] T018 [US3] Classificar timeout, offline, sessão, `PGRST202`, `PGRST203`, `42883`, 5xx, contrato de resposta e desconhecido sem propagar/logar throwable bruto em `app/src/main/java/com/trustarx/pitgo/core/vehicle/repository/VehicleRepository.kt`
- [X] T019 [US3] Preservar draft em falhas recuperáveis, limpar estado em perda/troca de sessão, bloquear jobs e resultados tardios e manter falhas gerais fora do mapa de campos em `app/src/main/java/com/trustarx/pitgo/features/garage/viewmodel/VehicleFormViewModel.kt`
- [X] T020 [P] [US3] Exibir a mensagem literal de RPC indisponível, feedback de preparação/salvamento e bloquear descarte/back durante submissão sem marcar campos em `app/src/main/java/com/trustarx/pitgo/features/garage/ui/VehicleFormScreen.kt` e `app/src/main/res/values/strings.xml`
- [ ] T021 [US3] Executar os cenários de falha geral, resposta perdida, toque duplicado, preservação do draft e ausência de detalhes técnicos descritos em `specs/012-fix-vehicle-registration/quickstart.md`

**Checkpoint**: falhas operacionais orientam a próxima ação sem culpar campos válidos ou vazar dados.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: verificar integração, segurança e regressões após as três histórias

- [ ] T022 Validar no Supabase local assinaturas, `SECURITY DEFINER`, `search_path`, ACLs, role CLIENT, RLS, ownership e exatamente um principal seguindo `specs/012-fix-vehicle-registration/contracts/vehicle-rpcs.md` e `specs/012-fix-vehicle-registration/quickstart.md`
- [ ] T023 Executar `./gradlew :app:assembleDebug :app:lintDebug` e percorrer o quickstart completo em `specs/012-fix-vehicle-registration/quickstart.md`
- [X] T024 Revisar o diff e os logs para confirmar ausência de alterações em migrations antigas, `service_role`, `user_id` no payload, exceções brutas e valores completos de placa/Renavam/chassi conforme `specs/012-fix-vehicle-registration/plan.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: começa imediatamente.
- **Foundational (Phase 2)**: depende de T001 e bloqueia as histórias.
- **US1 (Phase 3)**: depende de T002 e T003; entrega o MVP.
- **US2 (Phase 4)**: depende da RPC funcional de T004 e dos tipos/validação fundamentais.
- **US3 (Phase 5)**: depende do Repository e do estado integrados por US1/US2.
- **Polish (Phase 6)**: depende das histórias incluídas na entrega.

### User Story Dependencies

```text
Setup -> Foundation -> US1 (MVP) -> US2 -> US3 -> Polish
```

- **US1** é testável com dados válidos mesmo antes das mensagens detalhadas de US2/US3.
- **US2** é testável com falhas locais e SQLSTATEs de campo depois que a RPC de US1 existe.
- **US3** é testável isolando cada falha operacional depois que o fluxo de submit está integrado.

### Within Each User Story

- Contrato/modelo antes do Repository e ViewModel.
- RPC antes do fluxo end-to-end que a chama.
- ViewModel antes da integração final de UI/navegação.
- Validação manual somente após a implementação da história correspondente.

### Parallel Opportunities

- T002 e T003 podem ser executadas em paralelo.
- Em US1, T005 e T009 podem avançar em paralelo enquanto T004 implementa a RPC.
- Em US2, T015 pode avançar em paralelo com T012/T013.
- Em US3, T020 pode preparar UI/recursos em paralelo com T018, conciliando os nomes tipados antes do merge.
- As validações T022 e a revisão estática T024 usam frentes diferentes depois da implementação.

---

## Parallel Example: User Story 1

```text
Task T004: implementar as RPCs em supabase/migrations/20260823000000_restore_vehicle_rpcs.sql
Task T005: adicionar projeção segura em core/auth/repository/SessionRepository.kt
Task T009: corrigir decisão de cadastro/skip no onboarding UI/ViewModel
```

## Parallel Example: User Story 2

```text
Task T012: adicionar códigos SQLSTATE e tradução de constraints na migration
Task T015: adicionar textos específicos em app/src/main/res/values/strings.xml
```

## Parallel Example: User Story 3

```text
Task T018: classificar falhas gerais em core/vehicle/repository/VehicleRepository.kt
Task T020: preparar banner/loading/back em features/garage/ui/VehicleFormScreen.kt e strings.xml
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Concluir T001–T003.
2. Implementar T004–T010.
3. Validar T011 de forma independente.
4. Parar e confirmar criação mínima, principal único e navegação por origem antes de avançar.

### Incremental Delivery

1. **Setup + Foundation**: migration e contratos Android preparados.
2. **US1**: salvamento funcional e navegação correta — MVP.
3. **US2**: diagnóstico e correção por campo.
4. **US3**: falhas operacionais seguras e acionáveis.
5. **Polish**: banco local, build/lint, segurança e quickstart completo.

### Parallel Team Strategy

Depois da Foundation:

- uma frente implementa SQL/RPC;
- outra prepara sessão/onboarding;
- outra prepara mensagens/UI sem tocar nos mesmos arquivos simultaneamente;
- integrações sequenciais em Repository/ViewModel evitam conflitos e preservam a ordem dos contratos.

## Notes

- Tarefas `[P]` usam arquivos independentes no momento em que são liberadas.
- Não editar migrations históricas nem aplicar `supabase db push` remoto automaticamente.
- Não adicionar testes automatizados, dependências, Use Cases ou persistência local.
- Commits devem representar uma mudança lógica e usar mensagens descritivas.
