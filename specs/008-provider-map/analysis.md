# Análise: Mapa de localização de providers (estáticos e dinâmicos)

> Documento só de análise/levantamento — **nada foi implementado**. Objetivo: mapear o que já
> existe no código, o que falta, e as decisões de arquitetura/produto necessárias antes de
> implementar um mapa que mostre providers estáticos (endereço salvo) e providers dinâmicos
> (localização atualizando em tempo real).

## 1. O que já existe hoje

### 1.1 Dados de endereço do provider (estático)

A tabela `public.addresses` (migration `20260820000000_provider_registration.sql`) já guarda
`latitude`/`longitude` opcionais por provider:

```sql
latitude numeric(9,6),
longitude numeric(10,6),
constraint addresses_coordinates_pair check ((latitude is null) = (longitude is null)),
constraint addresses_latitude_range check (latitude is null or latitude between -90 and 90),
constraint addresses_longitude_range check (longitude is null or longitude between -180 and 180),
constraint addresses_provider_unique unique (provider_id)
```

- Um endereço por provider (`UNIQUE(provider_id)`), coordenadas opcionais e validadas.
- O formulário (`ProviderRegistrationScreen`/`AddressDraft`) já coleta `latitude`/`longitude` como
  texto livre — **hoje isso é preenchimento manual**, não captura de GPS nem geocoding automático
  a partir do endereço digitado.
- A RPC `create_provider_registration` já normaliza e persiste esses campos.

Conclusão: a peça "provider estático com pino fixo no mapa" já tem onde guardar o dado. Falta
capturar/validar a coordenada de forma confiável (hoje é só um texto que o usuário digita) e expor
essa leitura para outros usuários (ver §2.1 sobre RLS).

### 1.2 Localização em tempo real (dinâmico)

**Não existe nada hoje.** Não há:
- coluna/tabela para posição corrente de um provider;
- nenhuma dependência de localização no Gradle (`FusedLocationProviderClient`,
  `play-services-location` etc.);
- nenhuma permissão de localização no `AndroidManifest.xml` (só há `INTERNET`);
- nenhum serviço em foreground, WorkManager ou canal de streaming;
- o módulo `realtime-kt` do Supabase (usado para *postgres_changes*/broadcast) **não está no
  catálogo de dependências** (`gradle/libs.versions.toml` só tem `supabase-kt`, `auth-kt`,
  `postgrest-kt`).

### 1.3 Mapa / SDK de mapas

Nenhuma dependência de mapa (`Google Maps Compose`, `Mapbox`, `osmdroid` etc.) está no projeto.
Vai ser necessário escolher e integrar um SDK do zero, com chave de API e billing (ver §5).

### 1.4 O fluxo de "pedir serviço" hoje é uma demo desconectada dos providers reais

`RequestServiceUiState`/`DemoServiceCatalog` (`core/common/model/DemoServiceCatalog.kt`) são uma
lista **estática, fixa no app**, de serviços (`BATTERY`, `OIL_CHANGE`, `TIRES`...) com preço fixo.
O tempo de chegada mostrado na tela é literalmente uma string fixa:

```xml
<string name="request_arrival_value">20–40 min</string>
```

Ou seja: hoje não existe nenhum vínculo entre "o cliente pede um serviço" e "um provider real
cadastrado no banco". O RLS reforça essa separação: os providers só conseguem ler a própria linha.

### 1.5 RLS atual bloqueia qualquer leitura cross-user

```sql
create policy providers_select_own on public.providers
for select to authenticated using ((select auth.uid()) = id);
create policy addresses_select_own on public.addresses
for select to authenticated using ((select auth.uid()) = provider_id);
```

Um cliente autenticado **não consegue** hoje listar providers de terceiros nem seus endereços — só
o próprio provider vê a própria linha. Um mapa visível para clientes exige uma política nova.

### 1.6 Arquitetura atual (onde a feature se encaixaria)

- DI simples por construtor em `PitGoAppContainer` (sem Hilt/Koin) — um novo
  `ProviderMapRepository`/`ProviderLocationRepository` entraria do mesmo jeito que
  `ProviderRegistrationRepository`.
- Navegação centralizada em `PitGoNavHost`/`PitGoRoutes` (rotas com `SignUpOrigin`-like enums para
  parâmetros). Uma tela de mapa novo seria mais uma rota autenticada, plugável no `Home`
  (`onOpenGarage`/bottom nav já seguem esse padrão) ou no `RequestService`.
- `minSdk = 24`, `targetSdk = 36`, `compileSdk = 37` — relevante para runtime permissions de
  localização (Android 10+ trata `ACCESS_BACKGROUND_LOCATION` como permissão separada, exigida se
  quisermos atualizar a posição do provider com o app em background).

