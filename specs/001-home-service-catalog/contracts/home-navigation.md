# UI and Navigation Contract: Homepage to Provider Search

## Purpose

Definir a fronteira verificável entre o catálogo local da homepage e o início do fluxo de busca de
prestadores, sem incluir a listagem ou fontes de dados de prestadores.

## Home destination

- **Route**: `home`
- **Input**: `HomeUiState` produzido por `HomeViewModel`.
- **Output**: `onCategorySelected(ServiceCategoryId)`.
- **Ownership**: `features/home`.
- **Navigation dependency**: nenhuma dentro de `HomeScreen`; o NavHost fornece o callback.

### UI contract

- Renderizar exatamente os cinco itens na ordem definida em `data-model.md`.
- Usar uma grade rolável e adaptável à largura disponível.
- Tratar cada card como um único alvo clicável com nome e finalidade acessíveis.
- Manter o nome completo com quebra de linha; não usar truncamento como comportamento normal.
- Preservar estado de rolagem ao retornar do destino seguinte.
- Funcionar sem internet, autenticação, localização ou dados pessoais.

## Provider-search entry destination

- **Route pattern**: `provider-search/{categoryId}`.
- **Argument**: `categoryId`, um dos route values definidos em `ServiceCategoryId`.
- **Ownership**: fronteira mínima em `features/provider`; a futura busca substitui/expande o
  conteúdo sem alterar o contrato de entrada.
- **Current content**: título “Busca de prestadores”, nome da categoria recebida e ação de voltar.
- **Out of scope**: lista de prestadores, disponibilidade, proximidade, rede, solicitação,
  pagamento e contratação.

### Navigation rules

1. O card emite somente seu `ServiceCategoryId`.
2. O NavHost converte o ID no route value centralizado.
3. A navegação só é aceita quando a entrada Home está `RESUMED`.
4. `launchSingleTop` impede duplicação do mesmo destino no topo.
5. O destino valida o route value antes de apresentar a categoria.
6. Um valor desconhecido não causa crash: o destino mostra erro local compreensível e permite
   voltar.
7. O botão Voltar remove o destino e revela a mesma instância lógica da homepage.

## Accepted values

| categoryId | Destination label |
|------------|-------------------|
| `tire-shop` | Borracharia |
| `vehicle-electrician` | Eletricista veicular |
| `battery` | Bateria |
| `tire-change` | Troca de pneu |
| `mechanic` | Mecânico |

## Invariants

- Um toque válido produz no máximo uma entrada nova no back stack.
- Toques rápidos posteriores são ignorados enquanto Home não estiver ativa.
- O identificador recebido corresponde exatamente ao card selecionado.
- Nenhum objeto completo ou texto visível é usado como argumento de navegação.
- Rotas e construção de argumentos ficam centralizadas em `core/navigation`.
- `HomeScreen` permanece stateless em relação à navegação.
