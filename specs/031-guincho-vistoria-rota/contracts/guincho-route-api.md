# Contrato do percurso de transporte do Guincho

Escopo: rota exibida ao guincheiro depois do início confirmado. [Modelo](../data-model.md), [pesquisa](../research.md), FR-019–024. Não alterar cotação, preço, confirmação de início ou contratos de PitTec.

## Endpoint

`POST /functions/v1/guincho-route`, invocado pelo Supabase Kotlin com JWT da sessão. Configuração `verify_jwt=true`; handler valida `auth.getUser(token)` e não considera uma apikey identidade suficiente. Secrets SUPABASE_URL/ANON_KEY/SERVICE_ROLE_KEY e PITTEC_ROUTING_BASE_URL/API_KEY ficam no backend. Não aceitar URL, coordenadas, preço ou usuário pelo body.

Body fechado:

| Campo | Regra |
| --- | --- |
| appointment_id | UUID válido |
| reason | INITIAL, RESUME, DEVIATION ou RETRY |
| known_route_version | inteiro seguro >=0; zero quando ainda não há snapshot conhecido |

Campos extras ou valores inválidos produzem 400 INVALID_REQUEST. Timeout do roteador: 4 s, incluindo leitura do corpo; limitar resposta upstream a 1 MiB antes da decodificação, geometria a 10000 pontos e distância a 2000000 m. Requisição OSRM-compatible com origem/destino server-side, `overview=full`, `geometries=geojson`, `steps=false`, `alternatives=false`.

## Autorização e admissão

Antes de devolver qualquer rota ou usar cache/lease, conferir identidade e participação no atendimento. Exigir modalidade GUINCHO_DIRECT, status IN_PROGRESS, service_started_at confirmado e condições vigentes de pagamento/vistoria. Para novo cálculo, posição do prestador deve ter published_at dos últimos 30 s e captured_at entre now()-30 s e now()+10 s, com precisão >0..100 m. A tolerância futura de captura preserva a regra vigente de publicação e pequenas diferenças de relógio; não amplia a idade máxima aceita. Destino vem somente de appointments.guincho_trip_snapshot.

Não compartilhar Promises em memória por appointment_id antes de verificar o solicitante. O limite entre instâncias é o lease no banco, não um Map do isolate. O app novo solicita rota só na tela do guincheiro; eventual leitura por outro participante continua autorizada pelo servidor, sem liberar contas alheias.

## RPCs e acesso ao cache

| RPC | Permissão | Contrato |
| --- | --- | --- |
| get_guincho_route_snapshot(p_appointment_id uuid) | authenticated participante | FOUND com rota anterior autorizada ou NOT_FOUND; não depende de fix atual para retornar referência anterior; etapa encerrada é recusada |
| claim_guincho_route_refresh(p_appointment_id uuid, p_requesting_user_id uuid, p_reason text, p_known_route_version bigint) | somente service_role | Revalida usuário/estado, obtém fix/destino autoritativos, trava admissão e devolve REUSED, CLAIMED, BUSY, RETRY_AFTER ou erro classificado |
| finish_guincho_route_refresh(p_appointment_id uuid, p_lease_id uuid, p_geometry jsonb, p_distance_meters integer) | somente service_role | Confere lease/etapa/destino/origem; grava sucesso e incrementa route_version; retorna snapshot ou LEASE_LOST |
| fail_guincho_route_refresh(p_appointment_id uuid, p_lease_id uuid, p_failure_code text) | somente service_role | Libera lease válido, registra backoff e mantém geometria anterior; lease antigo não altera trabalho novo |

p_requesting_user_id é derivado pelo handler do usuário validado; não vem do body. Claim devolve lease e contexto somente à Edge Function. Origem, destino e sua revisão ficam congelados em lease_context, separado dos campos da rota confirmada; claim não troca a origem de uma geometria anterior ainda exibida. Finish promove o grupo inteiro no sucesso e não recebe coordenadas arbitrárias do solicitante. Não manter transação aberta durante HTTP.

`guincho_route_snapshots` tem RLS e nenhum SELECT/INSERT/UPDATE/DELETE direto para anon/authenticated. RPC de leitura é security-definer com search_path restrito e validação explícita. Escritas service-role não substituem a revalidação de estado/participante no claim. Cleanup ao sair de IN_PROGRESS invalida cache/lease e impede publicação tardia.

## Cache, recálculo e limites

