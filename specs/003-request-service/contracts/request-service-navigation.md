# UI and Navigation Contract: Home → RequestService

## Purpose

Definir a fronteira verificável entre a Home, o destino de solicitação, seu estado local e as ações
que podem alterar a pilha. Não existe API, deep link público, persistência ou atendimento real.

## Producer: Home

- **Route**: `home`
- **Output alterado**: `onServiceClick(DemoServiceId)` solicita navegação para o serviço tocado.
- **Outputs preservados**:
  - `onDiagnosticClick()` — feedback Material; permanece em Home.
  - `onBottomItemClick(BottomItemId)` — feedback Material; permanece em Home com `HOME`
    selecionado.
- **Ownership**: `features/home`.

### Producer invariants

- Cada card emite exatamente seu próprio ID.
- Nome, preço e ícone não são enviados como argumentos.
- Toques repetidos durante a transição produzem no máximo um RequestService no topo.
- A Home recebe callbacks e não conhece `NavController`.

## Route contract

- **Pattern**: `request-service?serviceId={serviceId}`
- **Argument name**: `serviceId`
- **Argument representation**: String canônica de `DemoServiceId.name`.
- **Argument nullability**: opcional no padrão para permitir o estado verificável de ID ausente.
- **Builder input**: `DemoServiceId` não nulo.
- **Builder output**: rota com apenas o ID codificado.

| Raw input | Result |
|---|---|
| Nome exato de um dos seis IDs | `RequestServiceUiState.Content` correspondente |
| Ausente ou vazio | `RequestServiceUiState.InvalidService` |
| Desconhecido ou case diferente | `RequestServiceUiState.InvalidService` |

## Consumer: RequestService

- **Ownership**: `features/requestservice`.
- **Input**: valor bruto de `serviceId` recebido por `SavedStateHandle`.
- **State owner**: `RequestServiceViewModel`.
- **Outputs**:
  - `onBack()` — solicita retorno seguro.
  - `onProblemDescriptionChanged(String)` — atualiza somente a descrição temporária.
  - `onPaymentMethodSelected(PaymentMethod)` — substitui a seleção atual.
  - `onSubmit()` — executa o callback mock uma vez quando o status está `READY`.
  - `onBottomItemClick(BottomItemId)` — feedback Material; permanece em RequestService e mantém
    `HOME` selecionado.

### Content UI contract

- `Scaffold` mantém TopAppBar e NavigationBar fixas; uma única LazyColumn rola entre elas.
- `innerPadding`, safe drawing e IME mantêm o conteúdo e o aviso alcançáveis.
- Serviço, veículo e local usam uma apresentação visual coerente com os cards da Home.
- A descrição é multiline, começa vazia e não bloqueia submit.
- Estimativas aparecem lado a lado quando há espaço e empilham sem perda de conteúdo quando não há.
- Pagamento é um grupo de seleção exclusiva, iniciado em PIX e anunciado como tal.
- O CTA usa feedback Material, gradiente e fica desabilitado após o primeiro callback aceito.
- A tela recebe estado/callbacks e não conhece `NavController`.

### InvalidService UI contract

- Mantém título, ação de voltar e bottom navigation.
- Exibe mensagem compreensível de serviço indisponível.
- Não mostra nome, ícone, preço, formulário, estimativas, pagamentos nem CTA de outro serviço.
- Oferece ação visível de retorno usando o mesmo callback `onBack()`.

## Back and stack rules

1. `PitGoRoutes` centraliza Home, padrão, nome do argumento e builder de RequestService.
2. O NavHost envolve a navegação da Home com estado `RESUMED`, guarda local pendente e
   `launchSingleTop = true`.
3. O fluxo normal produz `[Welcome, Home, RequestService]`.
4. Back do sistema usa o comportamento padrão e revela a mesma Home.
5. Back da TopAppBar e a ação do estado inválido chamam `popBackStack()`.
6. Se `popBackStack()` retornar `false`, o NavHost navega para Home e remove o RequestService
   inválido do topo, sem encerrar o aplicativo em uma tela sem recuperação.
7. Itens inferiores não criam rotas nesta feature.

## Accessibility and interaction contract

- A ação Back possui descrição localizada e alvo de pelo menos 48 × 48 dp.
- Título e títulos de seção são headings na ordem de leitura.
- Ícones repetidos por label/valor são decorativos; informação não duplicada recebe descrição.
- Cada opção de pagamento pertence a um `selectableGroup`, usa papel de radio e anuncia seleção.
- Check visível complementa borda, fundo e texto do pagamento selecionado.
- Textos quebram linha e controles crescem verticalmente; ellipsis não remove informação essencial.
- Todo toque válido mostra ripple/pressed state, inclusive itens inferiores demonstrativos.

## Invariants

- Um ID válido resolve exatamente um serviço, e o preço exibido vem desse mesmo item.
- Um ID inválido nunca seleciona Bateria ou reaproveita conteúdo anterior.
- Exatamente uma forma de pagamento permanece selecionada.
- Uma instância do destino executa o callback mock no máximo uma vez.
- Nenhuma ação cria Booking/Order, consulta rede/localização ou inicia cobrança.
- RequestService e Home renderizam a mesma definição da bottom navigation.
