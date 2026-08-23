# Contract: Supabase — cadastro de empresa prestadora

## Escopo

Este contrato define as leituras autenticadas do catálogo/agregado próprio e a única mutação usada
pela feature. Ele não autoriza criação ou edição de categoria/serviço, edição posterior da empresa,
múltiplos endereços ou escrita direta nas tabelas.

## Pré-condições de schema

- `providers.id` é UUID PK/FK de `auth.users.id`; o RPC ainda exige profile ativo antes de criar
  o provider, então a regra "só empresa com profile" continua valendo sem a FK apontar para
  `profiles.id`;
- `addresses.provider_id` referencia `providers.id` e é único no cadastro inicial;
- `services.category_id` referencia `service_categories.id`;
- `service_categories` tem nome único case-insensitive e `services` tem nome único
  case-insensitive por categoria, após preflight de dados existentes;
- `provider_services(provider_id, service_id)` é único;
- a coluna física de bairro é `addresses.neighbornhood`;
- se o ambiente tiver `provider` singular, outra grafia de FK da categoria ou as duas grafias de
  bairro, a migration deve reconciliar por preflight e abortar diante de ambiguidade. Nunca criar
  duas fontes equivalentes;
- os validadores privados já existentes de telefone/CNPJ são reutilizados.

## Leituras

Todas exigem JWT autenticado e são paginadas até uma página menor que a página técnica.

### Categorias

Origem: `public.service_categories`.

Resposta mínima:

```text
id: UUID opaco
name: String não vazio
description: String? (se disponível no contrato físico)
```

Ordenação estável: nome case-insensitive, depois ID. Lista vazia é `Empty`, não erro.

### Serviços por categoria

Origem: `public.services`, filtrada por `category_id` selecionado.

Resposta mínima:

```text
id: UUID opaco
category_id: UUID
name: String não vazio
description: String?
```

O Repository rejeita qualquer registro cujo `category_id` não seja o solicitado. Lista vazia é
estado explícito. A feature não envia nome/descrição de serviço ao salvar.

### Agregado próprio

O bootstrap consulta `providers` para o usuário derivado da sessão e, se existir, carrega:

- exatamente um provider;
- exatamente um endereço;
- uma ou mais associações `provider_services`;
- nomes de serviço e categoria somente leitura.

Zero provider significa que o formulário pode ser aberto. Provider sem exatamente um endereço ou
sem oferta é inconsistência de contrato e não deve ser reparado silenciosamente pelo cliente.

## RPC de criação

### Assinatura

```sql
public.create_provider_registration(
  p_provider jsonb,
  p_address jsonb,
  p_offers jsonb
) returns jsonb
```

### Autorização

- `SECURITY DEFINER` com `SET search_path = ''`;
- `EXECUTE` revogado de `public` e `anon`, concedido somente a `authenticated`;
- relações e funções totalmente qualificadas;
- `auth.uid()` obrigatório e profile correspondente ativo;
- nenhum argumento de owner/provider/address/category.

### Payload

`p_provider`:

```json
{
  "name": "Oficina Exemplo",
  "description": "Descrição comercial opcional",
  "email": "contato@example.com",
  "phone": "11999999999",
  "cnpj": "<CNPJ_NORMALIZADO>"
}
```

`p_address`:

```json
{
  "street": "Rua Exemplo",
  "number": "s/n",
  "neighborhood": "Centro",
  "zip_code": "01001000",
  "city": "São Paulo",
  "state": "SP",
  "country": "BR",
  "complement": null,
  "latitude": null,
  "longitude": null
}
```

`p_offers` é array JSON não vazio e sem limite de produto:

```json
[
  {
    "service_id": "<SERVICE_UUID>",
    "price": "320.00",
    "duration_minutes": 90,
    "description": "Descrição comercial opcional",
    "is_free": false
  },
  {
    "service_id": "<OTHER_SERVICE_UUID>",
    "price": "0.00",
    "duration_minutes": 30,
    "description": null,
    "is_free": true
  }
]
```

