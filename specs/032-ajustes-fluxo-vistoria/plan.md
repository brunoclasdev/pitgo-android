# Implementation Plan: Aviso, recuperação de fotos e retorno ao mapa na vistoria

> Correção de escopo pelo usuário em 2026-10-21: somente aviso dentro do app; Firebase/FCM, notificação do sistema, registro de instalações, ledger de postagem e worker foram retirados. As seções/tarefas abaixo que descrevem push representam o planejamento anterior e estão superadas por esta correção. Referências atuais: `spec.md`, contrato de avisos e seção de correção do A33 no `quickstart.md`. Recuperação da foto e retorno ao mapa mantêm os requisitos existentes. O aviso global usa diálogo Material sem alterar as medidas da navegação, consulta autenticada a cada 5 s em primeiro plano e deduplicação de dispensa restaurável por conta/versão. A migration `20261021130000_guincho_inspection_in_app_alerts.sql` retira cron/despacho/fanout e revoga RPCs de push, preservando avisos e registros existentes.

**Branch**: `feature/refinamento-guincho` (Git atual) | **Date**: 2026-10-21 | **Spec**: [spec.md](spec.md)

**Input**: `specs/032-ajustes-fluxo-vistoria/spec.md`; diretório ativo independente da branch, resolvido pelo Spec Kit como `032-ajustes-fluxo-vistoria`.

## Summary

Avisar o cliente quando uma versão de vistoria for enviada, recuperar a mesma captura após falha e fechar a vistoria do guincheiro após confirmação do envio, retornando ao mapa do atendimento. Aproveitar a fila/journal existente de fotos, o replay idempotente das operações e a navegação atual do aceite do cliente.

Para atender também ao segundo plano, acrescentar FCM com registro autenticado de instalações, aviso transacional e worker Edge. No aplicativo visível, um ViewModel global consulta a pendência sem depender da tela de vistoria. O receptor FCM agenda validação autenticada via WorkManager antes de publicar notificação; nenhuma mensagem autoriza acesso ou aceite. O Worker recebe Repository e notifier por factory simples da Application, preservando a injeção atual e sem introduzir framework de DI.

## Technical Context

**Language/Version**: Kotlin integrado ao AGP `9.1.1`, plugins Compose/Serialization `2.2.10`, compatibilidade Java 11; SQL PostgreSQL e TypeScript/Deno nas Edge Functions existentes.

**Primary Dependencies**: Compose BoM `2026.06.01`, Lifecycle `2.10.0`, Navigation Compose `2.9.8`, Supabase Kotlin `3.2.6`, Ktor `3.3.1`, DataStore `1.2.1`, Coil `3.3.0`; preservar versões existentes. Adicionar Firebase BoM `35.0.2`, Messaging gerenciado pela BoM, Google Services `4.5.0` e WorkManager `2.12.0`, conforme [pesquisa](research.md).

**Storage**: PostgreSQL/Supabase e bucket privado existentes; journal de capturas em arquivos com `AtomicFile`, particionado pelo fingerprint de ambiente/conta. Novos registros privados de instalação, aviso e entrega; ledger local pequeno em DataStore, sem backup de token/instalação, e intenção de envio em `SavedStateHandle`. Sem Room adicional.

**Testing**: Compilação/lint das variantes e validação manual dos cenários no [quickstart](quickstart.md). Não criar testes automatizados, fixtures ou utilitários de teste sem solicitação. Não executar cobranças nem implantar como parte deste planejamento.

**Target Platform**: Android minSdk 24, targetSdk 36 e compileSdk 37, como no projeto; aparelho/emulador com Google Play services para validar FCM. Flavors Dev, Homol e Prod, mesmo applicationId, substituindo a instalação anterior.

**Project Type**: Aplicativo Android nativo com backend Supabase existente.

**Performance Goals**: Aviso perceptível em até 10 s em ≥95% de 20 envios por contexto; retorno ao mapa em até 2 s em ≥95% de 20 envios confirmados. Poll global 5 s, uma consulta em voo; callbacks de push sem rede longa. Retry reaproveita a foto com uma ação, sem abrir câmera.

