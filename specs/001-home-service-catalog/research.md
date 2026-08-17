# Phase 0 Research: Catálogo de Serviços na Homepage

## 1. Compatibilidade do toolchain e SDK

**Decision**: Manter AGP 9.1.1, Gradle 9.3.1, Kotlin integrado 2.2.10 e JDK 17. Alterar
`compileSdk` de 36.1 para 37, mantendo `targetSdk 36` e `minSdk 24`.

**Rationale**: O build de baseline falha porque `core-ktx 1.19.0` exige compilação contra API 37.
AGP 9.1.1 suporta oficialmente API 37 e já está combinado ao wrapper Gradle correto. Alterar apenas
o SDK de compilação corrige a incompatibilidade sem optar o aplicativo por novos comportamentos de
runtime. A máquina ainda precisa instalar `platforms;android-37` antes do primeiro build.

**Alternatives considered**:

- Rebaixar Core KTX: rejeitado porque o AGP atual já suporta API 37 e a constituição prioriza
  bibliotecas estáveis atuais.
- Atualizar AGP para 9.2: rejeitado por não ser necessário para a linha Compose escolhida.
- Alterar também targetSdk: rejeitado como mudança de comportamento fora do escopo.

Referência: [compatibilidade oficial do AGP 9.1.1](https://developer.android.com/build/releases/agp-9-1-0-release-notes).

## 2. Kotlin e compilador Compose

**Decision**: Usar Kotlin integrado do AGP e aplicar `org.jetbrains.kotlin.plugin.compose` 2.2.10.
Não aplicar `org.jetbrains.kotlin.android` nem configurar
`composeOptions.kotlinCompilerExtensionVersion`.

**Rationale**: AGP 9 habilita Kotlin por padrão, e o projeto confirma KGP 2.2.10 nas propriedades
Gradle. Desde Kotlin 2.0, o plugin do compilador Compose acompanha a versão do Kotlin.

**Alternatives considered**:

- Plugin Kotlin Android separado: rejeitado por conflito com Kotlin integrado.
- Compilador Compose legado: rejeitado por ser a configuração anterior ao Kotlin 2.0.

Referências: [Kotlin integrado ao AGP](https://developer.android.com/build/migrate-to-built-in-kotlin) e
[plugin do compilador Compose](https://kotlinlang.org/docs/compose-compiler-migration-guide.html).

## 3. Linha de dependências Compose/Jetpack

**Decision**: Usar Compose BOM 2026.06.01, Activity Compose 1.13.0, Lifecycle 2.10.0 e Navigation
Compose 2.9.8. Declarar somente UI, Foundation, Material 3, tooling preview/debug, runtime Compose,
ViewModel Compose/KTX e Navigation Compose.

**Rationale**: O BOM mantém versões Compose compatíveis; a linha 2026.06.01 usa UI/Foundation
1.11.4 e Material 3 1.4.0. Compose 1.12 e Lifecycle 2.11 exigem AGP 9.2 para seus artefatos Compose,
por isso a última linha estável compatível evita um upgrade de build sem relação com a feature.
Navigation 2.9.8 e Activity 1.13.0 são estáveis.

**Alternatives considered**:

- BOM 2026.08.00 e Lifecycle 2.11: rejeitados pela exigência de AGP 9.2.
- Versões individuais de Compose: rejeitadas pelo risco de divergência.
- Navigation 3 ou Navigation 2.10 pré-estável: rejeitados pela constituição e estabilidade.

Referências: [Compose BOM](https://developer.android.com/develop/ui/compose/bom),
[Compose UI releases](https://developer.android.com/jetpack/androidx/releases/compose-ui),
[Activity releases](https://developer.android.com/jetpack/androidx/releases/activity),
[Lifecycle releases](https://developer.android.com/jetpack/androidx/releases/lifecycle) e
[Navigation releases](https://developer.android.com/jetpack/androidx/releases/navigation).

## 4. Fonte local e estado MVVM

**Decision**: `HomeViewModel` expõe um `HomeUiState` imutável via StateFlow com exatamente cinco
categorias locais e ordenadas. Não criar Repository, Use Case ou estados Loading/Error/Empty.

**Rationale**: Não existe I/O, operação assíncrona ou regra de negócio. Um Repository que apenas
devolve constantes e um estado com situações impossíveis seriam camadas cerimoniais. A UI coleta
o fluxo respeitando lifecycle e recebe somente estado e callback.

**Alternatives considered**:

- Repository local ou Use Case: rejeitados por não reduzirem complexidade.
- Catálogo remoto/híbrido: rejeitado pela clarificação formal da feature.
- Lista criada no Composable: rejeitada porque a constituição atribui estado de tela ao ViewModel.

Referências: [UI layer](https://developer.android.com/topic/architecture/ui-layer) e
[recomendações de arquitetura](https://developer.android.com/topic/architecture/recommendations).

## 5. Layout responsivo e acessível

**Decision**: Usar Scaffold e `LazyVerticalGrid` com `GridCells.Adaptive`, chaves estáveis e largura
mínima de card definida no tema. O card inteiro é um único alvo Material acessível, com texto
quebrável, ícone vetorial decorativo, ação semântica e área de toque mínima de 48 dp.

**Rationale**: A grade adaptável responde à largura real em smartphones, tablets e multi-window
sem layouts duplicados ou dependência adicional de Window Size Classes. Um único foco evita anúncio
duplicado do ícone e do texto; nome e finalidade não dependem apenas de cor.

**Alternatives considered**:

- Duas colunas fixas: rejeitadas por adaptação ruim em janelas grandes ou estreitas.
- Layout por tipo de dispositivo: rejeitado; a largura disponível é o critério correto.
- Material Icons Extended ou imagens remotas: rejeitados; cinco vetores locais bastam.

Referências: [grades lazy em Compose](https://developer.android.com/develop/ui/compose/lists),
[semântica](https://developer.android.com/develop/ui/compose/accessibility/semantics) e
[padrões acessíveis de API](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).

## 6. Navegação e limite da busca de prestadores

**Decision**: `HomeScreen` recebe `onCategorySelected(ServiceCategoryId)` e não conhece
`NavController`. O NavHost centraliza `home` e `provider-search/{categoryId}`, transmite apenas o ID
estável e abre uma tela mínima de entrada da busca que mostra a categoria e permite voltar. A lista
de prestadores não será simulada.

**Rationale**: O destino mínimo torna seleção, categoria e retorno verificáveis de ponta a ponta
sem implementar dados fora do escopo. Uma rota simples centralizada evita a dependência adicional
de Kotlin Serialization para um único argumento interno.

**Alternatives considered**:

- Passar o objeto completo: rejeitado por acoplamento e restauração frágil.
- Rotas tipadas com Serialization: adiadas até existir um fluxo de busca real com mais contratos.
- Callback sem destino: rejeitado porque não comprova que a jornada foi iniciada.
- Lista falsa de prestadores: rejeitada por YAGNI e por contradizer o limite do escopo.

Referências: [navegação Compose testável](https://developer.android.com/guide/navigation/testing/compose) e
[dados mínimos na navegação](https://developer.android.com/develop/ui/compose/migrate/migration-scenarios/navigation).

## 7. Toques repetidos, retorno e estado efêmero

**Decision**: Proteger a ação com `dropUnlessResumed` e `launchSingleTop = true`. Usar o back stack
padrão para retornar e `rememberLazyGridState` para preservar a posição visível. A navegação é um
evento de UI e não será armazenada em Channel ou no estado do ViewModel.

**Rationale**: A ação só é aceita enquanto Home está RESUMED; o destino não é duplicado no topo.
Isso é determinístico e preserva a navegação de retorno sem debounce arbitrário.

**Alternatives considered**:

- Debounce por tempo: rejeitado por poder bloquear uma ação legítima.
- Evento one-shot no ViewModel: rejeitado por adicionar estado de entrega sem necessidade.
- Remover Home do back stack: rejeitado porque viola o retorno exigido.

Referências: [eventos de UI e navegação](https://developer.android.com/topic/architecture/ui-layer/events),
[back stack](https://developer.android.com/guide/navigation/backstack) e
[estado de UI Compose](https://developer.android.com/develop/ui/compose/state-saving).

## 8. Migração do scaffold e limites de dependência

**Decision**: Substituir AppCompat/ViewBinding/XML/JNI por ComponentActivity/Compose e remover os
artefatos de exemplo diretamente obsoletos. Manter um design system Compose mínimo para cores,
tipografia e tema Light/Dark; não adicionar tema manual nesta feature.

**Rationale**: O repositório ainda é o scaffold Native C++ e não contém comportamento de produto a
preservar. Manter dois sistemas de UI, CMake e JNI sem uso aumentaria build e manutenção. O sistema
operacional define o tema conforme a constituição; DataStore só será necessário quando houver
seletor manual.

**Alternatives considered**:

- ComposeView dentro do XML: rejeitado porque migração gradual não traz benefício para uma tela.
- Manter C++ e testes de exemplo: rejeitado como código sem uso.
- Adicionar Hilt, Retrofit/Ktor, Room, DataStore, Coil ou testes: rejeitado por ausência de requisito.

## Resolution Status

Todas as decisões técnicas estão resolvidas e não restam pendências para o design.
