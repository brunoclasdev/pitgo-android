<!--
Sync Impact Report
- Version change: scaffold sem versão -> 1.0.0
- Modified principles:
  - scaffold principle 1 -> I. Simplicidade e entrega orientada ao requisito
  - scaffold principle 2 -> II. Android nativo moderno com MVVM
  - scaffold principle 3 -> III. Estado previsível e experiência consistente
  - scaffold principle 4 -> IV. Dados, segurança e falhas explícitas
  - scaffold principle 5 -> V. Qualidade proporcional e evolução sustentável
- Added sections:
  - Restrições Técnicas e de Produto
  - Fluxo de Desenvolvimento e Qualidade
- Removed sections: nenhuma
- Follow-up TODOs: nenhum
-->
# PitGo Constitution

## Core Principles

### I. Simplicidade e entrega orientada ao requisito

Toda implementação DEVE resolver completamente o requisito atual pela solução mais simples,
moderna, segura e sustentável. Funcionalidade, experiência do usuário, legibilidade,
manutenção, segurança e performance DEVEM ser priorizadas, nessa ordem, antes de abstração.
KISS e YAGNI são obrigatórios: features futuras não solicitadas, infraestrutura sem uso,
camadas especulativas, boilerplate e abstrações prematuras NÃO DEVEM ser criados. DRY DEVE
eliminar duplicação relevante, mas uma duplicação pequena é preferível a uma abstração sem
responsabilidade clara.

Cada dependência, camada, pattern e arquivo DEVE ter uso atual demonstrável. Entre soluções
equivalentes, a de menor complexidade que preserve qualidade, segurança e manutenção DEVE ser
escolhida. Essa disciplina mantém o desenvolvimento rápido sem transformar escalabilidade em
overengineering.

### II. Android nativo moderno com MVVM

O aplicativo DEVE usar Kotlin, Android SDK, Jetpack Compose, Material Design, Android Jetpack,
ViewModel, StateFlow, Coroutines, Navigation Compose e Gradle Kotlin DSL. Retrofit ou Ktor PODE
ser usado para APIs; Room somente quando houver persistência local necessária; DataStore para
preferências simples. Na implementação, versões estáveis e atuais DEVEM ser confirmadas, e uma
dependência externa só PODE ser adicionada quando Android, Kotlin ou Jetpack não oferecerem uma
solução adequada e houver benefício claro e manutenção ativa.

MVVM é a arquitetura oficial e DEVE seguir o fluxo UI -> ViewModel -> Repository -> Data Source.
Use Case PODE ser inserido entre ViewModel e Repository somente quando encapsular lógica de
negócio relevante; ele NÃO DEVE apenas repassar chamadas. MVI, MVP, Redux, VIPER, Clean
Architecture completa, múltiplas camadas sem necessidade e arquiteturas híbridas NÃO DEVEM ser
introduzidos.

A organização DEVE ser por feature. Cada feature PODE conter apenas os grupos necessários entre
`data`, `model`, `repository`, `ui` e `viewmodel`; recursos compartilhados DEVEM possuir
responsabilidade clara em áreas como `core/network`, `core/database`, `core/navigation`,
`core/ui`, `core/design`, `core/utils` ou `core/common`. Pacotes genéricos como `helpers`,
`managers`, `misc` e `others` NÃO DEVEM existir sem responsabilidade específica.

### III. Estado previsível e experiência consistente

A UI DEVE apenas renderizar estado e emitir eventos. Ela NÃO DEVE conter regra de negócio nem
acessar API, banco ou Repository diretamente. ViewModels DEVEM controlar o estado da tela,
processar ações, aplicar regras de apresentação, chamar Repository ou Use Case e representar
loading, sucesso, erro e vazio quando aplicável. ViewModels NÃO DEVEM reter Activity, Fragment,
View ou Context, salvo necessidade estrita documentada.

Estado de tela DEVE ser imutável, previsível e exposto preferencialmente por StateFlow. Estados
mutuamente exclusivos DEVEM ser modelados por um único tipo explícito em vez de vários Boolean
independentes. Composables DEVEM ser pequenos, previsíveis, preferencialmente stateless, usar
state hoisting quando apropriado e delegar lógica de negócio ao ViewModel.

A interface DEVE usar componentes oficiais do Material sempre que atendam ao requisito e um
design system centralizado para cores, tipografia, formas, espaçamento, componentes, ícones e
elevação. Um componente compartilhado só DEVE ser criado quando houver reutilização real. Cada
tela DEVE apresentar hierarquia clara, ação principal identificável, navegação simples, feedback
imediato, mensagens compreensíveis e o menor número razoável de interações.

