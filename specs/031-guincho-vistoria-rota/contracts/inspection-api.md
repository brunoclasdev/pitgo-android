# Contrato de vistoria, arquivos e retomada

Desenho complementar à [feature 030](../../030-guincho-vistoria-fotos/contracts/guincho-inspection-api.md). Catálogo e limites em [data-model.md](../data-model.md). Não substituir aprovação, confirmação de conclusão ou regras financeiras.

## Interfaces

| Interface | Entrada/saída e alteração |
| --- | --- |
| get_guincho_inspection_v2 | p_appointment_id; documento autorizado com categoria e metadados de miniatura |
| list_guincho_inspection_photos_v2 | Mesmos cursores/limites v1, com campos novos por foto |
| prepare_guincho_inspection_photo_v2 | Intenção imutável da captura, categoria e tamanho da miniatura; reserva paths gerados pelo servidor |
| confirm_guincho_inspection_photo_v2 | Mesmos argumentos de confirmação v1; confirma original e miniatura conforme modalidade da linha |
| submit_guincho_inspection | Preservado, com contagem explícita dos quatro pontos e validação dos assets exigidos |
| decide_guincho_inspection / begin_guincho_inspection_correction | Preservados; cópia do manifesto inclui detail_point |
| discard_guincho_inspection_photo / unselect_guincho_inspection_photo | Preservados, conscientes da categoria e guards da versão |

Todas as interfaces de participante exigem sessão válida e autorização existente para aquele papel/atendimento. Tabelas permanecem sem escrita direta por authenticated/anon. Nome v2 evita overload ambíguo de PostgREST.

## Preparação v2

Argumentos obrigatórios: `p_appointment_id uuid`, `p_expected_revision bigint`, `p_operation_id uuid`, `p_version_id uuid`, `p_point text`, `p_captured_at timestamptz`, `p_byte_size bigint`, `p_thumbnail_byte_size bigint`. Argumentos opcionais: `p_caption text`, `p_replaces_photo_id uuid`, `p_detail_point text`.

- p_point continua no enum legado. Categoria opcional nomeada exige p_point=DETAIL e código válido; principais e detalhe livre usam categoria nula.
- Original: 1..5242880 bytes; thumb: 1..262144 bytes; legenda e captura seguem os limites vigentes. Os derivados foram concluídos e congelados antes desta chamada.
- Servidor gera photo_id e paths, registra autoria e versão, mas não remove a seleção anterior na reserva.
- Reserva nova exige versão atual DRAFT e condições vigentes de editabilidade. Repetição autorizada da mesma operação reconhece a reserva existente antes de revisão/editabilidade, inclusive READY após avanço do atendimento.
- A comparação de replay inclui versão, ponto/categoria, legenda normalizada, captured_at, tamanhos e foto substituída. Alteração desses campos com o mesmo operation_id é OPERATION_CONFLICT; expected_revision pode avançar em retry após novo snapshot.
- Uma reserva ativa por ponto nomeado e uma substituição ativa para a mesma foto. Não deduzir unicidade de todos os DETAIL, pois detalhes livres são múltiplos.

Envelope mantém `outcome=APPLIED | EXISTING | REVISION_CONFLICT`, snapshot/inspection/server_now e photo quando aplicável. Foto v2 inclui `detail_point`, `thumbnail_object_path`, `thumbnail_byte_size`; restante da identidade/metadados permanece.

## Upload e confirmação

Bucket `guincho-inspections` privado. Originais em `{appointment_id}/{photo_id}.jpg`; miniaturas em `{appointment_id}/{photo_id}.thumb.jpg`. Somente JPEG, sem upsert, UPDATE ou DELETE por participantes.

1. Persistir resultado da reserva no journal antes do upload.
2. Enviar original e miniatura, preservando os bytes congelados.
3. Chamar confirmação com `p_appointment_id`, `p_expected_revision`, `p_operation_id` de confirmação durável e `p_photo_id`.
4. Sob locks vigentes, verificar proprietário, MIME e tamanho de cada objeto exigido; marcar READY e alterar seleção do manifesto atomicamente, incluindo categoria.
5. Só depois da resposta confirmada ou reconciliação equivalente marcar SAVED no journal e limpar os arquivos locais dispensáveis.

A foto anterior permanece selecionada até o passo 4. Original sem thumb de captura v2 permanece RESERVED. Repetição da mesma confirmação reconhece READY; outra operação para o mesmo resultado mantém a semântica de conflito existente.

Conflito de Storage é classificado por código estruturado de objeto existente, admitindo HTTP 400/409; não transformar todo 400 em sucesso. Quando a resposta do upload se perdeu, reconciliar reserva e objetos. Se for necessário conferir bytes de objeto já existente, baixar de modo autenticado e comparar SHA-256 com o payload local; divergência não pode ser sobrescrita ou confirmada silenciosamente. Hash local não constitui checksum remoto validado nem prova pericial.

## Reserva salva antes da perda de confirmação

