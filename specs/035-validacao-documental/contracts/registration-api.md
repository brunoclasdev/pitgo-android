# Contract: Cadastro, documentos e revisão

Contrato proposto para implementação. RPCs usam `/rest/v1/rpc/{nome}`, JSON, sessão Supabase autenticada e chave pública do ambiente. Nenhuma credencial administrativa no APK. Objetos privados não são acessados com URLs públicas. Campos `p_` abaixo são parâmetros; resultados usam snake_case. Comandos derivam o titular de `auth.uid()`; somente consultas administrativas aceitam case de outro titular.

## Contexto e formulário inicial

- Signup continua via Supabase Auth, com e-mail/senha e metadados `first_name`, `last_name`, `name` compatível, `phone`, documento (`cnpj` obrigatório e sem `cpf` para PROVIDER; exatamente um de `cpf`/`cnpj` para CLIENT), `account_type` (`CLIENT | PROVIDER`). A confirmação de senha é validação local e não é enviada. Trigger valida dados, cria identidade, membership e case INCOMPLETE numa operação consistente; preserva o fluxo separado de dashboard staff. Metadados nunca determinam aprovação, dispensa ou privilégio administrativo.
- `get_my_account_context_v2()` retorna `user_id`, `memberships[]` com `account_type`, `verification_status`, `verification_required`, `legacy_exempt`, `revision`, `current_submission_id`, `can_use_marketplace`, `operational_block_reason`; retorna também o contexto operacional necessário às telas existentes. Falta de conexão não produz aprovação local. Preservar v1 para consumidores existentes, com bloqueio server-side de novas contas.
- `start_my_registration_mode(p_account_type, p_operation_id)` cria membership/case caso ausente. Não altera membership anterior nem conceder capacidade. Cliente aprovado iniciando PROVIDER mantém CLIENT. Retorna contexto v2 e submissão DRAFT.
- `get_my_registration(p_account_type)` retorna case, revisão, draft atual, dados persistidos, evidências por slot, pendências e decisões do próprio cadastro; nenhuma senha ou URL assinada. Não reclassificar restrição documental esperada como perfil corrompido.

## Recuperação de acesso

Usar recuperação de senha do Supabase Auth por e-mail para contas restritas e aprovadas. A solicitação apresenta mensagem genérica sem confirmar existência de conta, trata limite de envio e não grava tokens/senha em logs. O retorno abre rota específica do Android, valida o fluxo de recuperação e permite nova senha com política vigente e confirmação local. Link expirado, inválido ou reutilizado não altera senha e oferece novo envio. Sessão temporária de recuperação só permite concluir a troca; não equivale a aprovação documental. Depois da troca, recarregar o contexto no servidor: cadastro, documentos, memberships e bloqueios permanecem iguais. Configurar URLs de retorno permitidas por ambiente, sem executar alterações remotas nesta fase de design.

## Comandos do titular

Todos recebem `p_operation_id` UUID persistido entre retries e `p_expected_revision` inteiro da leitura anterior. Sucesso retorna `case_id`, `account_type`, `status`, `revision`, `submission_id` e dados específicos. Duplicata idêntica retorna resultado anterior; revisão conflitante requer nova consulta. O servidor não aceita `user_id`, estado de aprovação ou ativação no payload do titular.

| RPC | Demais parâmetros | Regras e retorno específico |
|---|---|---|
| `save_my_registration_draft` | `p_account_type`, `p_data` | Campos permitidos por modalidade, normalizados; persistência parcial somente no DRAFT; retorna pendências e IDs estáveis dos veículos declarados. |
| `reserve_my_registration_evidence` | `p_submission_id`, `p_kind`, `p_vehicle_reference_id` opcional, `p_mime_type`, `p_byte_size` | Reserva slot válido no DRAFT; retorna `evidence_id`, `bucket`, `object_path` e limites. Não recebe arquivo base64. |
| `submit_my_registration` | `p_submission_id` | Exige dados completos e todas as fotos RECEIVED; congela snapshot e vínculos registration_submission_evidence; passa a IN_REVIEW. |
| `begin_my_registration_correction` | `p_case_id` | Só após CORRECTION_REQUESTED; cria uma nova versão DRAFT ligada ao case, reaproveita dados e cria vínculos aos arquivos RECEIVED mantidos no mesmo case, sem duplicar caminhos ou editar vínculos antigos, e permite corrigir; nova chamada idempotente não duplica versão. |

Uploads: `POST /storage/v1/object/registration-documents/{object_path}`, com JWT do titular, MIME permitido, no máximo 10 MiB e `upsert=false`. Reserva deve pertencer ao titular, à versão corrente DRAFT e ao slot informado. Mesmo se houver corrida entre upload e submissão, reserva não confirmada nunca satisfaz completude.

Confirmação: Edge Function `registration-evidence-confirm`, corpo `{evidence_id, operation_id, expected_revision}` e JWT do titular. Valida identidade, reserva e versão, lê objeto com limite de bytes e verifica imagem JPEG/PNG decodificável e metadados antes de confirmar. Backend privilegiado, quando necessário, fica somente na função. RPC interna de confirmação não recebe execução de anon/authenticated. Resultado RECEIVED só após confirmação transacional da revisão; objeto falho/órfão não conta. Retentativa de upload após resposta perdida consulta a reserva existente, sem sobrescrever objeto nem criar outra evidência automaticamente.

