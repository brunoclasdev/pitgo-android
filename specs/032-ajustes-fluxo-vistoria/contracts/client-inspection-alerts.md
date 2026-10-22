# Contract: Aviso interno de revisão da vistoria

Escopo corrigido pelo usuário em 2026-10-21: aviso simples dentro do app, sem Firebase, FCM ou notificação do sistema.

## Origem e autorização

A transição efetiva para `SUBMITTED` cria um aviso único por cliente, atendimento e versão na mesma transação. Backfill preserva somente versões vigentes pendentes. Decisão, substituição e encerramento invalidam o aviso. As tabelas são privadas, com RLS; somente RPCs autenticadas retornam dados do próprio cliente. Não incluir imagens nem credenciais no aviso.

Dev usa ambiente `dev`; no backend compartilhado Homol/Prod, o ambiente deriva de `payments.checkout_environment` da cobrança vinculada, com default legado `prod`.

## RPCs usadas pelo aplicativo

| RPC | Comportamento |
|---|---|
| `list_my_pending_guincho_inspection_approvals(p_environment_id, p_after_created_at, p_after_id, p_limit)` | Retorna `{items,next_cursor}` apenas do cliente autenticado e ambiente solicitado; cursor exclusivo `(created_at,id)`, limite padrão 20 e máximo 100. |
| `get_my_guincho_inspection_alert(p_alert_id,p_environment_id)` | Revalida a associação e situação vigente. Retorna `{outcome:PENDING|RESOLVED,alert}`; ID desconhecido ou de outra conta retorna `RESOLVED` sem identificadores. |

Cada item contém `alert_id`, `appointment_id`, `version_id`, `version_number`, `submitted_at`, `created_at`, `revision` e `environment_id`.

## Apresentação

ViewModel global consulta ao autenticar, retomar e reconectar, e a cada 5 s enquanto o app estiver visível e conectado. Cancelar a consulta e limpar a apresentação na troca de conta; conferir conta após cada resposta. Falha de leitura não significa aprovação.

Fora da própria vistoria, apresentar diálogo Material “Vistoria para aprovação”, texto “Há uma vistoria aguardando seu aceite”, ações “Revisar vistoria” e “Agora não”. Não alterar tamanho, insets nem posicionamento da tela ou mapa. Dispensar preserva a pendência no atendimento e não reabre o mesmo aviso por polling; dispensa é restaurável na Activity e separada por conta/versão. Nova versão possui outro aviso. Dentro da vistoria correspondente, usar a indicação existente da tela.

“Revisar vistoria” revalida via RPC antes de navegar: pendência abre a vistoria, resolução abre o acompanhamento vigente; não autoriza aceite automático. Progresso e erro de abertura permanecem explícitos.

Não há entrega em segundo plano; retorno ao app consulta a pendência vigente. Não pedir permissão de notificação para esse fluxo. Notificações preexistentes do prestador e serviço de localização mantêm suas regras.

## Retirada do push anterior

A migration `20261021130000_guincho_inspection_in_app_alerts.sql` remove o cron e gatilho de disparo, retira fanout dos eventos e revoga acesso às RPCs de registro/entrega. O dispatcher anterior vira no-op e a função Edge de push é removida dos projetos publicados. Registros antigos são preservados. Não editar migrations já aplicadas.
