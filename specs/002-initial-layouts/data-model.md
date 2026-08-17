# Data Model: Layouts iniciais do PitGo

## Overview

Todos os dados são demonstrativos, locais, imutáveis e disponíveis desde a primeira composição.
Recursos textuais e visuais são referenciados por IDs locais para permitir localização e evitar
`Context` nos ViewModels. Não há persistência, sincronização, identidade de backend ou estados de
carregamento/erro/vazio.

## WelcomeBenefitId

Identificador estável de cada benefício da Welcome.

| Value | Display order |
|---|---:|
| `RAPID_ASSISTANCE` | 0 |
| `VERIFIED_TECHNICIANS` | 1 |
| `ALL_IN_APP` | 2 |

## WelcomeBenefit

| Field | Type | Rules |
|---|---|---|
| `id` | `WelcomeBenefitId` | Obrigatório e único na lista |
| `titleRes` | String resource ID | Título não vazio e localizado |
| `descriptionRes` | String resource ID | Descrição não vazia e localizada |
| `iconRes` | Drawable resource ID | Vetor local coerente; ícone decorativo quando o texto já identifica o item |
| `displayOrder` | Integer | Único e contínuo de 0 a 2 |

## WelcomeUiState

| Field | Type | Rules |
|---|---|---|
| `benefits` | Immutable list of `WelcomeBenefit` | Exatamente três itens únicos, ordenados por `displayOrder` |

O estado nasce completo e não possui transições internas. “Criar conta” e “Cadastrar empresa” não
o alteram; “Entrar” é um evento de navegação tratado fora do ViewModel.

## DemoProfile

| Field | Type | Rules |
|---|---|---|
| `name` | String resource ID | Valor demonstrativo “Rafael” |
| `initial` | String resource ID | Um único grafema correspondente a “R” |

## DemoVehicle

| Field | Type | Rules |
|---|---|---|
| `model` | String resource ID | “BMW 320i M Sport” |
| `year` | Integer | `2020` |
| `plate` | String resource ID | “PGO-2A20” |
| `address` | String resource ID | “Av. Paulista, 1500 · São Paulo” |
| `iconRes` | Drawable resource ID | Vetor local de veículo |

O ano e a placa são formatados na apresentação como `2020 · PGO-2A20`.

## DiagnosticPrompt

| Field | Type | Rules |
|---|---|---|
| `titleRes` | String resource ID | “Não sei o que aconteceu” |
| `descriptionRes` | String resource ID | “Descreva o problema e sugerimos o serviço” |
| `iconRes` | Drawable resource ID | Vetor local coerente com diagnóstico/ajuda |

O toque não altera estado nem navegação; apenas produz feedback Material.

## DemoServiceId

| Value | Display order | Starting price (BRL) |
|---|---:|---:|
| `BATTERY` | 0 | 320 |
| `OIL_CHANGE` | 1 | 220 |
| `TIRES` | 2 | 180 |
| `DASHBOARD_LIGHT` | 3 | 150 |
| `MINOR_REPAIRS` | 4 | 260 |
| `CAR_WONT_START` | 5 | 280 |

## DemoService

| Field | Type | Rules |
|---|---|---|
| `id` | `DemoServiceId` | Obrigatório e único na lista |
| `nameRes` | String resource ID | Nome completo e localizado |
| `iconRes` | Drawable resource ID | Vetor local distinto e coerente |
| `startingPriceReais` | Positive integer | Valor exato da tabela; maior que zero |
| `displayOrder` | Integer | Único e contínuo de 0 a 5 |

A UI combina o preço numérico com o recurso localizado “A partir de R$ %1$d”. Selecionar um
serviço emite somente seu `DemoServiceId`, fornece feedback e mantém a Home.

## BottomItemId

| Value | Label | Selected in this feature |
|---|---|---|
| `HOME` | Início | Yes |
| `SERVICES` | Serviços | No |
| `GARAGE` | Garagem | No |
| `APPOINTMENTS` | Atendimentos | No |
| `PROFILE` | Perfil | No |

## BottomNavigationItem

| Field | Type | Rules |
|---|---|---|
| `id` | `BottomItemId` | Obrigatório e único na lista |
| `labelRes` | String resource ID | Rótulo exato e localizado |
| `iconRes` | Drawable resource ID | Vetor local coerente |
| `displayOrder` | Integer | Único e contínuo de 0 a 4 |

## HomeUiState

| Field | Type | Rules |
|---|---|---|
| `profile` | `DemoProfile` | Obrigatório |
| `vehicle` | `DemoVehicle` | Obrigatório |
| `diagnostic` | `DiagnosticPrompt` | Obrigatório |
| `services` | Immutable list of `DemoService` | Exatamente seis itens únicos e ordenados |
| `bottomItems` | Immutable list of `BottomNavigationItem` | Exatamente cinco itens únicos e ordenados |
| `selectedBottomItem` | `BottomItemId` | Sempre `HOME` nesta entrega |

## Relationships and invariants

```text
WelcomeUiState 1 ─── 3 WelcomeBenefit

HomeUiState 1 ─── 1 DemoProfile
            1 ─── 1 DemoVehicle
            1 ─── 1 DiagnosticPrompt
            1 ─── 6 DemoService
            1 ─── 5 BottomNavigationItem
```

- Listas são criadas uma vez, ordenadas e expostas somente para leitura.
- IDs e ordens não se repetem.
- Nenhum modelo contém URL, token, localização real, credencial ou identificador remoto.
- Cards e itens inferiores não alteram `HomeUiState` nesta feature.
- Não existe mapeamento DTO → domínio → UI porque há somente uma representação local útil.

## Navigation state transitions

```text
WELCOME --Enter--> HOME
HOME --System Back--> WELCOME
WELCOME --System Back--> Activity closed
```

- `Create account` e `Register company`: `WELCOME → WELCOME` com feedback visual.
- `Diagnostic`, `DemoService` e item inferior: `HOME → HOME` com feedback visual.
- Toques repetidos em Enter durante a transição não adicionam outra Home.
