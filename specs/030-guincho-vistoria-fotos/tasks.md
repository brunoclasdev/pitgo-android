# Tasks: Vistoria fotográfica do veículo no Guincho

**Input**: Artefatos em `specs/030-guincho-vistoria-fotos/`.
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contrato de backend](contracts/guincho-inspection-api.md), [contrato de UI](contracts/guincho-inspection-ui.md), [quickstart.md](quickstart.md).
**Created**: 2026-10-14.
**Organization**: Setup, fundação compartilhada, US1 (P1), US2 (P1), US3 (P2), acabamento e validação.
**Tests**: Não criar suítes, mocks ou fixtures automatizados sem solicitação, conforme a constituição. Os critérios de verificação são manuais; compilar/lint e conferir contratos não substitui executar o fluxo com os participantes.

## Format: `[ID] [P?] [Story] Description`

Cada item começa com checkbox, ID sequencial, `[P]` somente para trabalho independente e `[USn]` somente dentro da fase da história. `[P]` indica oportunidade de execução paralela após os pré-requisitos descritos; não autoriza editar o mesmo arquivo simultaneamente. Os checkpoints abaixo não declaram tarefas concluídas.

## Path Conventions

Caminhos Android/Supabase são relativos à raiz `/Users/brunocesar/projetos-mobile/android/pitgo`. Caminhos do dashboard são absolutos, pois pertencem ao repositório `/Users/brunocesar/projetos-web/pitgo`. Ler seu AGENTS.md e os guias Next.js locais antes de editar código nele.

As oito migrations abaixo têm nomes propostos com timestamps válidos, posteriores ao histórico conhecido. Em T001, conferir colisões/migrations novas e, se necessário, ajustar os nomes e todas as referências preservando a ordem. Não modificar migrations já aplicadas. Criar somente arquivos com implementação efetiva; não criar esqueletos vazios.

## Phase 1: Setup — Integrações existentes

**Purpose**: Confirmar os pontos de extensão e acrescentar apenas as dependências necessárias.

- [X] T001 Conferir os corpos vigentes de ARRIVE/START/COMPLETE, schema de Storage, wrappers legados, isolamento de ambientes e instruções do dashboard, tomando como referência `supabase/migrations/20260930154000_guincho_accept_before_payment.sql`, `supabase/migrations/20260930144000_guincho_shared_dispatch_guards.sql`, `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt` e `/Users/brunocesar/projetos-web/pitgo/AGENTS.md`; registrar baseline, colisões de migrations e restrições encontradas em `specs/030-guincho-vistoria-fotos/quickstart.md`.
- [X] T002 Acrescentar `storage-kt` alinhado ao Supabase instalado e Jetpack ExifInterface 1.4.2, confirmando compatibilidade sem upgrades gerais, em `gradle/libs.versions.toml` e `app/build.gradle.kts`.
- [X] T003 Instalar o módulo Storage no cliente autenticado existente em `app/src/main/java/com/trustarx/pitgo/core/network/SupabaseClientProvider.kt`, preservando carregamento centralizado da sessão e configurações atuais; depende de T002.

**Checkpoint**: Dependências e pontos de integração confirmados; nenhum ambiente remoto foi alterado por estas tarefas de preparação.

## Phase 2: Foundational — Dados, autorização e transições

**Purpose**: Criar a base usada pelas três histórias. Esta fase bloqueia a integração das histórias.

