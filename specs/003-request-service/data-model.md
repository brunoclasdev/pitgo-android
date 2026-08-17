# Data Model: Solicitar Atendimento

## Overview

Os dados são locais e temporários. O catálogo e o veículo demonstrativo são fontes compartilhadas
entre Home e RequestService; a tela recebe somente o identificador do serviço e deriva nome, ícone,
endereço e preço dessas fontes. Não há entidade de atendimento persistida, DTO, backend, cache,
localização real ou dados de cartão.

## DemoServiceId

Identificador canônico e estável transportado pela navegação.

| Value | Display order | Starting price (BRL) |
|---|---:|---:|
| `BATTERY` | 0 | 320 |
| `OIL_CHANGE` | 1 | 220 |
| `TIRES` | 2 | 180 |
| `DASHBOARD_LIGHT` | 3 | 150 |
| `SMALL_REPAIRS` | 4 | 260 |
| `CAR_WONT_START` | 5 | 280 |

## DemoService

| Field | Type | Rules |
|---|---|---|
| `id` | `DemoServiceId` | Obrigatório e único no catálogo |
| `nameRes` | String resource ID | Nome localizado não vazio |
| `startingPrice` | Positive integer | Valor em reais maior que zero e igual à tabela acima |
| `iconRes` | Drawable resource ID | Vetor local coerente com o serviço |
| `displayOrder` | Integer | Único e contínuo de 0 a 5 |

O lookup usa igualdade exata do `DemoServiceId`. Não existe serviço padrão para entrada inválida.

## DemoVehicle

| Field | Type | Rules |
|---|---|---|
| `modelRes` | String resource ID | “BMW 320i M Sport” |
| `year` | Integer | `2020` |
| `plateRes` | String resource ID | “PGO-2A20”; reutilizada pela Home, não exibida nesta tela |
| `addressRes` | String resource ID | “Av. Paulista, 1500 · São Paulo” |
| `iconRes` | Drawable resource ID | Vetor local de veículo |

RequestService formata veículo como `BMW 320i M Sport · 2020` e expõe o endereço separadamente.

## PaymentMethod

Forma de pagamento exclusivamente selecionável para a simulação.

| Value | Display label | Initial |
|---|---|---|
| `PIX` | PIX | Yes |
| `CREDIT` | Crédito | No |
| `DEBIT` | Débito | No |

Regras:

- exatamente um valor existe em todo estado `Content`;
- selecionar um valor substitui atomicamente o anterior;
- não há dados de conta, cartão, autorização ou cobrança.

## SubmissionStatus

Estado local da ação mockada, sem equivalência com um atendimento real.

| Value | Meaning | CTA enabled |
|---|---|---|
| `READY` | A ação simulada ainda pode ser acionada | Yes |
| `TRIGGERED` | O callback já foi aceito nesta instância do destino | No |

### State transition

```text
READY --first accepted tap--> TRIGGERED
TRIGGERED --additional tap--> TRIGGERED
```

Não há transição para PENDING/ACCEPTED/Booking, pois nenhum atendimento é criado.

## RequestServiceUiState

Tipo raiz mutuamente exclusivo.

### Content

| Field | Type | Rules |
|---|---|---|
| `selectedService` | `DemoService` | Exatamente um item resolvido pelo ID recebido |
| `vehicle` | `DemoVehicle` | Veículo demonstrativo compartilhado com a Home |
| `addressRes` | Derived String resource ID | Sempre `vehicle.addressRes`; não armazenar cópia divergente |
| `problemDescription` | String | Inicialmente vazia; opcional; multiline; temporária |
| `estimatedArrivalRes` | String resource ID | Valor local “20-40 min” |
| `estimatedPrice` | Derived integer | Sempre `selectedService.startingPrice` |
| `paymentMethod` | `PaymentMethod` | Inicialmente `PIX`; exatamente um valor |
| `submissionStatus` | `SubmissionStatus` | Inicialmente `READY` |

### InvalidService

Estado usado quando o argumento está ausente, vazio, desconhecido ou com representação diferente
dos IDs canônicos. Não contém serviço, preço, formulário ou fallback; oferece mensagem e retorno.

## Bottom navigation model

O modelo compartilhado existente mantém cinco itens ordenados e únicos:

| ID | Label | Selected |
|---|---|---|
| `HOME` | Início | Yes |
| `SERVICES` | Serviços | No |
| `GARAGE` | Garagem | No |
| `APPOINTMENTS` | Atendimentos | No |
| `PROFILE` | Perfil | No |

Nesta etapa, selecionar outro item produz feedback e não muda rota nem seleção.

## Relationships and invariants

```text
DemoServiceCatalog 1 ─── 6 DemoService
RequestServiceUiState ─── Content | InvalidService
Content 1 ─── 1 DemoService
        1 ─── 1 DemoVehicle
        1 ─── 1 PaymentMethod
        1 ─── 1 SubmissionStatus
```

- IDs e ordens do catálogo não se repetem.
- Nome, ícone e preço são sempre obtidos do mesmo `DemoService`.
- Trocar descrição ou pagamento não altera serviço, veículo, endereço, chegada ou preço.
- `InvalidService` nunca contém preço anterior nem substitui o ID por Bateria.
- `TRIGGERED` impede nova execução do callback enquanto o destino existir.
- Nenhum estado contém `NavController`, `Context`, credencial, coordenada, token ou dado de cartão.
- O estado existe somente durante a instância do destino; encerramento de processo não exige
  restauração da descrição ou pagamento.

## Navigation state transitions

```text
HOME --select valid serviceId--> REQUEST_SERVICE(Content)
HOME --select invalid/missing serviceId--> REQUEST_SERVICE(InvalidService)
REQUEST_SERVICE --Back--> HOME
REQUEST_SERVICE --bottom item--> REQUEST_SERVICE
```

- A pilha normal preserva Welcome e Home abaixo de RequestService.
- Seleções rápidas do mesmo card criam no máximo um destino no topo.
- Em entrada direta inválida sem Home na pilha, a ação de retorno usa Home como fallback seguro.