O tema claro DEVE usar roxo como cor primária, fundo e superfícies predominantemente brancos,
texto primário preto e texto secundário cinza-escuro. O tema escuro DEVE usar roxo como cor
primária, fundo preto, superfícies próximas ao preto, texto primário branco e texto secundário
cinza-claro; ele NÃO DEVE ser mera inversão automática. O aplicativo DEVE oferecer Light, Dark e
System Default, respeitar o sistema por padrão e persistir seleção manual com DataStore.

Telas DEVEM ser responsivas em tamanhos e densidades Android suportados, usar `dp`, `sp` e Window
Size Classes quando o layout exigir. Conteúdo DEVE suportar aumento de fonte, contraste adequado,
áreas de toque mínimas, labels compreensíveis e content descriptions quando aplicável; informação
NÃO DEVE depender apenas de cor.

### IV. Dados, segurança e falhas explícitas

Repositories DEVEM centralizar APIs, banco local, cache e serviços externos. Modelos de API,
domínio e UI só DEVEM ser separados quando a separação trouxer benefício real; mapeamentos
redundantes NÃO DEVEM ser criados. Coroutines DEVEM executar operações assíncronas respeitando o
lifecycle, com `viewModelScope` em ViewModels; callbacks aninhados, Threads manuais e GlobalScope
NÃO DEVEM ser usados.

Comunicação de rede DEVE distinguir loading, sucesso, erro HTTP, timeout, ausência de internet,
erro de servidor e erro desconhecido quando aplicável. Erros DEVEM ser classificados e tratados
de forma centralizada quando isso reduzir repetição. A UI NÃO DEVE expor detalhes técnicos e
DEVE apresentar mensagens acionáveis. Entradas como telefone, e-mail, CPF/CNPJ, endereço,
valores, datas e horários DEVEM ser validadas localmente antes do envio quando possível, sem
substituir a validação definitiva do backend.

Secrets, service role keys, tokens administrativos, senhas, chaves privadas e credenciais de
infraestrutura NÃO DEVEM ser incluídos no aplicativo. Informações sensíveis NÃO DEVEM aparecer em
logs. Tokens de autenticação DEVEM usar armazenamento apropriado e dados recebidos do backend
DEVEM ser tratados como não confiáveis até validação. O estado de autenticação DEVE ser
centralizado e cobrir login, logout, sessão expirada, renovação de token e usuário não autenticado;
cada tela NÃO DEVE implementar sua própria verificação manual.

Estados relevantes do marketplace on-demand DEVEM ser tipos explícitos, como enum ou sealed type,
e NÃO textos arbitrários. O fluxo de solicitação, disponibilidade, aceite, execução, conclusão,
pagamento, cancelamento e avaliação DEVE preservar transições válidas entre estados como PENDING,
ACCEPTED, IN_PROGRESS, COMPLETED e CANCELLED. Somente entidades exigidas pela funcionalidade
atual entre User, Provider, Company, Service, Product, Category, Order, Booking, Address, Payment,
Review, Favorite e Notification DEVEM ser criadas.

Quando localização for necessária, permissões DEVEM ser solicitadas no momento de uso, com
tratamento de recusa, indisponibilidade, GPS desativado e precisão adequada. A ausência de
localização NÃO DEVE bloquear todo o aplicativo quando ela não for essencial ao fluxo.

### V. Qualidade proporcional e evolução sustentável

Código DEVE ter nomes claros, funções pequenas, responsabilidade única, alta coesão e baixo
acoplamento. Comentários DEVEM explicar por que uma decisão existe, não narrar código. Patterns
como Repository, Factory, Strategy, Adapter, Observer e DI só PODEM ser adotados quando reduzirem
complexidade real. Dependency Injection DEVE permanecer simples; Hilt ou a solução já adotada
PODE ser usada quando tornar dependências substituíveis e testáveis.

Testes unitários, de UI e de integração, mocks, fixtures e utilitários de teste NÃO DEVEM ser
gerados automaticamente. Eles só DEVEM ser implementados quando forem explicitamente solicitados;
nessa situação, lógica de negócio relevante e ViewModels têm prioridade. A ausência de testes não
solicitados NÃO DEVE bloquear a entrega de uma feature.

Código gerado DEVE compilar, ser necessário, seguir o padrão existente e estar integrado ao fluxo.
Placeholders, TODOs desnecessários, arquivos vazios e classes sem uso NÃO DEVEM ser adicionados.
Mudanças DEVEM preservar comportamento funcional fora do escopo, evitar grandes refatorações não
relacionadas e remover código obsoleto diretamente relacionado somente quando for seguro.