**Constraints**: MVVM, sessão centralizada, permissões do aparelho, fotos privadas e versões imutáveis. Não depender de socket vivo em segundo plano; sem serviço de localização para clientes. Push não altera aprovação/início. Homol/Prod compartilham backend e Dev permanece isolado; ambiente não substitui autorização por usuário. Provisionamento Firebase é requisito operacional ainda não comprovado.

**Scale/Scope**: Três histórias e 14 requisitos. No máximo uma versão aberta por atendimento, conforme índice existente; fanout apenas às instalações ativas do cliente. Leituras paginadas de 20 itens, máximo 100; worker reivindica até 50 entregas por chamada. Sem novo catálogo, laudo, central genérica ou mudança financeira.

## Constitution Check

*GATE: revisado antes da pesquisa e novamente após o desenho da fase 1.*

| Princípio | Antes da pesquisa | Após desenho |
|---|---|---|
| I. Simplicidade e escopo | PASS: resolver os três ajustes | PASS: reaproveitar captura, Storage, replay, navegação e extensões do banco; persistência push limitada à entrega exigida |
| II. Android nativo e MVVM | PASS: manter arquitetura atual | PASS: UI → ViewModel → Repository; receptor, Worker e notifier em `features/guincho/data`, dentro dos grupos permitidos; sem regra em Composable |
| III. Estado e experiência | PASS: loading/erro/sucesso explícitos | PASS: retry por captura, aviso acionável sem navegação forçada, retorno apenas após confirmação; texto acessível e strings centralizadas |
| IV. Dados, segurança e falhas | PASS: conta participante e idempotência | PASS: RLS/RPC, secrets somente no servidor, mensagens sem fotos, revalidação e cancelamento por conta/ambiente |
| V. Qualidade proporcional | PASS: sem refatoração ampla/testes não pedidos | PASS: validação manual e build/lint; bibliotecas novas justificadas por entrega externa e janela curta do callback |

Nenhuma violação ou exceção necessária. SDK Android não oferece transporte push remoto equivalente ao FCM. WorkManager é componente Jetpack e permite a consulta autenticada fora do callback curto do FCM. Não se declara entrega implementada ou validada nesta etapa.

## Project Structure

### Documentation (this feature)

```text
specs/032-ajustes-fluxo-vistoria/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── client-inspection-alerts.md
│   └── inspection-recovery-navigation.md
└── checklists/requirements.md
```

`tasks.md` será produzido somente por `$speckit-tasks`.

### Source Code (repository root)

```text
app/build.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
app/src/{dev,homol,prod}/google-services.json             # configuração pública de cada flavor
app/src/main/AndroidManifest.xml
app/src/main/java/com/trustarx/pitgo/
├── MainActivity.kt, PitGoApplication.kt
├── core/
│   ├── app/PitGoAppContainer.kt
│   ├── environment/EnvironmentStateRepository.kt
│   └── navigation/{PitGoNavHost,PitGoRoutes}.kt
└── features/guincho/
    ├── data/{GuinchoInspectionPendingStore,GuinchoInspectionCaptureFiles,GuinchoInspectionDtos}.kt
    ├── data/GuinchoInspectionAlertStore.kt                # novo: ledger e vínculo local push
    ├── model/{GuinchoInspectionModels,GuinchoInspectionAlertModels}.kt
    ├── repository/{GuinchoInspectionRepository,GuinchoInspectionAlertRepository}.kt
    ├── data/GuinchoInspectionNotifier.kt                 # novo: adaptador de notificação Android
    ├── data/GuinchoInspectionMessagingService.kt         # novo: receptor FCM
    ├── data/GuinchoInspectionAlertWorker.kt              # novo: execução gerenciada da validação
    ├── viewmodel/{GuinchoInspectionViewModel,GuinchoInspectionSyncViewModel}.kt
    ├── viewmodel/GuinchoInspectionAlertViewModel.kt        # novo: aviso no app autenticado
    └── ui/{GuinchoInspectionScreen,GuinchoInspectionAlert}.kt
app/src/main/res/{values/strings.xml,drawable/,xml/}
supabase/
├── migrations/                                          # novas migrations aditivas após a cadeia 031
├── functions/guincho-inspection-push/index.ts             # novo: worker de entrega FCM
└── config.toml
```