Ao reabrir, repetir preparação com a operação original ou consultar a foto autorizada por identidade conhecida. READY remoto resolve a pendência mesmo sem arquivo local e mesmo quando a versão já não é editável. Reconhecimento é leitura de resultado existente, não licença para nova escrita em atendimento encerrado. Se ainda RESERVED, verificar contexto atual antes de prosseguir; manter falha individual quando não puder editar ou recuperar arquivo.

Em REVISION_CONFLICT, aplicar snapshot mais novo, manter IDs/payload e repetir apenas se contexto permitir. Serializar mutações por atendimento. Não iniciar uma correção automática nem anexar captura de versão antiga à nova versão.

## Leitura e Storage

- Documento v2 mantém campos atuais, inclusive required_photos contendo apenas principais, pending_photos, current_version e resumo com required_points_ready 0..4. Adiciona named_photos, limitado pelos índices a 17 pontos nomeados selecionados, para que o álbum do guincheiro identifique opcionais salvas mesmo após a primeira página de detalhes livres. O conjunto completo continua na listagem paginada; a projeção v1 omite named_photos e os metadados novos, inclusive na resposta de preparação.
- Listagem conserva ordenação `(photo_order,id)`, página padrão 20, máximo vigente 100; versões conservam padrão 10/máximo vigente 50. Cursores e nomes de campos seguem v1; não reiniciar carregamentos de IDs persistentes após mudança de revisão.
- Snapshot/documento autorizado é revalidado na abertura, retomada e perda de permissão; cada download continua sujeito a RLS. Não fazer um RPC de documento para cada miniatura.
- Helpers `private.inspection_object_read/insert` reconhecem exclusivamente os dois paths registrados na foto. INSERT exige autor, reserva ativa, versão DRAFT e editabilidade. Preservar policies permissivas e restritivas, inclusive no_update/no_delete e negações a anon; não autorizar somente por prefixo de pasta.
- Foto legada sem miniatura usa original com decode limitado; não gerar objetos retrospectivos em versões enviadas/aprovadas.
- Suporte continua autorizado pelo caso e auditado; se utilizar variante thumb, ampliar o autorizador existente para o path exato. Não alterar o dashboard como pré-requisito: o original e seu path permanecem compatíveis.

## Envio e aprovação

Submissão exige principais explícitos FRONT/REAR/LEFT/RIGHT, todos READY, todos os assets exigidos presentes e nenhuma reserva ativa não abandonada. No app, qualquer pendência local incluída também bloqueia. Opcional não iniciada não bloqueia; opcional com falha precisa de recuperação ou retirada explícita. Os gates também se aplicam aos chamadores v1, para impedir confirmação ou submissão parcial de uma captura v2.

Cliente consulta o conjunto enviado imutável. Originais devem ser apresentados/validados com sucesso para liberar aprovação; miniaturas aceleram o álbum, mas não substituem esse gate. Pré-carga limitada de originais é interrompível para dar prioridade à imagem aberta pelo usuário. Falha em qualquer imagem incluída mantém aprovação bloqueada, sem inutilizar as demais. Decisão válida retorna à mesma transação; início continua ação posterior do guincheiro.

## Compatibilidade v1

- Não enviar códigos novos em point. Projeções v1 mantêm enum/shape antigos; opcionais nomeadas aparecem como DETAIL. As v2 incluem a categoria e a miniatura.
- Reserva v1 continua original-only; v2 exige ambos, detectado pelos campos da própria linha. Confirm v1 deve respeitar exigência dos assets da linha v2.
- Ao substituir por v1 uma foto DETAIL já nomeada, conservar a categoria do replaces_photo_id na nova reserva; não degradar silenciosamente para detalhe livre. Nova inclusão livre v1 permanece com categoria nula.
- Atualizar helpers/guards/cópia de manifesto para campos novos; não afrouxar bloqueios de versões fechadas nem atualizar linhas READY.
- Sem fallback de escrita v2 para v1 no app novo: backend sem capacidade exige atualização de serviço com erro recuperável, evitando perder categoria ou thumb. Reconciliar uma reserva v1 já existente pelo contrato original é compatibilidade, não fallback para criar capturas novas; preservar sua modalidade e bytes. Leitura de fotos legadas continua prevista.

## Falhas e observação

Preservar erros SESSION/FORBIDDEN/CLOSED/CONFLICT/INCOMPLETE/IMAGE/OFFLINE/TIMEOUT. Adicionar diferenciação de ausência de arquivo local e asset remoto incompleto sem expor dados técnicos na UI. Poll de documento existente continua a cada 2 s enquanto tela visível, com uma consulta em voo e merge monotônico de revisões; não bloquear o poll pelo upload global. Sinais existentes podem acordar consulta, mas não alegar um broadcast dedicado de vistoria que ainda não existe.

Registrar apenas etapa, variante, duração, quantidade e categoria de erro em métricas proporcionais, sem bytes, caminhos privados, tokens, nomes, placas ou coordenadas. Medições do quickstart comprovam desempenho; o contrato não afirma que já foram executadas.
