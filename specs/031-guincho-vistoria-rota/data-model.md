# Modelo de dados — Vistoria ampliada e percurso

Referências: [spec](spec.md), [pesquisa](research.md), [fotos](contracts/inspection-api.md), [rota](contracts/guincho-route-api.md). As estruturas abaixo são desenho; nenhuma alteração foi aplicada.

## 1. Catálogo e identidade do ponto

`point` permanece `FRONT | REAR | LEFT | RIGHT | DETAIL`. Nova propriedade `detail_point` anulável, presente apenas quando `point=DETAIL`. Os quatro códigos principais são obrigatórios e exclusivos por versão. Os opcionais nomeados também são exclusivos por versão; detalhes livres continuam múltiplos.

| Código efetivo | point | Nome | Obrigatório |
| --- | --- | --- | --- |
| FRONT | FRONT | Dianteira | Sim |
| REAR | REAR | Traseira | Sim |
| LEFT | LEFT | Lateral esquerda | Sim |
| RIGHT | RIGHT | Lateral direita | Sim |
| TRUNK_INTERIOR | DETAIL | Interior do porta-malas | Não |
| INSTRUMENT_PANEL | DETAIL | Painel de instrumentos | Não |
| FRONT_SEATS | DETAIL | Bancos dianteiros | Não |
| REAR_SEATS | DETAIL | Bancos traseiros | Não |
| FULL_DASHBOARD | DETAIL | Painel completo | Não |
| TOOL_KIT | DETAIL | Kit ferramenta | Não |
| SPARE_TIRE | DETAIL | Estepe | Não |
| FRONT_LEFT_TIRE | DETAIL | Pneu dianteiro esquerdo | Não |
| FRONT_RIGHT_TIRE | DETAIL | Pneu dianteiro direito | Não |
| REAR_RIGHT_TIRE | DETAIL | Pneu traseiro direito | Não |
| REAR_LEFT_TIRE | DETAIL | Pneu traseiro esquerdo | Não |
| ENGINE | DETAIL | Motor | Não |
| BATTERY | DETAIL | Bateria | Não |

`DETAIL` com categoria nula não é um 18º ponto obrigatório: é a coleção existente de fotos livres. Para pontos nomeados, chave efetiva `coalesce(detail_point, point)`; para detalhe livre, identidade individual da foto. Contagem obrigatória usa somente `FRONT, REAR, LEFT, RIGHT`, nunca `point != DETAIL` como regra implícita de novo catálogo.

## 2. Tabelas existentes de vistoria

### guincho_inspection_photos

Preservar identidade, autoria, captura, original, operações idempotentes, substituição, ordem e estados existentes. Adições anuláveis:

| Campo novo | Tipo | Validação |
| --- | --- | --- |
| detail_point | text | Nulo ou código opcional do catálogo com point=DETAIL |
| thumbnail_object_path | text | Nulo em legado; em v2 é caminho exato gerado pelo servidor |
| thumbnail_byte_size | bigint | Nulo junto com path; caso contrário 1..262144 |

Original continua `{appointment_id}/{photo_id}.jpg`, JPEG de 1..5242880 bytes. Miniatura v2: `{appointment_id}/{photo_id}.thumb.jpg`, JPEG com lado maior até 480 px. Novo fluxo exige ambos para `READY`; captura v1 continua exigindo somente original. Não adicionar checksum remoto nesta feature: os hashes locais identificam o payload da fila e não são certificação do conteúdo.

Invariantes: uma reserva ativa por versão/ponto nomeado; substituição explícita da foto vigente; originais/miniaturas são imutáveis; metadados novos entram nos guards de imutabilidade. Estados persistidos continuam `RESERVED → READY`, com abandono explícito da reserva. Um original enviado sem miniatura não promove captura v2 para `READY`.

### guincho_inspection_version_photos

Adicionar `detail_point` anulável, igual ao da foto referenciada. O guard valida atendimento, ponto e categoria, comparando categorias anuláveis sem confundir nulo com divergência; `selected=true` admite uma foto por ponto nomeado. Detalhes livres não recebem essa unicidade. Correção copia categoria, seleção e referência da foto, sem copiar bytes ou alterar a versão anterior.

### guincho_inspection_versions

Sem novos estados ou tabela de aprovação. Permanecem `DRAFT → SUBMITTED → APPROVED | CORRECTION_REQUESTED`; correção abre nova versão. Submissão exige quatro principais `READY` e nenhuma reserva ativa não abandonada, inclusive opcional/substituição. Uma captura apenas local também bloqueia o envio no aparelho que a detém até ser resolvida.

