# Contract: Vistoria do Guincho — RPCs, Storage e suporte

**Status**: interfaces propostas para implementação; endpoints novos ainda não existem.
**Base**: Supabase autenticado, contratos existentes de Guincho e [data-model.md](../data-model.md).

## Convenções

Todos os RPCs públicos negam anon e exigem `auth.uid()`. Escritas são SECURITY DEFINER com search_path vazio, sem grants diretos de INSERT/UPDATE/DELETE das tabelas ao app. Autorizar participante por atendimento e papel; IDs do usuário, veículo, caminho do objeto e datas do servidor não são autoridade do payload.

Toda mutação abaixo, exceto abertura idempotente de correção, recebe `p_appointment_id uuid`, `p_expected_revision bigint`, `p_operation_id uuid`. Revisão é a do atendimento. O ID da operação permanece o mesmo em retry da mesma intenção, inclusive quando a resposta se perde. Retorno comum: `{outcome, snapshot, inspection, server_now}`, com `outcome` APPLIED, EXISTING ou REVISION_CONFLICT. Conflito não é sucesso; atualizar o estado e solicitar nova ação quando necessário. IDs e campos específicos são listados na tabela.

Inspeção resumida no snapshot:

```json
{
  "inspection": {
    "required": true,
    "state": "SUBMITTED",
    "current_version_id": "uuid",
    "current_version_number": 1,
    "required_points_ready": 4,
    "photo_count": 4,
    "approved_at": null,
    "correction_reason": null,
    "allowed_actions": ["OPEN_INSPECTION", "APPROVE", "REQUEST_CORRECTION"]
  }
}
```

`inspection` é campo aditivo nos snapshots compartilhados. Antes da chegada: required=false, state=null; ARRIVE atualiza a obrigatoriedade. Legado já chegado: required=false e state=null. O backend novo sempre emite o resumo para GUINCHO_DIRECT. Ausência inesperada/erro de parse no app novo é falha de contrato para liberação do novo fluxo, não uma dispensa presumida. Snapshot de demais modalidades pode continuar sem o campo.

## Leitura e paginação

| RPC | Entrada | Saída e autorização |
|---|---|---|
| `get_guincho_inspection` | `p_appointment_id uuid` | Participantes; snapshot, identidade congelada do veículo, resumo, versão vigente, decisão, contagens, reservas pendentes e required_photos com os quatro pontos selecionados independentemente da paginação; sem URLs ou bytes |
| `list_guincho_inspection_versions` | appointment_id, `p_before_version integer` opcional, `p_limit integer=10` | Versões em ordem decrescente; clamp 1..50; keyset por número da versão |
| `list_guincho_inspection_photos` | appointment_id, `p_version_id uuid` opcional, `p_after_order bigint` e `p_after_id uuid` opcionais, `p_limit integer=20` | Manifesto daquela versão somente com associações selected=true e fotos READY, ponto, descrição, autoria, captured_at, saved_at, caminho privado e ordenação; clamp 1..100 |

Na listagem de fotos, version_id null consulta todas as fotos READY do pedido, incluindo capturas substituídas/não enviadas; expõe associação/indicador de envio e ordenação estável própria. Cursor deve conter a chave completa da ordenação escolhida, sem usar apenas timestamp. Para o modo “todas”, usar `photo_order` persistido da reserva como `display_order` estável, mais id, evitando mistura de cursores com versão. Respostas incluem `has_more` e próximo cursor. O cliente não pode consultar manifesto de outro pedido passando version_id alheio.

Atualizar `private.guincho_snapshot`, preservando projeção financeira por papel, privacidade antes do pagamento e tracking autorizado. `get_guincho_appointment_snapshot`, `get_pittec_appointment_snapshot`, consultas ativas e `list_my_guincho_appointments` devem receber o mesmo resumo pelo caminho já existente. Detalhes de fotos não entram no polling do mapa.

## Mutação da vistoria

