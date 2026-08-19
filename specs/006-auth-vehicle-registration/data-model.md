# Data Model: Sessão autenticada e cadastro de veículos

## Overview

O modelo separa identidade autenticada, dados autorizados da conta, veículos e estado transitório de
interface. UUIDs são strings opacas no Android e `uuid` no PostgreSQL. Nenhum modelo de UI contém
token; senha e rascunho de veículo vivem somente em memória enquanto suas telas existem.

## 1. AppSessionState

Estado raiz mutuamente exclusivo, mantido pelo `SessionRepository` e exposto pelo
`SessionViewModel`.

| Variante | Dados | Significado |
|---|---|---|
| `Initializing` | nenhum | SDK Auth ainda está carregando a sessão local |
| `Verifying` | nenhum dado renderizável | existe uma operação de validação/bootstrap em andamento |
| `Unauthenticated` | motivo opcional tipado | não há sessão válida e só o fluxo público pode aparecer |
| `OfflineVerification` | nenhum dado renderizável | sessão local preservada, mas rede impediu validação |
| `ProfileBlocked` | `Missing`, `Inactive` ou `Inconsistent` | identidade válida sem perfil autorizado para Home |
| `Authenticated` | `AccountSnapshot` | identidade, perfil e seleção de veículo foram validados |

### Transitions

```text
App start -> Initializing
Initializing -> Unauthenticated                  (nenhuma sessão)
Initializing -> Verifying                        (sessão armazenada encontrada)
Verifying -> Authenticated                       (identidade + perfil válidos)
Verifying -> OfflineVerification                 (rede/timeout; sessão preservada)
Verifying -> ProfileBlocked                      (perfil ausente/inativo/inconsistente)
Verifying -> Unauthenticated                     (sessão inválida/expirada)
OfflineVerification -> Verifying                 (retry)
ProfileBlocked -> Verifying                      (retry)
ProfileBlocked -> Unauthenticated                (Sair)
Unauthenticated -> Verifying                     (login ou cadastro finalizado)
Authenticated -> Verifying                       (refresh explícito/bootstrap)
Authenticated -> OfflineVerification             (refresh expirado sem rede)
Authenticated -> Unauthenticated                 (logout, revogação ou sessão inválida)
```

Invariante: somente `Authenticated` pode entregar perfil ou veículo à UI. Toda saída dessa variante
remove imediatamente o snapshot anterior.

## 2. AccountSnapshot

Representa a conta autorizada no momento do bootstrap.

| Campo | Tipo | Regra |
|---|---|---|
| `userId` | UUID/string | vem exclusivamente da sessão Supabase |
| `authEmail` | string | e-mail retornado pela identidade validada |
| `profile` | `Profile` | deve possuir o mesmo `id` e estar ativo |
| `primaryVehicle` | `Vehicle?` | null ou um veículo ativo/principal do mesmo usuário |

Validações:

- `profile.id == userId`;
- `profile.email` corresponde a `authEmail` sem diferença de caixa;
- nome, e-mail e telefone do perfil são não vazios;
- exatamente um entre CPF e CNPJ existe;
- `profile.active == true`;
- se houver veículo, `vehicle.userId == userId`, `active == true` e `isPrimary == true`.

## 3. Profile

Mapeia a projeção de `public.profiles` necessária nesta feature.

| Campo Android | Coluna | Tipo | Regra |
|---|---|---|---|
| `id` | `id` | UUID/string | PK e FK 1:1 para `auth.users.id` |
| `name` | `name` | string | obrigatório, trim externo, não vazio |
| `email` | `email` | string | obrigatório, normalizado para minúsculas |
| `phone` | `phone` | string | obrigatório, dígitos normalizados |
| `cpf` | `cpf` | string? | exatamente um documento; nunca logado completo |
| `cnpj` | `cnpj` | string? | exatamente um documento; nunca logado completo |
| `avatarUrl` | `avatar_url` | string? | HTTPS quando preenchido; falha visual usa iniciais |
| `active` | `active` | boolean | precisa ser true para liberar a área autenticada |

Derivações:

- `displayName`: `name` após trim;
- `initials`: primeira letra de até duas palavras significativas, em caixa alta; fallback neutro se
  o nome for inconsistente;
- CPF/CNPJ não são exibidos nas telas desta feature.

## 4. Vehicle

Mapeia `public.vehicles`.

| Campo Android | Coluna | Tipo | Regra |
|---|---|---|---|
| `id` | `id` | UUID/string | gerado pelo banco |
| `userId` | `user_id` | UUID/string | sempre `auth.uid()`, nunca vem do formulário |
| `brand` | `brand` | string | obrigatório, trim externo, não vazio |
| `model` | `model` | string | obrigatório, trim externo, não vazio |
| `version` | `version` | string? | trim externo; vazio vira null |
| `manufactureYear` | `manufacture_year` | int? | 1900 até ano atual + 1 |
| `modelYear` | `model_year` | int? | 1900 até ano atual + 1 |
| `plate` | `plate` | string? | canônica, sete caracteres, legado ou Mercosul |
| `renavam` | `renavam` | string? | exatamente 11 dígitos, preserva zeros |
| `chassis` | `chassis` | string? | 17 caracteres VIN em caixa alta, sem I/O/Q |
| `color` | `color` | string? | trim externo; vazio vira null |
| `fuelType` | `fuel_type` | string? | texto livre nesta entrega |
| `transmission` | `transmission` | string? | texto livre nesta entrega |
| `engine` | `engine` | string? | trim externo; vazio vira null |
| `mileage` | `mileage` | int? | maior ou igual a zero |
| `isPrimary` | `is_primary` | boolean | controlado exclusivamente pelos RPCs |
| `active` | `active` | boolean | true na criação; inativo nunca é selecionável |
| `createdAt` | `created_at` | instante/string | gerado pelo banco |
| `updatedAt` | `updated_at` | instante/string | gerado/atualizado pelo banco |