- [X] T004 [P] Criar `supabase/migrations/20261014120000_guincho_inspection_schema.sql` com flag `guincho_inspection_required` default false, tabelas de versões/fotos/associações, FKs do mesmo pedido, estados explícitos, `photo_order`, `abandoned_at`, seleção e chaves de operação; incluir unicidade por intenção, guardas de imutabilidade e helpers privados de autorização/elegibilidade conforme `specs/030-guincho-vistoria-fotos/data-model.md`, sem backfill de vistoria para pedidos já chegados.
- [X] T005 [P] Implementar tipos de ponto, estado, versão, foto, decisão, resumo, cursores e falhas em `app/src/main/java/com/trustarx/pitgo/features/guincho/model/GuinchoInspectionModels.kt` e DTOs com conversão validada em `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoInspectionDtos.kt`, distinguindo foto RESERVED/READY e ausência de vistoria de falha de contrato.
- [X] T006 [P] Criar `supabase/migrations/20261014121000_guincho_inspection_storage.sql` com bucket privado JPEG até 5 MiB, INSERT apenas no objeto reservado ao guincheiro enquanto pedido/rascunho forem editáveis e SELECT aos participantes inclusive após encerramento; bloquear UPDATE/DELETE e reservas abandonadas, revisar policies permissivas existentes e revogar DML direto nas tabelas; depende de T004.
- [X] T007 [P] Criar `supabase/migrations/20261014122000_guincho_inspection_snapshots.sql` com resumo aditivo e ações por papel em `private.guincho_snapshot`, RPCs `get_guincho_inspection`, `list_guincho_inspection_versions` e `list_guincho_inspection_photos`, paginação estável e histórico de todas as fotos READY; manifesto de versão deve filtrar selected=true, preservando privacidade financeira/pré-pagamento e caminhos compartilhados de snapshot; depende de T004.
- [X] T008 [P] Integrar o resumo aos modelos e parsers compartilhados em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/model/MobileDispatchModels.kt` e `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/data/MobileDispatchDtos.kt`, com defaults para outras modalidades e verificação conservadora de dados inesperadamente ausentes no Guincho; depende de T005.
- [X] T009 Substituir explicitamente a transição atual em `supabase/migrations/20261014123000_guincho_inspection_transport_guards.sql`: primeira ARRIVE atual registra chegada/required=true/DRAFT sem iniciar, repetição preserva resultado, START exige aprovação vigente e ação do responsável e COMPLETE exige início registrado; manter wrappers antigos, locks busca→atendimento, `#variable_conflict use_column`, revisão/broadcast, `coalesce(payment_amount,customer_total_amount)` e `payment_can_release`; depende de T004, T006 e T007.

**Checkpoint**: Fundação pronta. Pedido novo que chega fica bloqueado para transporte; pedido já chegado antes da ativação mantém a compatibilidade. Banco é autoridade, inclusive para clientes antigos. Aplicar esta base isoladamente em ambiente compartilhado não constitui uma entrega funcional completa.

## Phase 3: User Story 1 — Fotografar o veículo ao chegar (Priority: P1)

**Goal**: Guincheiro captura os quatro pontos, pode refazer/acrescentar detalhes e envia uma versão fixa para aprovação, com retomada das fotos confirmadas.

**Independent Test**: Em Dev, usar um pedido aceito e pago, tocar em Cheguei, confirmar quatro fotos, interromper e retomar a captura, reenviar após falha e submeter; o cliente deve receber a pendência e o transporte continuar bloqueado. Esta verificação não depende da implementação da decisão da US2.

### Implementation for User Story 1

