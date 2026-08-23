# Data Model: Cadastro de empresa prestadora

## Visão geral

O agregado de cadastro é identificado pela própria conta autenticada. Uma confirmação cria ou
reconhece exatamente um `Provider`, exatamente um `Address` inicial e uma ou mais
`ProviderService`. `ServiceCategory` e `CatalogService` são catálogo somente leitura. Nenhum ID de
proprietário é originado no formulário.

```text
Profile/Auth user (1) ── (0..1) Provider (1) ── (1) Address
                                  │
                                  └── (1..N) ProviderService (N..1) CatalogService
                                                                  │
                                                                  └── (N..1) ServiceCategory
```

## Entidades persistidas

### Provider (`public.providers`)

| Campo | Tipo | Regra |
|---|---|---|
| `id` | UUID | PK; FK `auth.users(id)` com cascade; sempre `auth.uid()` |
| `name` | text | obrigatório, trim, não vazio |
| `description` | text nullable | trim; vazio vira null |
| `email` | text | obrigatório, lowercase + trim, formato de e-mail |
| `phone` | text | obrigatório, 10 ou 11 dígitos, DDD brasileiro válido |
| `cnpj` | text | obrigatório, uppercase, 12 caracteres alfanuméricos + 2 DVs numéricos, DV válido |

Invariantes:

- uma conta possui no máximo um provider porque `id` também é a chave da conta;
- `email` e `cnpj` são únicos após normalização;
- e-mail comercial pode diferir de `profiles.email`;
- o cliente não envia `id`.

### Address (`public.addresses`)

| Campo | Tipo | Regra |
|---|---|---|
| `id` | UUID | PK gerada no banco |
| `provider_id` | UUID | FK `providers(id)` com cascade; derivada pelo banco; UNIQUE nesta feature |
| `street` | text | obrigatório, trim, não vazio |
| `number` | text | obrigatório; aceita `s/n`, bloco e lote |
| `neighbornhood` | text | obrigatório; grafia física legada preservada |
| `zip_code` | text | exatamente 8 dígitos |
| `city` | text | obrigatório, trim, não vazio |
| `state` | text | uma das 27 siglas de UF, uppercase |
| `country` | text | constante `BR`; UI exibe “Brasil” |
| `complement` | text nullable | trim; vazio vira null |
| `latitude` | numeric nullable | presente junto com longitude; -90 a 90 |
| `longitude` | numeric nullable | presente junto com latitude; -180 a 180 |

No domínio Kotlin a propriedade chama `neighborhood`; o DTO/RPC é a única fronteira que conhece
`neighbornhood`. Não haverá segunda coluna equivalente.

### ServiceCategory (`public.service_categories`)

| Campo | Tipo | Regra |
|---|---|---|
| `id` | UUID | identidade opaca do catálogo |
| `name` | text | obrigatório e exibido no seletor |
| `description` | text nullable | informativo; não editado pela feature |

A feature apenas consulta categorias. Nome livre informado pelo usuário não é aceito.
Nomes são trim/não vazios e únicos sem diferenciar caixa, para que as opções do seletor não sejam
visualmente ambíguas.

### CatalogService (`public.services`)

| Campo | Tipo | Regra |
|---|---|---|
| `id` | UUID | identidade opaca do catálogo |
| `category_id` | UUID | FK para `service_categories(id)` |
| `name` | text | obrigatório e exibido no seletor |
| `description` | text nullable | descrição global somente leitura |

Cada serviço pertence a exatamente uma categoria. A categoria é derivada deste vínculo no banco;
ela não é gravada em `provider_services` e não entra na RPC de criação.
Dentro de uma categoria, nomes de serviço são trim/não vazios e únicos sem diferenciar caixa.

### ProviderService (`public.provider_services`)

| Campo | Tipo | Regra |
|---|---|---|
| `provider_id` | UUID | FK `providers(id)` com cascade; derivada por `auth.uid()` |
| `service_id` | UUID | FK `services(id)` com restrict |
| `price` | numeric | escala máxima 2; zero somente após comando gratuito; caso contrário > 0 |
| `duration_minutes` | integer | 1 a 10.080 |
| `description` | text nullable | descrição comercial; trim; vazio vira null |

Invariantes:

- UNIQUE `(provider_id, service_id)`;
- ao menos uma associação no agregado concluído;
- `price=0` é a representação persistida de gratuito. `isFree` existe no comando para provar a
  escolha explícita durante a validação, mas não duplica esse estado na tabela;
- a descrição comercial não altera `services.description`.

## Modelos do fluxo Android

### ProviderRegistrationDraft

```text
ProviderRegistrationDraft
├── company: CompanyDraft
├── address: AddressDraft
└── offers: List<OfferDraft>
```

O draft fica somente na memória do ViewModel e nunca contém `providerId`/`userId`.

### CompanyDraft

- `name: String`
- `description: String`
- `email: String` — baseline inicial é `AccountSnapshot.authEmail`
- `phone: String`
- `cnpj: String`

### AddressDraft

