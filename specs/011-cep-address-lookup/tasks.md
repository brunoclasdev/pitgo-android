# Tasks: Busca de endereço por CEP

**Input**: Design documents from `/specs/011-cep-address-lookup/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: não foram solicitados testes automatizados. As tarefas usam build, lint e os cenários
manuais de [quickstart.md](./quickstart.md).

**Organization**: tarefas agrupadas por história de usuário para permitir implementação e
validação incremental.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode ser executada em paralelo com outra tarefa indicada porque altera arquivo distinto
  e usa um contrato já definido.
- **[Story]**: associa a tarefa à história correspondente da especificação.
- Todos os caminhos são relativos à raiz do repositório.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: preparar os textos compartilhados pelos estados de consulta e pela validação completa.

- [X] T001 Adicionar textos localizados para Buscar CEP, loading, sucesso completo/incompleto, CEP não encontrado, falhas offline/timeout/serviço/resposta, modalidade obrigatória e limites de caracteres em `app/src/main/res/values/strings.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: criar o contrato de domínio, acesso HTTP e injeção usados pelas histórias de consulta.

**⚠️ CRITICAL**: concluir esta fase antes das histórias que consultam o ViaCEP.

- [X] T002 Adicionar `PostalAddressResult`, `PostalCodeLookupResult`, `PostalCodeLookupFailure`, `PostalCodeLookupState`, `CepAddressField`, `CepAutofillProvenance`, `POSTAL_CODE_NOT_FOUND` e os novos campos imutáveis de `ProviderEditingContent` em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/model/ProviderRegistrationModels.kt`
- [X] T003 Implementar `ViaCepAddressLookupRepository` com um único `HttpClient(OkHttp)`, endpoint/host fixos, DTO JSON privado tolerante a `erro` string/boolean, validação de CEP/cidade/UF, timeouts 2500/1500/2000 ms, classificação sanitizada de falhas, cancelamento relançado e ausência de auth/log/retry/cache em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ViaCepAddressLookupRepository.kt`
- [X] T004 Registrar uma instância process-scoped de `ViaCepAddressLookupRepository` em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt` e encaminhá-la pela factory do `ProviderRegistrationViewModel` em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` e `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt`

**Checkpoint**: domínio, cliente HTTP e DI compiláveis; nenhuma migration, rota, manifest ou
dependência Gradle adicionada.

---

## Phase 3: User Story 1 - Preencher endereço pelo CEP (Priority: P1) 🎯 MVP

**Goal**: consultar explicitamente um CEP válido e preencher rua, complemento, bairro, cidade e UF,
mantendo número manual e campos editáveis.

**Independent Test**: informar `01001000`, confirmar que digitar não consulta sozinho, tocar
`Buscar CEP` e verificar Praça da Sé, lado ímpar, Sé, São Paulo e SP; Número permanece vazio e
editável, e uma correção manual continua válida para o CEP atual.

### Implementation for User Story 1

- [X] T005 [US1] Implementar `lookupPostalCode()` com job único, `attemptId` monotônico, comparação de tentativa+CEP, aplicação atômica dos cinco campos não vazios, proveniência por campo, preservação de Número, foco pós-sucesso e invalidação da verificação anterior em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt`
- [X] T006 [P] [US1] Substituir o item simples de CEP por `CepLookupField` responsivo com `OutlinedTextField`, máscara existente, botão textual explícito, `ImeAction.Search`, loading com texto, sucesso completo/incompleto, alvos mínimos e layout Row/Column conforme largura/fontScale em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`
- [X] T007 [US1] Integrar as máquinas de estado de lookup e submit: desabilitar temporariamente somente os cinco campos deriváveis durante `Loading`, manter CEP/Número/restante do formulário utilizáveis, bloquear submit concorrente, bloquear busca durante `Submitting` e cancelar/inutilizar resultado tardio ao iniciar submit em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`

**Checkpoint**: a história positiva funciona sem persistir nada durante a consulta e pode ser
validada isoladamente com a seção 4 do quickstart.

---

## Phase 4: User Story 2 - Identificar campos obrigatórios com erro (Priority: P2)

**Goal**: mostrar simultaneamente todos os campos obrigatórios/formatos inválidos, explicar cada
motivo e levar a pessoa ao primeiro erro na ordem visual.

**Independent Test**: deixar modalidade, Número e Cidade vazios e informar CEP incompleto; após uma
única tentativa de cadastro, os campos/grupos são marcados com mensagens próprias, o primeiro fica
visível e corrigir um campo remove somente o erro dele quando realmente válido.

### Implementation for User Story 2

- [X] T008 [US2] Reordenar a validação para refletir Modalidade → empresa → endereço → verificação → ofertas, manter coleta completa de erros e adicionar validação pontual reutilizável por campo com limites específicos em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/validation/ProviderRegistrationValidation.kt`
- [X] T009 [US2] Alterar handlers de campos, seletores e ofertas para revalidar somente erros já apresentados, preservar erro enquanto a entrada continuar inválida e manter valores/erros alheios, usando o resultado completo no submit em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt`
- [X] T010 [P] [US2] Remover o offset rígido `FORM_ITEMS_BEFORE_OFFERS`, derivar índice/foco da mesma ordem de linhas da `LazyColumn`, rolar antes de focar, e adicionar erro visual/textual/semântico à modalidade, campos, seletores e grupos não focáveis com mensagens de limite acionáveis em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`

