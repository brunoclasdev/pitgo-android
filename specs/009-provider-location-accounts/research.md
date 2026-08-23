# Research: Contas independentes e localização de prestadores

## 1. Papel de conta autoritativo

**Decision**: criar `account_roles`, com uma linha imutável `CLIENT` ou `PROVIDER` por
`auth.users.id`. O signup informa `account_type` em metadata apenas como entrada; um Auth Hook e um
trigger validam e copiam o valor. Sessão, RLS e navegação consultam somente a tabela/RPC autoritativa.
Usuários existentes com `profiles` são backfilled como `CLIENT`, inclusive providers legados.

**Rationale**: Supabase autentica ambos como role PostgreSQL `authenticated`; `raw_user_meta_data` é
editável pelo usuário e não pode autorizar. A chave primária garante exatamente um papel e elimina a
ambiguidade de inferir pela presença de profile/provider.

**Alternatives considered**: metadata/JWT mutável; inferência por tabelas; dois projetos Supabase;
custom claim como única fonte. Todas foram rejeitadas por segurança, ambiguidade legada ou custo.

Fontes: [User data](https://supabase.com/docs/guides/auth/managing-user-data),
[RLS e metadata](https://supabase.com/docs/guides/database/postgres/row-level-security),
[Before User Created Hook](https://supabase.com/docs/guides/auth/auth-hooks/before-user-created-hook).

## 2. Signup e sessão de empresa

**Decision**: “Sou uma empresa” abre `ProviderSignUp`, não `SignUp`. O primeiro cria somente uma
conta Auth `PROVIDER`; o contexto resultante é `Provider.Onboarding` até a RPC de cadastro confirmar
o agregado. Signup cliente envia `CLIENT` e somente ele cria `profiles`. Login continua único e a
classificação remota escolhe Home cliente, onboarding/dashboard provider ou bloqueio.

**Rationale**: Auth e Data API não compartilham uma única transação. Um estado de onboarding
retomável é mais seguro que fingir atomicidade ou criar um profile lateral. Resposta perdida pode ser
recuperada com login e nova leitura do contexto.

**Alternatives considered**: reutilizar SignUp de profile com pessoa jurídica; criar provider antes
da sessão; converter papel depois. Violam a independência ou permitem escalada.

## 3. Identidade estável da empresa e migração legada

**Decision**: `providers.id` vira UUID estável de negócio e `providers.account_id` vira FK única da
conta empresarial proprietária. Registros antigos preservam `id` e filhos; transferir altera somente
`account_id`. A migração assistida usa desafio de 32 bytes, hash privado, validade de 15 minutos e
duas RPCs: a sessão cliente inicia e a nova sessão provider, com e-mail diferente, reivindica.

**Rationale**: preserva endereço, categorias, serviços e referências sem copiar/recriar linhas. Locks,
token de uso único e estado auditável tornam begin/claim atômicos e idempotentes.

**Alternatives considered**: atualizar a PK com cascata; copiar a empresa; conversão automática;
service-role no APK. Todas ampliam risco de perda, duplicidade ou violam a especificação.

Fontes: [Database functions](https://supabase.com/docs/guides/database/functions),
[PostgreSQL constraints](https://www.postgresql.org/docs/current/ddl-constraints.html),
[advisory locks](https://www.postgresql.org/docs/current/explicit-locking.html).

## 4. Cadastro atômico de provider/categorias/serviços/endereço

**Decision**: a RPC `create_provider_registration` deriva a conta de `auth.uid()`, exige papel
`PROVIDER`, valida todo o payload antes do primeiro insert e grava provider, um endereço,
`provider_categories` e `provider_services` na mesma transação. `provider_categories` é derivada das
categorias dos serviços escolhidos. Unique constraints e advisory lock tornam retry idempotente.

**Rationale**: o cliente não escolhe owner nem consegue combinar serviço com categoria errada.
Qualquer exception reverte o agregado completo; resposta perdida retorna `ALREADY_REGISTERED`.

**Alternatives considered**: múltiplos inserts PostgREST; category ID confiado ao app; upsert que
sobrescreve cadastro existente. Rejeitados por estado parcial e adulteração.

## 5. Geocodificação

**Decision**: usar uma Edge Function autenticada como proxy estreito para Google Geocoding API.
A chave web fica em Supabase Secrets. A função valida endereço BR, papel provider, fix preciso e
qualidade, geocodifica, calcula a distância e cria uma prova privada efêmera. A RPC de cadastro
consome apenas o ID da prova. Latitude/longitude do dispositivo não são persistidas.

Como coordenadas do Geocoding podem ser mantidas por 30 dias, salvar `place_id`, `geocoded_at` e
`geocode_valid_until`, renovar a partir do dia 25 por Edge Function agendada e excluir da descoberta
endereços expirados ou cuja renovação mudou de forma suspeita. A API key do Maps Android é separada
e restrita por package/SHA-1; nunca reutiliza a chave web.

**Rationale**: Android `Geocoder` não garante disponibilidade/qualidade para um cadastro obrigatório;
uma chamada web direta exporia a chave. A prova efêmera impede o APK de substituir silenciosamente
a coordenada devolvida pelo servidor.

**Alternatives considered**: Android Geocoder; Nominatim público; self-hosted Pelias/Nominatim;
Mapbox permanente. Foram rejeitados por confiabilidade, política operacional ou troca completa de
stack. A renovação é o custo explícito da escolha Google.

Fontes: [Geocoding API](https://developers.google.com/maps/documentation/geocoding/geocoding),
[policies](https://developers.google.com/maps/documentation/geocoding/policies),
[Maps API security](https://developers.google.com/maps/api-security-best-practices).

## 6. Comparação endereço/dispositivo

**Decision**: exigir `FINE` e um fix com precisão `<=100 m` e idade `<=30 s`. Capturar por
`getCurrentLocation` high accuracy, sem usar `lastLocation` como autoridade. Até 500 m aceitar;
acima disso exigir confirmação explícita, sempre persistindo o ponto geocodificado do endereço.
Editar qualquer campo do endereço invalida a prova e a confirmação.

**Rationale**: localização aproximada pode ter escala de quilômetros e não sustenta o limite de
500 m. Os limites de 100 m/30 s tornam “qualidade suficiente” verificável e são validados no app e
na Edge Function.

**Alternatives considered**: aceitar coarse; salvar o ponto do aparelho; permitir lat/lng manual;
comparar só no app. Rejeitados por precisão, privacidade ou adulteração.

Fontes: [Android location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions),
[retrieve current location](https://developer.android.com/develop/sensors-and-location/location/retrieve-current),
[location settings](https://developer.android.com/develop/sensors-and-location/location/change-location-settings).

## 7. Mapa e consulta espacial

**Decision**: Google Maps Compose/Utils 8.4.0, clustering e uma lista acessível equivalente. A Home
carrega UUIDs reais do catálogo e navega para `service-map/{serviceId}`. Uma RPC PostGIS exige sessão
CLIENT, filtra serviço e categoria exatos, provider/owner ativos e disponíveis e endereço geocodificado
vigente; retorna somente projeção pública mínima, por raio/viewport, no máximo 200 itens.

**Rationale**: filtro e distância no banco usam índice GiST e não confiam em associação local/mock.
Providers FIXED e MOBILE usam endereço público; a lista permite uso acessível sem depender do mapa.

**Alternatives considered**: filtrar DemoServices no cliente; consultar tabelas completas; posição
móvel na descoberta; mapa sem lista. Rejeitados por inconsistência, vazamento e acessibilidade.

Fontes: [Maps Compose releases](https://github.com/googlemaps/android-maps-compose/releases),
[Maps accessibility](https://developers.google.com/maps/documentation/android-sdk/accessibility),
[Supabase PostGIS](https://supabase.com/docs/guides/database/extensions/postgis).

## 8. Permissões e foreground service

**Decision**: cadastro pede `COARSE`+`FINE` no momento da verificação; precise é obrigatório para
provider. Não pedir `ACCESS_BACKGROUND_LOCATION`. Tracking móvel começa por ação visível numa tela
de atendimento e usa foreground service `location`, podendo continuar em background. Em Android 13+
notificação negada limita tracking à tela visível. Service é `START_NOT_STICKY`, sem boot receiver.

**Rationale**: um location FGS iniciado visivelmente pode continuar em background; background
permission seria acesso excessivo. A notificação persistente e o indicador in-app tornam o uso claro.

**Alternatives considered**: WorkManager; ViewModel; background permission no cadastro; reinício no
boot. Não atendem 10 s ou ampliam coleta invisível.

Fontes: [FGS location](https://developer.android.com/develop/background-work/services/fgs/service-types),
[FGS restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start),
[notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

## 9. Aquisição/publicação de posição móvel

**Decision**: Play services Location 21.4.0 com high accuracy a cada 10 s, sem batching ou min
distance. Validar precisão `<=100 m` e idade `<=30 s`. Offline mantém somente o fix mais recente em
memória. A RPC publica por `appointment_id`, deriva o provider, valida modalidade/estado/participação
e aceita somente sequência/timestamp monotônicos. Um tracker fresco bloqueia segundo dispositivo;
após 30 s outro pode assumir.

**Rationale**: heartbeat mantém recência mesmo parado; uma linha corrente evita histórico e a
arbitragem no servidor impede regressão por respostas antigas ou múltiplos aparelhos.

**Alternatives considered**: fila local/histórico; min distance; provider ID no Intent/RPC; último
write wins. Rejeitados por privacidade e concorrência.

Fontes: [LocationRequest.Builder](https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest.Builder),
[Play services releases](https://developers.google.com/android/guides/releases).

## 10. Realtime privado e recência

**Decision**: instalar Supabase Realtime e trocar Ktor Android por OkHttp 3.3.1, que suporta
WebSocket. Uma trigger na única linha corrente emite Database Broadcast privado no tópico
`appointment-location:<id>`. RLS em `realtime.messages` autoriza somente participantes de
`ACCEPTED/IN_PROGRESS`. O cliente assina, busca snapshot depois do subscribe e descarta versões
antigas; timer local marca stale em `published_at + 30 s`.

**Rationale**: Broadcast é recomendado pelo Supabase para escala/segurança. Snapshot pós-subscribe
fecha a corrida inicial e a versão fecha reordenação; Broadcast não precisa armazenar histórico.

**Alternatives considered**: Postgres Changes; Broadcast direto do aparelho; canal público;
Presence. Rejeitados por escala, bypass da RPC ou exposição.

Fontes: [Database Broadcast](https://supabase.com/docs/guides/realtime/subscribing-to-database-changes),
[Realtime authorization](https://supabase.com/docs/guides/realtime/authorization),
[Ktor engines](https://ktor.io/docs/client-engines.html).

## 11. Âncora mínima de atendimento

**Decision**: criar `appointments` somente como fronteira de autorização, com participantes e
estados `PENDING`, `ACCEPTED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`. Esta feature não cria UI/RPC
de despacho/aceite. Tracking só funciona para registros `ACCEPTED/IN_PROGRESS` criados pelo fluxo
autoritativo atual/futuro; quickstart usa fixture administrativa.

**Rationale**: sem participante e estado server-owned, qualquer `appointment_id` do cliente poderia
autorizar rastreamento indevido. A entidade mínima cumpre segurança sem implementar o marketplace
completo fora de escopo.

**Alternatives considered**: confiar em status do payload; liberar tracking por provider disponível;
adiar toda a feature. As duas primeiras são inseguras; o schema mínimo permite integração segura.

## 12. Localização do cliente

**Decision**: não criar tabela nem FGS de cliente. A descoberta obtém fix one-shot em memória e o
envia apenas como centro da consulta; sem permissão usa região escolhida/pan do mapa. Em acompanhamento,
coleta existe somente enquanto a tela `STARTED`, se uma finalidade visível realmente exigir.

**Rationale**: atende busca e privacidade sem histórico. Ao sair, trocar conta ou serviço, jobs,
callback e channel são removidos.

**Alternatives considered**: rastreamento global; persistência em DataStore/Room; publicação contínua.
Rejeitados por FR-025/026 e ausência de finalidade.
