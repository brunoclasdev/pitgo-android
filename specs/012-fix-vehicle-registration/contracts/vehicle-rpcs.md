# Contract: Vehicle RPCs

## Fronteira de acesso

- Transporte: Supabase PostgREST RPC autenticada.
- Papel aceito: `authenticated` com `account_roles.account_type = 'CLIENT'` e profile ativo.
- Ownership: sempre `auth.uid()`; nenhum parâmetro de owner é aceito.
- Tabelas continuam com RLS habilitado e sem DML direto concedido ao APK.
- Funções: `SECURITY DEFINER SET search_path = ''`, relações totalmente qualificadas.
- `EXECUTE`: revogado de `public` e `anon`; concedido somente a `authenticated`.

## `create_vehicle`

### Assinatura

```text
create_vehicle(
  p_brand text,
  p_model text,
  p_version text,
  p_manufacture_year integer,
  p_model_year integer,
  p_plate text,
  p_renavam text,
  p_chassis text,
  p_color text,
  p_fuel_type text,
  p_transmission text,
  p_engine text,
  p_mileage integer,
  p_make_primary boolean
) -> exatamente uma linha public.vehicles
```

### Comportamento

1. Confere sessão, papel CLIENT e profile ativo.
2. Adquire lock transacional por `auth.uid()`.
3. Normaliza e valida todos os parâmetros.
4. Determina `is_primary = p_make_primary OR nenhum veículo ativo existente`.
5. Se necessário, desmarca o principal anterior.
6. Insere o veículo com `user_id=auth.uid()`, `active=true` e opcionais vazios como `null`.
7. Retorna a linha criada. Qualquer erro reverte toda a operação.

O cliente Kotlin envia exatamente os nomes `p_*` acima e usa `decodeSingle<VehicleDto>()`.

## `set_primary_vehicle`

### Assinatura

```text
set_primary_vehicle(p_vehicle_id uuid) -> exatamente uma linha public.vehicles
```

### Comportamento

1. Confere sessão, papel CLIENT e profile ativo.
2. Adquire lock transacional por `auth.uid()`.
3. Localiza e bloqueia somente um veículo ativo do próprio usuário.
4. Se não existir, falha de forma neutra, sem revelar ownership/existência.
5. Desmarca outro principal e marca o alvo dentro da mesma transação.
6. Retorna a linha atualizada.

## Contrato de erros sanitizados

| Origem remota | Resultado de domínio | Tratamento de UI |
|---|---|---|
| `P2001` | `FieldFailure(BRAND, REQUIRED)` | Marca |
| `P2002` | `FieldFailure(MODEL, REQUIRED)` | Modelo |
| `P2003` | `FieldFailure(PLATE, INVALID_FORMAT)` | Placa |
| `P2004` | `FieldFailure(RENAVAM, INVALID_FORMAT)` | Renavam |
| `P2005` | `FieldFailure(CHASSIS, INVALID_FORMAT)` | Chassi |
| `P2006` | `FieldFailure(MANUFACTURE_YEAR, OUT_OF_RANGE)` | Ano de fabricação |
| `P2007` | `FieldFailure(MODEL_YEAR, OUT_OF_RANGE)` | Ano do modelo |
| `P2008` | `FieldFailure(MILEAGE, OUT_OF_RANGE)` | Quilometragem |
| `P2011` | `FieldFailure(PLATE, UNAVAILABLE)` | Placa |
| `P2012` | `FieldFailure(RENAVAM, UNAVAILABLE)` | Renavam |
| `P2013` | `FieldFailure(CHASSIS, UNAVAILABLE)` | Chassi |
| `23505` não traduzido pela RPC | `Failure(Unknown)` | Aviso geral; nenhum campo marcado |
| `PGRST202`, `PGRST203`, `42883` ou função 404 incompatível | `Failure(RpcUnavailable)` | Aviso geral temporariamente indisponível |
| HTTP 401/403 ou `42501` | `Failure(SessionExpired)` | Refresh/gate de sessão |
| timeout | `Failure(Timeout)` | Aviso geral |
| falha de conexão | `Failure(Offline)` | Aviso geral |
| HTTP 5xx | `Failure(ServiceUnavailable)` | Aviso geral |
| `P0002` | `Failure(VehicleUnavailable)` | Aviso neutro |
| qualquer resposta não permitida | `Failure(Unknown)` | Aviso geral |

Os códigos internos servem apenas ao mapeamento exato. A função converte as três violações únicas
esperadas para `P2011..P2013`, sem o detalhe padrão que conteria o valor. UI, logs e analytics nunca
recebem SQLSTATE, constraint, `message`, `details`, `hint`, URL, payload ou throwable bruto.

## Invariantes de compatibilidade

- Não sobrecarregar nomes de função com outra assinatura.
- O nome e os tipos de todos os parâmetros permanecem idênticos ao DTO Kotlin existente.
- A resposta mantém as colunas de `VehicleDto`.
- A migration pode ser aplicada quando a função não existe ou quando existe numa versão anterior.
- A migration solicita reload do schema cache do PostgREST; a validação remota confirma que a
  função está exposta antes do release.
