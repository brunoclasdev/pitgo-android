# Data Model: Vistoria fotográfica do Guincho

**Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

Desenho proposto, ainda não implementado. O agregado lógico “vistoria” usa o atendimento existente, três tabelas novas e um bucket privado. Tipos de estados devem ter restrições explícitas no banco e equivalentes Kotlin.

## Atendimento existente — `public.appointments`

| Campo | Regra |
|---|---|
| `id`, `dispatch_search_id`, `provider_id`, `client_id` | Identidade e participantes existentes; prestador autenticado por `providers.account_id` |
| `guincho_trip_snapshot` | Fonte congelada do veículo e dados contratados; `vehicle.id` identifica o veículo |
| `arrived_at`, `service_started_at`, `status`, `revision` | Mantidos como fonte de verdade da execução |
| `guincho_inspection_required` | Novo boolean NOT NULL default false, controlado exclusivamente pelo backend |

Na primeira ARRIVE após ativação, gravar `guincho_inspection_required=true`, `arrived_at` e versão 1 DRAFT na mesma transação, mantendo `status=ACCEPTED` e `service_started_at=null`. ARRIVE repetido retorna o estado existente e não modifica a regra. Pedidos já chegados antes da ativação permanecem false. Não criar versão para PitTec, atendimento fixo ou Guincho legado já chegado.

Uma vistoria só é editável quando `guincho_inspection_required=true`, `status=ACCEPTED`, chegada registrada, transporte não iniciado e pagamento válido. Encerramento torna todos os rascunhos somente leitura sem reescrever a decisão do cliente.

## `public.guincho_inspection_versions`

| Campo | Tipo/conteúdo |
|---|---|
| `id` | UUID gerado no servidor |
| `appointment_id` | FK para atendimento, sem cascata que apague evidência |
| `version_number` | Inteiro positivo, crescente por atendimento |
| `parent_version_id` | Versão que recebeu solicitação de correção; null na primeira |
| `state` | `DRAFT`, `SUBMITTED`, `CORRECTION_REQUESTED`, `APPROVED` |
| `created_by`, `created_at` | Conta do guincheiro e momento do servidor |
| `submitted_at`, `submission_operation_id` | Preenchidos juntos no envio |
| `decided_by`, `decided_by_name`, `decided_at`, `decision_operation_id` | Cliente responsável, nome congelado para apresentação e momento do servidor; null antes da decisão |
| `correction_reason` | Texto aparado de 1 a 1000 caracteres somente para correção |

Regras:

- UNIQUE `(appointment_id,version_number)` e `(id,appointment_id)` para FKs compostas que impeçam misturar pedidos.
- No máximo uma versão DRAFT ou SUBMITTED por atendimento; a criação de correção é serializada pelo lock do atendimento e UNIQUE `parent_version_id`.
- Versão vigente = maior `version_number`; o estado nunca é escolhido pelo cliente.
- `DRAFT` não tem envio/decisão; `SUBMITTED` tem envio e nenhuma decisão; APPROVED/CORRECTION_REQUESTED têm envio e uma decisão válida. Somente CORRECTION_REQUESTED tem motivo obrigatório.
- APPROVED é final para a vistoria; não criar novas versões depois dela. A versão corrigida conserva seu estado CORRECTION_REQUESTED ao criar a sucessora.
- Proibir escritas diretas dos participantes. Imutabilidade de envio/decisão é aplicada nos RPCs e por guardas de integridade; não basta ocultar botões.
- Índice `(appointment_id,version_number DESC)` atende consulta atual e histórico paginado. Índices únicos parciais `(appointment_id,submission_operation_id)` e `(appointment_id,decision_operation_id)` garantem unicidade de cada intenção quando preenchida.

## `public.guincho_inspection_photos`

