# Pesquisa técnica — Vistoria ampliada e rota do Guincho

Data: 2026-10-14. Escopo: decisões para [spec.md](spec.md), incluindo as duas respostas registradas em Clarifications. Pesquisa somente leitura; nenhuma migration, deploy ou transação foi executada.

## 1. Catálogo sem quebrar clientes e registros existentes

**Decision**: Preservar `point = FRONT | REAR | LEFT | RIGHT | DETAIL`. Adicionar `detail_point` anulável à foto e ao manifesto para os 13 opcionais nomeados; `DETAIL` sem categoria continua sendo detalhe livre. Novas leituras e preparação têm nomes v2; as v1 mantêm sua projeção e os códigos legados. Contar obrigatórios explicitamente pelos quatro códigos principais.

**Rationale**: `GuinchoInspectionModels.kt:7` usa enum fechado. Enviar novos valores diretamente em `point` quebraria leitores antigos. As tabelas e funções da feature 030 já implementam imutabilidade, seleção por versão e correção, que devem ser ampliadas sem reclassificar fotos antigas. As reservas e a seleção precisam de unicidade pela categoria efetiva, incluindo opcionais nomeados.

**Alternatives considered**: Substituir o enum inteiro; criar outra vistoria; inferir categoria pela legenda. Todas aumentam incompatibilidade ou permitem classificação incorreta.

## 2. Persistência de capturas e retomada no aplicativo aberto

**Decision**: Journal JSON por captura com `AtomicFile` e arquivos privados em `filesDir`, separados por ambiente/conta/atendimento. Persistir intenção, identidade e URI antes da câmera; manter IDs idempotentes e derivados congelados. O Repository atual ganha o runner; um ViewModel da feature, vinculado à raiz autenticada, ativa a retomada ao abrir o app e ao recuperar conexão, inclusive fora do álbum. Logout/troca de ambiente invalida geração e limpa apresentação/cache de leitura, preservando pendências privadas até confirmação ou descarte explícito permitido. A mesma conta no mesmo ambiente só retoma após revalidação de permissões. Sem requisito de envio com app encerrado, Room ou WorkManager.