- [X] T010 [P] [US1] Implementar `prepare_guincho_inspection_photo`, `confirm_guincho_inspection_photo`, `discard_guincho_inspection_photo` e `unselect_guincho_inspection_photo` em `supabase/migrations/20261014124000_guincho_inspection_photo_commands.sql`, com locks/revisões, comparação do payload no retry, checagem do objeto real, preservação de READY e validação de replaces_photo_id no mesmo ponto/pedido/rascunho selecionado na reserva e confirmação; rejeitar alvos desatualizados, cancelados ou abandonados.
- [X] T011 [US1] Implementar `submit_guincho_inspection` em `supabase/migrations/20261014125000_guincho_inspection_submission.sql`, exigindo quatro pontos distintos selecionados READY e nenhuma reserva ativa não abandonada, congelando manifesto/versão e registrando operação; incrementar revisões do atendimento/busca e reutilizar CHANGED sem fotos/URLs; depende de T010.
- [X] T012 [P] [US1] Implementar arquivos privados e normalização fora da Main Thread em `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoInspectionCaptureFiles.kt`, com orientação/espelhamento, JPEG até 2560 px/5 MiB e prévia do resultado; configurar FileProvider restrito em `app/src/main/AndroidManifest.xml` e `app/src/main/res/xml/guincho_inspection_file_paths.xml`, sem galeria ou permissão CAMERA desnecessária, isolando arquivos por fingerprint de ambiente e conta.
- [X] T013 [US1] Implementar consulta da vistoria vigente, seleção de fotos, reserva/upload autenticado com upsert=false/confirmação, descarte/desmarcação, envio e download privado em `app/src/main/java/com/trustarx/pitgo/features/guincho/repository/GuinchoInspectionRepository.kt`; manter chave da intenção em retry, tratar objeto já existente, conflitos/rede/sessão e cache por ambiente/conta/foto com limite local 128 MiB; depende de T010, T011 e T012.
- [X] T014 [P] [US1] Implementar o fluxo de captura/envio em `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoInspectionViewModel.kt` com StateFlow e SavedStateHandle para ponto/URI/operação/conta/fingerprint; recuperar dados remotos, sobreviver a STOP/rotação, rejeitar callback de outra sessão/ambiente, limitar uma captura/envio por vez e nunca anunciar salvamento local como remoto; depende de T013.
- [X] T015 [P] [US1] Acrescentar textos pt-BR de pontos, orientações, progresso, captura, descarte, envio, erros e espera em `app/src/main/res/values/strings.xml`, com labels acessíveis, quatro vistas em relação ao veículo e fotos extras opcionais; pode ocorrer junto de T014 após T013.
- [X] T016 [US1] Implementar modo de captura da tela em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoInspectionScreen.kt`, com TakePicture registrado consistentemente, prévia/refazer, status por ponto, extras, descarte e botão de envio condicionado ao estado; tratar câmera indisponível/cancelada, imagem vazia e retry sem apagar fotos confirmadas; depende de T014 e T015.
- [X] T017 [US1] Integrar Repository, arquivos e factory da ViewModel em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`, usando Context de aplicação somente fora da ViewModel; cancelar operações e limpar referências/cache/entradas Coil tanto no logout quanto no callback clearIncompatible de EnvironmentStateRepository.validate, preservando captura ativa durante simples STOP; depende de T013 e T014.
- [X] T018 [US1] Integrar rota centralizada e abertura da vistoria após ARRIVE confirmado em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt`, `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`, `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoTrackingContent.kt`; oferecer Continuar vistoria na retomada e remover o uso de arrive como início implícito do Guincho, preservando PitTec/demais serviços; após envio confirmado, exibir “Aguardando aprovação das fotos” no acompanhamento de ambos os participantes ao receber SUBMITTED, mantendo os controles de revisão e aprovação para T025; depende de T016 e T017.

**Checkpoint**: US1 verificável como captura/envio; não é ainda MVP operacional completo, pois não há decisão do cliente. O bloqueio permanece até US2.

## Phase 4: User Story 2 — Validar fotos antes do transporte (Priority: P1)

**Goal**: Cliente revisa, aprova ou solicita correção da versão vigente; guincheiro corrige e inicia explicitamente somente após OK.

**Independent Test**: Partir de conjunto enviado pela US1; cliente solicita correção, guincheiro envia versão nova, cliente aprova, ambos reabrem o app e permanecem aguardando início. Somente tocar em Iniciar transporte muda para IN_PROGRESS; conclusão continua bilateral. A história é verificável sem a UI histórica da US3.

### Implementation for User Story 2

- [X] T019 [US2] Implementar `decide_guincho_inspection` e `begin_guincho_inspection_correction` em `supabase/migrations/20261014130000_guincho_inspection_review.sql`, com cliente responsável, versão vigente SUBMITTED, motivo obrigatório na correção, decisão única, operação idempotente e sucessora DRAFT única por parent_version_id; copiar apenas referências selecionadas e preservar conjunto/decisão anteriores sem iniciar transporte na aprovação.
- [X] T020 [US2] Acrescentar decisão e abertura de correção em `app/src/main/java/com/trustarx/pitgo/features/guincho/repository/GuinchoInspectionRepository.kt`, reutilizando resultados/falhas e atualizando a revisão antes de nova intenção; nunca reaplicar decisão a outra versão automaticamente; depende de T019.
- [X] T021 [P] [US2] Implementar revisão do cliente e atualização por versão/revisão em `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoInspectionViewModel.kt`, com motivo, envio de decisão, carregamento por version_id/photo_id, limpeza de confirmações quando o conjunto mudar e observação leve somente enquanto visível, sem consultas concorrentes duplicadas; depende de T020.
- [X] T022 [P] [US2] Acrescentar textos de aprovação, solicitação de correção, motivo, fotos aprovadas, espera pelo guincheiro e conflitos em `app/src/main/res/values/strings.xml`, distinguindo OK das fotos e confirmação de conclusão; pode ocorrer junto de T021 após T020.
- [X] T023 [US2] Integrar modo de revisão em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoInspectionScreen.kt`, com miniaturas paginadas, ampliação Coil, loading/erro/retry e aprovação apenas após apresentação bem-sucedida de todas as fotos do manifesto; não exigir abrir cada foto em tela cheia e exigir motivo não vazio para correção; depende de T021 e T022.
- [X] T024 [US2] Separar `startTransport()` de `arrive()` em `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt` e expor chamada explícita a `start_guincho_transport` em `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/repository/MobileDispatchRepository.kt`, preservando comportamento das demais modalidades e tratando INSPECTION_NOT_APPROVED/conflitos sem sucesso otimista; depende de T019.
- [X] T025 [US2] Integrar bloco visível de revisão pendente, correção/espera, fotos aprovadas e botão Iniciar transporte por ações do servidor em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoTrackingContent.kt` e `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt`; reutilizar polling/CHANGED atuais, manter ACCEPTED após OK e habilitar conclusão só após START confirmado; depende de T023 e T024.
- [X] T026 [US2] Integrar a abertura/retomada da sucessora de correção pelo guincheiro em `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoInspectionViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoInspectionScreen.kt`, exibindo motivo, reutilizando fotos inalteradas e protegendo conjuntos enviados/aprovados; preservar aprovação/espera por início ao reabrir e transformar fluxo cancelado em consulta; depende de T020, T023 e T025.

**Checkpoint**: US1 + US2 formam o MVP utilizável: chegada → fotos → correção/OK → início explícito → conclusão bilateral. US3 continua necessária para entregar todo o escopo solicitado.

## Phase 5: User Story 3 — Consultar vistoria no histórico (Priority: P2)

**Goal**: Participantes recuperam registro completo, inclusive versões corrigidas/fotos não enviadas, após encerramento; suporte autorizado consulta no caso permitido e com auditoria.

**Independent Test**: Consultar um pedido concluído com versão corrigida e aprovada, um cancelado com fotos READY e um contestado, como ambos os participantes; verificar material/decisões imutáveis e acesso negado a terceiros. Na contestação, MANAGER+ consulta foto do caso com auditoria; foto de outro pedido é negada.

### Implementation for User Story 3

- [X] T027 [P] [US3] Integrar paginação de versões, manifesto selecionado e consulta geral de fotos READY em `app/src/main/java/com/trustarx/pitgo/features/guincho/repository/GuinchoInspectionRepository.kt`, mantendo cursores completos/estáveis e diferenciação de foto não enviada, decisão e versão aprovada; usar os RPCs de T007 e download autenticado existente.
- [X] T028 [US3] Implementar estado de consulta histórica em `app/src/main/java/com/trustarx/pitgo/features/guincho/viewmodel/GuinchoInspectionViewModel.kt`, com páginas por versão, conjunto aprovado destacado, fotos sem envio e retomada após falha, sem disponibilizar mutações para registros encerrados ou solicitar câmera; depende de T027.
- [X] T029 [US3] Integrar apresentação histórica e textos em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoInspectionScreen.kt` e `app/src/main/res/values/strings.xml`, mostrando ponto, autoria, captura/salvamento, versões, motivos, decisões e ausência de vistoria, preservando fotos desmarcadas sem rotulá-las como aprovadas; depende de T028.
- [X] T030 [US3] Acrescentar Ver vistoria ao detalhe histórico existente em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoTrackingContent.kt`, mantendo a entrada de `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoHistoryScreen.kt` e integrando o retorno correto em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`; não substituir atendimento ativo por consulta de caso antigo; depende de T029.
- [X] T031 [P] [US3] Criar `supabase/migrations/20261014131000_guincho_inspection_support_reads.sql` com `dashboard_get_guincho_inspection`, `dashboard_list_guincho_inspection_versions`, `dashboard_list_guincho_inspection_photos` e `dashboard_authorize_guincho_inspection_photo` VOLATILE, sessão MANAGER+, escopo de contestação, foto READY do mesmo caso e auditoria via record_dashboard_action; falha de auditoria nega leitura, sem ampliar RLS geral de Storage ou conceder aprovação ao suporte; pode ocorrer junto de T027–T030.
- [X] T032 [US3] Atualizar contratos tipados em `/Users/brunocesar/projetos-web/pitgo/src/types/database.ts` e integrar leitura auditada/paginada em `/Users/brunocesar/projetos-web/pitgo/src/services/contestacoes.ts` usando a sessão SSR do funcionário e erros compreensíveis; seguir os guias locais Next.js e preservar decisões financeiras; depende de T031.
- [X] T033 [P] [US3] Implementar proxy em `/Users/brunocesar/projetos-web/pitgo/src/app/api/contestacoes/[disputeId]/fotos/[photoId]/route.ts`, validando UUIDs/sessão e chamando autorização auditada antes do download daquele objeto com cliente administrativo; retornar imagem private,no-store/nosniff e documentar esse uso server-side em `/Users/brunocesar/projetos-web/pitgo/src/lib/supabase/admin.ts`, sem aceitar caminho livre ou expor credencial/URL pública; depende de T032.
- [X] T034 [P] [US3] Integrar galeria somente leitura em `/Users/brunocesar/projetos-web/pitgo/src/app/(dashboard)/contestacoes/[id]/page.tsx`, criando `/Users/brunocesar/projetos-web/pitgo/src/components/contestacoes/GuinchoInspectionGallery.tsx` se útil para a separação da UI; mostrar versão, fotos, decisão e correção por páginas usando o serviço de T032 e a rota acordada em T033, com erro/retry e sem alterar o formulário de resolução; pode ser desenvolvida junto de T033, integração funcional depende de ambos.