## 2. Requisito do usuário, decomposto

> "mostra a localização dos providers no mapa, vai ter providers dinâmicos (não estáticos), pega a
> localização deles em tempo real e vai atualizando no mapa; os que salvar endereço aparecem
> estático"

Isso implica **dois modos de provider coexistindo no mesmo mapa**:

| Modo | Fonte da posição | Atualização | Exemplo de caso de uso |
|---|---|---|---|
| **Estático** | `addresses.latitude/longitude` (endereço cadastrado) | Nunca (até o provider editar o endereço — feature de edição ainda nem existe) | Oficina fixa, loja de peças |
| **Dinâmico** | GPS do device do provider, enviado periodicamente | Tempo real (segundos) enquanto o provider estiver "ativo"/em rota | Mecânico/guincho que vai até o cliente (o próprio catálogo de serviços do app — bateria, pane seca, troca de óleo no local — sugere que isso é o caso comum) |

Isso é uma decisão de produto que precisa ser explícita no schema: **como um provider é marcado
como estático ou dinâmico?** Hoje não existe esse conceito. Opções (ver §3.1).

## 3. Lacunas de dados e proposta de modelagem

### 3.1 Marcar o "modo" do provider

Precisa de um campo novo, por exemplo:

```sql
alter table public.providers
  add column location_mode text not null default 'STATIC'
  check (location_mode in ('STATIC', 'DYNAMIC'));
```

Perguntas em aberto (produto):
- É uma escolha fixa no cadastro (provider decide "sou fixo" ou "eu me desloco") ou pode alternar
  a qualquer momento (ex.: oficina que também manda um guincho)?
- Um provider "dinâmico" ainda tem endereço-base (`addresses`) como fallback quando está offline/
  sem GPS ativo, ou some do mapa quando não está transmitindo?

### 3.2 Onde guardar a posição em tempo real

**Não deve** ficar na tabela `addresses` (ela representa o endereço cadastrado, imutável na leitura
normal, e tem `UNIQUE(provider_id)` para o endereço declarado). Proposta: tabela nova,
que também facilita RLS diferente (o provider grava a própria linha; qualquer autenticado lê):

```sql
create table public.provider_live_locations (
  provider_id uuid primary key references public.providers(id) on delete cascade,
  latitude numeric(9,6) not null,
  longitude numeric(10,6) not null,
  heading numeric(5,2),          -- opcional: direção do movimento, para orientar o pino
  accuracy_meters numeric(6,2),  -- opcional: raio de precisão do GPS
  is_online boolean not null default true,
  updated_at timestamptz not null default now()
);
```

- `updated_at` permite ao client (ou a uma policy) descartar posições "velhas" (ex.: provider que
  fechou o app sem avisar) — precisa de uma estratégia de expiração (ver §3.4).
- Uma linha por provider (upsert contínuo) é mais simples e barato que uma tabela de histórico de
  posições; se quisermos trilha/histórico (ex.: replay do trajeto), seria uma tabela de eventos
  separada (fora de escopo até virar requisito).

### 3.3 RLS necessária

- `provider_live_locations`: `insert/update` só para o próprio provider (`auth.uid() = provider_id`
  e a linha só existe se `providers.location_mode = 'DYNAMIC'`, reforçado via `check`/trigger ou na
  RPC); `select` para qualquer `authenticated` (ou restringir por proximidade/serviço — decisão de
  produto).
- `providers`/`addresses`: a policy atual (`select own`) precisa de uma política adicional de
  leitura pública/autenticada para permitir que clientes vejam provider + endereço de terceiros no
  mapa. Hoje isso simplesmente não é possível. Precisa decidir **quais campos** ficam visíveis
  publicamente (nome, categoria de serviço, cidade/bairro aproximado) vs. o que continua privado
  (CNPJ completo, telefone, e-mail — hoje só o CNPJ é mascarado na leitura do próprio provider; uma
  leitura pública precisaria de uma *view* própria que já mascare/omita esses campos, em vez de
  liberar `select` direto na tabela crua).

### 3.4 Presença / expiração de dinâmico

Sem heartbeat, um provider que perde conexão fica "grudado" no mapa como se ainda estivesse lá.
Precisa de uma regra explícita, por exemplo:
- client marca `is_online = false` ao sair da tela/perder o app em background (best effort);
- e/ou o app cliente ignora localmente qualquer `updated_at` mais velho que N minutos;
- e/ou um job periódico (Supabase Edge Function agendada, ou `pg_cron`) zera `is_online` para linhas
  velhas — mais robusto, mas é infraestrutura nova no projeto (hoje não há Edge Functions nem
  `pg_cron` configurados).

## 4. Transporte em tempo real