Arquivos novos são propostas de destino, não arquivos já implementados. A classe de storage de aviso possui uso concreto para vínculo da instalação e dedupe; evitar nova camada genérica de notificações. Funções SQL e envio FCM ficam exclusivos desta feature.

**Structure Decision**: Manter módulos atuais e organização por feature. `PitGoAppContainer` injeta componentes e integra logout/ambiente; `MainActivity` recebe destino tipado de revisão, distinto dos chamados do prestador; `PitGoNavHost` renderiza aviso global e consome navegação validada. O mapa continua `appointmentTracking(appointmentId)`.

## Phase 0 — Research

Concluída em [research.md](research.md), com pesquisa separada de entrega de notificações e recuperação/navegação. Todas as decisões de desenho resolvidas: FCM de dados, worker autenticado, outbox transacional com retry, reutilização do journal e replay do envio próprio.

## Phase 1 — Design

### Avisos e entrega (US1; FR-001–FR-005)

1. Trigger da transição real `DRAFT → SUBMITTED` cria aviso único e entregas para instalações vinculadas. Registro já enviado não gera novo aviso no replay. Decisão/encerramento invalida aviso e atualiza revisão. Fazer backfill apenas de versões ainda pendentes, não avisar histórico decidido.
2. RPCs autenticadas registram/revogam instalação, listam pendências vigentes e consultam aviso por ID. Tabelas privadas e funções worker não são expostas ao cliente. Sem aceitar `user_id` escolhido no request.
3. Worker Edge recebe apenas segredo próprio de Vault, reivindica entregas com lease, revalida versão/cliente/instalação e envia HTTP v1 FCM; timeout do provedor pode resultar em repetição, tratada pelo ledger Android. Retry exponencial, tokens inválidos revogados e falhas de credencial observáveis, sem expor secretos.
4. Receptor agenda Worker único por ambiente/conta/aviso/revisão. Antes de exibir, ele valida vínculo local, autenticação e snapshot vigente; cancela aviso resolvido e ignora mensagem de outra conta/ambiente. Sob o mutex compartilhado, consulta o ledger e só autoriza a primeira postagem bem-sucedida daquela versão por conta/ambiente/instalação; dispensa ou expiração visual não apaga essa marca nem permite republicação. Permissão recusada não conta como postagem; uma nova versão pode gerar novo aviso. Push de invalidação usa prioridade normal; TTL de 5 min para envelope, sem expirar a pendência do atendimento. Notificação expira visualmente em 5 min, mantendo aviso no app.
5. ViewModel global combina sessão, conectividade e visibilidade: consulta inicial, retomada e poll de 5 s, interrompendo ao ocultar/sair. Consulta completa das páginas antes de cancelar avisos ausentes; uma página incompleta/erro não é prova de resolução. Compartilha o mesmo ledger e exclusão mútua com Worker. Aviso persistente tem ação “Revisar vistoria”; na tela alvo somente atualiza o documento.
6. Permissão `POST_NOTIFICATIONS` é solicitada no contexto de atendimento/revisão, com recusa não bloqueante. No logout/troca, cancelar notificações/trabalhos, limpar vínculo visível e rejeitar mensagens antigas; revogar remotamente quando possível e reconciliar ao novo login/token.

### Recuperação (US2; FR-006–FR-010)

Usar pipeline e mutex atuais. ViewModel expõe por captura `canRetrySave`, tentativa ativa e necessidade real de recaptura; UI apresenta “Tentar salvar novamente” somente quando editável e recuperável. Reconciliar `READY` primeiro, reutilizar arquivos íntegros e classificar falta de espaço, fonte ausente e falha transitória separadamente.