### Database invariants

- placa, Renavam e chassi são únicos quando não nulos;
- `is_primary` implica `active`;
- no máximo um veículo principal por usuário;
- após qualquer criação concluída para uma conta com veículos, exatamente um veículo ativo é
  principal;
- marca/modelo não podem ser vazios após trim;
- regras de representação e intervalos são repetidas no RPC/constraints.

## 5. VehicleDraft

Entrada imutável normalizada criada somente no submit do formulário.

| Campo | Tipo | Origem |
|---|---|---|
| campos descritivos/documentais | mesmos campos editáveis de `Vehicle` | texto da UI normalizado |
| `makePrimary` | boolean | escolha do usuário; ignorada como false se for o primeiro veículo |

Não contém `id`, `userId`, `active`, `isPrimary`, timestamps ou qualquer valor de sessão. O
repositório converte o draft em parâmetros do RPC. O rascunho editável não usa `SavedStateHandle`,
DataStore ou persistência; expiração da sessão substitui o estado por valores vazios e remove a rota.

### Local field errors

```text
VehicleField = BRAND | MODEL | VERSION | MANUFACTURE_YEAR | MODEL_YEAR |
               PLATE | RENAVAM | CHASSIS | COLOR | FUEL_TYPE |
               TRANSMISSION | ENGINE | MILEAGE

VehicleFieldError = REQUIRED | INVALID_FORMAT | OUT_OF_RANGE | UNAVAILABLE
```

O primeiro erro segue a ordem visual e recebe foco. Conflitos remotos marcam somente placa,
Renavam ou chassi quando a constraint allowlisted identificar o campo.

## 6. VehicleOnboardingPending

Preferência local simples representada como `Set<String>` de UUIDs.

| Operação | Efeito |
|---|---|
| `markPending(userId)` | adiciona o UUID depois de perfil criado e antes do sucesso do cadastro |
| `isPending(userId)` | decide convite versus Home após sessão validada |
| `resolve(userId)` | remove o UUID antes de navegar por qualquer escolha explícita |

Não armazena escolha, veículo, documento, senha ou token. Logout não remove a pendência; ela precisa
ser reapresentada quando a mesma conta voltar. Limpeza de dados/desinstalação pode remover a
preferência, o que está fora da garantia de simples encerramento/reabertura.

## 7. Screen states

### LoginUiState

- campos: `email`, `password`, erros por campo;
- operação: `Editing`, `Submitting`, `Failure(reason)`;
- razões remotas: `InvalidCredentials`, `Offline`, `Timeout`, `RateLimited`, `Service`, `Unknown`;
- senha não é salva e é limpa após falha remota ou saída da tela.

### HomeUiState

```text
Loading
Content(profile, primaryVehicle, services)
NoVehicle(profile, services)
Failure(reason)
```

`Content` e `NoVehicle` só são construídos de `AppSessionState.Authenticated`.

### GarageUiState

```text
Loading
Empty
Content(vehicles, changingPrimaryVehicleId?)
Failure(reason)
```

Lista contém somente veículos ativos do usuário atual. Durante troca de principal, taps concorrentes
são ignorados; sucesso atualiza a lista de forma que exatamente um item fique selecionado.

### VehicleFormUiState

- campos editáveis em memória;
- `existingVehicleCount` decide visibilidade da opção de principal;
- operação: `Editing`, `Submitting`, `Success`, `Failure(reason)`;
- `isInputEnabled` é derivado da operação;
- origem: `ONBOARDING` ou `GARAGE`, usada somente para destino após sucesso/voltar.

### SessionGateUiState

Derivado diretamente de `AppSessionState`:

- initializing/verifying: progresso sem ações;
- offline: mensagem + `Tentar novamente`;
- perfil bloqueado: mensagem + `Tentar novamente` + `Sair`.

## 8. Relationships

```text
auth.users (1) ───── (1) profiles
                         │
                         └──── (0..N) vehicles

AppSessionState.Authenticated
  └── AccountSnapshot
       ├── Profile (exactly 1)
       └── primaryVehicle (0..1 active Vehicle)

VehicleOnboardingPending
  └── references userId only; no database foreign key
```

## 9. Sensitive-data lifecycle

- senha: memória do login/cadastro até conclusão/falha/saída; nunca persistida ou logada;
- token: gerido exclusivamente pelo Supabase Auth e removido localmente no logout; excluído de
  backup/transferência;
- CPF/CNPJ: carregados somente quando necessários à consistência do perfil; nunca renderizados ou
  registrados nesta feature;
- Renavam/chassi: rascunho apenas em memória e DTO de request; mensagens usam apenas o nome do campo;
- logout/troca/expiração: remove `AccountSnapshot`, estados Home/Garagem e rascunho antes de mostrar
  outro fluxo.