Leitura: endpoint autenticado do Storage, autorizado pelo titular/case do arquivo e seus vínculos de versão ou por staff. Uma foto reutilizada conserva seu caminho de origem; o detalhe e a aprovação usam os vínculos da submissão corrente. Interface exige sessão e trata 401/403; não colocar JWT em URL. Documentos não entram em cache público ou backup do Android.

## Operações administrativas

Acesso exige `dashboard_staff.active` e nível mínimo MANAGER, conferido em cada chamada; ADMIN/OWNER também são autorizados. Impedir autoanálise. Não confiar em nível enviado pelo dashboard nem em `raw_user_meta_data`.

| RPC | Parâmetros | Resultado/regra |
|---|---|---|
| `list_registration_reviews` | `p_status`, `p_account_type` opcionais, `p_after_submitted_at`, `p_after_id` opcionais, `p_limit` padrão 20/máximo 100 | Fila por data e ID, paginação por cursor estável, identificação mínima, situação e modalidade; sem fotos ou CPF completo na listagem. |
| `get_registration_review` | `p_case_id` | Dados completos, snapshot corrente, histórico, evidências e revisão esperada. Imagens são buscadas sob a mesma autorização de staff. |
| `decide_registration_review` | `p_submission_id`, `p_expected_revision`, `p_operation_id`, `p_result`, `p_checked_evidence_ids`, `p_correction_items`, `p_reason` | APPROVED exige conferência explícita de todos os documentos necessários do snapshot; CORRECTION_REQUESTED exige motivo e itens. Lock, versão, decisão, ativação e auditoria atômicos; nunca liberar outra modalidade. |

Histórico é append-only. Duas decisões concorrentes: apenas uma confirma; a outra recebe conflito e deve reler. Repetição da operação vencedora retorna resultado prévio. Ativadores antigos do dashboard também devem exigir elegibilidade documental; operação genérica de `active=true` não substitui a decisão acima nem pode liberar uma nova conta pendente.

## Erros de domínio

| Código | Resultado para a interface |
|---|---|
| `AUTH_REQUIRED` | Reautenticar, preservando somente progresso já confirmado. |
| `FORBIDDEN` | Sem permissão; não revelar dados do cadastro solicitado. |
| `REGISTRATION_REQUIRED` | Abrir cadastro da modalidade, sem executar operação de negócio. |
| `REVIEW_PENDING` | Mostrar análise; não iniciar contratação ou atendimento. |
| `CORRECTION_REQUIRED` | Mostrar itens de correção. |
| `INCOMPLETE_REGISTRATION` | Listar campos/slots obrigatórios ausentes. |
| `INVALID_IMAGE` / `UPLOAD_NOT_CONFIRMED` | Identificar slot que precisa de novo envio. |
| `REVISION_CONFLICT` / `SUBMISSION_NOT_CURRENT` | Recarregar antes de editar ou decidir. |
| `OPERATION_KEY_REUSED` | Recusar payload diferente para chave já usada. |
| `INVALID_STATE` | Não alterar cadastro; atualizar situação. |

Erros SQL usam SQLSTATE apropriado (`42501` permissão, `22023` entrada, `P0002` ausência, `40001` conflito), com código de domínio estável, sem dados pessoais em mensagens. Edge Function traduz para HTTP 401/403/400/409/413/415 conforme o caso, preservando o código de domínio no corpo. Offline, timeout, limite de chamadas e falha de serviço recebem tratamentos distintos; não repetir automaticamente uma decisão com nova chave.

## Exemplos de integração administrativa

Fila: chamar `list_registration_reviews` com `{"p_status":"IN_REVIEW","p_account_type":"PROVIDER","p_limit":20}` e JWT de staff ativo. O resultado contém `case_id`, modalidade, revisão e data; abrir `get_registration_review` com o case selecionado para obter as versões e evidências.

Aprovação: enviar a `decide_registration_review` o `p_submission_id` corrente, `p_expected_revision` lida, novo `p_operation_id` UUID, `p_result:"APPROVED"`, `p_checked_evidence_ids` com todos os IDs efetivamente conferidos, `p_correction_items:[]` e `p_reason:null`. Retorno: `case_id`, `status:"APPROVED"` e nova revisão. Não enviar URLs públicas ou o conteúdo das imagens nesse comando.

Correção: usar `p_result:"CORRECTION_REQUESTED"`, motivo compreensível e lista de itens como `["CNH_FRONT"]`. O titular consulta os mesmos motivos em `get_my_registration`, abre a nova versão com `begin_my_registration_correction` e reenvia. Versões seguintes mantêm vínculos com os arquivos preservados do mesmo case; `checked_evidence_ids` sempre se refere aos arquivos selecionados na versão em análise.

Complemento Android: `sync_my_registration_complement(p_account_type)` captura empresa/endereço/ofertas persistidos pelos formulários existentes, mantendo a lista de veículos do rascunho. A edição de empresa/endereço continua sujeita às validações atuais; o salvamento genérico do rascunho não aceita alterações nesses campos. Recibos idempotentes podem retornar estado corrente já mais recente após releitura, sem repetir efeitos do comando original.
