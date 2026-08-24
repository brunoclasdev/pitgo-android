# Data Model: Corrigir cadastro de veículos e erros por campo

## Vehicle

Representa um veículo persistido e pertencente a uma conta cliente.

| Campo | Tipo | Obrigatório | Regras |
|---|---|---:|---|
| `id` | UUID | sim | Gerado pelo banco; chave primária |
| `user_id` | UUID | sim | Derivado exclusivamente de `auth.uid()`; FK para `profiles.id` |
| `brand` | text | sim | `trim`, não vazio |
| `model` | text | sim | `trim`, não vazio |
| `version` | text | não | `trim`; vazio vira `null` |
| `manufacture_year` | integer | não | 1900 até ano corrente + 1 |
| `model_year` | integer | não | 1900 até ano corrente + 1 |
| `plate` | text | não | Canônica em maiúsculas e sem separador; `AAA9999` ou `AAA9A99` |
| `renavam` | text | não | Exatamente 11 dígitos; preserva zeros iniciais |
| `chassis` | text | não | 17 caracteres `[A-HJ-NPR-Z0-9]`, em maiúsculas |
| `color` | text | não | `trim`; vazio vira `null` |
| `fuel_type` | text | não | `trim`; vazio vira `null` |
| `transmission` | text | não | `trim`; vazio vira `null` |
| `engine` | text | não | `trim`; vazio vira `null` |
| `mileage` | integer | não | Maior ou igual a zero |
| `is_primary` | boolean | sim | Primeiro ativo é `true`; no máximo um ativo principal por conta |
| `active` | boolean | sim | `true` na criação |
| `created_at` | timestamptz | sim | Gerado pelo banco |
| `updated_at` | timestamptz | sim | Atualizado em criação/troca de principal |

### Relacionamentos e invariantes

- Uma `ClientAccount` possui zero ou mais `Vehicle` por `vehicles.user_id`.
- Um `Vehicle` pertence a exatamente uma conta cliente e nunca troca de proprietário nesta feature.
- Quando uma conta possui ao menos um veículo ativo, exatamente um pode estar principal depois de
  uma criação/troca concluída.
- Placa, Renavam e chassi são únicos globalmente quando presentes; múltiplos `null` são permitidos.
- Veículo principal precisa estar ativo.

## ClientAccount

Contexto autenticado autorizado a operar veículos.

| Campo conceitual | Origem | Regra |
|---|---|---|
| `userId` | `auth.uid()` | Não é aceito do formulário/RPC |
| `accountType` | `account_roles.account_type` | Deve ser `CLIENT` |
| `profileActive` | `profiles.active` | Deve ser verdadeiro |

Uma conta `PROVIDER`, uma sessão ausente ou uma conta cliente sem perfil ativo não pode criar nem
selecionar veículo.

### Projeção do principal na sessão

`AccountSnapshot.primaryVehicle` pode ser atualizado em memória somente com o `Vehicle` retornado
pela RPC e somente quando `vehicle.userId` coincide com a conta atual, `vehicle.active=true` e
`vehicle.isPrimary=true`. Drafts e IDs informados pela UI nunca alteram essa projeção. A próxima
carga remota permanece a autoridade definitiva.

## VehicleFormDraft

Estado textual, somente em memória, usado enquanto a pessoa preenche o formulário.

- Contém os treze campos visíveis e `makePrimary`.
- Valores textuais não são aparados durante a digitação, exceto normalização visual de placa,
  Renavam e chassi onde isso melhora a entrada.
- Na submissão, produz `VehicleDraft` normalizado somente quando não há erros.
- Não contém `userId`, `vehicleId`, `active` nem timestamps.
- Não é persistido em DataStore ou `SavedStateHandle`.

## VehicleDraft

Payload normalizado enviado à RPC.

- `brand` e `model`: strings não vazias.
- Campos opcionais textuais: `String?`, nunca string vazia.
- Anos e quilometragem: `Int?` já validados.
- `makePrimary`: `false` para o primeiro veículo também é válido; o banco detecta que ele é o
  primeiro e força `is_primary=true`.
- Não possui propriedade de ownership.

## VehicleField e VehicleFieldError

`VehicleField` preserva exatamente a ordem visual:

1. `BRAND`
2. `MODEL`
3. `VERSION`
4. `MANUFACTURE_YEAR`
5. `MODEL_YEAR`
6. `PLATE`
7. `RENAVAM`
8. `CHASSIS`
9. `COLOR`
10. `FUEL_TYPE`
11. `TRANSMISSION`
12. `ENGINE`
13. `MILEAGE`

Erros possíveis:

| Erro | Uso |
|---|---|
| `REQUIRED` | Marca ou Modelo ausente após `trim` |
| `INVALID_FORMAT` | Placa, Renavam, chassi ou número textual inválido |
| `OUT_OF_RANGE` | Ano fora do intervalo ou quilometragem negativa |
| `UNAVAILABLE` | Placa, Renavam ou chassi já cadastrado |

O mapa de erros pode conter vários campos ao mesmo tempo. A primeira chave seguindo a ordem acima
gera um novo `VehicleFocusRequest(field, requestId)`.

## VehicleFormOperation

Estados mutuamente exclusivos:

```text
Loading -> Editing
Loading -> Failure
Editing -> Editing (alteração/validação local)
Editing -> Submitting
Submitting -> Success
Submitting -> Editing (falha específica de campo)
Submitting -> Failure (falha geral recuperável)
Submitting -> Failure(SessionExpired)
Failure -> Editing (nova edição/tentativa)
```

- `Loading`: obtém a quantidade atual de veículos para configurar a opção de principal.
- `Editing`: permite alterações e submissão.
- `Submitting`: inputs/CTA bloqueados; toques adicionais são ignorados.
- `Failure`: mantém o draft em falhas recuperáveis; sessão expirada limpa o estado sensível.
- `Success`: terminal para a instância do formulário; dispara navegação uma única vez.

## VehicleMutationResult

Resultado do Repository, sem exceção bruta:

- `Success(vehicle)`
- `FieldFailure(field, error)`
- `Failure(reason)`

Falhas gerais: `Offline`, `Timeout`, `SessionExpired`, `RpcUnavailable`,
`ServiceUnavailable`, `VehicleUnavailable`, `Contract` e `Unknown`. Somente `FieldFailure` altera a
borda/supporting text de um campo.

## VehicleFormOrigin

| Origem | Conclusão bem-sucedida |
|---|---|
| `ONBOARDING` | Resolver pendência, atualizar sessão e substituir pilha pela Home |
| `GARAGE` | Voltar para a Garagem; `ON_RESUME` recarrega a lista |

A pendência de onboarding não é resolvida quando o formulário é apenas aberto. Ela é resolvida
após a criação confirmada ou pela ação explícita de pular o onboarding.
