# Research: Layouts iniciais do PitGo

## 1. Base técnica e dependências

**Decision**: manter a configuração existente: Kotlin/Compose Compiler 2.2.10, AGP 9.1.1, Gradle
9.3.1, Compose BOM 2026.06.01, Material 3, Activity Compose 1.13.0, Lifecycle 2.10.0,
Navigation Compose 2.9.8, minSdk 24, targetSdk 36 e compileSdk 37. Nenhuma dependência será
adicionada. “Sem XML” se aplica às telas: nenhum arquivo `res/layout` será criado; manifest,
strings, temas de launch e drawables vetoriais continuam como recursos Android existentes.

**Rationale**: o projeto já é Compose-only e contém Foundation, Material 3, ViewModel,
`collectAsStateWithLifecycle`, Navigation Compose e tooling. Essas capacidades cobrem gradiente,
rolagem, grade, estado, temas e navegação. Atualizar toolchain ou adicionar Hilt, Room, DataStore,
Coil, Retrofit/Ktor, Material Icons Extended ou Window Size Classes não resolve requisito atual.

**Alternatives considered**: atualizar bibliotecas durante a feature; adicionar pacote de ícones;
adicionar infraestrutura de dados. Todas ampliam escopo sem benefício funcional.

## 2. Relação com a feature anterior

**Decision**: a feature 002 substitui o contrato visual e comportamental da Home 001. O grafo
ativo conterá somente Welcome e Home; rota, argumentos, modelos, strings e tela de busca de
prestadores serão removidos quando não tiverem outro consumidor. A documentação da feature 001
permanece como histórico.

**Rationale**: a Home anterior possui cinco categorias que navegam para prestadores. A nova Home
exige seis serviços com preços e feedback sem navegação. Manter as duas implementações criaria
fontes de verdade incompatíveis e código morto.

**Alternatives considered**: criar `HomeV2`; ocultar a Home antiga em outra rota; mesclar os dois
catálogos; preservar a rota de prestadores “para o futuro”. Todas violam YAGNI e o escopo de duas
telas.

## 3. MVVM e estado local

**Decision**: usar `WelcomeViewModel` com `StateFlow<WelcomeUiState>` contendo três benefícios e
`HomeViewModel` com um único `StateFlow<HomeUiState>` contendo perfil, veículo, diagnóstico, seis
serviços, cinco itens inferiores e `HOME` como seleção. Os estados são preenchidos imediatamente;
não existem Loading, Error ou Empty. Não criar Repository ou Use Case.

**Rationale**: a solução segue o requisito explícito de MVVM e mantém as telas stateless sem
inventar fonte de dados. Cada ViewModel representa o conteúdo local da própria feature e não
retém contexto Android.

**Alternatives considered**: manter a Welcome sem ViewModel, por ser estática; usar um ViewModel
compartilhado; criar Repository de constantes. O primeiro economiza uma classe, mas não segue tão
claramente o fluxo MVVM por feature pedido pelo projeto; os demais aumentam acoplamento ou criam
camadas pass-through.

## 4. Layout responsivo da Welcome

**Decision**: usar um fundo de tela inteira com `Brush.verticalGradient` entre `#7C32E8` e
`#5520A8`, independente do modo do sistema. Wordmark, título, subtítulo, benefícios e ações ficam
em uma única coluna com rolagem e padding de safe drawing mais 24 dp laterais. Não usar alturas
fixas nem `Spacer(weight)` dentro do conteúdo rolável.