**Checkpoint**: Todo o escopo funcional das três histórias está integrado, incluindo histórico e consulta excepcional auditada.

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Conferir qualidade, compatibilidade e evidência de entrega nas três histórias.

- [X] T035 Revisar acessibilidade, fonte ampliada, tamanhos de tela, Light/Dark/System, estados de loading/erro e botões alcançáveis em `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoInspectionScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoTrackingContent.kt` e `/Users/brunocesar/projetos-web/pitgo/src/components/contestacoes/GuinchoInspectionGallery.tsx` quando existente, corrigindo somente desvios do fluxo solicitado.
- [X] T036 Revisar invariantes finais de ownership, pagamento, idempotência, FKs, imutabilidade, substituição de ponto, aprovação vigente, cancelamento/expiração e wrappers legados nas migrations novas de `supabase/migrations/20261014120000_guincho_inspection_schema.sql` até `supabase/migrations/20261014131000_guincho_inspection_support_reads.sql`, ajustando as migrations ainda não aplicadas e registrando achados em `specs/030-guincho-vistoria-fotos/quickstart.md`.
- [ ] T037 Validar a aplicação do schema/policies e chamadas autorizadas/negadas em Supabase local descartável conforme `supabase/config.toml` e `specs/030-guincho-vistoria-fotos/quickstart.md`, incluindo versão antiga, concorrência de decisões, objeto imutável, cancelamento durante upload e herança de pagamento Homol; registrar resultados reais sem fixtures/suítes geradas nem alterar banco compartilhado por engano.
- [X] T038 Executar builds Dev/Homol/Prod, lint Homol, validateEnvironmentIsolation e git diff --check com os comandos de `specs/030-guincho-vistoria-fotos/quickstart.md`, corrigir falhas nos arquivos Android alterados e registrar resultado/limites no mesmo roteiro; não atribuir aceitação funcional ao build.
- [X] T039 Executar typecheck, lint e build do dashboard conforme `/Users/brunocesar/projetos-web/pitgo/package.json`, corrigir falhas da integração e registrar resultados em `specs/030-guincho-vistoria-fotos/quickstart.md`, preservando seu AGENTS.md e guias locais.
- [ ] T040 Validar manualmente as três histórias e recuperação no ambiente Dev com participantes reais conforme `specs/030-guincho-vistoria-fotos/quickstart.md`, incluindo câmera/STOP, fotos faltantes, correção, OK sem início automático, conclusão bilateral, histórico, troca de conta/fingerprint, suporte auditado e regressões de outros serviços; medir SC-001 a SC-006 e registrar execuções/pendências reais, sem inventar sucesso quando faltar ambiente ou participantes.
- [X] T041 Consolidar evidência de validação, correspondência FR/SC, migrations e compatibilidade em `specs/030-guincho-vistoria-fotos/quickstart.md` e conferir a entrega contra `specs/030-guincho-vistoria-fotos/plan.md`; registrar a coordenação necessária entre app e backend compartilhado Homol/Prod, sem tratar geração de tarefas como publicação/aplicação remota e sem marcar checks funcionais não executados como concluídos. O responsável pela publicação deverá incluir, nas notas da versão e nas instruções de distribuição do APK, a versão mínima compatível, o link de atualização e a data de ativação da vistoria, informando que novos atendimentos exigirão fotos aprovadas antes do transporte. Mensagem sugerida: “Atualize o PitGo para registrar ou aprovar as fotos do veículo. Essa etapa será obrigatória antes de iniciar o transporte.”

