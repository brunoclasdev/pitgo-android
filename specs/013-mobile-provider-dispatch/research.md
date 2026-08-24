# Research: Busca e despacho de prestadores móveis

## 1. Fluxo dedicado por semântica de catálogo

**Decision**: adicionar ao catálogo uma semântica estável de despacho, por exemplo
`dispatch_kind = MOBILE_SEQUENTIAL`. O card de guincho abre `mobile-dispatch/new/{serviceId}`;
serviços comuns preservam `service-map/{serviceId}` e o fluxo genérico atual. A migration deve
fazer preflight e marcar exatamente um serviço de guincho confirmado; zero ou múltiplos resultados
abortam a migration para correção explícita. A associação pelo nome pode existir somente nesse
backfill controlado; em runtime o Android usa exclusivamente `dispatch_kind` e o UUID do catálogo.
Adicionar `provider_services.active` com default true permite retirar uma oferta da elegibilidade
sem apagar seu histórico relacional.

**Rationale**: `ServiceMap` hoje lista providers por endereço e `RequestService` ainda conclui um
submit apenas em memória. Despacho móvel exige candidato único ordenado pelo servidor, resposta do
provider, cotação congelada e retomada persistente.

**Alternatives considered**: comparar o texto “Guincho” na Home é frágil a tradução/renomeação;
adaptar `RequestService` misturaria orçamento genérico e despacho; direcionar todos os serviços ao
novo fluxo alteraria empresas fixas fora do escopo.

## 2. Posição móvel de descoberta separada do tracking privado

**Decision**: criar `provider_discovery_locations`, com uma única linha por provider, point
PostGIS, qualidade, timestamps, versão e identidade do tracker. Manter
`provider_current_locations` para o atendimento aceito/em andamento. Nenhuma tabela guarda trilha.

