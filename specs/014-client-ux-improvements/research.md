# Research: Melhoria de UX das telas do cliente

Todas as decisões abaixo foram resolvidas por inspeção direta do código atual (não há
`NEEDS CLARIFICATION` pendente no Technical Context do plan.md). Uma descoberta corrige uma
suposição do `spec.md` (endereço do cliente); as demais confirmam que parte do escopo já está
implementada, reduzindo o trabalho real desta feature.

## 1. Endereço do cliente (Home/Perfil — FR-005, FR-007)

- **Decision**: Criar uma nova tabela mínima `public.client_addresses` (uma linha por cliente,
  upsert), com o mesmo formato de campos já validado em `public.addresses`
  (rua/número/bairro/CEP/cidade/UF/complemento/lat/long), acessada só por duas RPCs
  `SECURITY DEFINER` (`get_my_client_address` / `upsert_my_client_address`), RLS restringindo cada
  cliente à própria linha, no mesmo padrão de autorização já usado no restante do backend
  (`auth.uid()`, sem grant direto de tabela). A busca de CEP reaproveita a lógica já existente em
  `ViaCepAddressLookupRepository` (hoje em `features/providerregistration/repository`), promovida
  para um local compartilhado (`core/address` ou equivalente) para uso por cliente e prestador sem
  acoplamento entre features.
- **Rationale**: A suposição do spec de que "endereço do cliente" já existia estava **errada** —
  `HomeScreen.NeutralLocationRow()` só renderiza o texto estático `home_address_empty`, sem
  nenhuma linha, ViewModel ou tabela por trás; não há rota de cadastro de endereço para conta
  `CLIENT` hoje (`PitGoRoutes.kt` só tem endereço para provider). É preciso criar essa capacidade,
  mas de forma proporcional (reaproveitar validação, CEP lookup e o padrão de RPC/RLS já
  estabelecidos), não uma feature nova do zero.
- **Alternatives considered**:
  - Alterar `public.addresses` para aceitar `client_id` opcional → rejeitado: essa tabela já tem
    responsabilidade acoplada ao modo `FIXED` do prestador (geocodificação pública,
    `geocode_expires_at`, descoberta por `discover_service_providers`); misturar o endereço privado
    do cliente ali arrisca regressão nessas regras e mistura dois propósitos de dado diferentes.
  - Não persistir nada e só pedir um fix de GPS a cada solicitação → rejeitado: os critérios de
    aceite pedem explicitamente "ver ou editar o endereço atual" (US5, FR-007), o que implica um
    dado que persiste entre sessões, não uma leitura pontual.

## 2. Posição do cliente nos mapas (FR-011, FR-012, FR-014)

- **Decision**: Reaproveitar `DeviceLocationRepository` (já usado por
  `ClientMobileDispatchViewModel`/`ProviderDashboardViewModel` para leituras pontuais de GPS) para
  obter a posição do cliente ao abrir/reabrir a tela de acompanhamento, sem novo serviço em
  primeiro/segundo plano. Já decidido via `/speckit-clarify` (sessão 2026-08-23).
- **Rationale**: Não existe nem é necessário criar rastreamento contínuo do cliente; reduz
  permissões, bateria e complexidade, e usa uma capacidade já testada em produção pela busca de
  serviço.
- **Alternatives considered**: serviço de rastreamento contínuo (rejeitado, ver spec Clarifications);
  reaproveitar `service_searches.search_point` estático (rejeitado por ficar desatualizado se o
  cliente se mover entre a solicitação e o atendimento).

## 3. Mapa do prestador durante o atendimento (US3, FR-013 a FR-015)

- **Decision**: Adicionar um `MapLibreMapHost` dentro do estado "atendimento aceito" de
  `ProviderIncomingRequestScreen` (hoje 100% textual, sem mapa), seguindo o mesmo padrão visual e
  de ciclo de vida já usado em `AppointmentTrackingScreen`/`ClientMobileDispatchScreen`. A câmera
  inicial enquadra os dois pontos (posição do prestador vinda do tracking já publicado pela feature
  013; posição do cliente pontual, item 2 acima); após o enquadramento inicial, gestos manuais do
  prestador (arrastar/zoom) não são revertidos automaticamente — mesmo comportamento de
  `followProvider` opcional já existente em `AppointmentTrackingViewModel`, generalizado para dois
  pontos.
