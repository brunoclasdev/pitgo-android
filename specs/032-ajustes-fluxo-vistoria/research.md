# Research: Ajustes no fluxo de vistoria

> Correção de escopo pelo usuário em 2026-10-21: somente aviso dentro do app; Firebase/FCM, notificação do sistema, registro de instalações, ledger de postagem e worker foram retirados. As seções/tarefas abaixo que descrevem push representam o planejamento anterior e estão superadas por esta correção. Referências atuais: `spec.md`, contrato de avisos e seção de correção do A33 no `quickstart.md`. Recuperação da foto e retorno ao mapa mantêm os requisitos existentes. O aviso global usa diálogo Material sem alterar as medidas da navegação, consulta autenticada a cada 5 s em primeiro plano e deduplicação de dispensa restaurável por conta/versão. A migration `20261021130000_guincho_inspection_in_app_alerts.sql` retira cron/despacho/fanout e revoga RPCs de push, preservando avisos e registros existentes.

**Data**: 2026-10-21 | **Escopo**: [spec.md](spec.md)

## 1. Aviso ao cliente dentro e fora do aplicativo

**Decision**: Manter um observador autenticado da pendência no nível do aplicativo enquanto visível, com uma leitura inicial e polling a cada 5 segundos; usar Firebase Cloud Messaging (FCM) para entrega em segundo plano. Ambos consultam a mesma pendência autorizada. Não criar serviço de localização ou foreground service para clientes.

**Rationale**: `ProviderIncomingRequestNotifier` gera notificações locais; sua alimentação fora da UI depende de `ProviderLocationTrackingService`, exclusivo do prestador. `GuinchoInspectionSyncViewModel` também depende do ciclo de vida visível. Nenhum é receptor remoto do cliente. O Android pode suspender conexões de aplicativos em segundo plano; polling ou Realtime sozinhos não satisfazem FR-003. Poll global limitado evita depender da tela de vistoria e não requer novo canal Realtime.

**Alternatives considered**: Reutilizar o serviço do prestador associa a permissão de localização a uma necessidade sem localização; manter socket ou serviço permanente no cliente aumenta consumo e não oferece entrega adequada; implementar uma central genérica de notificações extrapola o pedido.