**Rationale**: a tabela atual depende de `appointment_id`, portanto não existe antes da escolha do
candidato. Misturar posição pública-condicional de descoberta com posição privada de participantes
ampliaria RLS, lifecycle e risco de exposição. `ST_DWithin` sobre geography usa índice espacial e
distância em metros. [PostGIS ST_DWithin](https://postgis.net/docs/ST_DWithin.html)

**Alternatives considered**: usar endereço empresarial para `MOBILE` não representa o veículo;
reutilizar `provider_current_locations` exige appointment inexistente e mistura audiências; manter
histórico é proibido; enviar posição diretamente por Broadcast contorna validação autoritativa.

## 3. Um foreground service com dois alvos

**Decision**: generalizar `ProviderLocationTrackingService` para `DISCOVERY` e
`APPOINTMENT(appointmentId)`. O toggle Disponível, acionado com Activity visível após permissões,
inicia discovery; aceite troca para tracking privado; encerramento retorna a discovery quando a
intenção `providers.available` continuar ativa. Usar `START_STICKY` como tentativa do sistema,
notificação persistente e interrupção sincronizada com backend. Não pedir
`ACCESS_BACKGROUND_LOCATION` nesta entrega. O service deve inicializar a sessão idempotentemente no
processo recriado e nunca prometer reinício após force-stop/reboot.
`POST_NOTIFICATIONS` é pedido separado e não bloqueia legalmente o FGS; FINE continua obrigatória.
Após alterar modo/disponibilidade, o Dashboard aplica o snapshot confirmado no
`SessionRepository`, evitando que o service leia uma cópia antiga.

**Rationale**: o app já possui FGS `location`, Fused Location a aproximadamente 10 segundos,
permissões e estado processual. Android permite que um location FGS iniciado enquanto a Activity
está visível continue em segundo plano; Android 14+ impede criar esse serviço do background sob a
permissão while-in-use. [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types),
[background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)

**Alternatives considered**: dois services duplicariam sensores/notificações/corridas; ViewModel
morre com a tela; WorkManager não atende cadência contínua de 10 segundos; background location
antecipada aumenta permissão e política sem ser necessária ao início visível; FCM não transporta
localização e não é necessário enquanto o provider elegível mantém o FGS.

## 4. Intenção disponível separada da elegibilidade efetiva

**Decision**: preservar `providers.available` como intenção do provider. Elegibilidade é derivada
por `active`, `available`, modo `MOBILE`, posição recente, oferta exata válida e ausência de
appointment `PENDING`, `ACCEPTED` ou `IN_PROGRESS`. Um índice parcial impede mais de uma dessas
solicitações por provider. Ao aceitar, remover a posição de discovery e publicar somente no
appointment; depois do terminal, o primeiro novo fix válido o torna elegível novamente.

**Rationale**: trocar o boolean para false no aceite perderia a intenção necessária para retorno
automático e criaria corrida com Dashboard. O lease `PENDING` evita várias notificações simultâneas;
o aceite estende a reserva sem depender do cliente.

**Alternatives considered**: várias propostas pendentes por provider exigiriam invalidar outras em
cascata; gravar um segundo boolean `restore_available` duplica estado; manter discovery durante o
atendimento vazaria posição para novas buscas.

## 5. Estado persistente mínimo de busca, candidato e solicitação

**Decision**: criar `service_searches` em `public`, `dispatch_candidates` em `private` e estender
`appointments`. Uma busca guarda somente um ponto atual do cliente enquanto ativa, raio, candidato,
appointment e revisão. Cada candidato fotografa nome público, localidade, distância e preço, mas
não a coordenada. Appointment congela preço, prazo, autor/motivo de cancelamento e vínculo com a
busca. Estados transitórios de recusa/expiração ficam no candidato/appointment; o estado corrente
da busca segue para o próximo candidato.

**Rationale**: esse é o conjunto mínimo que permite retomar após process death, não repetir uma
opção, diferenciar recusa/skip/expiração, preservar preço e auditar cancelamento. A posição do
cliente é uma única referência ativa e deve ser apagada no terminal, não um histórico.

**Alternatives considered**: estado só no ViewModel falha ao reabrir; guardar tudo em DataStore/Room
duplica autoridade sensível; pré-calcular todos os candidatos congela disponibilidade/localização;
uma tabela genérica de eventos ou comandos excede os requisitos atuais.

## 6. Seleção sequencial autoritativa e idempotência natural

**Decision**: uma função privada, sob row lock da busca, escolhe apenas o próximo provider elegível
que ainda não possui candidato nessa busca. Filtra 10 km, depois 25 km e 50 km com `ST_DWithin`,
ordena por `ST_Distance` e `provider.id`, e só então persiste a proposta. RPCs recebem o candidato
corrente e `expected_revision`; constraints naturais tornam start, next, request e transições
reexecutáveis sem duplicar.

**Rationale**: disponibilidade e localização mudam enquanto a busca vive. Selecionar sob lock e
registrar `UNIQUE(search_id, provider_id)` garante ordem e não repetição. `dispatch_candidate_id`
único no appointment impede dois requests da mesma proposta; revisão evita que um segundo toque em
Próximo pule duas opções. [PostgreSQL explicit locking](https://www.postgresql.org/docs/current/explicit-locking.html),
[partial indexes](https://www.postgresql.org/docs/current/indexes-partial.html)

**Alternatives considered**: ordenar no Android permite divergência e manipulação; armazenar lista
completa fica stale; tabela genérica de idempotency keys é desnecessária quando as chaves naturais
e revisões cobrem cada comando; deduplicar por payload permitiria colisões legítimas.

## 7. Prazo de 60 segundos no banco

**Decision**: persistir `response_deadline = statement_timestamp() + interval '60 seconds'` e
agendar `private.expire_due_mobile_dispatch_requests()` a cada segundo por Supabase Cron, em lotes
com `FOR UPDATE SKIP LOCKED`. Todo snapshot e mutation RPC também reconcilia deadlines antes de
responder. O app mostra countdown a partir de `server_now`/deadline, mas ao chegar a zero recarrega
o snapshot em vez de inventar a transição.

**Rationale**: somente um relógio do servidor pode negar aceite tardio e avançar quando apps estão
fechados. Supabase Cron aceita intervalos em segundos em versões Postgres compatíveis; o quickstart
deve conferir a versão/execução local e remota. [Supabase Cron](https://supabase.com/docs/guides/cron),
[Cron quickstart](https://supabase.com/docs/guides/cron/quickstart)

**Alternatives considered**: timer Compose falha em background/process death; expirar apenas no
próximo RPC deixa estado pendente indefinidamente; cron de um minuto viola o prazo; Edge Function
agendada adiciona deploy e segredo sem melhorar a transação no Postgres.

## 8. RPCs seguras e snapshot único

**Decision**: expor RPCs para publicar/parar discovery, iniciar/retomar busca, avançar, solicitar,
responder, cancelar, iniciar e concluir atendimento. Todas são `SECURITY DEFINER`,
`SET search_path=''`, totalmente qualificadas, executáveis somente por `authenticated`, derivando
papel/owner de `auth.uid()`. Retornam o mesmo snapshot JSON com `server_now`, revisão, estado,
candidato, appointment e ações permitidas.

**Rationale**: as transições alteram várias tabelas e precisam ser atômicas. Uma projeção fechada
evita múltiplas leituras inconsistentes e reduz a superfície de dados. Supabase recomenda fixar
`search_path` e restringir EXECUTE em funções definidoras. [Supabase Database Functions](https://supabase.com/docs/guides/database/functions),
[Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)

**Alternatives considered**: DML PostgREST direto não garante seleção/reserva/estado juntos;
service-role no APK é proibido; Edge Function ainda precisaria delegar atomicidade ao banco;
exceções com mensagens SQL como contrato podem vazar detalhes e são frágeis.

## 9. Broadcast privado como invalidação

**Decision**: usar tópicos privados `mobile-dispatch:<searchId>` e
`provider-dispatch:<providerId>`. Triggers enviam apenas IDs opacos, status, deadline e revisão. O
cliente assina somente sua busca; o provider somente seu tópico. Após subscribe, evento ou
reconnect, o Repository chama o snapshot RPC e ignora revisão igual/antiga.

**Rationale**: Broadcast privado escala melhor para mudanças frequentes e a autorização ocorre por
RLS em `realtime.messages`. Tratar o evento como invalidação resolve perda, reconexão e ordem sem
duplicar o estado de negócio no payload. [Realtime Authorization](https://supabase.com/docs/guides/realtime/authorization),
[Broadcast](https://supabase.com/docs/guides/realtime/broadcast)

**Alternatives considered**: Broadcast direto do aparelho contorna o banco; payload completo pode
ficar stale e amplia exposição; Postgres Changes acopla a UI às tabelas e escala pior; FCM é uma
evolução futura para entrega com processo morto, não requisito-base da feature.

## 10. Telas, retomada e MapLibre

**Decision**: criar um fluxo cliente e um destino urgente do provider. A tela cliente apresenta
estado/proposta/actions textuais e um mapa suplementar com um car marker. O Dashboard mostra
publicação, degradação, reserva e incoming request; notificação abre
`provider-request/{requestId}`. Um `DispatchResumeViewModel` consulta o snapshot ativo depois da
autenticação e antes de estabilizar a Home/Dashboard. SavedState contém no máximo IDs de rota, nunca
o estado autoritativo.

**Rationale**: o `MapLibreMapHost` e controllers atuais já atualizam GeoJSON sem recriar mapa. A
proposta textual oferece acessibilidade e funciona quando estilo/tiles falham. A retomada central
elimina tela errada após process death e limpa dados na troca de identidade.

**Alternatives considered**: símbolos MapLibre como única UI não são semanticamente suficientes;
bottom navigation durante despacho facilita abandono acidental; navegar por eventos locais sem
snapshot perde estado; misturar incoming request no tracking existente confunde pré-aceite e
atendimento privado.

## 11. Dependências e manutenção

**Decision**: não adicionar dependências. Reutilizar versões fixadas de supabase-kt Realtime,
MapLibre Native OpenGL, Ktor, Compose e Play Services Location. Manter permissões atuais e não
declarar background location.

**Rationale**: toda capacidade técnica necessária já existe e está integrada ao lifecycle do app.
Menos bibliotecas reduzem conflitos nativos, tamanho e manutenção.

**Alternatives considered**: biblioteca de state machine é desnecessária para estados fechados;
SDK de jobs/push amplia operação; outro renderer de mapa reabre decisão já concluída na feature
010.