Performance DEVE evitar recomposições desnecessárias, APIs repetidas, trabalho pesado na Main
Thread, objetos excessivos durante recomposição e carregamento de imagens maior que o necessário.
Listas extensas DEVEM usar componentes lazy e paginação quando o volume justificar. Imagens
remotas DEVEM ter carregamento assíncrono, cache, placeholder, loading, erro e fallback; Coil PODE
ser usado quando aplicável.

## Restrições Técnicas e de Produto

- O produto é um marketplace on-demand Android nativo que conecta usuários a prestadores,
  estabelecimentos, produtos ou serviços sob demanda.
- Navigation Compose DEVE ser usado. Rotas DEVEM ser centralizadas e não strings dispersas; os
  fluxos relevantes entre Splash, Onboarding, Login, Home, Search, Details, Checkout, Orders e
  Profile DEVEM respeitar corretamente o botão Voltar do Android.
- Material Design estável mais recente disponível no momento da implementação DEVE ser usado.
  Componentes oficiais como Scaffold, TopAppBar, NavigationBar, NavigationRail, Cards, Buttons,
  TextField, Snackbar, ModalBottomSheet, Dialog e FloatingActionButton NÃO DEVEM ser recriados
  manualmente sem necessidade demonstrável.
- Valores visuais duplicados diretamente em telas DEVEM ser movidos ao design system quando forem
  tokens compartilhados. Abstrações visuais de uso único NÃO DEVEM ser criadas sem outra razão.
- Room, cache e persistência local só DEVEM existir quando houver requisito atual. Preferências
  simples, inclusive tema manual, DEVEM usar DataStore.
- Logs DEVEM ser úteis e proporcionais ao ambiente. Senhas, tokens, cartões, documentos completos
  e dados confidenciais NÃO DEVEM ser registrados; logs excessivos NÃO DEVEM permanecer em
  produção.
- Segurança e privacidade são requisitos funcionais: dados sensíveis, permissões e respostas de
  backend DEVEM receber tratamento explícito no design e na implementação.

## Fluxo de Desenvolvimento e Qualidade

Para cada nova feature, a implementação DEVE:

1. confirmar o fluxo e o requisito atual;
2. localizar a feature responsável;
3. criar somente componentes necessários;
4. implementar UI e ViewModel;
5. adicionar Repository apenas quando houver acesso a dados;
6. integrar API ou persistência somente quando requerido;
7. representar loading, erro, vazio e sucesso quando aplicável;
8. integrar navegação e verificar o fluxo completo;
9. não gerar documentação extensa nem testes sem solicitação explícita.

Refatorações DEVEM declarar objetivo ligado ao escopo, preservar comportamento existente e
reutilizar estruturas adequadas. Dependências novas DEVEM ser justificadas pela ausência de solução
nativa/Jetpack adequada, redução verificável de complexidade, manutenção ativa e benefício atual.

Toda revisão de mudança DEVE verificar, conforme o risco: compilação, fluxo funcional, estados de
UI, tratamento de falhas, acessibilidade, segurança, performance e ausência de complexidade
especulativa. Exceções a esta constituição DEVEM conter justificativa explícita no artefato de
planejamento ou revisão correspondente.

Commits DEVEM ser pequenos e representar uma mudança lógica. Mensagens DEVEM ser descritivas,
preferencialmente no formato `feat:`, `fix:`, `refactor:`, `style:` ou `chore:`; mensagens genéricas
como `update`, `changes`, `fix`, `teste` ou `alterações` NÃO DEVEM ser usadas isoladamente.

## Governance

Esta constituição prevalece sobre práticas, templates e decisões conflitantes do projeto. Toda
spec, plano, tarefa, implementação e revisão DEVE comprovar conformidade proporcional ao seu
escopo. Complexidade adicional DEVE ser justificada por requisito concreto; caso contrário, DEVE
ser removida do plano ou da mudança.

Emendas DEVEM ser propostas com motivação, impacto, itens afetados e eventual plano de migração.
Uma emenda só entra em vigor após aprovação explícita dos responsáveis pelo projeto e atualização
deste documento. A data de última alteração DEVE refletir a adoção da emenda.

O versionamento segue SemVer para governança: MAJOR para remoções ou redefinições incompatíveis de
princípios; MINOR para princípio ou seção nova e para expansão material de orientação; PATCH para
clareza, correção ou refinamento sem mudança normativa. Revisões DEVEM conferir se a versão e o
Sync Impact Report correspondem à alteração.

Conformidade DEVE ser revista durante especificação, planejamento e revisão de código. Violações
conhecidas DEVEM ser corrigidas antes da conclusão ou registradas como exceção aprovada, com razão,
responsável e prazo. A constituição DEVE ser reavaliada quando decisões arquiteturais ou de produto
alterarem seus pressupostos.

**Version**: 1.0.0 | **Ratified**: 2026-08-16 | **Last Amended**: 2026-08-16
