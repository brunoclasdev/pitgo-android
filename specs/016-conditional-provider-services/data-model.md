# Data Model: Seleção de serviços condicionada à modalidade do prestador

## Convenções

- Nenhuma tabela nova e nenhuma migração são necessárias; este documento descreve apenas os campos
  adicionais que o app Android passa a ler/derivar a partir de dados já existentes.
- `dispatch_kind` já existe em `public.services` (`BROWSE`, `MOBILE_SEQUENTIAL`,
  `PITTEC_AUTO_SEQUENTIAL`) e já é a fonte de verdade para outras partes do app (catálogo da Home,
  despacho automático).

## Entidades existentes lidas de forma nova

### `Serviço de Catálogo` (leitura de cadastro)

| Campo | Origem | Uso nesta funcionalidade |
|---|---|---|
| `id` | já lido | identifica o serviço a associar ao prestador |
| `categoryId` | já lido | necessário para preencher a categoria implícita da oferta |
| `name` | já lido | exibido apenas no resumo de Fixo; não exibido para Móvel/PitTec |
| `description` | já lido | inalterado |
| `dispatchKind` | **novo campo lido** | usado para localizar, sem depender do nome, qual serviço é
  o implícito de Móvel (`MOBILE_SEQUENTIAL`) ou de PitTec (`PITTEC_AUTO_SEQUENTIAL`) |

Nenhuma outra tela ou fluxo além do cadastro de prestador precisa desse campo adicional; ele já é
lido normalmente pela query existente (a tabela não muda), só passa a ser desserializado.

### `Rascunho de Cadastro do Prestador` (estado de tela, não persistido)

Estrutura já existente (`ProviderRegistrationDraft`/`OfferDraft`) reaproveitada sem novos campos.
Mudança é de **comportamento**, não de forma:

| Modalidade | Ofertas no rascunho ao selecionar a modalidade |
|---|---|
| Fixo | lista de ofertas controlada manualmente pelo prestador (uma vazia inicialmente, podendo adicionar mais); campos categoria, serviço, preço, duração e descrição continuam editáveis |
| Móvel | exatamente uma oferta, com categoria e serviço travados no serviço de `dispatch_kind = MOBILE_SEQUENTIAL` resolvido; apenas preço e duração continuam editáveis pelo prestador |
| PitTec | exatamente uma oferta, com categoria e serviço travados no serviço de `dispatch_kind = PITTEC_AUTO_SEQUENTIAL` resolvido, preço zero, `isFree = true` e uma duração padrão interna; nenhum campo de oferta é editável ou exibido |

Trocar a modalidade durante o preenchimento substitui a lista de ofertas pela combinação acima; não
há mesclagem entre o que foi preenchido para uma modalidade e o que vale para outra (ver Edge Cases
no spec).

### `Oferta enviada ao backend` (`OfferRegistration` → RPC `create_provider_registration`)

Sem mudança de forma. Continua exigindo `serviceId`, `price`, `durationMinutes`, `isFree`,
`description?` — válidos segundo as mesmas regras já aplicadas hoje. Para Móvel e PitTec, esses
valores agora são montados automaticamente pelo ViewModel em vez de digitados pelo prestador (exceto
preço/duração de Móvel, que continuam vindo do prestador).

## Resultado de resolução do serviço implícito (novo, em memória)

Não é uma entidade persistida; representa o resultado de localizar o serviço de uma modalidade pelo
`dispatchKind`.

| Resultado | Significado | Efeito no cadastro |
|---|---|---|
| Encontrado (exatamente um) | catálogo consistente | oferta implícita é montada normalmente |
| Ausente (nenhum) | catálogo sem o serviço dessa modalidade | cadastro não pode ser enviado; falha compreensível exibida (FR-009) |
| Duplicado (mais de um) | catálogo com mais de um serviço para o mesmo `dispatchKind` | cadastro não pode ser enviado; falha compreensível exibida (FR-009) |

## Regras de validação (inalteradas, apenas reafirmadas)

- Fixo: pelo menos uma oferta válida é obrigatória para envio (`ProviderField.Offers` = REQUIRED se
  vazio) — comportamento já existente, preservado.
- Móvel/PitTec: a oferta implícita sempre está presente no rascunho antes do envio (nunca vazia),
  então a validação de "pelo menos uma oferta" já passa sem mudança de código na validação.
- Duplicidade de serviço por prestador (`provider_services_provider_service_unique`) continua
  válida e inatingível nestes fluxos, já que cada modalidade produz no máximo uma oferta.
