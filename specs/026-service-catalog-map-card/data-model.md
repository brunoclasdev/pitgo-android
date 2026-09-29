# Data Model: Catálogo de serviços por categoria e mapa PitTec

## Existing entities reused

### `service_categories`

- `id`: UUID, identidade da categoria.
- `name`: texto único sem distinção de maiúsculas/minúsculas, entre 1 e 150 caracteres.
- `description`: texto opcional até 1000 caracteres.
- `active`: estado de disponibilidade já usado pelo catálogo.
- `created_at`, `updated_at`: auditoria temporal.

### `services`

- `id`: UUID, identidade do serviço.
- `category_id`: UUID obrigatório para `service_categories`.
- `name`: único dentro da categoria, normalizado, até 150 caracteres.
- `description`: opcional até 1000 caracteres.
- `active`: serviço disponível para novas seleções.
- `dispatch_kind`: `BROWSE`, `MOBILE_SEQUENTIAL` ou `PITTEC_AUTO_SEQUENTIAL`.
- `created_at`, `updated_at`: auditoria temporal.

### `provider_services`

- `provider_id`, `service_id`: vínculo único por prestador e serviço.
- `price`, `duration_minutes`, `description`: dados atuais da oferta.
- A oferta só é criada para serviços selecionados; não há associação automática dos demais itens da categoria.

### `provider_categories`

- `provider_id`, `category_id`: projeção de categorias oferecidas pelo prestador.
- Para cadastro fixo, deve conter a categoria principal derivada das ofertas selecionadas.
- Móvel/PitTec continuam usando a projeção existente de seus serviços implícitos.

## New/changed invariants

1. Um cadastro `FIXED` deve conter uma única categoria principal entre as ofertas enviadas.
2. Cada `service_id` enviado deve existir, estar ativo, pertencer à categoria principal e ter uma categoria ativa.
3. O mesmo `service_id` não pode aparecer duas vezes no payload; a unicidade de banco continua protegendo gravações posteriores.
4. A carga inicial contém até 15 especialidades ativas selecionáveis por FIXED em cada categoria, excluindo os serviços implícitos Guincho/PitTec. Expansões autorizadas podem ultrapassar esse número.
5. `MOBILE_SEQUENTIAL` e `PITTEC_AUTO_SEQUENTIAL` continuam sendo resolvidos pelo fluxo implícito; não devem ser transformados em seleção manual de cadastro.
6. Especialidades das categorias Guincho/PitTec usam BROWSE, são selecionáveis somente por FIXED e seguem descoberta/contratação de serviços fixos.

## PitTec presentation state

O estado de tela existente deve ganhar somente a informação necessária para apresentação:

- `summaryExpanded: Boolean`, inicial `false` quando o acompanhamento entra em `ACCEPTED`.
- `statusLabel`: derivado do estado autorizado do appointment/dispatch.
- `distanceLabel`/`arrivalLabel`: derivado do snapshot de rota/localização existente.
- `canCancel`: derivado das ações autorizadas e exposto somente quando `summaryExpanded` for true.
- `viewport`: continua representando a última posição manual do mapa; comandos de reenquadramento só são emitidos no primeiro carregamento ou quando solicitados explicitamente.

Estados terminais devem recolher o resumo ou substituí-lo pela mensagem terminal existente, sem manter ações de cancelamento ativas.
