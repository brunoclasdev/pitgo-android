# Data Model: Catálogo de Serviços na Homepage

## Scope

O modelo é totalmente local e em memória. Não existe esquema de banco, DTO remoto, cache ou
migração. Recursos de texto e ícone são empacotados no aplicativo e resolvidos somente pela UI.

## ServiceCategoryId

Identidade canônica e estável de uma categoria.

| Value | Meaning | Route value |
|-------|---------|-------------|
| `TIRE_SHOP` | Borracharia | `tire-shop` |
| `VEHICLE_ELECTRICIAN` | Eletricista veicular | `vehicle-electrician` |
| `BATTERY` | Assistência de bateria | `battery` |
| `TIRE_CHANGE` | Troca de pneu | `tire-change` |
| `MECHANIC` | Mecânico | `mechanic` |

### Validation rules

- Cada valor é único e não muda após publicado.
- O valor de rota usa apenas ASCII minúsculo e hífen.
- Somente estes cinco valores são válidos nesta feature.

## ServiceCategory

Representa um item apresentável e selecionável do catálogo.

| Field | Type | Rules |
|-------|------|-------|
| `id` | `ServiceCategoryId` | Obrigatório e único no catálogo |
| `nameRes` | Localized string reference | Nome completo não vazio |
| `iconRes` | Local vector reference | Recurso local válido; ícone decorativo na UI |
| `accessibilityActionRes` | Localized string reference | Descreve “Buscar prestadores de {categoria}” |
| `displayOrder` | Integer | Único, de 0 a 4 |

### Initial records

| displayOrder | id | Visible name |
|--------------|----|--------------|
| 0 | `TIRE_SHOP` | Borracharia |
| 1 | `VEHICLE_ELECTRICIAN` | Eletricista veicular |
| 2 | `BATTERY` | Bateria |
| 3 | `TIRE_CHANGE` | Troca de pneu |
| 4 | `MECHANIC` | Mecânico |

## HomeUiState

Estado imutável observado pela homepage.

| Field | Type | Rules |
|-------|------|-------|
| `categories` | Immutable list of `ServiceCategory` | Exatamente cinco itens únicos, ordenados por `displayOrder` |

### State lifecycle

1. O ViewModel é criado com o catálogo completo.
2. O mesmo conteúdo é exposto imediatamente e permanece válido offline.
3. Recriação de Activity/ViewModel reconstrói o mesmo catálogo determinístico.

Loading, Error e Empty não são estados válidos porque não existe fonte externa nem catálogo
opcional neste escopo.

## CategorySelection

Evento efêmero emitido pela UI ao usuário tocar em um card.

| Field | Type | Rules |
|-------|------|-------|
| `categoryId` | `ServiceCategoryId` | Deve pertencer ao catálogo atual |

O evento não é persistido no ViewModel. O NavHost converte o ID para o argumento mínimo do destino
de entrada da busca. Enquanto a homepage não estiver `RESUMED`, novas seleções são ignoradas.

## Relationships and invariants

- `HomeUiState` contém exatamente cinco `ServiceCategory`.
- Cada `ServiceCategory` possui um `ServiceCategoryId` exclusivo.
- Uma `CategorySelection` referencia exatamente uma categoria existente.
- O destino de busca recebe somente o ID; nome, ícone e ordem não atravessam a fronteira.
- Nenhuma entidade contém localização, autenticação, dados pessoais ou dados de prestadores.