## Dependencies & Execution Order

### Phase Dependencies

```text
Setup T001–T003
  └─ Fundação: T004 || T005
       ├─ T004 → T006 || T007
       ├─ T005 → T008
       └─ T004 + T006 + T007 → T009
            └─ US1 T010–T018
                 └─ US2 T019–T026
                      └─ US3 T027–T034
                           └─ Acabamento/validação T035–T041
```

As linhas paralelas representam escrita independente depois dos respectivos pré-requisitos; a fase seguinte aguarda toda a fundação, inclusive T008. Migrations são aplicadas na ordem dos timestamps, mesmo quando escritas em paralelo. T001 precede alterações; T003 depende de T002.

### User Story Dependencies

- **US1**: depende da fundação. Entrega captura e conjunto pendente sem depender da decisão da US2; independente para demonstração/verificação dessa etapa.
- **US2**: depende do conjunto persistido da US1. O encadeamento é inerente ao negócio; verificar revisão/decisão partindo de pedido já enviado pelo fluxo real, sem mocks.
- **US3**: usa os registros de US1/US2. A consulta de registros pode ser verificada em casos existentes do ambiente sem repetir transporte; suporte/backend e histórico Android têm trilhas independentes.
- **Entrega completa**: exige US1, US2 e US3; conclusão da US1 isolada não justifica ativar em Homol/Prod um fluxo cujo cliente ainda não consegue aprovar.