**Checkpoint**: todos os erros aparecem juntos; primeiro erro offscreen é alcançado; correção
individual não apaga erro ainda inválido nem erros de outros campos.

---

## Phase 5: User Story 3 - Corrigir CEP inválido ou inexistente (Priority: P3)

**Goal**: impedir consulta de CEP malformado, identificar CEP inexistente e invalidar corretamente
os valores derivados quando o CEP mudar.

**Independent Test**: tentar consultar CEP incompleto sem chamada HTTP; depois consultar
`99999999` e receber erro no próprio campo; alterar um CEP previamente encontrado e confirmar que
somente campos provenientes da resposta anterior são limpos, enquanto Número é preservado.

### Implementation for User Story 3

- [X] T011 [US3] Implementar guarda local de oito dígitos sem rede, estado `NotFound`, erro `POSTAL_CODE_NOT_FOUND`, limpeza desse erro ao editar CEP e invalidação baseada em `CepAutofillProvenance` que preserva Número e campos nunca preenchidos pelo ViaCEP em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt`
- [X] T012 [P] [US3] Renderizar CEP inválido/inexistente com `isError`, supporting text, semântica `error`, botão habilitado apenas com oito dígitos e ação de nova tentativa sem apagar os outros dados em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`

**Checkpoint**: formato inválido nunca chega à rede, `erro=true` não aplica endereço e a troca de
CEP não mistura valores derivados nem apaga Número.

---

## Phase 6: User Story 4 - Continuar após falha temporária (Priority: P4)

**Goal**: distinguir falhas recuperáveis, permitir retry com o mesmo CEP e manter preenchimento
manual/conclusão disponível.

**Independent Test**: simular offline, timeout e 5xx; loading termina, CEP/draft são preservados,
mensagem e retry aparecem, e um endereço preenchido manualmente pode ser cadastrado sem exigir
sucesso do ViaCEP.

### Implementation for User Story 4

- [X] T013 [US4] Tratar `Offline`, `Timeout`, `ServiceUnavailable` e `UnexpectedResponse` como estados recuperáveis, implementar retry com novo `attemptId`, preservar todo o draft e garantir que falha temporária não entre no mapa de erros bloqueantes do submit em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt`
- [X] T014 [P] [US4] Exibir mensagens acionáveis e distintas com live region polite, `Tentar novamente` sem redigitação e campos manuais reabilitados após falha em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`
- [X] T015 [US4] Cancelar lookup e verificação de endereço em edição relevante, perda/troca de sessão e limpeza do draft; validar fingerprint/geração antes de aplicar retorno da verificação para impedir que callback antigo restaure endereço verificado em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt`

**Checkpoint**: a dependência externa pode falhar sem prender o formulário, e nenhum callback tardio
sobrescreve endereço, sessão ou estado mais novo.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: confirmar segurança, integração, build e experiência completa entre histórias.

- [X] T016 Revisar a fronteira de privacidade para garantir cliente ViaCEP sem Authorization/cookies/logging/cache/retry e nenhuma exposição de URL, CEP, body ou Throwable em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ViaCepAddressLookupRepository.kt` e `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`
- [X] T017 Executar `./gradlew :app:assembleDebug :app:lintDebug`, corrigindo somente achados relacionados à feature nos arquivos de `app/src/main/java/com/trustarx/pitgo/features/providerregistration/`, `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`, `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` e `app/src/main/res/values/strings.xml`
- [ ] T018 Executar todos os cenários manuais de `specs/011-cep-address-lookup/quickstart.md`, incluindo `01001000`, `99999999`, resposta tardia A→B→A, double tap, falhas temporárias, formulário com múltiplos erros, rotação, fonte 200%, TalkBack e inspeção de tráfego/logs; corrigir regressões somente nos arquivos listados em `specs/011-cep-address-lookup/plan.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: pode começar imediatamente.
- **Foundational (Phase 2)**: depende de T001 e bloqueia US1, US3 e US4; US2 usa apenas os modelos de
  erro existentes, mas deve partir do estado compilável da fase.
- **US1 (Phase 3)**: depende da fase Foundational e entrega o MVP/happy path.
- **US2 (Phase 4)**: depende da fase Foundational; pode ser desenvolvida em paralelo com US1 porque
  sua regra principal é a validação global existente.
- **US3 (Phase 5)**: depende de US1 para o controle de consulta e a proveniência já integrados.
- **US4 (Phase 6)**: depende de US1; pode avançar em paralelo com US3 depois do happy path.
- **Polish (Phase 7)**: depende de todas as histórias incluídas na entrega.

### User Story Dependencies

```text
Setup → Foundational ─┬─→ US1 (MVP) ─┬─→ US3 ─┐
                     │              └─→ US4 ─┤→ Polish
                     └─→ US2 ────────────────┘