- `street`, `number`, `neighborhood`, `postalCode`, `city`, `complement: String`
- `state: BrazilianState?`
- `latitude`, `longitude: String`
- país não é editável; o comando sempre contém `BR`

### OfferDraft

- `localId: Long` — chave monotônica apenas para UI, erro e foco
- `categoryId: String?`
- `serviceId: String?`
- `isFree: Boolean`
- `priceInput: String`
- `durationInput: String`
- `description: String`

Regras de edição:

- mudar `categoryId` limpa `serviceId`;
- marcar gratuito limpa/desabilita o preço visual e gera preço zero;
- remover uma oferta não renumera `localId`;
- a lista pode ficar vazia durante edição, mas não pode ser confirmada vazia;
- nenhum `take`, `MAX_OFFERS` ou limite de produto é aplicado;
- o mesmo `serviceId` não pode aparecer em duas ofertas.

### ProviderRegistrationCommand

Resultado imutável da validação local:

- campos textuais normalizados;
- `email` lowercase;
- `phone`/CEP sem máscara;
- CNPJ uppercase alfanumérico sem máscara;
- UF e país canônicos;
- coordenadas como `BigDecimal?` pareadas;
- cada oferta com `serviceId`, `price: BigDecimal`, `durationMinutes: Int`, descrição nullable e
  `isFree` de comando;
- nenhum ID de owner, provider, endereço ou categoria.

### ProviderSummary

- `provider: Provider`
- `address: Address`
- `offers: List<ProviderOfferSummary>`
- cada `ProviderOfferSummary` inclui nomes de serviço/categoria, preço, duração e descrição
  comercial; IDs permanecem internos ao data layer
- a UI nunca mostra CNPJ completo; utiliza forma mascarada

## Tipos de estado

### Estado raiz

```text
ProviderRegistrationUiState
├── Loading
├── StartupFailure(reason)
├── Editing(content)
├── AlreadyRegistered(summary)
├── Success(summary)
└── SessionExpired
```

`EditingContent` contém:

- `draft`;
- `categories: LoadState<List<ServiceCategory>>`;
- `servicesByCategory: Map<CategoryId, LoadState<List<CatalogService>>>`;
- `errors: Map<ProviderField, ProviderFieldError>`;
- `focusRequest: FocusRequest?` com contador monotônico;
- `submission: EditingSubmission`.

### Estados auxiliares

```text
LoadState<T> = Loading | Empty | Content<T> | Failure(reason)
EditingSubmission = Idle | Submitting | Failure(reason)
```

Campos de oferta em erro são identificados por `ProviderField.Offer(localId, OfferField)`, nunca
por índice. Assim remoção/reordenação não move erro ou foco para outra oferta.

## Transições

| Origem | Evento | Destino | Observação |
|---|---|---|---|
| Loading | provider completo encontrado | AlreadyRegistered | nenhum draft é criado |
| Loading | provider ausente + catálogo responde | Editing | e-mail recebe authEmail e primeira oferta vazia |
| Loading | catálogo vazio/falha | Editing | `LoadState` explícito; formulário continua editável, submit bloqueado |
| Loading | falha própria | StartupFailure | oferece retry |
| Editing | editar/adicionar/remover | Editing | limpa apenas erros afetados |
| Editing | selecionar categoria | Editing | limpa serviço e carrega/cacheia lista da categoria |
| Editing | submit inválido | Editing | todos os erros + foco no primeiro em ordem visual |
| Editing/Failure | submit válido | Editing(Submitting) | controles e back destrutivo bloqueados |
| Submitting | CREATED | Success | draft é removido da memória |
| Submitting | ALREADY_REGISTERED | AlreadyRegistered | cobre corrida e resposta perdida |
| Submitting | conflito conhecido | Editing | preserva draft e marca e-mail/CNPJ/oferta |
| Submitting | offline/timeout/5xx | Editing(Failure) | preserva draft em memória e permite retry |
| qualquer estado | sessão ausente/trocada | SessionExpired | cancela jobs e zera draft/cache/PII |

Resultados assíncronos só podem atualizar a tela se o `userId` observado ainda for o mesmo capturado
no bootstrap.

## Ordem de validação e foco

1. nome, e-mail, telefone, CNPJ e descrição;
2. CEP, rua, número, complemento, bairro, cidade, UF, latitude e longitude;
3. existência de ao menos uma oferta;
4. para cada oferta na ordem visual: categoria, serviço, gratuidade/preço, duração e descrição;
5. duplicidade de serviço entre ofertas.

A descrição opcional não gera erro quando vazia. Categoria/serviço removidos do catálogo geram
`UNAVAILABLE`, não aceitam texto livre e precisam de nova seleção.

## Invariantes de segurança

- owner é sempre `auth.uid()` no PostgreSQL;
- nenhum DTO de comando contém owner/provider ID;
- CNPJ, payload, IDs internos e erro bruto não são registrados;
- o draft não usa DataStore, SavedStateHandle ou arquivo;
- troca/expiração de sessão limpa imediatamente o estado visível;
- DML direto pelo papel `authenticated` permanece revogado; somente a RPC cria o agregado.