### Within Each User Story

Modelos/schema comuns precedem RPCs e Repository; Repository precede ViewModel; ViewModel/textos precedem tela; DI e navegação integram a etapa. Nunca executar simultaneamente tarefas que alterem o mesmo Repository, ViewModel, tela, strings, NavHost ou arquivo de tipos. Os checkpoints podem ser verificados sem reimplementar outras histórias; as dependências necessárias estão explícitas.

### Parallel Opportunities

- Fundação: T004 e T005; depois, T006/T007 e T008 quando seus pré-requisitos estiverem prontos.
- US1: T010 e T012 após a fundação; T014 e T015 após T013. T011 aguarda T010; T013 aguarda T011 e T012.
- US2: T021 e T022 após T020. T024 pode avançar enquanto a revisão visual é implementada, desde que não concorra com alterações do mesmo arquivo na US1, já concluída.
- US3: T027–T030 no Android em paralelo com T031–T032 no backend/dashboard; T033 e T034 após T032. A galeria pode ser escrita contra o contrato estável da rota, mas a validação integrada aguarda os dois.

## Parallel Example: User Story 1

Depois da fundação, implementar T010 (RPCs de foto) e T012 (arquivo/FileProvider) em arquivos distintos. Depois de T011 e ambos concluídos, implementar T013. Com o Repository pronto, T014 (ViewModel) e T015 (strings) podem avançar juntos. T016–T018 integram essa base sequencialmente.