Manter os índices das quatro principais e adicionar unicidade de reserva/seleção para categorias opcionais nomeadas; a chave efetiva é equivalente ao conjunto dessas restrições. Não executar backfill de imagem, recategorização por legenda ou atualização de linhas READY/versões enviadas.

## 3. Journal durável por captura

Local: `filesDir/guincho-inspections/pending/{environment_hash}/{account_id}/{appointment_id}/{capture_id}/`. Arquivos: `journal.json`, `raw.jpg`, `normalized.jpg`, `thumbnail.jpg` e temporários irmãos. Cada journal usa `AtomicFile`; Mutex e geração protegem commits/limpeza.

| Grupo | Campos |
| --- | --- |
| Identidade | schema_version=1, capture_id UUID, ambiente, conta, appointment_id, version_id |
| Intenção | point, detail_point, caption, replaces_photo_id, captured_at congelado após callback |
| Operações | reserve_operation_id, confirm_operation_id e discard_operation_id UUIDs estáveis |
| Artefatos | asset_mode (LEGACY_ORIGINAL ou ORIGINAL_WITH_THUMBNAIL), caminhos relativos internos, tamanhos, SHA-256 local dos derivados, orientação normalizada |
| Reconciliação | remote_photo_id/paths, etapa, última falha classificada, tentativa/retentativa |

Estados locais:

```text
AWAITING_CAMERA → RAW_CAPTURED → PREPARING → PREPARED
  → RESERVING → UPLOADING_ORIGINAL → UPLOADING_THUMBNAIL
  → CONFIRMING → SAVED
```

Falhas transitórias levam a `RETRY_WAIT`, com etapa retomável; ausência/corrupção de arquivo leva a `NEEDS_RECAPTURE`; versão/atendimento incompatível leva a `BLOCKED_CONTEXT`. Cancelamento explícito leva a `DISCARDED` somente após resolver eventual reserva remota. Replays podem avançar diretamente a `SAVED` quando o servidor já confirmou a foto.

Regras de recuperação:

- Journal AWAITING_CAMERA é commitado antes de lançar a câmera. SavedStateHandle guarda apenas capture_id/estado pequeno; launcher é registrado em toda recriação. Existência de raw parcial, isoladamente, não significa captura aceita.
- Se a tarefa foi encerrada e nenhum resultado da câmera puder ser restaurado, AWAITING_CAMERA deve sair da espera para NEEDS_RECAPTURE, preservando o arquivo para recuperação explícita. Não aceitar a foto pela mera existência do raw nem relançar a câmera automaticamente.
- Callback aceito grava RAW_CAPTURED e captured_at uma única vez. UI volta ao álbum usando raw imutável, sem aguardar normalização/rede.
- Derivados são gravados em temporários, validados, sincronizados e renomeados; somente então o journal avança a PREPARED. Raw permanece até esse commit; ao retomar, reconciliar arquivos concluídos com journal atrasado.
- Após PREPARED/reserva, bytes e metadados não mudam em retry. READY remoto é reconhecido antes de exigir arquivo local ou nova editabilidade; ausência local não deve forçar recaptura de foto já salva.
- Ao fechar o app, preservar journal/arquivos. Raiz autenticada retoma a fila da conta inteira, mesmo fora do álbum; rede recuperada reativa automaticamente. Jobs podem ser pausados ao ficar oculto; não se exige envio com app encerrado.
- Geração e conta são conferidas antes/depois de suspensões e dentro do Mutex de commit. Logout explícito/troca de ambiente invalida imediatamente, cancela Jobs e limpa somente apresentação e caches de leitura em IO. Preservar journals e arquivos de capturas aceitas ainda não confirmadas, privados e separados por conta/ambiente, sem exibi-los ou enviá-los em outro contexto. Retomar somente com autenticação da mesma conta no mesmo ambiente e revalidação das permissões. Remover pendências somente após salvamento confirmado ou descarte explícito permitido; Jobs de geração anterior não podem recriar/alterar arquivos. Expiração temporária de sessão suspende envios até autenticação válida.
- Caso outra sessão envie/aprove a versão antes de uma captura local ser reservada, manter a imagem em BLOCKED_CONTEXT com explicação; não anexar à versão fechada nem criar correção/aprovação automaticamente.

Limites operacionais propostos: fila até 512 MiB e pelo menos 64 MiB livres antes de nova captura; downloads até 128 MiB. Atingir limite bloqueia nova captura com recuperação, nunca remove imagens aceitas ainda não salvas. Não há limite comercial novo de detalhes adicionais. Excluir a árvore privada de backup/transferência; FileProvider concede acesso somente à URI raw da captura solicitada.

## 4. Estado de apresentação e carregamento

