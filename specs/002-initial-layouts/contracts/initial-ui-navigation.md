# UI and Navigation Contract: Welcome → Home

## Purpose

Definir a fronteira verificável entre as duas telas da feature, seus estados locais e as ações que
podem ou não alterar o destino. Não existe API, persistência nem argumento de navegação.

## Welcome destination

- **Route**: `welcome`
- **Role**: destino inicial do aplicativo.
- **Input**: `WelcomeUiState` produzido por `WelcomeViewModel`.
- **Outputs**:
  - `onCreateAccount()` — feedback Material; permanece em Welcome.
  - `onEnter()` — solicita navegação única para Home.
  - `onRegisterCompany()` — feedback Material; permanece em Welcome.
- **Ownership**: `features/welcome`.

### UI contract

- O gradiente ocupa toda a janela e não muda entre Light/Dark.
- O conteúdo usa safe drawing insets, 24 dp laterais e uma única rolagem vertical.
- Wordmark textual, título, subtítulo “Encontre assistência para bateria, óleo, pneus e muito mais,
  onde você estiver.”, três benefícios e três ações permanecem alcançáveis.
- A tela recebe callbacks e não conhece `NavController`.
- Os três benefícios aparecem na ordem definida em `data-model.md`.

## Home destination

- **Route**: `home`
- **Input**: `HomeUiState` produzido por `HomeViewModel`.
- **Outputs**:
  - `onDiagnosticClick()` — feedback Material; permanece em Home.
  - `onServiceClick(DemoServiceId)` — feedback Material; permanece em Home.
  - `onBottomItemClick(BottomItemId)` — feedback Material; permanece em Home e mantém `HOME`
    selecionado.
- **Ownership**: `features/home`.

### UI contract

- `Scaffold` mantém `NavigationBar` fixa e fornece padding ao conteúdo.
- Uma única `LazyVerticalGrid(GridCells.Fixed(2))` contém as seções full-span e seis serviços.
- A ordem visual/semântica segue `data-model.md`.
- Nenhum card usa altura fixa ou truncamento como comportamento normal.
- O último serviço pode ser rolado integralmente acima da barra inferior.
- A Home usa os papéis Light/Dark do tema do sistema.
- A tela recebe callbacks e não conhece `NavController`.

## Navigation rules

1. `PitGoRoutes` contém apenas `Welcome` e `Home` para esta experiência.
2. `NavHost` usa `Welcome` como `startDestination`.
3. Ao receber `onEnter`, o NavHost verifica flag local de navegação pendente e aceita somente a
   primeira ação válida; o flag é reiniciado quando Welcome volta a `RESUMED` após o retorno.
4. A ação aceita é envolvida por `dropUnlessResumed` e navega com `launchSingleTop = true`.
5. A navegação não usa `popUpTo`; o back stack resultante é `[Welcome, Home]`.
6. Back em Home revela a mesma Welcome; Back em Welcome encerra a Activity pelo comportamento
   padrão Android.
7. Nenhuma outra ação cria rota, altera seleção ou inicia fluxo de autenticação, diagnóstico,
   serviço, garagem, atendimento ou perfil.
8. As barras permanecem transparentes no shell edge-to-edge: ícones são claros na Welcome e usam
   o contraste apropriado ao modo Light/Dark na Home.

## Accessibility and interaction contract

- Cada Card/controle clicável forma um único alvo semântico de no mínimo 48 × 48 dp.
- Rótulo, finalidade e ação são anunciados sem duplicar o nome por meio do ícone.
- Ícones acompanhados por texto são decorativos; informação não repetida recebe descrição.
- Headings são marcados semanticamente e a ordem de foco segue a leitura visual.
- O estado selecionado de Início é anunciado, não apenas colorido.
- Todo toque válido mostra ripple/pressed state mesmo quando permanece no destino.

## Invariants

- Uma sequência rápida de toques em Entrar produz no máximo uma Home.
- Welcome sempre antecede Home no histórico desta feature.
- `HOME` é o único item inferior selecionado.
- Nenhuma tela depende de internet, sessão, permissão, dado pessoal real ou fonte externa.
- Remover a rota antiga de prestadores não exige migração: seu identificador era interno, não
  persistido e não exposto como deep link.
