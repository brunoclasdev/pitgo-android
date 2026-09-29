# Contract: Gestão autorizada do catálogo

## Caller

Somente usuário autenticado com nível de dashboard autorizado (`MANAGER`, `ADMIN` ou `OWNER`) conforme a política de gestão do projeto. Prestador e cliente não podem executar estas operações.

## Operations

### `create_service_category`

Input: `name`, `description`, `active`.

Rules: nome normalizado e único; descrição dentro do limite; registra ação no audit log.

### `create_service`

Input: `category_id`, `name`, `description`, `dispatch_kind`, `active`.

Rules: categoria existe; nome único dentro da categoria; dispatch kind válido; registra ação.

Na carga inicial, contar até 15 especialidades BROWSE por categoria; excluir os serviços implícitos
Guincho/PitTec da contagem. Preservar a unicidade e identidade desses serviços implícitos.
Especialidades dessas categorias são elegíveis somente para prestadores fixos.

### `update_service_catalog_item`

Input: `entity_id`, campos editáveis e `active`.

Rules: preserva relações existentes; desativação impede novas seleções, mas não remove ofertas históricas; registra ação.

### `list_service_catalog`

Output: categorias ativas e serviços ativos agrupados por categoria, ordenados por nome.

Access: leitura autenticada conforme as políticas atuais; a edição permanece restrita a staff.

## Failure contract

- `STAFF_REQUIRED` / `HIERARCHY_VIOLATION`: usuário sem permissão.
- `CATEGORY_NOT_FOUND`: categoria inexistente ou inativa.
- `DUPLICATE_NAME`: nome repetido na mesma categoria.
- `INVALID_CATALOG_INPUT`: nome, descrição ou dispatch kind inválido.
- Falhas não devem expor chaves, SQL ou valores sensíveis na UI.

## Assinaturas consolidadas na implementação

Todas as mutações retornam `jsonb` com a linha gravada, inclusive `id`, `active`,
`created_at` e `updated_at`; não retornam dados do operador.

```sql
create_service_category(p_name text, p_description text default null,
  p_active boolean default true) returns jsonb
create_service(p_category_id uuid, p_name text, p_description text default null,
  p_dispatch_kind text default 'BROWSE', p_active boolean default true) returns jsonb
update_service_catalog_item(p_entity_type text, p_entity_id uuid, p_name text,
  p_description text, p_active boolean) returns jsonb
```

`p_entity_type` é obrigatório e aceita `category` ou `service`. Atualização recebe
os valores finais de nome, descrição e ativo; `null` na descrição remove o texto.
Nomes são aparados e únicos sem distinção de maiúsculas; limite de 150 caracteres,
descrição de 1.000. Não é possível mover um serviço para outra categoria nem alterar
seu dispatch kind pela atualização.

Criação de serviços permite somente `BROWSE`. Editar os dois serviços implícitos ou
suas categorias retorna `IMPLICIT_SERVICE_PROTECTED`, preservando sua identidade e
disponibilidade fora deste catálogo. Serviços inexistentes retornam `SERVICE_NOT_FOUND`.
Erros de catálogo usam `22023`; falta de autorização usa `42501`/`STAFF_REQUIRED`.
Auditoria chama `record_dashboard_action` na mesma transação. Nenhuma operação de
gestão impõe limite de 15.

`list_service_catalog` é um contrato de leitura, atendido pelas consultas autenticadas
existentes às tabelas: categoria ativa, serviço ativo, ordenação por nome e ID e
paginação de 100 itens. Não é criado um RPC adicional com esse nome.