Quadros são identificados por ponto efetivo; detalhes livres por photo_id/capture_id. Separar câmera ativa, operação de documento e estado de cada imagem. Chaves de leitura: ambiente/conta/photo_id/variante `THUMBNAIL | ORIGINAL`; prévia local usa capture_id e caminho imutável.

Documento v2 inclui named_photos com até 17 selecionadas nomeadas, mantendo required_photos restrito às quatro principais. Esse snapshot evita depender da primeira página de detalhes livres para preencher os quadros; o conjunto completo permanece paginado. Capturas locais são filtradas pela versão para bloquear edição/submissão: pendência de versão anterior fica preservada e identificada separadamente, sem integrar ou bloquear uma nova correção. Tempos locais usam capturedElapsedNanos/capturedBootCount opcionais; são métricas locais e não alteram a autoria/data enviada ao servidor nem permitem comparar reinicializações distintas.

Merge por IDs conserva arquivos, sucessos e tarefas de fotos que permanecem no conjunto. Downloads gravam `.part` e promovem por rename após validação; nunca publicar arquivo parcial. Miniaturas têm prioridade no álbum, e abertura de original pelo usuário preempta pré-carga. Cliente valida originais do conjunto com concorrência pequena antes de habilitar aprovação; sucesso de thumb não promove original para validado. Erro posterior invalida prontidão daquela foto.

## 5. Snapshot de percurso do Guincho

Nova `public.guincho_route_snapshots`, uma linha por `appointment_id`, FK com exclusão em cascata. Cache operacional, não histórico de localização ou financeiro. RLS habilitada, sem SELECT/DML direto por anon/authenticated; leitura filtrada por RPC e escrita somente service-role.

| Grupo | Campos |
| --- | --- |
| Identidade | appointment_id PK, route_version bigint crescente, destination_latitude/longitude do snapshot contratado |
| Rota confirmada | geometry GeoJSON LineString, distance_meters, generated_at, provider='osrm' |
| Origem da rota | origin_latitude/longitude, origin_accuracy_m, origin_captured_at, origin_published_at, provider_location_revision |
| Admissão de atualização | lease_id UUID, lease_expires_at, lease_context JSON com origem/destino/revisão congelados, last_attempt_at, retry_after_at, failure_count, last_failure_code |

Geometry/distance/origem/generated_at são todos nulos antes do primeiro sucesso, ou completos juntos. Geometry tem 2..10000 pares finitos `[longitude,latitude]`; distância 0..2000000 m. Lease inicial reserva trabalho sem apagar rota anterior. lease_context guarda separadamente a origem e o destino do cálculo pendente; claim não altera os campos da rota confirmada. Somente finish promove geometry/origem/distance/generated_at juntos. Nenhum campo de lease é exposto ao app.

Acesso/atualização exigem participante autorizado, GUINCHO_DIRECT, `status=IN_PROGRESS`, `service_started_at` confirmado e regras vigentes de pagamento/vistoria. Origem vem de provider_current_locations, com captured_at entre now()-30 s e now()+10 s, published_at recente em até 30 s e precisão >0..100 m; destino vem exclusivamente de guincho_trip_snapshot. Leitura de uma rota anterior autorizada continua possível quando falta fix atual, com indicação de indisponibilidade.

Claim/finish usam transações curtas e locks, sem HTTP dentro do banco. Lease de 10 s, mínimo de 5 s entre tentativas; falha preserva geometria anterior e aplica backoff 5/10/20/30 s. Commit verifica lease, estado atual, destino e ordenação da origem por `(published_at, version)`. A existência de um fix mais novo não invalida automaticamente uma rota calculada com origem ainda válida. Ao sair de IN_PROGRESS, apagar o cache/lease; não alterar o início, pagamento ou fotos.

## 6. Modelo Android de rota

`GuinchoRouteSnapshot`: atendimento, route_version, origem/revisão/publicação, destino, geometry compartilhada de core/routing, distância e geração. `GuinchoRouteState`: Empty, Loading(last), Ready(snapshot), Unavailable(reason,last,retryAt). Snapshot recebido precisa corresponder a atendimento, destino e sessão; responses de outra geração/versão de rota são descartadas.

Desvio: três fixes distintos, intervalo total >=4 s, idade <=10 s, precisão <=35 m e distância à polilinha maior que max(50 m, 2×precisão). Resetar série quando regressar ao corredor ou perder qualidade. Uma chamada em voo; posição mais recente fica pendente. Posição normal atualiza marcador sem solicitar nova geometria. Estados de GPS e percurso são independentes: uma geometria antiga pode continuar válida enquanto o guincho segue por ela; nunca marcar GPS antigo como ao vivo.