| Campo | Tipo/conteúdo |
|---|---|
| `id` | UUID atribuído pelo servidor à reserva de upload |
| `appointment_id` | FK para o mesmo atendimento |
| `reserved_for_version_id` | Rascunho que originou a captura, usado na permissão de upload |
| `point` | `FRONT`, `REAR`, `LEFT`, `RIGHT`, `DETAIL` |
| `caption` | Texto opcional, aparado, até 1000 caracteres |
| `captured_by`, `captured_by_name` | Conta autenticada e nome do guincheiro congelado na reserva, atribuídos pelo servidor; não recebidos como autoridade no payload |
| `captured_at` | Data/hora UTC informada pelo aparelho ao terminar a captura |
| `reserved_at`, `saved_at`, `abandoned_at` | Datas/horas do servidor; saved_at após confirmação; abandoned_at somente para reserva descartada |
| `photo_order` | Bigint identity atribuído pelo servidor para ordenar todas as capturas e paginar seu histórico |
| `state` | `RESERVED` ou `READY` |
| `object_path` | UNIQUE `<appointment_id>/<id>.jpg`, atribuído pelo servidor |
| `content_type`, `byte_size` | `image/jpeg`, de 1 a 5.242.880 bytes; conferidos com o objeto |
| `reserve_operation_id`, `confirm_operation_id`, `discard_operation_id` | Chaves duráveis para repetição das etapas |
| `replaces_photo_id` | Foto selecionada anteriormente que esta captura substituirá no rascunho, quando aplicável |

Regras:

- UNIQUE `(appointment_id,reserve_operation_id)` e índices únicos parciais `(appointment_id,confirm_operation_id)` e `(appointment_id,discard_operation_id)`; reusar chave na mesma operação com outro alvo/payload retorna conflito. Na reserva, comparar também ponto, rascunho, descrição e metadados.
- READY exige objeto existente, pertencente à reserva, tipo/tamanho compatíveis e saved_at. Sem acesso administrativo no app.
- Após READY, imagem e metadados descritivos ficam imutáveis. Refazer significa criar outra foto e atualizar apenas a seleção do rascunho.
- `replaces_photo_id`, quando preenchido, deve ser foto READY selecionada no mesmo rascunho, pedido e ponto. Verificar na reserva e novamente na confirmação; substituição desatualizada retorna conflito sem desmarcar outra foto.
- Captura não prova tecnicamente a veracidade da cena nem a precisão do relógio do aparelho; exibir também o momento de salvamento no servidor. Não prometer validação automática do conteúdo ou assinatura pericial.
- Fotos READY conservam vínculo e acesso pelo pedido mesmo quando substituídas antes do envio. A consulta as identifica como “Não enviada” quando não aparecem em versão enviada.
- Reservas não confirmadas não contam no progresso nem são anunciadas como fotos salvas. Um upload sem confirmação após cancelamento não pode virar READY. Descartar uma reserva grava abandoned_at e impede upload/confirm posterior; repetir descarte não cria nova alteração. Envio exige concluir ou descartar toda reserva ativa do rascunho.
- Índices por `(appointment_id,saved_at,id)` e `(reserved_for_version_id,state)`; leitura paginada.

## `public.guincho_inspection_version_photos`

| Campo | Regra |
|---|---|
| `appointment_id` | Permite FKs compostas garantindo versão e foto do mesmo pedido |
| `version_id`, `photo_id` | PK composta; somente fotos READY |
| `point` | Mesmo ponto imutável da foto; não aceitar mudança para completar artificialmente outro ponto |
| `display_order` | Bigint com ordenação estável dos detalhes e associação dentro da versão |
| `selected`, `unselect_operation_id` | Seleção do rascunho; desmarcar é idempotente, sem apagar foto ou associação |

O conjunto enviado contém exatamente uma foto selecionada por ponto obrigatório e zero ou mais DETAIL. Índice único parcial `(version_id,point)` para FRONT/REAR/LEFT/RIGHT com selected=true; detalhes podem se repetir. Uma captura substituta só troca a associação DRAFT depois de sua confirmação, preservando a seleção anterior se o upload falhar. Uma nova versão copia somente as associações selecionadas da anterior e pode trocar/adicionar fotos; não copia objetos. Desmarcar um detalhe ou ponto só altera selected enquanto DRAFT; se for ponto obrigatório, o envio fica bloqueado até outra foto selecionada ser confirmada.

Depois de SUBMITTED, associações não podem ser inseridas, alteradas ou apagadas. O manifesto enviado é o conjunto selected=true fixado no envio; as desmarcações anteriores continuam preservadas como histórico de rascunho. Toda seleção tem FK da foto `(photo_id,appointment_id)` e versão `(version_id,appointment_id)`; confirmação de ponto e estado READY permanece responsabilidade do RPC/guarda. Índice único parcial `(appointment_id,unselect_operation_id)` impede reaplicar a intenção em outro alvo; retries comparam versão e foto.

