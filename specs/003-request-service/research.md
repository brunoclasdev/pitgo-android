# Research: Solicitar Atendimento

## 1. Base técnica e dependências

**Decision**: manter integralmente o toolchain e as bibliotecas atuais: Kotlin/Compose Compiler
2.2.10, AGP 9.1.1, Gradle 9.3.1, Compose BOM 2026.06.01, Material 3, Foundation/UI, Activity
Compose 1.13.0, Lifecycle 2.10.0, Navigation Compose 2.9.8, minSdk 24, targetSdk 36 e compileSdk 37.
Nenhuma dependência será adicionada.

**Rationale**: o projeto já oferece navegação, `SavedStateHandle`, ViewModel, `StateFlow`, coleta
lifecycle-aware, componentes Material, layout responsivo, semântica e tooling necessários. A tela
é local e não precisa de rede, banco, DI, serialização, carregador de imagem ou biblioteca de
ícones.

**Alternatives considered**: atualizar o toolchain durante a feature; adicionar serialização para
rotas tipadas; Material Icons Extended; Hilt; Repository; Room/DataStore. Todas aumentam escopo sem
resolver requisito atual.

## 2. Contrato de navegação e argumento

**Decision**: preservar o estilo de rotas String existente e adicionar um padrão centralizado
`request-service?serviceId={serviceId}`. Um builder aceita `DemoServiceId`, codifica seu `name` e
gera a rota; a Home emite somente o ID. O destino declara argumento String opcional para que ID
ausente também alcance o estado de erro especificado.

**Rationale**: um escalar estável atende o requisito de não transportar o objeto inteiro e mantém
nome, ícone e preço fora da URL. Query opcional permite validar ausência; um segmento obrigatório
nem sequer corresponderia ao destino nesse caso. Os nomes atuais do enum são estáveis e não
carregam dados pessoais.

**Alternatives considered**: passar nome/preço/ícone ou objeto completo, rejeitado por duplicar a
fonte de verdade; rota tipada serializável, rejeitada por exigir plugin/runtime novo para um único
escalar; usar Bateria como fallback, rejeitado porque ocultaria ID inválido e mostraria preço
incorreto.

## 3. Resolução do serviço e propriedade dos dados

**Decision**: mover `DemoServiceId`, `DemoService` e `DemoServices` de Home para
`core/common/model/DemoServiceCatalog.kt`, com lookup exato e case-sensitive. Mover também
`DemoVehicle`/`DemoHomeVehicle` para `core/common/model/DemoVehicle.kt`. Home e RequestService
passam a importar essas fontes compartilhadas; nenhuma cópia de preços ou veículo será criada.

**Rationale**: serviço e veículo terão dois consumidores reais. A extração pequena elimina
acoplamento entre features e garante que os valores 320/220/180/150/260/280 vistos na Home sejam
os mesmos da estimativa. Os dados continuam constantes locais, portanto não justificam Repository
ou Use Case.

**Alternatives considered**: importar modelos privados da Home, rejeitado pelo acoplamento
direcional; duplicar mocks na nova feature, rejeitado pelo risco de divergência; criar uma camada
de domínio/repositório completa, rejeitada por YAGNI.

## 4. Estado MVVM e serviço inválido

**Decision**: `RequestServiceViewModel` recebe `SavedStateHandle`, lê o valor bruto e expõe um
`StateFlow<RequestServiceUiState>` selado com `Content` e `InvalidService`. `Content` contém
serviço, veículo, descrição, chegada, pagamento e status de acionamento; endereço e preço são
propriedades derivadas do veículo e do serviço. A resolução é síncrona e não possui Loading.

**Rationale**: o tipo selado impede combinações como serviço nulo com conteúdo aparentemente
válido. Preço derivado não pode ficar desatualizado. ID ausente, desconhecido ou com case incorreto
leva a `InvalidService`, sem fallback, e a UI oferece retorno. Não há espera, lista vazia ou falha
de I/O que justifique outros estados.

**Alternatives considered**: data class única com nullables/booleans, rejeitada por estados
contraditórios; Loading/Success/Error genérico, rejeitado por modelar eventos impossíveis; manter
estado editável em `remember`, rejeitado porque descrição, pagamento e guarda de ação pertencem ao
ViewModel.

## 5. Pagamento, descrição e ação simulada

**Decision**: modelar pagamento como enum `PIX`, `CREDIT`, `DEBIT`, inicializado em PIX, e
acionamento como enum `READY`, `TRIGGERED`. Eventos explícitos atualizam descrição e pagamento.
`onSubmitClicked()` muda sincronamente `READY → TRIGGERED` e informa se o callback deve ser
executado; novos toques em `TRIGGERED` são ignorados e o botão permanece desabilitado até o
destino ser recriado. Não há Snackbar, delay, cobrança ou objeto de atendimento.

**Rationale**: a transição anterior ao callback impede chamadas duplicadas sem temporizador,
Channel ou fluxo de efeitos. A mudança visual de enabled/disabled e o feedback Material do botão
atendem a etapa mock sem sugerir criação real. A descrição começa vazia, aceita múltiplas linhas e
não recebe limite arbitrário não solicitado.

**Alternatives considered**: debounce temporal, rejeitado por fragilidade; boolean
`isSubmitting`, rejeitado por semântica menos explícita; Channel/SharedFlow e Snackbar, rejeitados
como infraestrutura desnecessária para um callback síncrono sem confirmação; persistir rascunho,
rejeitado por estar fora do escopo.