Se derivados forem perdidos, reconstruí-los em temporário. Com reserva existente, manter hashes/tamanhos/UUIDs antigos; só reutilizar bytes reconstruídos que correspondam ao manifesto. Se forem diferentes e houver fonte suficiente, persistir reparação local, abandonar idempotentemente a reserva incompleta e, após confirmação, gerar novos IDs em escrita atômica para a mesma captura. Não passar pelo estado `DISCARDED`, que apaga arquivos. A câmera só é exigida se não houver fonte recuperável. [Detalhes](contracts/inspection-recovery-navigation.md).

### Envio e navegação (US3; FR-011–FR-014)

Guardar intenção de envio com versão explícita e operação estável. `submit` passa a preservar `outcome` do envelope e aplicar documento retornado; `APPLIED` ou `EXISTING` da intenção própria confirmam o envio. Erro não apaga a intenção; replay de reconciliação independe de `canSubmit` e mantém a versão original. Expor confirmação pendente de navegação em estado, limpar somente após consumo pelo NavHost. Não navegar em função de simples leitura de `SUBMITTED`.

Reaproveitar retorno existente do cliente: se o mapa anterior for do mesmo atendimento, remover a vistoria; caso contrário, navegar ao mapa correto removendo-a e evitando duplicata. Consulta explícita posterior permanece aberta. Se a decisão do cliente já tiver ocorrido, mapa mostra estado vigente, sem forçar texto antigo. Não iniciar transporte.

## Validation and Rollout

Seguir [quickstart.md](quickstart.md): evidências de requisitos no aplicativo e banco, builds/lint Dev/Homol/Prod e proteção de fluxos existentes. Não criar testes automatizados nesta fase. Push exige projeto Firebase, credenciais protegidas, migration e worker em backend de desenvolvimento, além de aparelho com serviços Google; notificação local isolada não comprova US1.

Publicação futura segue ordem: provisionamento por ambiente → migrations/RPC/worker aditivos → Android com configuração do flavor → validação dos três contextos de aviso e regressões. Esquema antigo e decisões seguem compatíveis; clientes antigos podem produzir avisos pelo trigger sem conhecer FCM. Desabilitar o worker interrompe só o push, preservando pendência e fluxo manual; não reverter nem remover evidências de vistoria. Não implantar Homol/Prod nem gerar cobranças para validar automaticamente.

## Complexity Tracking

Sem violações constitucionais. O custo adicional de FCM, WorkManager e persistência de entrega atende ao requisito atual de segundo plano; fila de fotos, arquitetura e regras de atendimento permanecem existentes. Não criar barramento, serviço genérico de notificações, banco local novo ou processo permanente.

## Ajustes confirmados na execução — 2026-10-21

- Receptor FCM, Worker e notifier implementados em `features/guincho/data`; injeção simples e rotas existentes preservadas.
- Firebase ainda não provisionado no workspace: Debug compila com `INSPECTION_PUSH_CONFIGURED=false` para validar aviso interno, fotos e navegação. Release ou `-PrequireInspectionPush=true` exige o JSON real do flavor e falha se estiver ausente. Configuração inválida e isolamento de projetos são verificados; não foram gerados JSONs fictícios nem considerado push concluído.
- Migrations aditivas aplicadas e registradas somente em Dev; função Edge publicada em Dev. Foram acrescidas uma RPC restrita de revalidação da entrega antes de enviar e configuração Vault `INSPECTION_PUSH_ENVIRONMENT=dev`. Credenciais FCM e segredo de despacho ainda são pré-requisitos pendentes.
- T005/T021/T022 e validações em aparelhos permanecem abertas até haver configuração e cenários Dev apropriados. Os três aparelhos disponíveis têm Homol instalado; não houve troca de flavor, alteração de sessão ou transação real nesses aparelhos.