```

- **US1 (P1)**: primeira entrega funcional; não depende de outra história.
- **US2 (P2)**: independente da resposta do ViaCEP e testável com submit local inválido.
- **US3 (P3)**: reutiliza estado, ação e proveniência entregues por US1.
- **US4 (P4)**: reutiliza o lookup de US1, mas não depende de US3.

### Within Each User Story

- modelos e Repository antes da orquestração do ViewModel;
- estado/eventos do ViewModel antes da integração final da UI;
- validação deve produzir erro tipado antes da UI traduzi-lo;
- cancelamento/identidade da tentativa antes de aceitar qualquer callback;
- story checkpoint antes de iniciar a próxima dependente.

### Parallel Opportunities

- T006 pode ser preparada em paralelo com T005 usando o contrato de estado da fase Foundational;
- T010 pode ser feita em paralelo com T008, e T009 começa quando a validação pontual estiver pronta;
- US2 pode avançar em paralelo com US1 depois da fase Foundational;
- T012 pode ser feita em paralelo com T011;
- T014 pode ser feita em paralelo com T013;
- US3 e US4 podem avançar em paralelo depois da conclusão de US1.

---

## Parallel Example: User Story 1

```text
Task T005: implementar orquestração, concorrência e autofill no ProviderRegistrationViewModel.kt
Task T006: implementar o CepLookupField responsivo no ProviderRegistrationScreen.kt
Depois: Task T007 integra guards de lookup/submissão entre os dois arquivos
```

## Parallel Example: User Story 2

```text
Task T008: corrigir ordem e validação pontual em ProviderRegistrationValidation.kt
Task T010: substituir offset fixo e preparar UI de erro/foco em ProviderRegistrationScreen.kt
Depois: Task T009 conecta a revalidação pontual aos eventos do ViewModel
```

## Parallel Example: User Stories 3 e 4

```text
Após US1:
- frente A executa T011 e T012 para CEP inválido/inexistente
- frente B executa T013 e T014 para falhas recuperáveis e retry
Depois: T015 fecha cancelamento e proteção contra callbacks antigos
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Concluir T001–T004.
2. Concluir T005–T007.
3. Parar e validar o CEP `01001000`, preenchimento exato, número manual, edição e ausência de busca
   automática.
4. Só então adicionar validação completa e estados negativos.

### Incremental Delivery

1. Setup + Foundational → contrato HTTP/estado compilável.
2. US1 → busca e autofill positivo.
3. US2 → todos os erros de cadastro e foco confiável.
4. US3 → CEP inválido/inexistente e troca segura de CEP.
5. US4 → falhas temporárias, retry e continuação manual.
6. Polish → segurança, build/lint e quickstart completo.

### Parallel Team Strategy

Após T001–T004:

- uma frente implementa US1;
- outra frente pode implementar US2 em arquivos de validação/UI coordenando o merge da tela;
- depois de US1, US3 e US4 podem avançar em paralelo;
- tarefas que alteram o mesmo `ProviderRegistrationViewModel.kt` ou
  `ProviderRegistrationScreen.kt` devem ser integradas sequencialmente para evitar conflito.

## Notes

- tarefas `[P]` alteram arquivos distintos da tarefa paralela indicada, mas o merge deve preservar o
  contrato comum de modelos;
- não criar testes, fixtures, mocks, migrations, rotas, manifest entries ou dependências Gradle;
- o cadastro final e a verificação/geocodificação existentes permanecem separados do lookup;
- evitar logs com CEP, URL, resposta, sessão, documentos ou Throwable bruto;
- realizar commits pequenos por tarefa ou grupo lógico, com mensagens descritivas.
