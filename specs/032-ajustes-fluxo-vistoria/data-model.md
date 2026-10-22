# Data Model: Ajustes na vistoria

> Correção de escopo pelo usuário em 2026-10-21: somente aviso dentro do app; Firebase/FCM, notificação do sistema, registro de instalações, ledger de postagem e worker foram retirados. As seções/tarefas abaixo que descrevem push representam o planejamento anterior e estão superadas por esta correção. Referências atuais: `spec.md`, contrato de avisos e seção de correção do A33 no `quickstart.md`. Recuperação da foto e retorno ao mapa mantêm os requisitos existentes. O aviso global usa diálogo Material sem alterar as medidas da navegação, consulta autenticada a cada 5 s em primeiro plano e deduplicação de dispensa restaurável por conta/versão. A migration `20261021130000_guincho_inspection_in_app_alerts.sql` retira cron/despacho/fanout e revoga RPCs de push, preservando avisos e registros existentes.

**Referências**: [spec.md](spec.md), [contrato de avisos](contracts/client-inspection-alerts.md), [recuperação e navegação](contracts/inspection-recovery-navigation.md).

## Entidades existentes preservadas

- `appointments`: `id`, `client_id`, prestador, estado, revisão e etapas do atendimento. Cliente destinatário vem desta linha; envio não altera início/conclusão.
- `guincho_inspection_versions`: `id`, `appointment_id`, `version_number`, `state`, `submission_operation_id`, `submitted_at`, decisão. Continua uma versão aberta por atendimento. Ciclo: `DRAFT → SUBMITTED → APPROVED | CORRECTION_REQUESTED`; correção cria outro `DRAFT`.
- Fotos/manifesto: `RESERVED → READY`, ou reserva abandonada. Conjunto enviado/aprovado é imutável. Operações continuam com UUID estável e validação de revisão/conta.

## Instalação push — `private.guincho_inspection_push_installations` (nova)

| Campo | Regra |
|---|---|
| `installation_id` | UUID aleatório local, persistido fora de backup; não é ID de hardware |
| `environment_id` | `dev`, `homol` ou `prod`; allowlist do backend, não fronteira de autorização |
| `user_id` | Definido por `auth.uid()`, nunca fornecido livremente |
| `binding_id` | UUID de vínculo retornado pelo registro; muda ao trocar usuário/token/ambiente |
| `fcm_token`, `firebase_project_id` | Privados; projeto deve corresponder à configuração server-side do ambiente |
| `updated_at`, `revoked_at` | Rotação e revogação; revogado não recebe fanout |

Unicidade por instalação/ambiente e pelo token/projeto ativo. Reassociação atômica impede o mesmo token ficar ativo em duas contas. Registro requer sessão válida; revogação só afeta o vínculo do próprio usuário. Configuração de projeto Firebase enviada pelo cliente não escolhe credenciais do servidor. Tokens não entram em snapshots, logs ou payload de push.

Localmente manter fingerprint, conta, instalação e `binding_id`; mudança invalida trabalhos anteriores. Logout offline limpa vínculo local imediatamente e deixa revogação remota pendente, sem bloquear saída.

## Aviso — `private.guincho_inspection_review_alerts` (novo)

| Campo | Regra |
|---|---|
| `id` | UUID estável do aviso |
| `client_id`, `appointment_id`, `version_id` | Relação com cliente e versão enviados; consistência validada no servidor |
| `environment_id` | Resolvido server-side: Dev pelo backend; Homol/Prod pela cobrança vinculada, conforme contrato |
| `revision` | Crescente por aviso; incrementada na invalidação |
| `created_at`, `invalidated_at` | Criação na transação de envio; invalidação na decisão/substituição/encerramento |

Unique `(client_id, appointment_id, version_id)`. Não criar evento para tentativa de envio recusada nem duplicar em replay. O status consultável é derivado também do estado real de atendimento/versão; a linha de aviso nunca substitui autorização ou decisão. Retenção do aviso não expira fotos nem pendência. Avisos resolvidos podem ser limpos após 30 dias; consulta de ID autorizado já limpo retorna `RESOLVED`, sem dados de outra conta.

## Entrega — `private.guincho_inspection_push_deliveries` (nova)

| Campo | Regra |
|---|---|
| `alert_id`, `installation_id`, `binding_id`, `revision` | Identidade única de uma entrega para o vínculo e revisão |
| `state` | `PENDING`, `LEASED`, `ACCEPTED`, `OBSOLETE` ou `FAILED_CONFIGURATION` |
| `attempt_count`, `next_attempt_at` | Retry transitório a partir de 60 s, exponencial até 1 h, respeitando `Retry-After` |
| `lease_token`, `lease_until` | Claim worker exclusivo, duração 60 s; ACK exige token do lease vigente |
| `accepted_at`, `last_error_code` | Aceitação FCM não comprova exibição/leitura; erro normalizado sem token/dados pessoais |