Duas opções realistas com o stack atual (Supabase):

| Opção | Como funciona | Prós | Contras |
|---|---|---|---|
| **Supabase Realtime — Postgres Changes** | Client assina mudanças na tabela `provider_live_locations` via websocket; cada `UPDATE` chega pro app | Mesma fonte de verdade (Postgres), simples de auditar, sem infra nova além do módulo `realtime-kt` | Precisa habilitar replication na tabela; volume de updates (um provider em rota manda posição a cada poucos segundos) gera bastante tráfego de WAL se muitos providers ativos ao mesmo tempo |
| **Supabase Realtime — Broadcast** (canal efêmero, não persiste no Postgres) | Provider publica `lat/lng` num canal `provider:{id}`; clientes assinam o canal do provider que estão acompanhando | Mais leve para alta frequência (não bate no Postgres a cada update); dá pra combinar com *Presence* do Realtime pra saber quem está online sem heartbeat manual | Perde o histórico/observabilidade via SQL; exige lógica própria pra "descobrir quais canais assinar" perto do usuário (provavelmente ainda precisa de uma consulta inicial via Postgrest pra saber quais providers existem e then assinar broadcast de cada um) |

Recomendação de leitura futura (não é decisão, é ponto de partida): usar **Postgres Changes** para
a query inicial + fallback, e considerar **Broadcast/Presence** se o volume de updates por segundo
se mostrar um problema depois de medir. De qualquer forma, `implementation("io.github.jan-tennert.
supabase:realtime-kt:<versão>")` precisa entrar no catálogo de dependências — hoje não está lá.

Alternativa fora do Supabase (Firebase Realtime Database/Firestore) resolveria o mesmo problema,
mas duplicaria a fonte de autenticação/dados (o app já usa Supabase Auth + Postgrest para tudo) —
não recomendo introduzir um segundo backend só para isso, a menos que o volume justifique.

## 5. Cliente Android — peças que faltam

### 5.1 Permissões e captura de posição (lado do provider)

- `ACCESS_FINE_LOCATION` (mínimo); `ACCESS_BACKGROUND_LOCATION` se quisermos continuar mandando
  posição com o app em background — no Android 10+ isso é uma segunda permissão, pedida em uma
  segunda tela, com texto de justificativa exigido pela Play Store (declaração de uso de
  localização em background no Play Console, política mais rígida de revisão).