| RPC | Campos específicos | Comportamento |
|---|---|---|
| `prepare_guincho_inspection_photo` | `p_version_id uuid`, `p_point text`, `p_caption text?`, `p_captured_at timestamptz`, `p_byte_size bigint`, `p_replaces_photo_id uuid?` | Guincheiro; DRAFT vigente; cria/resgata RESERVED e retorna photo_id/object_path fixos, limite e content_type JPEG. Não conta como foto salva |
| `confirm_guincho_inspection_photo` | `p_photo_id uuid` | Guincheiro; verifica objeto reservado existente, proprietário, tamanho e tipo; READY, saved_at e seleção do rascunho atômicos. Retake troca seleção somente depois da confirmação; foto anterior permanece salva |
| `submit_guincho_inspection` | `p_version_id uuid` | Guincheiro; DRAFT vigente; exige quatro pontos distintos READY, todos os detalhes selecionados READY e nenhuma reserva RESERVED não abandonada desse rascunho; fixa manifesto selected=true e SUBMITTED |
| `decide_guincho_inspection` | `p_version_id uuid`, `p_decision text`, `p_reason text?` | Cliente; somente SUBMITTED vigente. APPROVE ou REQUEST_CORRECTION; motivo aparado 1..1000 na correção. Registra uma decisão; APPROVE mantém ACCEPTED |
| `begin_guincho_inspection_correction` | `p_appointment_id uuid`, `p_expected_revision bigint`, `p_parent_version_id uuid` | Guincheiro; pai vigente CORRECTION_REQUESTED; cria uma sucessora DRAFT e copia referências. Retry retorna essa sucessora sem criar outra; não exige operation_id separado |

Uma reserva que o usuário decidiu descartar precisa de `discard_guincho_inspection_photo(p_appointment_id,p_expected_revision,p_operation_id,p_photo_id)`: somente RESERVED originada no DRAFT vigente, sem criar aprovação; marcar `abandoned_at` e `discard_operation_id` na reserva, impedir upload/confirm posterior e liberar o envio das fotos escolhidas. Não apagar fotos READY nem objetos confirmados. Um detalhe READY já confirmado pode ficar fora do rascunho por `unselect_guincho_inspection_photo` com os mesmos parâmetros: só DRAFT, registra `unselect_operation_id` na associação em vez de apagar o registro, preserva histórico e pode deixar ponto obrigatório pendente. Envio considera somente associações selecionadas. Cada seleção deve ter `selected boolean default true`; copiar correção inclui só selecionadas. Esta marcação é imutável após envio.

Se outra reserva de substituição do mesmo ponto ainda estiver ativa, não aceitar uma nova intenção ambígua: exigir concluir ou descartar a anterior. Em retry após READY/unselect, retornar EXISTING com estado vigente; não reativar seleção antiga. Fotos READY podem ser apresentadas como não enviadas no histórico.

Toda substituição valida replaces_photo_id como associação READY atualmente selecionada do mesmo ponto, pedido e rascunho, tanto na reserva quanto na confirmação. Mudança concorrente do alvo retorna conflito; não desmarcar imagem diferente. Operações unselect têm chave única por atendimento e comparam versão/foto no retry. Fotos desmarcadas ficam fora do manifesto enviado/aprovado e aparecem na consulta geral como “Não enviada” quando não integrarem outra versão enviada.

## Transporte e compatibilidade

Preservar assinaturas existentes:

- `arrive_guincho_appointment(uuid,bigint,uuid)`: primeira chegada atual registra vistoria obrigatória e DRAFT, conserva ACCEPTED. Repetição retorna EXISTING sem retroatividade.
- `start_guincho_transport(uuid,bigint,uuid)`: guincheiro responsável; se required=true, exige versão vigente APPROVED e chegada registrada, então define IN_PROGRESS/service_started_at. Se required=false, mantém caminho legado válido.
- `request_guincho_completion(uuid,bigint,uuid)`: somente IN_PROGRESS, com início registrado; required=true também exige aprovação preservada.

Enforcement central em `private.guincho_provider_transition`; manter guardas em `start_mobile_appointment` e `complete_mobile_appointment`. Chamadas antigas não podem dispensar vistoria. Cliente em versão antiga pode precisar atualizar o app para revisar; silêncio não autoriza início. Preservar `coalesce(payment_amount,customer_total_amount)`, quote_version, ownership e `private.payment_can_release`.

Ações gerais do snapshot não expõem START antes da aprovação nem COMPLETE antes de IN_PROGRESS. Ações da inspeção dependem do ator, etapa e elegibilidade do pedido. Qualquer estado terminal devolve ações de escrita vazias; consultar histórico continua permitido. Cancelamento e prazo existente não alteram o registro de aprovação.

## Storage