`price` viaja como string decimal canônica para não passar por `Double`. `is_free` valida a escolha
explícita, mas somente `price` é persistido. Categoria é derivada de `service_id`.

### Validação definitiva

Antes do primeiro INSERT, a função deve:

1. normalizar/validar provider e endereço;
2. exigir `p_offers` como array com pelo menos um item;
3. rejeitar `service_id` repetido;
4. validar todos os serviços e seus vínculos de categoria ainda existentes;
5. validar preço: escala <= 2 e `(is_free && price=0) || (!is_free && price>0)`;
6. validar duração inteira entre 1 e 10.080;
7. validar CEP/UF/país e o par de coordenadas.

Só depois insere provider, endereço e todas as ofertas. Qualquer exceção não tratada reverte a
execução inteira.

### Concorrência e idempotência

1. Adquirir `pg_advisory_xact_lock` com namespace estável e `auth.uid()`.
2. Se não existe provider, validar e criar o agregado, retornando `CREATED`.
3. Se existe provider com exatamente um endereço e uma ou mais ofertas, não alterar nada e retornar
   `ALREADY_REGISTERED`.
4. Se existe agregado parcial, retornar falha de contrato neutra.

Resposta fechada:

```json
{
  "outcome": "CREATED",
  "offer_count": 2
}
```

`outcome` aceita somente `CREATED` ou `ALREADY_REGISTERED`. A resposta não precisa retornar
`provider_id`, pois ele já é a conta autenticada. Depois da RPC, o Repository recarrega o agregado
próprio antes de emitir sucesso.

## RLS e grants

| Tabela | `anon` | `authenticated` | Policy SELECT |
|---|---|---|---|
| `providers` | nenhum | SELECT | `id = (select auth.uid())` |
| `addresses` | nenhum | SELECT | `provider_id = (select auth.uid())` |
| `provider_services` | nenhum | SELECT | `provider_id = (select auth.uid())` |
| `service_categories` | nenhum | SELECT | catálogo disponível |
| `services` | nenhum | SELECT | catálogo disponível |

Não há grant/policy de INSERT, UPDATE ou DELETE para o cliente nesta feature. A migration não deve
remover policies de outras relações de endereço sem antes provar que pertencem ao mesmo contrato;
ela adiciona policies nomeadas e falha em conflito de ownership.

## Resultado e falhas de domínio

| Sinal interno | Resultado do Repository | Comportamento UI |
|---|---|---|
| `CREATED` + agregado confirmado | `Created(summary)` | confirmação/resumo |
| `ALREADY_REGISTERED` + agregado completo | `AlreadyRegistered(summary)` | resumo existente |
| timeout | `Timeout` | preservar draft e tentar novamente |
| falha de transporte | `Offline` | preservar draft e tentar novamente |
| HTTP 401/403 ou SQLSTATE `42501` | `SessionExpired` | limpar estado e refrescar gate |
| HTTP 5xx | `ServiceUnavailable` | mensagem neutra/retry |
| `23505` + `providers_email_unique` | `EmailUnavailable` | erro no e-mail |
| `23505` + `providers_cnpj_unique` | `CnpjUnavailable` | erro no CNPJ |
| `23505` + provider/service UNIQUE | `DuplicateService` | erro na oferta |
| serviço/FK indisponível | `CatalogChanged` | recarregar catálogo/reselecionar |
| `22023`/`23514` | `Contract` | mensagem neutra, sem raw detail |
| outro | `Unknown` | mensagem genérica |

O mapeamento pode inspecionar somente códigos e nomes allow-listed. Mensagem, details, headers,
payload e Throwable completo nunca chegam à UI, log ou crash reporting.

## Operações explicitamente ausentes

- criação/edição de `service_categories` ou `services`;
- update/delete de provider/endereço/ofertas;
- geocodificação, CEP remoto ou localização;
- service role, Edge Function ou segredo administrativo;
- uma RPC por oferta;
- persistência local do draft.