- Uma linha por atendimento, lease 10 s e pelo menos 5 s entre tentativas de cálculo. Backoff após falhas transitórias: 5, 10, 20, 30 s; respeitar retry_after também no botão manual.
- INITIAL obtém o primeiro percurso imediatamente quando não há tentativa anterior. RESUME/RETRY podem reaproveitar rota do mesmo destino quando a origem atual permanece a até 50 m da origem calculada; caso contrário, atualizar respeitando admissão.
- DEVIATION não pode receber uma rota antiga apenas por generated_at ter menos de 15 s. Verificar a posição atual contra a geometria: se está novamente no corredor, pode reaproveitar; se saiu, admitir atualização conforme limite. Snapshot mais novo que o conhecido só é reaproveitado quando atende à posição atual.
- Detecção Android exige três fixes distintos cobrindo >=4 s, idade <=10 s, precisão <=35 m e distância à polilinha >max(50 m, 2×precisão). Calcular distância mínima aos segmentos em metros fora da Main Thread, normalizando longitude; no servidor reutilizar PostGIS para conferir o afastamento do fix atual. Claim confere qualidade e afastamento do fix atual autoritativo, sem inventar histórico de GPS no banco. Posição insuficiente produz LOCATION_NOT_READY/LOCATION_UNCERTAIN.
- Atualização de marcador de 2 s não é sinal para chamar o roteador. Um pedido em voo; pedidos adicionais conflam a posição mais recente, reavaliada ao concluir.
- Commit ordena origens por `(published_at,version)` e verifica lease. Não rejeitar apenas porque o GPS publicou outro fix enquanto HTTP rodava: origem ainda válida pode gerar uma boa rota. Não aceitar resposta de outra etapa/destino, route_version antiga ou geração de sessão anterior.
- Em falha, preservar rota anterior explicitamente como referência; ela não equivale a posição atual. Sem rota viária, mostrar destino e erro, sem linha direta apresentada como caminho válido.
- Tempo de espera por lease/cooldown faz parte da medição de SC-006; não reiniciar a contagem para esconder atraso.

## Resposta de sucesso

HTTP 200, status ROUTE, com:

- appointment_id, route_version >0, provider_location_revision >0 e provider_location_published_at;
- origin: latitude, longitude, accuracy_m, captured_at, published_at;
- destination: latitude/longitude do snapshot contratado;
- geometry: GeoJSON LineString, pares `[longitude,latitude]` finitos, exatamente duas componentes, limites geográficos válidos e 2..10000 pontos;
- distance_meters inteiro 0..2000000, generated_at e reused boolean.

Android valida todos os campos, status, atendimento e destino antes de renderizar. Usar route_version, não apenas revisão do GPS, para ordenar rotas; publicação de posição pode reiniciar version após remoção/recriação da linha. Uma geometria válida pode continuar útil por mais de 30 s com o veículo no percurso; o indicador de GPS ao vivo continua usando sua própria atualidade.

## Respostas de espera/falha

| HTTP / status | Código | Comportamento |
| --- | --- | --- |
| 202 PENDING | ROUTE_UPDATING | Lease em andamento; retry_after_ms bounded 500..1000, manter referência anterior |
| 400 UNAVAILABLE | INVALID_REQUEST | Não repetir automaticamente payload inválido |
| 401 UNAVAILABLE | AUTH_REQUIRED | Recuperar sessão, não insistir com credencial inválida |
| 403 UNAVAILABLE | NOT_PARTICIPANT | Encerrar acesso e limpar estado local daquela consulta |
| 409 UNAVAILABLE | APPOINTMENT_NOT_TRACKABLE | Encerrar rota ativa sem alterar o atendimento |
| 409 UNAVAILABLE | LOCATION_NOT_READY / LOCATION_UNCERTAIN | Manter destino/referência com aviso; aguardar fix suficiente |
| 429 UNAVAILABLE | RATE_LIMITED | Respeitar retry_after_ms/Retry-After; não apagar última rota |
| 502 UNAVAILABLE | ROUTER_FAILURE / ROUTER_INVALID_RESPONSE | Recuperação com backoff, preservando referência |
| 504 UNAVAILABLE | ROUTER_TIMEOUT | Mesma recuperação, sem rota fictícia |
| 503 UNAVAILABLE | SERVICE_UNAVAILABLE | Configuração/serviço indisponível, retry recuperável |

Falhas incluem retryable e retry_after_ms quando houver limite. LEASE_LOST interno reconsulta o estado autorizado: devolver uma rota mais nova válida ou PENDING; nunca afirmar sucesso de uma publicação recusada. Encerramento entre claim e finish não recria a linha apagada pelo cleanup.

## Integração Android

GuinchoRouteRepository é específico da feature, reutilizando RouteGeometry/RoutePoint e MapLibre existentes. Dedupe tem dono, cleanup em finally não cancelável e conclusão/cancelamento do deferred para não deixar seguidores esperando após saída da tela. Chave inclui conta/ambiente/atendimento; callbacks revalidam geração.

AppointmentTrackingViewModel reage tanto ao sucesso de startTransport quanto à mudança confirmada de etapa no poll/retomada. Um mesmo fix já conhecido deve permitir calcular a primeira rota imediatamente; não esperar nova publicação de GPS. onHidden cancela requests/retries da rota, não o transporte nem o tracking existente. Reabertura consulta snapshot e ativa RESUME. Respostas posteriores à conclusão/cancelamento são ignoradas.

GuinchoTrackingContent passa a polilinha própria ao MapRenderModel apenas na apresentação do guincheiro. Construir a polilinha uma vez por route_version no ViewModel; o renderer atualiza GeoJSON da rota somente quando geometria/estilo mudarem, resetando essa memória ao instalar um novo estilo. Movimento do marcador não deve reconstruir a linha inteira. Enquadrar rota/destino uma vez no início ou troca de rota e respeitar gesto manual; não recentrar para o pickup em cada fix. Preservar comportamento e fallback atuais de PitTec em seus ramos.

Métricas: latência por INITIAL/RESUME/DEVIATION, cache reuse, número de upstreams e códigos de falha. Nunca registrar coordenadas, URL do roteador com query/credencial, corpo completo ou JWT.