- **Rationale**: `ProviderIncomingRequestScreen` de fato não tem nenhum mapa hoje (grep por
  `Map`/`map` só retorna a content description do botão voltar); reaproveitar o host de mapa e o
  padrão de câmera já existentes evita duplicar lógica de estilo/erro/fallback de mapa.
- **Alternatives considered**: navegar para a tela `AppointmentTrackingScreen` compartilhada em vez
  de embutir o mapa na tela do prestador → parcialmente já acontece hoje ("Abrir acompanhamento"),
  mas essa tela centraliza só o prestador (ver item 4) e não é o texto explícito do pedido do
  usuário ("incluir **no mapa do guincho**"); a decisão é complementar, não substituir, o botão
  existente.

## 4. Câmera dupla em `AppointmentTrackingScreen` (FR-011, SC-006)

- **Decision**: Estender `AppointmentTrackingViewModel.moveCameraTo` para considerar as duas
  coordenadas (cliente e prestador) ao montar o `MapViewportCommand`, em vez de usar apenas
  `location.coordinate` do prestador como hoje.
- **Rationale**: Confirmado em código: `moveCameraTo` centraliza exclusivamente a coordenada do
  prestador; é o motivo exato do cliente não ver a própria posição referenciada no mapa hoje.
- **Alternatives considered**: nenhuma — é uma correção direta do bug já identificado no spec.

## 5. Botão voltar/retomada automática (US4, FR-017, FR-018)

- **Decision**: Ajustar a condição do `LaunchedEffect(dispatchResumeState, sessionState,
  currentRoute)` em `PitGoNavHost.kt` para só disparar a navegação de retomada quando a revisão do
  atendimento/appointment ativo muda em relação à última vez que essa mesma navegação foi
  disparada nesta sessão de app (comparação por `revision`, já presente no snapshot), e não a cada
  recomposição em que `dispatchResumeState` simplesmente continua `Active`. Um "voltar" manual passa
  a ser respeitado até a próxima mudança real de revisão (novo evento) ou até reinício de sessão.
- **Rationale**: A causa raiz já foi confirmada nesta mesma sessão de trabalho: a correção
  anterior (que passou a observar `provider-dispatch`/`mobile-dispatch` continuamente, corrigindo o
  bug de "empresa não recebe solicitação") teve o efeito colateral de reexecutar esse
  `LaunchedEffect` a cada emissão do estado, mesmo sem mudança real — inclusive logo após o
  usuário apertar voltar —, dando a impressão de botão travado.
- **Alternatives considered**: remover completamente a navegação automática → rejeitado, quebraria
  a retomada esperada ao reabrir o app com atendimento ativo (US4 cenário 3, já coberta pelo spec);
  usar uma flag "usuário saiu manualmente" persistida → rejeitado por adicionar estado extra quando
  a comparação por `revision` (já existente no domínio) resolve o mesmo problema de forma mais
  simples.

## 6. Feedback de carregamento em botões (FR-002)

- **Decision**: Ao lado/no lugar do texto do botão, quando `state.pending != null` (provider) ou
  `state.pending != null` (client), mostrar um `CircularProgressIndicator` pequeno inline (mesmo
  componente já usado em `ClientMobileDispatchScreen` para o estado de tela cheia) ou um texto
  "Processando…", em vez de apenas desabilitar o botão silenciosamente.
- **Rationale**: Confirmado em código: `ProviderIncomingRequestScreen` desabilita ACEITAR/RECUSAR
  via `enabled = state.pending == null` sem nenhum indicador visual próximo; mesmo padrão em
  `ClientMobileDispatchScreen` para SOLICITAR/PRÓXIMO.
- **Alternatives considered**: `Snackbar` informando "processando" → rejeitado, é menos direto que
  um indicador inline junto ao próprio botão que mudou de estado.

## 7. Formatação de distância (FR-003)