**Rationale**: uma lista pequena cabe melhor em `verticalScroll` do que em `LazyColumn`. A rolagem
integral mantém todos os botões alcançáveis em telas baixas, paisagem e texto ampliado. O gradiente
nativo dispensa bitmap. Referências: [Brush](https://developer.android.com/develop/ui/compose/graphics/draw/brush)
e [window insets](https://developer.android.com/develop/ui/compose/system/insets).

**Alternatives considered**: imagem de fundo; layout sem scroll; bloco de ações fixo no rodapé;
`LazyColumn`. Os três primeiros prejudicam adaptação e o último é desnecessário para poucos itens.

## 5. Home com conteúdo único e barra fixa

**Decision**: usar `Scaffold(bottomBar = NavigationBar)` e um único `LazyVerticalGrid` com
`GridCells.Fixed(2)`. Cabeçalho, veículo, endereço, diagnóstico e título ocupam `maxLineSpan`; os
seis serviços ocupam uma célula cada. O `innerPadding` do Scaffold integra o `contentPadding` da
grade para manter o último card acima da barra.

**Rationale**: duas colunas são requisito explícito, e uma única superfície lazy evita rolagens
aninhadas. `NavigationBar` e `NavigationBarItem` são os componentes Material oficiais para cinco
destinos compactos. Referências: [lazy grids](https://developer.android.com/develop/ui/compose/lists),
[Scaffold](https://developer.android.com/develop/ui/compose/components/scaffold) e
[NavigationBar](https://developer.android.com/develop/ui/compose/components/navigation-bar).

**Alternatives considered**: `GridCells.Adaptive`, linhas manuais dentro de `Column`, listas
aninhadas, barra customizada e `NavigationRail`. Elas quebram o contrato de duas colunas/barra
inferior ou adicionam controle manual desnecessário.

## 6. Design system e temas

**Decision**: atualizar o design system existente com a identidade solicitada e papéis semânticos
Light/Dark. Welcome desenha seu gradiente fixo; Home usa apenas `MaterialTheme.colorScheme`. Dynamic
Color permanece desabilitado. Tokens repetidos de espaçamento, forma, tamanho e elevação ficam em
`core/design/theme`. No shell edge-to-edge, os ícones das barras do sistema ficam claros sobre a
Welcome e acompanham o contraste Light/Dark da Home; essa aparência é coordenada no nível da
navegação/tema, nunca no ViewModel.

**Rationale**: a clarificação exige Welcome estável e Home seguindo o sistema. Esquemas separados
preservam contraste e evitam uma inversão automática; tokens eliminam hex/dp espalhados.
Referência: [Material 3 theming](https://developer.android.com/develop/ui/compose/designsystems/material3).

**Alternatives considered**: segundo tema completo para Welcome; cores hardcoded nas telas;
Dynamic Color. Todas aumentam duplicação ou diluem a identidade roxa.

## 7. Ícones e recursos

**Decision**: usar recursos vetoriais locais. Reaproveitar somente ícones antigos semanticamente
compatíveis (bateria, pneus e reparos), criar vetores para os demais elementos e remover recursos
órfãos. Wordmark é texto, não imagem. Todo texto visível e acessível fica em `strings.xml`. O
subtítulo da Welcome será “Encontre assistência para bateria, óleo, pneus e muito mais, onde você
estiver.”.

**Rationale**: vetores locais funcionam offline, escalam sem perda e não exigem biblioteca de
ícones ou carregador de imagens. Reutilização semântica preserva a clareza visual.

**Alternatives considered**: emojis, Material Icons Extended, imagens remotas ou logo bitmap.
Foram rejeitados por inconsistência, dependência desnecessária ou conflito com a clarificação.

## 8. Interação e acessibilidade

**Decision**: Cards e controles usam componentes Material clicáveis. Diagnóstico e serviços
emitem callbacks, mostram ripple/pressed state e permanecem na Home; itens inferiores inativos
também não alteram seleção. Cada card clicável é um único nó semântico, ícones redundantes são
decorativos, headings são marcados e todos os alvos respeitam pelo menos 48 × 48 dp. Textos podem
quebrar linha e cards crescem verticalmente.

**Rationale**: cumpre a clarificação de feedback sem criar destino. Semântica agrupada evita
anúncios duplicados e informação não depende somente de cor. Referências: [semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)
e [accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).

**Alternatives considered**: cards informativos sem clique; Snackbar “em breve”; fontes reduzidas;
ellipsis; alturas fixas. A primeira contradiz a resposta de clarificação e as demais ampliam escopo
ou prejudicam acessibilidade.

## 9. Navegação e proteção contra duplicação

**Decision**: centralizar apenas `welcome` e `home`. Welcome é `startDestination`. `Entrar` navega
com flag local de ação pendente, `dropUnlessResumed` e `launchSingleTop`; o flag volta a `false`
quando Welcome retorna a `RESUMED` após o Back. Não usar `popUpTo`. Telas recebem callbacks e não
conhecem `NavController`.

**Rationale**: as três proteções evitam dois eventos antes/durante a transição, enquanto o reset
permite entrar novamente após voltar. O back stack natural fica `[Welcome, Home]`, portanto Back
na Home retorna à Welcome e Back na Welcome sai. Referências:
[navigation events](https://developer.android.com/topic/architecture/ui-layer/events) e
[back stack](https://developer.android.com/guide/navigation/backstack).

**Alternatives considered**: debounce temporal, evento one-shot no ViewModel, `popUpTo`,
`BackHandler`, rotas tipadas com nova dependência de serialização ou telas placeholder. Nenhuma é
necessária para duas rotas sem argumentos.

## 10. Validação

**Decision**: executar `./gradlew clean assembleDebug lintDebug` e o roteiro manual em dispositivo
API 24+, sem criar testes automatizados.

**Rationale**: a constituição e o usuário proíbem geração de testes nesta feature. Build, lint e
validação manual cobrem compilação, conteúdo, responsividade, temas, acessibilidade, feedback e
navegação.

**Alternatives considered**: testes unitários de constantes e testes Compose. Não foram
autorizados e têm valor menor que a inspeção visual nesta entrega.