**Rationale**: Hoje `GuinchoInspectionCaptureFiles.kt:21` usa cache removível e `GuinchoInspectionViewModel.kt:73–82` restaura uma captura sem reiniciar seu envio. Resultado da câmera não preserva sozinho o contexto da operação; IDs pequenos continuam no SavedStateHandle, bytes e journal ficam no disco. AtomicFile exige exclusão concorrente externa: mutações e limpeza usam Mutex e geração da sessão. [Activity Result](https://developer.android.com/training/basics/intents/result), [AtomicFile](https://developer.android.com/reference/android/util/AtomicFile), [arquivos privados](https://developer.android.com/training/data-storage/app-specific).

**Alternatives considered**: Apenas SavedStateHandle/cache; fila genérica de domínio; serviço permanente de upload. Não atendem à durabilidade ou excedem o comportamento escolhido pelo usuário.

## 3. Preparação recuperável e consumo de memória

**Decision**: Conservar o arquivo bruto imutável após o callback; criar original normalizado e miniatura em outros caminhos, com temporários e commit atômico. Ler dimensões, amostrar antes de decodificar, aplicar as oito orientações EXIF e limitar o original a 2560 px/5 MiB. Miniatura com maior lado até 480 px/256 KiB. Um normalizador, um pipeline de mutação/envio por atendimento e até dois downloads, com prioridade para imagem aberta pelo usuário.

**Rationale**: A normalização atual só amostra acima de 5120 px, podendo decodificar aproximadamente 64 MB em 16 MP antes da cópia; também substitui o arquivo que a UI pode estar lendo. Cancelamento não pode apagar capturas aceitas. Não recomprimir depois da reserva: tamanhos e bytes precisam continuar idênticos nos retries. [Bitmaps grandes](https://developer.android.com/topic/performance/graphics/load-bitmap), [otimização de imagens Compose](https://developer.android.com/develop/ui/compose/graphics/images/optimization), [cancelamento cooperativo](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/ensure-active.html).

**Alternatives considered**: Processar full-resolution; normalizar in-place; decodificar todos os originais em paralelo. Elevam memória, corridas e custo de recuperação.

## 4. Álbum estável e orientação

**Decision**: Portrait restrito à rota da vistoria em telefones em que seja respeitado, restaurando a preferência anterior na saída. Em janelas maiores, manter o conteúdo do álbum em grade/coluna vertical estável e responsiva; restauração de estado funciona mesmo quando o sistema gira a Activity. Não mudar orientação global nem usar `configChanges` como correção de perda de estado.

**Rationale**: O projeto tem minSdk 24, targetSdk 36 e compileSdk 37. Android 16 limita bloqueios em telas >=600dp; target 37 restringe ainda mais as exceções. A captura externa pode ser horizontal enquanto o álbum mantém a apresentação estável. [Orientação adaptativa](https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability), [restrições no Android 17](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored).

**Alternatives considered**: Bloquear todas as telas em portrait; impedir foto horizontal; depender do bloqueio para preservar a operação. Não atendem ao escopo e à adaptação de telas.

## 5. Miniaturas privadas, leitura e aprovação

**Decision**: Gerar miniatura no aparelho e armazená-la no bucket privado existente, em caminho derivado e reservado. Confirmar os dois objetos atomicamente para capturas v2; linhas antigas continuam exigindo apenas o original. Cache por foto/variante/conta/ambiente, downloads temporários e merge por ID, sem limpar todas as imagens a cada revisão. Validar originais para habilitar aprovação, preservando o gate existente; miniatura sozinha não aprova a imagem.

**Rationale**: A UI atual já usa Coil com constraints. O Repository faz um RPC de documento por imagem e o ViewModel cancela todos os downloads a cada revisão. Um snapshot autorizado do conjunto, revalidado na retomada, elimina esses RPCs repetidos; Storage continua aplicando RLS em cada leitura. Miniaturas locais não significam salvamento compartilhado. [Coil Compose](https://coil-kt.github.io/coil/compose/), [Storage e RLS](https://supabase.com/docs/guides/storage/security/access-control).

**Alternatives considered**: Bucket público, transformação paga ou download completo para cada quadro. Não são necessários para este álbum. Fotos antigas sem thumb usam o original, sem backfill em versões imutáveis.

## 6. Idempotência e substituição

**Decision**: Operações de reserva e confirmação têm UUIDs duráveis e payload congelado; replay autorizado é reconhecido antes de revisão/editabilidade, sem autorizar novas escritas após encerramento. `READY` remoto permite concluir a pendência mesmo se o arquivo local não existir mais. Upload continua `upsert=false`; conflito de objeto exige conferir o resultado existente. SHA-256 local identifica bytes congelados; não adicionar hash remoto nem alegar certificação criptográfica. Em conflito incerto de upload, comparar bytes remotos com o hash local antes de confirmar.

**Rationale**: O banco atual verifica owner, MIME e tamanho, não checksum. A confirmação já troca a seleção anterior na mesma transação; manter isso com original e miniatura. Submissão atual bloqueia qualquer reserva ativa, inclusive opcional: preservar. A API de Storage pode sinalizar duplicidade com 400 ou 409, portanto tratar código estruturado. [Uploads padrão e conflito de objetos](https://supabase.com/docs/guides/storage/uploads/standard-uploads).

**Alternatives considered**: Gerar novas operações a cada tentativa; considerar todo 400 sucesso; sobrescrever objeto; descartar a imagem anterior na reserva. Criam duplicação, falsos sucessos ou perda de evidência.

## 7. Rota própria da etapa de transporte

**Decision**: Nova Edge Function `guincho-route`, com JWT do usuário, `auth.getUser()` e autorização do atendimento antes de qualquer cache. Ler posição publicada do prestador e destino imutável de `appointments.guincho_trip_snapshot`; nunca aceitar coordenadas do app. Reutilizar a configuração de roteador OSRM-compatible usada na cotação. Novo snapshot/lease específico do Guincho, um registro por atendimento, com escrita somente service-role. Android reutiliza geometria/renderer comuns e ganha Repository/estado específico, preservando PitTec.

**Rationale**: `GuinchoTrackingContent.kt` envia somente marcadores ao mapa. `pittec-route` calcula até o cliente e tem regras/cache próprios; `guincho-quote` solicita `overview=false`, fornecendo distância sem traçado. A rota de transporte deve existir apenas em `IN_PROGRESS` com início confirmado, rumo ao destino do veículo. `overview=full`, `geometries=geojson` e `steps=false` fornecem o traçado sem navegação por voz. [OSRM Route](https://project-osrm.org/docs/v5.24.0/api/).

**Alternatives considered**: Usar diretamente a cotação; chamar PitTec como se o cliente fosse destino; modificar todas as rotas existentes; guardar geometria e lease novos na tabela compartilhada de PitTec. Uma integração localizada evita alterar contratos de outra modalidade e a lógica financeira.

## 8. Desvio confirmado, concorrência e resposta atrasada

**Decision**: Detectar desvio com três fixes distintos cobrindo ao menos 4 s, idade <=10 s, precisão <=35 m e distância à polilinha maior que `max(50 m, 2 × precisão)`. Uma solicitação por vez; conflar a posição mais recente. Atualização normal do marcador não recalcula rota. Admissão distribuída no banco: lease de 10 s e intervalo mínimo de 5 s entre tentativas por atendimento, com backoff em falha; não reutilizar cegamente cache de 15 s quando há desvio real. Timeout upstream de 4 s, sem fallback de linha reta.

**Rationale**: GPS de 2 s não implica chamada ao roteador a cada fix. Comparar origem por `(published_at, version)` evita erro quando a linha de posição é recriada e a versão reinicia. Commit valida lease, destino, estágio e ordem dos snapshots, sem exigir que a origem ainda seja o último fix: essa exigência impediria toda resposta mais lenta que o GPS. UI aceita route_version crescente e reavalia a posição atual após o resultado. Locks ficam apenas nas transações curtas, nunca durante HTTP. [Locks PostgreSQL](https://www.postgresql.org/docs/current/explicit-locking.html).

**Alternatives considered**: Um Map em memória como limite global; recalcular em cada publicação; aceitar resultado só se GPS não mudou; refazer automaticamente por posição antiga/imprecisa. Causam duplicações, custo ou starvation.

## 9. Segurança, ambientes e validação

**Decision**: `guincho-route` usa `verify_jwt=true` e valida a identidade também no handler; os RPCs service-role recebem usuário obtido da sessão validada, não um ID arbitrário do body. Secrets do roteador permanecem no backend. Migrar aditivamente, disponibilizar RPCs/Storage/Edge Function antes do APK e validar Dev antes do backend compartilhado Homol/Prod. Aplicação ao mesmo projeto é sequencial e exige revisão de todas as migrations pendentes mostradas no dry-run, inclusive de outras histórias; arquivos incompletos bloqueiam aplicação, sem impedir desenvolvimento local independente. Preparar medições monotônicas/trace nos pontos de captura e rota antes dos ensaios, deixando T061 como revisão final. Manter dependências instaladas; sem upgrades gerais ou suítes automatizadas.

**Rationale**: O gateway aceita chaves e JWTs de formas diferentes; identidade validada e autorização continuam necessárias antes de acessar service-role. [Headers de autorização](https://supabase.com/docs/guides/functions/auth-headers). Não foi feita inventariação remota nesta fase: disponibilidade/deploy nos projetos-alvo é gate explícito de entrega, não evidência já obtida. A constituição exige MVVM, privacidade e verificação proporcional; os cenários serão manuais e os checks de build/lint reais na implementação.

**Alternatives considered**: Desabilitar autenticação para contornar erro; executar migrações nesta etapa; tratar build como prova funcional; testes/mocks não solicitados. Não atendem aos limites do fluxo.

## Resultado

Decisões técnicas resolvidas para a Fase 1. Permanecem como verificações de implementação: inventário real de funções/configuração por ambiente, medições SC-001–009, permissões RLS e ensaios de retomada/rotação em aparelhos. Nenhuma decisão de produto adicional é necessária para planejar.