- Se o objetivo for "provider aberto no app, tela ligada, em rota" já basta foreground location; se
  o objetivo for "provider continua visível mesmo com o app minimizado", precisa de
  `Foreground Service` com tipo `location` (`FOREGROUND_SERVICE_LOCATION` no manifest a partir do
  Android 14, que é o `targetSdk` atual) + notificação persistente ("Você está compartilhando sua
  localização").
- `FusedLocationProviderClient` (Google Play Services) é o caminho padrão; implica adicionar
  `com.google.android.gms:play-services-location` (não está no projeto hoje).

### 5.2 Mapa (lado do cliente)

- Escolher SDK: `Google Maps Compose` (mais simples de integrar com o resto do stack, requer chave
  de API do Google Cloud + billing habilitado, mesmo dentro da cota gratuita) é o caminho mais
  direto dado que o app já é 100% Jetpack Compose. Alternativas (Mapbox, OSM) evitam a dependência
  de billing do Google mas exigem mais trabalho de estilização/tiles.
- Nova tela (`features/providermap/` ou dentro de `requestservice/`) com:
  - pinos estáticos (um fetch único via Postgrest, sem assinatura);
  - pinos dinâmicos (assinatura Realtime, atualizando posição/anotação enquanto a tela estiver
    visível — cancelar ao sair da tela para não vazar conexão/bateria);
  - algum agrupamento (cluster) se o número de providers crescer, para não empilhar pinos.

### 5.3 Privacidade / consentimento

- Precisa de uma tela explícita de opt-in para o provider dinâmico ("Compartilhar minha localização
  em tempo real enquanto estiver disponível"), com jeito claro de desligar (parar de transmitir),
  já que localização em tempo real é dado sensível (LGPD) e a Play Store exige fluxo de consentimento
  específico para apps que coletam localização em background.
- Decidir se o cliente vê a posição exata do provider dinâmico o tempo todo, ou só quando há um
  pedido de serviço ativo entre os dois (mais alinhado com o padrão "Uber": localização em tempo
  real só é compartilhada durante uma corrida/atendimento, não 24/7 para qualquer usuário do app).
  Isso muda bastante o desenho de RLS/broadcast — hoje não existe nenhuma entidade "pedido de
  serviço" real no banco (lembrar: `RequestService` é 100% mockado, sem persistência), então esse
  vínculo também precisaria ser criado antes de restringir a visibilidade dessa forma.

## 6. Coisas que precisam existir antes/junto (dependências implícitas do pedido)

1. **Geocoding do endereço estático**: hoje `latitude`/`longitude` são digitados à mão no
   cadastro. Sem validação/preenchimento automático a partir do CEP/endereço, o pino estático vai
   ficar impreciso ou vazio na prática (o campo é opcional). Vale considerar geocoding automático
   (Google Geocoding API ou Nominatim/OSM) no momento do cadastro.
2. **Tela de edição de endereço/provider**: hoje o cadastro é *create-once* (a RPC recusa recriar
   se já existe provider completo). Não existe "editar meu endereço" — sem isso, um provider
   estático nunca corrige um pino errado depois do cadastro inicial.
3. **Entidade real de "pedido de serviço"**: se a visibilidade da localização em tempo real deve
   depender de haver um pedido ativo (ver §5.3), essa tabela/fluxo precisa ser desenhado — hoje
   não existe.
4. **Lista real de providers por serviço/categoria perto do cliente**: o mapa provavelmente nasce
   junto de "buscar provider perto de mim que oferece X" — hoje o catálogo de serviços mostrado ao
   cliente é o `DemoServiceCatalog` estático, desconectado de `service_categories`/`services`
   (que já existem no banco, usados hoje só no cadastro do provider). Conectar as duas pontas é
   pré-requisito para o mapa fazer sentido no fluxo de pedido.

## 7. Resumo do gap (o que falta, por camada)

| Camada | Existe? | Falta |
|---|---|---|
| Coordenada estática no banco | ✅ (`addresses.latitude/longitude`) | Geocoding automático; tela de edição |
| Coordenada dinâmica no banco | ❌ | Tabela `provider_live_locations` + RLS + expiração/presença |
| Marcação estático vs dinâmico | ❌ | Coluna `providers.location_mode` (ou equivalente) + regra de produto |
| Leitura pública de provider/endereço | ❌ (RLS só permite "ver a si mesmo") | Política nova + view que mascare dados sensíveis |
| Transporte em tempo real | ❌ | Dependência `realtime-kt` + canal (Postgres Changes ou Broadcast/Presence) |
| Captura de GPS no device do provider | ❌ | Permissões, `FusedLocationProviderClient`, Foreground Service, tela de opt-in |
| SDK de mapa no client | ❌ | Escolha de SDK (Google Maps Compose recomendado), chave de API, billing |
| Vínculo com pedido de serviço real | ❌ (`RequestService` é mock) | Modelo de "pedido" persistido, se a visibilidade for por pedido ativo |
| Catálogo de serviços real conectado ao mapa | ❌ (`DemoServiceCatalog` é estático) | Ligar `service_categories`/`services` (já existem) ao fluxo do cliente |

## 8. Perguntas em aberto para decidir antes de implementar

1. Provider escolhe estático/dinâmico no cadastro, ou isso é automático (ex.: dinâmico só enquanto
   estiver "em rota" para um pedido específico)?
2. A localização em tempo real fica visível para **qualquer** cliente logado o tempo todo, ou só
   para quem tem um pedido ativo com aquele provider?
3. Google Maps (com billing) é aceitável, ou preferimos evitar dependência de billing do Google
   (Mapbox/OSM)?
4. Precisamos de trilha/histórico de trajeto (replay), ou só a posição atual já resolve?
5. Qual a frequência aceitável de atualização (a cada quantos segundos/metros) — isso define
   consumo de bateria do provider e volume de mensagens realtime?
6. Vale a pena já entrar com "pedido de serviço" persistido nesta mesma iniciativa, já que o mapa
   só faz sentido pleno quando ligado a um pedido real, ou o mapa entra primeiro como "descobrir
   providers perto de mim" (sem pedido) e o vínculo com pedido vem depois?

## 9. Ordem sugerida de investigação/implementação (alto nível, não é o plano final)

1. Decisões de produto da seção 8.
2. Modelagem (`location_mode`, `provider_live_locations`, RLS/view pública) + geocoding do endereço
   estático no cadastro existente.
3. Ligar `RequestService`/Home ao catálogo real (`service_categories`/`services`) — sem isso o mapa
   fica solto, sem fluxo de negócio real em volta.
4. Cliente: tela de mapa só com pinos estáticos (mais simples, sem realtime) lendo a leitura
   pública nova.
5. Provider: opt-in + captura de GPS + escrita periódica em `provider_live_locations`.
6. Cliente: assinatura realtime dos pinos dinâmicos sobre o mesmo mapa da etapa 4.
7. Expiração/presença, testes de carga com múltiplos providers simultâneos, revisão de bateria e de
   política de privacidade/Play Store.