- Bucket privado `guincho-inspections`, limite 5.242.880 bytes, MIME `image/jpeg`.
- Upload autenticado via Storage SDK, `upsert=false`, caminho exatamente retornado pela reserva. Não aceitar upload por qualquer subpasta do usuário nem somente por prefixo do pedido.
- INSERT exige reservation RESERVED não abandonada, guincheiro responsável e pedido/versão elegíveis. UPDATE/DELETE proibidos para participantes; revisar qualquer policy ampla já instalada.
- Download autenticado para os participantes, inclusive histórico. Nunca gravar chave administrativa, JWT, bytes ou URLs autenticadas nos logs.
- Se upload retorna “objeto já existe” após resposta perdida, chamar confirm no mesmo ID; não usar upsert. Se confirm falha por falta do objeto, repetir upload ou descartar a reserva. Falha de tipo/tamanho mantém o ponto pendente e exige nova captura/reserva.
- Cancelamento concorrente invalida a confirmação; um blob que ficou sem READY não conta como registro salvo. Não remover fotos confirmadas no encerramento.

## Suporte no dashboard existente

1. `dashboard_get_guincho_inspection(p_dispute_id uuid)` VOLATILE: exige MANAGER+ e contestação existente, devolve identidade/resumo da vistoria e registra `GUINCHO_INSPECTION_VIEW` via `record_dashboard_action`. `dashboard_list_guincho_inspection_versions(p_dispute_id,p_before_version,p_limit)` e `dashboard_list_guincho_inspection_photos(p_dispute_id,p_version_id,p_after_order,p_after_id,p_limit)` usam a mesma paginação dos participantes, verificam novamente a permissão e auditam o caso. Nunca reutilizar RPC participante com privilégio administrativo indiscriminado.
2. `dashboard_authorize_guincho_inspection_photo(p_dispute_id uuid,p_photo_id uuid)` VOLATILE: valida novamente a sessão, contestação e foto READY pertencente ao atendimento; registra `GUINCHO_INSPECTION_PHOTO_VIEW` e retorna exclusivamente bucket/path/tipo de objeto autorizado.
3. Rota proposta do dashboard: `GET /api/contestacoes/{disputeId}/fotos/{photoId}`. Validar UUIDs e sessão server-side; chamar autorização usando cliente SSR do funcionário; somente após sucesso usar cliente administrativo para baixar aquele objeto. Retornar imagem com Cache-Control private, no-store e X-Content-Type-Options nosniff. Sem caminho livre, redirecionamento externo ou URL assinada no navegador. Falha de auditoria impede o download.
4. Galeria somente leitura no detalhe de contestação, sem alterar formulário de resolução ou liberar atendimento. Se a contestação estiver resolvida, manter consulta conforme permissão existente do suporte. Não oferecer suporte geral a pedidos sem contestação nesta feature.

Não tornar o RPC STABLE existente de contestação responsável por inserções de auditoria. Documentar a ampliação do uso do cliente administrativo server-side; nenhuma credencial nova no app.

## Falhas de domínio

| Código | Resultado esperado no app |
|---|---|
| `SESSION_REQUIRED`, `PARTICIPANT_REQUIRED`, `PROVIDER_REQUIRED`, `CLIENT_REQUIRED`, `STAFF_REQUIRED` | Encerrar acesso indevido ou recuperar sessão, sem detalhes internos |
| `REVISION_CONFLICT`, `VERSION_CONFLICT`, `OPERATION_CONFLICT` | Recarregar conjunto e explicar que o atendimento mudou; não repetir decisão em outra versão |
| `INSPECTION_REQUIRED`, `INSPECTION_NOT_APPROVED` | Manter bloqueio e abrir etapa pendente |
| `PHOTO_NOT_SAVED`, `REQUIRED_POINTS_MISSING`, `UPLOAD_PENDING` | Indicar pontos/fotos pendentes; corrigir antes de enviar |
| `INVALID_IMAGE`, `FILE_TOO_LARGE`, `REASON_REQUIRED` | Corrigir captura ou motivo sem perder fotos salvas |
| `PAYMENT_REQUIRED`, `APPOINTMENT_CLOSED`, `INVALID_TRANSITION` | Recuperar snapshot; manter somente ações válidas |
| Rede, timeout, 5xx, contrato inválido | Feedback recuperável sem confirmação falsa |