## Estados e transições

| Origem | Ação e ator | Destino | Efeito no atendimento |
|---|---|---|---|
| Guincheiro a caminho | ARRIVE do responsável | Versão 1 DRAFT / “Fotos pendentes” | Registra chegada; mantém ACCEPTED |
| DRAFT | Reservar, capturar, confirmar/refazer | DRAFT | Salva foto/progresso, sem liberar início |
| DRAFT completo | SUBMIT do guincheiro | SUBMITTED / “Aguardando aprovação das fotos” | Fixa conjunto; mantém ACCEPTED |
| SUBMITTED vigente | CORRECTION do cliente | CORRECTION_REQUESTED / “Correção solicitada” | Registra motivo; mantém bloqueio |
| CORRECTION_REQUESTED | BEGIN_CORRECTION do guincheiro | Nova versão DRAFT | Copia seleção anterior; mostra motivo |
| SUBMITTED vigente | APPROVE do cliente | APPROVED / “Fotos aprovadas” | Libera START; mantém ACCEPTED |
| APPROVED e ACCEPTED | START explícito do guincheiro | APPROVED | IN_PROGRESS, service_started_at registrado |
| IN_PROGRESS | COMPLETE do guincheiro | Vistoria preservada | Confirmação final bilateral existente |
| Qualquer etapa editável | Cancelamento existente, inclusive por prazo | Etapa preservada, ações vazias | Pedido CANCELLED, material somente leitura |

Legados sem obrigatoriedade continuam pelo caminho anterior e exibem “Vistoria não registrada” quando não houver fotos. O resumo diferencia explicitamente ausência de obrigatoriedade de falha/ausência de campo: um app novo não libera início de Guincho cuja informação obrigatória não pôde ser verificada.

## Revisões e idempotência

- Usar `appointments.revision` como revisão autoritativa da vistoria; não manter outro contador concorrente. Incrementar também `service_searches.revision` nas mutações efetivas para observadores existentes.
- Ordem de locks em mutações: busca, atendimento, versão, foto. Upload do blob ocorre fora dessa transação; confirmação repete as verificações.
- Reserva tem reserve_operation_id; confirmação tem confirm_operation_id; envio e decisão têm chaves próprias na versão. BEGIN_CORRECTION é idempotente por parent_version_id. ARRIVE/START existentes são idempotentes por timestamps/estado.
- Retry reconhecido retorna EXISTING sem reaplicar alteração ou reativar associação já substituída. Chave reutilizada com payload diferente retorna OPERATION_CONFLICT. Outra ação com revisão antiga retorna REVISION_CONFLICT e snapshot atual.
- Decisão exige version_id vigente e SUBMITTED. Uma versão aprovada não admite correção posterior; correção e aprovação concorrentes não vencem juntas.

## Armazenamento e acesso

Bucket privado `guincho-inspections`, JPEG e limite de 5 MiB. INSERT somente pelo guincheiro, no caminho da reserva, enquanto pedido/rascunho permitirem; zero UPDATE/DELETE para anon/authenticated. SELECT aos participantes autorizados para fotos vinculadas ao pedido, inclusive terminal. Verificar políticas preexistentes permissivas: adicionar uma policy restrita não neutraliza outra ampla.

Não alterar diretamente `storage.objects` para criar/apagar arquivos. Seu uso no RPC é de consulta/checagem; o blob é enviado pela API de Storage. Download autenticado no app; arquivos temporários em `cacheDir/guincho-inspections/<environment_fingerprint_hash>/<account_id>/`, sem galeria/backup. Cache e intenção de captura identificados por fingerprint de ambiente, conta e foto, invalidados no logout e no callback existente de troca de ambiente; não servir cache de outra sessão ou backend.

Suporte não ganha RLS geral sobre imagens: manifesto e autorização individual passam por RPC auditado, restrito à contestação e MANAGER+, seguido de download por proxy server-side do dashboard. Referências a funcionário usam auth.users/auditoria existente, pois funcionário pode não ter perfil móvel.

Não expirar fotos READY nesta entrega; preservar junto ao histórico. Não adicionar job de remoção especulativo. Exclusão administrativa e política futura de conservação não são capacidades dos participantes e não integram este fluxo.