Fanout é criado para vínculos ativos; registro de instalação também cria entrega de avisos ainda pendentes. Invalidar aviso torna entregas pendentes antigas obsoletas e cria invalidação na revisão nova para instalações pertinentes. Antes de enviar, validar vínculo e estado corrente; avisos resolvidos nunca reenviam `PENDING`. Limpeza das entregas acompanha aviso resolvido após 30 dias; pendências reais não são eliminadas por idade.

## Ledger Android de avisos (novo, pequeno)

Persistir no mesmo DataStore uma chave por fingerprint/conta/aviso, revisão mais recente, versão, `lastAlertedRevision`, status externo postado e vínculo atual; `resolvedAt` registra o instante de resolução para retenção. Limpeza de itens resolvidos após 30 dias; não remover aviso ativo por idade. Estado é compartilhado entre Worker e Repository sob mutex único. IDs de notificação usam `tag` com ambiente/conta/aviso e inteiro fixo deste tipo, sem colisão com chamados do prestador.

Antes de qualquer postagem, consultar o ledger sob o mutex compartilhado: se a mesma versão já tiver uma publicação externa bem-sucedida para aquela conta/ambiente/instalação, suprimir a republicação e o novo alerta. No código, `posted[chave]` representa `lastAlertedRevision` e registra essa publicação e não é apagado por dispensa, expiração visual ou reentrega; uma nova versão possui novo aviso e pode ser notificada. Gravar a marca somente após publicação bem-sucedida; permissão recusada não marca notificação externa como entregue. A tag estável e `onlyAlertOnce` complementam a proteção enquanto a notificação está ativa, sem substituir o ledger. Indicação interna persistente continua até resolver, mesmo depois de notificação dispensada ou expirada. Respostas antigas não reativam revisão resolvida; erro/página incompleta não remove aviso.

## Captura local existente — extensão compatível

Preservar `InspectionPendingCapture`: `captureId`, conta, atendimento, versão, ponto/detalhe, legenda, data, substituição, UUIDs de operação, tamanhos/hashes e fonte durável. Usar `resumeStage`, falha e campos existentes sempre que possível. Acrescentar somente estágio serializável de reparação de reserva, com valor padrão compatível para journals existentes; não incrementar `schemaVersion` sem leitor que aceite/migre os journals versão 1.

| Situação | Transição permitida |
|---|---|
| Falha transitória com assets válidos | `RETRY_WAIT →` etapa segura reconciliada; mesmos IDs |
| Falha no preparo com raw recuperável | `RETRY_WAIT → RAW_CAPTURED → PREPARING`; mesma captura |
| Remoto já `READY` | `qualquer pendência → SAVED` após reconciliação; sem novo upload |
| Derivado perdido, reserva com bytes reproduzíveis | Recuperar em temporário e conferir hashes antes de reutilizar reserva |
| Bytes novos necessários, reserva ainda incompleta | Persistir reparação → abandonar reserva com operação antiga → confirmar abandono → troca atômica de IDs → `RAW_CAPTURED`; mesmo `captureId` e fonte |
| Sem fonte suficiente | `NEEDS_RECAPTURE`, com nova captura explícita se editável |
| Conta/versão/atendimento sem edição | `BLOCKED_CONTEXT`; não descartar fonte nem declarar salva |

Somente `SAVED` ou descarte explícito do usuário permite limpeza de fonte pelo fluxo vigente. Reparação interna não entra em `DISCARDED` e não apaga arquivos; qualquer perda de resposta usa operações antigas até reconciliação. Nenhuma escrita remota altera foto `READY` ou objeto já existente com bytes divergentes.

## Intenção própria de envio (estado de navegação, não entidade remota)

`SavedStateHandle`: fingerprint, conta, atendimento, `versionId`, `operationId`, estado `IN_FLIGHT | UNCERTAIN | CONFIRMED`, token de consumo de navegação. Criar antes do submit; preservar até confirmação e consumo. Em `UNCERTAIN`, replay independe do botão de novo envio. Remover somente após retorno tratado; abrir vistoria para consulta não cria intenção.

Confirmação corresponde a `APPLIED`/`EXISTING` da mesma operação e versão. O documento atualizado pode já mostrar decisão posterior: a confirmação do envio continua válida, e o mapa mostra a etapa atual. Estado `SUBMITTED` observado isoladamente nunca gera navegação.