- **Decision**: Na formatação de `candidate.distanceMeters` (hoje sempre `metros / 1000.0` em
  km), quando o valor for menor que um limiar pequeno (ex.: 500 m), exibir um texto amigável
  ("Muito próximo") em vez do número; acima do limiar, manter o formato em km já existente.
- **Rationale**: Confirmado em código (`ClientMobileDispatchScreen`, uso direto de
  `candidate.distanceMeters / 1000.0` sem nenhum tratamento); o valor "0,0 km" observado em teste
  real não era um dado ausente/nulo, e sim uma distância real muito pequena exibida sem contexto,
  o que os usuários leem como um placeholder quebrado.
- **Alternatives considered**: ocultar a distância totalmente quando pequena → rejeitado, perde
  informação útil ("o prestador já está bem perto" é um dado positivo para o cliente).

## 8. Itens do spec já implementados (sem mudança de código necessária)

Confirmados por inspeção de código; mantidos no spec como comportamento esperado, não como
trabalho novo desta feature:

- **FR-001 (contagem regressiva)**: `ClientMobileDispatchViewModel.startClock()` já recalcula e
  publica `remainingSeconds` a cada 1s a partir de `responseDeadlineEpochMillis`; o mesmo padrão já
  existe no lado do prestador (`ProviderIncomingRequestViewModel.updateCountdown()`). O "travamento"
  observado em teste manual foi causado por uma solicitação já expirada há minutos (efeito de
  múltiplas reinstalações durante teste), não por um bug de ticker. Nenhuma mudança de código
  esperada aqui; verificar apenas ausência de regressão.
- **FR-004 (busca esgotada)**: `ClientMobileDispatchUiState.Empty` já existe e já renderiza título,
  mensagem e ação "Tentar novamente" (`CenterAction` com `onRetry`). Nenhuma mudança esperada.
- **FR-006 (Home sem veículo)**: `HomeScreen` já resolve `vehicle == null` com `NoVehicleCard`,
  título, mensagem e botão para abrir a Garagem. Nenhuma mudança esperada além de manter esse
  comportamento intacto.

## 9. Preferência de aparência (FR-008)

- **Decision**: Nova preferência DataStore (`AppearancePreferences`, no padrão já usado por
  `ProviderTrackingPreferences`/`VehicleOnboardingRepository`) guardando `LIGHT`/`DARK`/`SYSTEM`;
  `MainActivity` passa a ler essa preferência e repassar um `Boolean?` (null = seguir sistema) para
  o parâmetro já existente `PitGoTheme(darkTheme: Boolean = isSystemInDarkTheme())`.
- **Rationale**: Constatado que hoje `PitGoTheme` é chamado sem argumentos em `MainActivity.kt` —
  não existe nenhum mecanismo de override manual, apesar de a constituição do produto já exigir
  Light/Dark/System com persistência. `PitGoTheme` já aceita um `Boolean` de entrada, então a
  mudança é apenas de composição/leitura de preferência, sem alterar o design system.
- **Alternatives considered**: `SharedPreferences` → rejeitado, DataStore já é o padrão do projeto
  para preferências simples (constituição, princípio II).

## 10. Estrutura da aba Perfil (FR-007, FR-008)

- **Decision**: Introduzir um `ProfileViewModel` que agrega o estado hoje ausente (endereço do
  cliente via item 1, veículo padrão via `Garage`/sessão já existente, preferência de aparência via
  item 9), mantendo `ProfileScreen` como composable de apresentação (sem lógica de negócio), no
  mesmo padrão MVVM do restante do app. O card de veículo padrão reaproveita a navegação já
  existente para `Garage`/`VehicleForm`; nenhuma tela nova de veículo é criada.
- **Rationale**: `ProfileScreen` hoje não tem ViewModel próprio (recebe `AccountSnapshot` pronto);
  as novas seções (endereço, veículo, aparência) exigem estado e ações que não cabem só na tela.
- **Alternatives considered**: expandir o ViewModel que já fornece `AccountSnapshot` (provavelmente
  `SessionViewModel`) → rejeitado, misturaria responsabilidade de sessão/autenticação com
  apresentação específica da aba Perfil, contrariando responsabilidade única (constituição,
  princípio V).