## Parallel Example: User Story 2

Depois de T019–T020, implementar T021 (estado de revisão) e T022 (textos) em paralelo. T024 (comando explícito de início) pode ser implementada separadamente de T023 (tela de revisão), pois usam arquivos distintos e já têm os contratos do backend. T025 aguarda T023/T024, e T026 fecha a integração de correção.

## Parallel Example: User Story 3

Executar T027 (paginação do Repository Android) enquanto T031 (RPCs auditados do suporte) é implementada. Prosseguir T028–T030 no Android e T032 no dashboard. Após T032, separar T033 (proxy de foto) e T034 (galeria); integrar e validar somente depois de ambos concluídos.

## Implementation Strategy

### MVP First

O primeiro incremento verificável é **US1**, mas o **MVP operacional recomendado é US1 + US2**, depois de Setup/Fundação. Isso mantém a exigência do cliente: nenhum transporte sem fotos aprovadas e início explícito. Não adicionar dispensa provisória para demonstrar captura isolada.

### Incremental Delivery

1. Completar Setup/Fundação com schema, autorização e transições protegidas em ambiente isolado.
2. Completar US1 e verificar captura/envio/retomada, mantendo o bloqueio.
3. Completar US2 e verificar correção, aprovação e início manual; esta é a primeira fatia utilizável de ponta a ponta.
4. Completar US3 com histórico completo e acesso excepcional auditado; atingir todo o requisito solicitado.
5. Concluir validação/correções, registrar SCs e preparar entrega coordenada, preservando dados/compatibilidade.

A numeração é a ordem padrão de execução; `[P]` permite as exceções independentes descritas, não obriga delegação ou múltiplos agentes.

## Traceability

| Requisitos | Tarefas principais |
|---|---|
| FR-001, FR-010, FR-017 | T004, T007–T009, T018, T025 |
| FR-002, FR-003, FR-004, FR-005 | T004, T010–T016 |
| FR-006, FR-007, FR-008 | T019–T023, T026 |
| FR-009, FR-013 | T004, T006, T009–T011, T019, T036–T037 |
| FR-011 | T009, T024–T025 |
| FR-012, FR-016 | T012–T014, T017–T018, T021, T026, T028 |
| FR-014, FR-015 | T006–T007, T027–T034 |
| FR-018 | T009–T010, T019, T026, T028, T036–T037 |
| SC-001 | T009, T024–T025, T037, T040 |
| SC-002, SC-003 | T012–T016, T021–T025, T040 |
| SC-004, SC-005, SC-006 | T017, T019, T027–T034, T037, T040 |

## Notes

41 tarefas: 3 Setup, 6 Fundação, 9 US1, 8 US2, 8 US3 e 7 de acabamento/validação. Todas começam desmarcadas; conclusão de documentação não representa implementação. Tarefas de validação registram resultado efetivo e não geram testes automaticamente. Corrigir apenas código relacionado, preservar dados de evidência e não introduzir CameraX, Room, WorkManager ou novos fluxos financeiros.