## 6. Shell, rolagem e teclado

**Decision**: usar `Scaffold(topBar = TopAppBar, bottomBar = PitGoBottomNavigation)` e somente uma
`LazyColumn` para o corpo. O conteúdo incorpora `innerPadding`, safe drawing horizontal/superior e
`imePadding`; top bar e bottom navigation permanecem fixas. O campo usa `OutlinedTextField`,
`minLines = 4` e altura mínima de 120 dp, sem altura máxima rígida.

**Rationale**: uma única superfície rolável evita rolagens aninhadas e mantém o último aviso acima
da barra. `imePadding` e altura mínima tornam campo e CTA alcançáveis com teclado, tela baixa e
fonte ampliada. `TopAppBar`/`IconButton` oferecem back e semântica Material sem controles manuais.

**Alternatives considered**: tela inteira rolável, rejeitada porque moveria barras; CTA fixo,
rejeitado porque não foi solicitado e competiria com bottom bar/IME; `Column.verticalScroll`,
viável para conteúdo pequeno, mas menos flexível se os cards crescerem; `BasicTextField`, rejeitado
por reimplementar Material.

## 7. Cards, estimativas e pagamento responsivos

**Decision**: manter `InfoCard`, `EstimateCard` e `PaymentOption` privados no arquivo da tela.
Cards usam superfície, outline suave, elevação existente e conteúdo sem truncamento. As duas
estimativas usam layout que mantém duas colunas quando há largura útil e muda para uma coluna
quando largura/fonte não comportam leitura. Pagamentos formam um `selectableGroup`; cada opção é
uma `Surface` com `selectable(role = RadioButton)`, peso igual, alvo mínimo de 48 dp e ícone de
check quando selecionada.

**Rationale**: componentes privados removem repetição real sem criar API genérica. A adaptação
preserva os valores em smartphones compactos e texto ampliado. Grupo selecionável, semântica
radio, borda/fundo/texto e check visível garantem exclusividade sem depender somente de cor.

**Alternatives considered**: Row fixa para estimativas, rejeitada por apertar conteúdo; altura
fixa/ellipsis, rejeitada por acessibilidade; três implementações copiadas, rejeitadas por drift;
componente universal público, rejeitado por YAGNI; opções apenas clickable/coloridas, rejeitadas
por semântica insuficiente.

## 8. Ação gradiente e design system

**Decision**: usar `Button` Material com container transparente sobre um fundo recortado por
gradiente BrandPurple → DarkPurple, largura máxima, altura mínima de 56 dp e formato pill. Reusar
diretamente cores, shapes, tipografia, espaçamentos, elevação e ícones existentes; manter as
alturas de 56 dp e 120 dp locais à única tela que as usa e adicionar vetores locais apenas para
back, relógio e check.

**Rationale**: o Button preserva ripple, enabled, foco e semântica enquanto o gradiente atende à
identidade. Recursos locais funcionam offline e evitam nova biblioteca. Textos visíveis e de
acessibilidade permanecem em `strings.xml`.

**Alternatives considered**: Box custom clicável, rejeitado por reimplementar comportamento
Material; botão roxo sólido, rejeitado por não atender ao gradiente; bitmap ou imagem remota,
rejeitados por escala/dependência; novos tokens para valores usados uma única vez, rejeitados por
YAGNI.

## 9. Bottom navigation e back stack

**Decision**: mover modelos, dados e composable da barra hoje privados na Home para
`core/ui/PitGoBottomNavigation.kt`. Ambas as telas usam os mesmos cinco itens com `HOME`
selecionado. Os quatro itens restantes continuam demonstrativos: mostram feedback e não navegam.
Serviços, por outro lado, passa a navegar uma única vez para RequestService; back faz
`popBackStack()` e, em entrada inválida sem anterior, retorna à Home por fallback seguro.

**Rationale**: o contrato da feature 002 e o código atual definem a barra como no-op. Preservá-la
cumpre “mesma Bottom Navigation da Home” sem criar telas placeholder. Extrair um único componente
evita diferença visual. A pilha normal `[Welcome, Home, RequestService]` devolve o usuário à mesma
Home.

**Alternatives considered**: criar quatro telas vazias, rejeitado por escopo/YAGNI; duplicar a
barra, rejeitado por drift; Scaffold global, rejeitado por refatoração ampla; fazer RequestService
conhecer `NavController`, rejeitado por acoplamento da UI.

## 10. Validação

**Decision**: executar `./gradlew clean assembleDebug lintDebug` e seguir o roteiro manual em
dispositivo/emulador API 24+, sem criar testes automatizados.

**Rationale**: o usuário e a constituição excluem testes nesta feature. Build, lint e validação
manual cobrem compilação, recursos, seis mapeamentos, estado inválido, back, rolagem/IME, seleção
exclusiva, toque único, barra fixa, temas, responsividade, acessibilidade e operação offline.

**Alternatives considered**: testes unitários do ViewModel e testes Compose seriam úteis em outro
escopo, mas não foram autorizados; validação somente visual foi rejeitada porque não cobre build e
lint.

## Resolution status

Todas as decisões de Technical Context estão resolvidas; não restam pendências de esclarecimento.
