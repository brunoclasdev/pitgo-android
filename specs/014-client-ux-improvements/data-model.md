# Data Model: Melhoria de UX das telas do cliente

Esta feature é majoritariamente de apresentação (estados de UI, navegação, mapa). Só uma entidade
persistida é realmente nova (`ClientAddress`); as demais já existem em features anteriores e são
apenas referenciadas/exibidas de forma diferente. Uma preferência local nova (`AppearanceMode`)
também é adicionada, sem backend.

## 1. `ClientAddress` (nova)

Endereço privado do cliente, usado para preencher e revisar dados de contato/atendimento. Não tem
relação com `public.addresses` (endereço público/geocoded do prestador em modo `FIXED`) — são
conceitos e ciclos de vida diferentes; ver `research.md` #1 para a justificativa de não reaproveitar
a tabela do prestador.

| Campo | Tipo | Regra |
|---|---|---|
| `client_id` | UUID PK/FK | sempre `auth.uid()` de conta `CLIENT`; uma linha por cliente (upsert) |
| `street` | text | obrigatório, trim, não vazio, até 160 caracteres (mesmo limite de `addresses.street`) |
| `number` | text | obrigatório, trim, não vazio, até 40 caracteres |
| `neighborhood` | text | obrigatório, trim, não vazio, até 100 caracteres |
| `zip_code` | text | obrigatório, 8 dígitos (mesma regra de `addresses.zip_code`) |
| `city` | text | obrigatório, trim, não vazio, até 100 caracteres |
| `state` | text | obrigatório, UF válida (mesma lista de `addresses.state`) |
| `country` | text | fixo `'BR'` (mesmo padrão de `addresses.country`) |
| `complement` | text nullable | opcional, trim quando presente |
| `latitude`, `longitude` | numeric nullable | preenchidos pela busca de CEP/geocodificação, não editáveis manualmente |
| `created_at`, `updated_at` | timestamptz | server-side |

**Regras**: um cliente tem no máximo um endereço (upsert por `client_id`); ausência de linha =
"endereço não cadastrado" (estado já coberto pela Home/Perfil). RLS restringe cada cliente à
própria linha; nenhuma leitura/escrita direta é concedida ao APK — apenas via
`get_my_client_address()` / `upsert_my_client_address(...)`, ambas `SECURITY DEFINER`.

**Estados de UI**: `Loading`, `Empty` (sem endereço), `Content(address)`, `Saving`, `Error` — mesmo
vocabulário de estado usado no restante do app (constituição, princípio III).

## 2. `AppearanceMode` (novo, local)

```text
LIGHT
DARK
SYSTEM
```

Preferência local persistida via DataStore (`AppearancePreferences`), sem backend. `SYSTEM` é o
padrão até a primeira escolha manual do usuário (consistente com a constituição: "respeitar o
sistema por padrão e persistir seleção manual"). Consumida por `MainActivity` para decidir o
`Boolean?` passado a `PitGoTheme(darkTheme = ...)` (`null`/ausência de override → segue
`isSystemInDarkTheme()`).

## 3. `ClientCurrentLocation` (nova, persistência mínima e efêmera)

Leitura pontual de GPS do cliente (via `DeviceLocationRepository`, decisão de `/speckit-clarify`),
capturada ao abrir/reabrir a tela de acompanhamento. **Precisa ser persistida em uma tabela
mínima** — ao contrário do que a primeira versão deste documento assumia, ela não pode "viver só em
memória no dispositivo do cliente": para o prestador ver essa posição no próprio mapa (US3), ela
precisa atravessar o backend. Espelha exatamente o padrão já usado por `provider_current_locations`
(feature 013), sem os campos de lease/tracker (não há disputa entre múltiplos publicadores — é uma
leitura única por abertura de tela, não um stream contínuo).

| Campo | Tipo | Regra |
|---|---|---|
| `appointment_id` | UUID PK/FK | referencia `appointments`; uma linha por atendimento, sobrescrita a cada nova leitura |
| `latitude` | numeric | entre -90 e 90 |
| `longitude` | numeric | entre -180 e 180 |
| `accuracy_m` | numeric | `> 0` e `<= 5000` (mesma tolerância de `DeviceLocationRepository.currentClientFix()`, já usada por `ServiceMapViewModel`; mais permissiva que os 100 m exigidos do prestador, pois aqui o objetivo é centralizar o mapa, não guiar uma busca) |
| `captured_at` | timestamptz | quando o dispositivo capturou o fix |
| `published_at` | timestamptz | definido pelo servidor no momento do upsert |

**Regras**: só existe enquanto o atendimento estiver `ACCEPTED`/`IN_PROGRESS`; apagada ao chegar a
um estado terminal (mesmo padrão de limpeza já usado por `provider_current_locations`/
`provider_discovery_locations` em outras features). RLS restringe escrita ao cliente dono do
atendimento e leitura aos dois participantes (cliente e prestador), via RPCs `SECURITY DEFINER` —
ver `contracts/client-location-rpcs.md`.

## 4. Entidades existentes referenciadas (sem alteração de esquema)

- **`MobileDispatchSnapshot` / `DispatchAppointment` / `MobileCandidate`** (feature 013): status,
  preço, deadline, revisão — consumidos pelas correções de FR-002/FR-003 (loading inline,
  formatação de distância) sem mudança de estrutura.
- **`ProviderCurrentLocation`** (feature 013, canal `appointment-location:<id>`): posição em tempo
  real do prestador durante um atendimento aceito; consumida pelo mapa do cliente (já existente) e
  pelo novo mapa do prestador (item novo desta feature), sem mudança de esquema.
- **`Vehicle` / veículo padrão** (feature de Garagem): já modelado; esta feature só adiciona
  navegação a partir de Home/Perfil, sem novos campos.
- **`ProviderTrackingState` / `DispatchResumeState`**: usados pela correção do botão voltar
  (FR-017/FR-018); o campo `revision` já existente em `MobileDispatchSnapshot`/`DispatchAppointment`
  passa a ser comparado para decidir se a navegação automática deve disparar novamente.

## 5. Relações

```text
Client (auth.uid()) 1 ── 0..1 ClientAddress
Client 1 ── 0..1 AppearanceMode (local, DataStore)
Client 1 ── 0..1 Vehicle padrão (já existente)
Appointment (já existente) 1 ── 0..1 ClientCurrentLocation (nova, efêmera)
Appointment 1 ── 0..1 ProviderCurrentLocation (já existente, tempo real)
```

Duas tabelas novas: `client_addresses` (item 1) e `client_current_locations` (item 3); nenhuma
alteração em tabelas/RLS/RPCs das features 009/011/013 além da leitura já autorizada de
`ProviderCurrentLocation` pelo novo mapa do prestador (mesma autorização de participante do
atendimento já existente).