**Sources**: [Limites de execução em segundo plano](https://developer.android.com/about/versions/oreo/background), [recepção FCM](https://firebase.google.com/docs/cloud-messaging/android/receive-messages), [prioridade FCM](https://firebase.google.com/docs/cloud-messaging/android-message-priority).

## 2. Persistência e envio remoto

**Decision**: Criar registro privado de aviso por cliente/atendimento/versão na transação de transição efetiva para `SUBMITTED`, mais registro privado por instalação e revisão a entregar. Disparar worker Edge via `pg_net` após commit; usar `pg_cron` a cada minuto para recuperar entregas com falha. Reutilizar as extensões já utilizadas pelo reconciliador de pagamentos, com segredo e funções próprios da vistoria. Revalidar pendência antes de cada envio; marcar resolvida após decisão, substituição ou encerramento e enviar invalidação.

**Rationale**: O app do guincheiro não é fonte confiável de entrega e pode perder a resposta após o commit. A unicidade impede avisos novos para replay de `submission_operation_id`; reenvio após correção possui outra versão. Reserva de trabalho com lease evita envios concorrentes, embora transporte continue sendo pelo menos uma vez. Idempotência visual fica no consumidor. A chamada de rede não bloqueia o envio da vistoria.

**Alternatives considered**: Chamar FCM no aparelho expõe credenciais; depender de chamada secundária do guincheiro perde notificações; apenas cron de minuto não atende à meta de 10 segundos em condições normais. Não reutilizar fila nem secretos financeiros.

**Evidence**: `supabase/migrations/20261014143000_guincho_inspection_commands_v2.sql` reconhece replay antes de revisão/rascunho; `20260915023000_orders_payment_scheduler.sql` demonstra Vault, `net.http_post` e `cron.schedule` existentes.

**Sources**: [pg_net assíncrono após commit](https://supabase.com/docs/guides/database/extensions/pg_net), [agendamento de Edge Functions](https://supabase.com/docs/guides/functions/schedule-functions), [autorização FCM HTTP v1](https://firebase.google.com/docs/cloud-messaging/auth-server).

## 3. Consumo, permissão e privacidade

**Decision**: Enviar mensagens FCM de dados, prioridade alta para nova pendência e normal para invalidação. `FirebaseMessagingService` só valida o envelope e agenda trabalho único; um `CoroutineWorker` do WorkManager confirma conta, ambiente e situação no backend antes de exibir/cancelar a notificação. Usar trabalho expedited quando a entrega tiver prioridade alta, com fallback para trabalho comum. Aviso interno persistente até a decisão; notificação externa com ID estável por aviso e primeira postagem controlada pelo ledger por conta/ambiente/versão/instalação. Dispensa ou expiração visual não permite republicação; permissão recusada não conta como postagem. Receptor, Worker e notifier ficam em `features/guincho/data`, conforme os grupos permitidos pela constituição.

**Rationale**: `onMessageReceived` tem janela curta; consulta autenticada e recuperação de sessão precisam de execução gerenciada. Mensagem contendo apenas `notification` seria apresentada automaticamente no background, dificultando validação de conta, dedupe e cancelamento. Payload não contém foto, URL, placa, nome ou credencial. Revalidação no toque continua obrigatória. Falha de sessão/rede não produz aceite nem exposição; a pendência reaparece no foreground.

**Alternatives considered**: Exibir diretamente a partir do push dispensa a confirmação de situação vigente. Fazer rede longa no callback arrisca interrupção. WorkManager periódico não tem finalidade de entrega imediata e não substitui FCM.

**Sources**: [FCM e WorkManager](https://firebase.google.com/docs/cloud-messaging/android-message-priority), [expedited work](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work), [permissão Android 13+](https://developer.android.com/develop/ui/views/notifications/notification-permission).

## 4. Dependências e ambientes

**Decision**: Preservar versões atuais do projeto e acrescentar apenas Firebase Android BoM `35.0.2` (Messaging gerenciado `26.0.0`), plugin Google Services `4.5.0` e WorkManager `2.12.0`, versões estáveis verificadas nesta pesquisa. Não adicionar KTX Firebase separado. Configurar `google-services.json` por source set `dev`, `homol`, `prod`; a conta de serviço FCM e segredo do worker ficam somente no backend. Reconfirmar disponibilidade e compatibilidade dessas versões ao implementar, sem atualizar bibliotecas alheias.

**Rationale**: O repositório não tem FCM. O projeto usa minSdk 24, compatível com os SDKs selecionados. Homol e Prod compartilham o backend atualmente; `EnvironmentConfig.fingerprint` e `ENVIRONMENT_ID` precisam fazer parte da vinculação de instalação. Os flavors têm o mesmo applicationId e não coexistem no mesmo aparelho; não introduzir sufixos como efeito colateral. Instalação com Google Play services é pré-requisito de entrega FCM; aparelhos sem o serviço mantêm aviso interno, sem declarar o push validado.

**Ambiente do evento**: No backend Dev, usar `dev`; no backend compartilhado, derivar Homol/Prod de `payments.checkout_environment` da cobrança vinculada, já existente com default `prod`. Filtrar fanout por esse ambiente, sem depender de campo escolhido pelo guincheiro.

**Alternatives considered**: Upgrade geral do stack aumenta escopo; um segundo provedor push acrescenta integração não solicitada; misturar tokens de ambientes causa avisos cruzados. Um mesmo projeto Firebase pode atender Homol/Prod desde que roteamento e vínculo sejam separados; Dev deve permanecer isolado da produção.

**Sources**: [releases Firebase Android](https://firebase.google.com/support/release-notes/android), [plugin e arquivos de configuração](https://firebase.google.com/docs/android/google-services-plugin-and-file), [releases WorkManager](https://developer.android.com/jetpack/androidx/releases/work), [pré-requisitos FCM](https://firebase.google.com/docs/cloud-messaging/android/get-started).

**Operational prerequisite**: Provisionar apps/projeto Firebase, API HTTP v1 e identidade de envio autorizada; configurar credenciais protegidas no backend e associação dos ambientes. Isso é requisito de implantação, não prova de que já exista configuração nem motivo para trocar por uma solução que não entregue em segundo plano.

## 5. Recuperação da captura existente

**Decision**: Corrigir `retryPending` → `retryCapture` → `syncPending/process`, preservando `GuinchoInspectionPendingStore`, mutex por atendimento e journal `AtomicFile`. Distinguir falha transitória de preparo/derivados da perda real de fonte. Decidir recuperabilidade no Repository e expor a ação por captura no ViewModel. Reconciliar operação remota antes de editar, refazer derivados ou exigir câmera.

**Rationale**: Já existem IDs estáveis e arquivos duráveis por ambiente/conta/atendimento. Hoje todo `InspectionFailure.IMAGE` vira `NEEDS_RECAPTURE`, embora raw/original possam permanecer disponíveis; a UI usa ação genérica e não limita retry como limita descarte. Miniatura sozinha não comprova original recuperável. Falta de espaço deve usar `SPACE`, sem descartar o raw.

**Alternatives considered**: Nova fila de uploads duplica o mecanismo existente. Forçar captura para todo erro viola o pedido. Sobrescrever bytes de uma reserva já registrada viola os contratos de integridade; a recuperação deve reconciliar ou abandonar explicitamente a reserva incompleta, sem exigir descarte manual da captura pelo usuário.

**Evidence**: `GuinchoInspectionRepository.kt`, `GuinchoInspectionPendingStore.kt`, `GuinchoInspectionCaptureFiles.kt`, `GuinchoInspectionScreen.kt`, e [contrato anterior](../031-guincho-vistoria-rota/contracts/inspection-api.md).

## 6. Confirmação e retorno ao mapa

**Decision**: Persistir intenção própria de envio em `SavedStateHandle`, com conta/ambiente, atendimento, versão original e UUID da operação. Aplicar resultado retornado pelo Repository e produzir estado de navegação confirmado, consumido após o retorno pelo NavHost. Reconciliar com os mesmos parâmetros de identidade mesmo se o polling já tiver retirado `canSubmit`. Navegar para `PitGoRoutes.appointmentTracking(appointmentId)`, reaproveitando mapa anterior do mesmo pedido ou removendo a vistoria com `popUpTo` inclusivo e `launchSingleTop`.

**Rationale**: `submit()` atual ignora o documento retornado, remove `submitOperation` e chama `refresh(true)` sem emitir retorno. Um timeout após commit faz polling mostrar `SUBMITTED` e bloquear novo submit. SQL já oferece replay `EXISTING`; a versão original precisa ser explícita, pois o Repository hoje deriva a versão do documento corrente. O cliente já tem o callback `onApprovalConfirmed` com navegação adequada.

**Alternatives considered**: Fechar ao tocar perde erros; fechar sempre ao observar `SUBMITTED` impede consulta; novo UUID em retry duplica intenção; depender de nova leitura para navegar introduz falha após sucesso conhecido. O estado do mapa deve vir da situação vigente se o cliente decidir rapidamente.

## Resultado da fase 0

Escolhas técnicas resolvidas. Implantação e medições serão comprovadas na execução; não foram executados builds, publicação, pagamentos ou testes com aparelhos nesta fase. Verificação de acessibilidade e qualidade segue a constituição; não gerar suítes automatizadas sem solicitação.
