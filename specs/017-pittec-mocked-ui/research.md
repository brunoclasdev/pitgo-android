# Phase 0 Research: Experiência mockada de UI do PitTec

## 1. Isolamento do fluxo real existente

### Decision

Manter o código da feature real `015-pittec-on-demand`, mas retirar seu wiring do protótipo. A
rota cliente não cria `PitTecRepository`/`PitTecResumeViewModel` nem abre
`AppointmentTrackingViewModel`. Para `ProviderLocationMode.PITTEC`, o NavHost não cria
`ProviderDashboardViewModel`, exclui PITTEC do watchdog real e encerra qualquer tracking anterior
por uma ação local-only que não atualiza disponibilidade no servidor.

### Rationale

Hoje, apenas compor esses ViewModels já restaura despacho via Supabase, inicia Realtime/polling ou
publica localização. Trocar somente os Composables deixaria efeitos reais invisíveis, contrariando
FR-033. Preservar as classes evita uma exclusão ampla e permite reativar a feature 015 depois do
protótipo.

### Alternatives considered

- Mockar respostas dentro dos repositories reais: rejeitado porque mistura contratos locais e de
  backend, mantém risco de chamadas acidentais e dificulta provar isolamento.
- Apagar a implementação real: rejeitado por ampliar o escopo e destruir trabalho reutilizável.
- Usar uma flag remota: rejeitado porque adicionaria infraestrutura externa a um protótipo local.

## 2. Estado de sessão e independência entre perfis

### Decision

Usar dois ViewModels distintos, um para cliente e outro para prestador, escopados ao owner do
`PitGoNavHost` e chaveados pela identidade da sessão. Não usar `SavedStateHandle`, DataStore, Room
ou singleton de processo. Cada ViewModel mantém sua própria fixture, revisão e atendimento; uma
ação de um perfil nunca atualiza o outro.

### Rationale

O owner acima das destinations mantém o estado quando a tela é retirada/reaberta na mesma sessão
e durante mudanças de configuração. A morte do processo limpa naturalmente a simulação, que é o
limite definido pela spec. ViewModels por destination perderiam o estado ao voltar à Home; um
store persistente sobreviveria além do necessário.

### Alternatives considered

- Um coordenador compartilhado entre cliente e prestador: rejeitado porque criaria sincronização
  que a clarificação explicitamente excluiu.
- Repository/store singleton em memória: funciona, mas adiciona camada e lifecycle próprios sem
  necessidade quando o ViewModel host-scoped resolve o caso.
- `rememberSaveable`/`SavedStateHandle`: rejeitados porque poderiam restaurar o protótipo após
  recriação do processo e exigiriam serializar uma máquina de estados desnecessariamente.

### References

- [ViewModel overview](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [Save UI states](https://developer.android.com/develop/ui/compose/state-saving)

## 3. Localização real do prestador

### Decision

Reutilizar `AndroidLocationDataSource` e `DeviceLocationRepository`. Solicitar COARSE e FINE juntas
no momento de uso, mas considerar a Home pronta somente com localização precisa. Modelar
explicitamente `PermissionRequired`, `ApproximateOnly`, `ServicesDisabled`, `Locating`, `Ready` e
`Unavailable`. Sem `Ready`, não compor o mapa. O CTA abre os Ajustes adequados e o Route revalida
permissão/serviços no `ON_START` ao retornar.

### Rationale

O projeto já obtém localização sem Google Play Services e suporta API 24. Android 12+ pode
conceder somente localização aproximada mesmo quando as duas permissões são pedidas; isso precisa
ser um estado acionável. O requisito torna a posição real essencial somente para a Home PitTec,
portanto o bloqueio fica localizado nessa tela.

### Alternatives considered

- Fused Location Provider: rejeitado por exigir nova dependência sem benefício demonstrado.
- Coordenada mock de fallback: rejeitada pela decisão explícita do usuário.
- `ACCESS_BACKGROUND_LOCATION` ou FGS: rejeitados; a leitura é foreground e não deve publicar.
- Espera ilimitada pelo primeiro fix: rejeitada; timeout leva a `Unavailable` com retry.

### References

- [Request location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime)
- [LocationManagerCompat](https://developer.android.com/reference/androidx/core/location/LocationManagerCompat)
- [ACTION_LOCATION_SOURCE_SETTINGS](https://developer.android.com/reference/android/provider/Settings#ACTION_LOCATION_SOURCE_SETTINGS)

## 4. Fixtures, busca progressiva e preço

### Decision

Definir fixtures Kotlin determinísticas, sem JSON/arquivo externo. Cada cenário contém candidatos
ordenados por faixa de 5 km e uma resposta `ACCEPT` ou `DECLINE`. A tentativa principal percorre
5, 10 e 15 km para demonstrar R$ 60,00, R$ 70,00 e R$ 80,00; uma tentativa posterior esgota os
candidatos e termina em vazio. O preço usa `Long` em centavos:

```text
fixedCents = 5000
bandCents = 1000
bandCount = perimeterKm / 5
totalCents = fixedCents + bandCents * bandCount
```

Quando a faixa se esgota após recusa, a mesma busca avança 5 km, recalcula e anuncia a nova
composição; não pede uma segunda confirmação.

### Rationale

Fixtures tipadas tornam os cenários reproduzíveis e testáveis sem criar infraestrutura. Centavos
evitam erro de ponto flutuante. A continuação sem reconfirmação decorre de FR-017, que exige tentar
o próximo candidato e ampliar automaticamente a mesma busca.

### Alternatives considered

- Distância aleatória: rejeitada por tornar SC-003/SC-004 não reproduzíveis.
- Seletor técnico sempre visível: rejeitado porque altera a experiência inicial que FR-011 manda
  preservar.
- `Double` para dinheiro: rejeitado por arredondamento desnecessário.
- Congelar R$ 60,00 após ampliar para 10/15 km: rejeitado porque o preço deixaria de representar a
  faixa em que o prestador aceito foi encontrado.

## 5. Timers e lifecycle

### Decision

Cada ViewModel mantém no máximo um `Job` por responsabilidade. Respostas automáticas bilaterais
usam `delay(3_000)` com token de revisão e continuam mesmo se o usuário trocar de aba. A simulação
publica a posição inicial no aceite e, enquanto a experiência está `STARTED`, usa `delay(30_000)`
entre os demais passos; pausa ao esconder a experiência e cancela ao concluir. Ao voltar, primeiro
recalcula a idade do último ponto e só então retoma a cadência, sem mascarar um stale. Um ticker de
1 s calcula stale por `SystemClock.elapsedRealtime()` e marca a posição após 35.000 ms.

### Rationale

`collectAsStateWithLifecycle()` pausa a coleta da UI, não um loop quente em `viewModelScope`.
Eventos explícitos `onVisible`/`onHidden` mantêm o trabalho proporcional ao lifecycle. Relógio
monotônico não é afetado por alteração manual de data/hora. Tokens e checagem do estado impedem
que jobs antigos apliquem transições depois de retry, recusa ou conclusão.

### Alternatives considered

- WorkManager/AlarmManager: rejeitados porque são persistentes/background e imprecisos para uma
  demonstração em foreground.
- Loop global sempre ativo: rejeitado por atualizar tela escondida e dificultar cancelamento.
- Relógio civil: rejeitado porque ajustes do sistema podem produzir idade negativa ou saltos.

### References

- [Lifecycle-aware components and Compose](https://developer.android.com/topic/libraries/architecture/lifecycle)
- [StateFlow and SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
- [Kotlin coroutines on Android](https://developer.android.com/topic/libraries/architecture/coroutines)
- [SystemClock.elapsedRealtime](https://developer.android.com/reference/android/os/SystemClock#elapsedRealtime())

## 6. Pulso roxo e redução de movimento

### Decision

O ViewModel expõe uma revisão de alerta e se o efeito ainda não foi consumido. O Composable marca
o efeito como consumido ao iniciá-lo e executa três ciclos de até 1 s com `Animatable`, mantendo o
card roxo visível durante e depois do efeito. Se
`MotionDurationScale.scaleFactor == 0f`, não há pulso: o card persistente aparece imediatamente.
Texto, heading/live region e botões Aceitar/Recusar existem nas duas variantes.

### Rationale

`MotionDurationScale` já faz parte do runtime Compose e reflete a escala de animação fornecida ao
recomposer Android, sem adicionar `core-animation`. Consumir a revisão no ViewModel evita que a
animação seja repetida por recomposição ou ao voltar à tela. Três pulsos lentos atendem ao efeito
pedido sem comunicar estado só por movimento ou produzir uma frequência agressiva.

### Alternatives considered

- Animação infinita: rejeitada porque distrai, consome recursos e excede o requisito.
- Inferir redução de movimento por `AccessibilityManager.isEnabled`: rejeitado porque leitor de
  tela não equivale a preferência de animação.
- Depender apenas da cor: rejeitado por acessibilidade e FR-006/FR-031.
- Adicionar `androidx.core:core-animation`: rejeitado porque o runtime Compose já fornece a
  informação necessária.

### References

- [MotionDurationScale API](https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale)
- [Compose animation quick guide](https://developer.android.com/develop/ui/compose/animation/quick-guide)
- [WindowRecomposer Android source](https://android.googlesource.com/platform/frameworks/support/+/1abcb4178d48853948b9b566cabff9222d90ab69/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/WindowRecomposer.android.kt)

## 7. Mapa, distância e câmera

### Decision

Reutilizar `MapLibreMapHost`, `MapRenderModel`, `framingViewport` e o controller de participantes
existente, promovendo este último a `core/map/ui`. O primeiro frame aceito e o botão
“Centralizar” emitem comando revisionado para os dois pontos. Atualizações de 30 s nunca emitem
comando de câmera; atualizam somente marcador, linha direta, distância e timestamp. A distância é
haversine/local e apresentada como estimada; não existe chamada de rota.

### Rationale

O host já mantém viewport separado de comandos e preserva gestos. Não é necessário detectar o
motivo do movimento: basta não gerar comando automático nos ticks. O controller atual já desenha
cliente, PitTec, stale e linha tracejada, portanto sua promoção cria reutilização real sem duplicar
integração MapLibre.

### Alternatives considered

- Reenquadrar a cada tick: rejeitado porque rouba a câmera após pan/zoom manual.
- Adicionar callback de gesto ao host: desnecessário; nenhum tick reenquadra em qualquer caso.
- Reutilizar `AppointmentTrackingViewModel`: rejeitado porque ele chama backend e rota externa.
- Implementar roteamento mock: rejeitado; linha direta e distância estimada atendem ao protótipo.

### References

- [MapLibre camera bounds example](https://maplibre.org/maplibre-native/android/examples/camera/lat-lng-bounds/)

## 8. Bottom navigation e informações da sessão

### Decision

Estender `BottomItemId` e fornecer `PitTecBottomNavigationItems`, reutilizando o mesmo
`PitGoBottomNavigation`. O shell prestador mantém Mapa, Dados da corrida, Valores e Perfil como
conteúdos de uma única destination. Dados e Valores derivam do mesmo `currentOrLastAppointment`;
Perfil mostra os dados básicos já disponíveis no `ProviderSnapshot` e logout.

### Rationale

Uma destination com abas preserva naturalmente a simulação, evita quatro rotas artificiais e
mantém a estrutura visual já usada por Home/Garagem/Perfil do cliente. O perfil cliente existente
não é reutilizável porque inclui veículo, endereço e migração específicos.

### Alternatives considered

- Quatro rotas e quatro ViewModels: rejeitadas por fragmentar estado local e ampliar wiring.
- Reutilizar `ProfileScreen` cliente: rejeitado por dados e ações incorretos para prestador.
- Criar histórico/saldo: rejeitado porque FR-010A exclui ambos.

## 9. Limite de rede observável

### Decision

Considerar permitidas somente as dependências já existentes fora do protótipo: autenticação,
catálogo e carregamento do estilo/tiles do mapa. Nenhum evento novo do fluxo PitTec pode chamar
RPCs de descoberta/despacho, Realtime, publicação de localização, Edge Function de rota,
notificação ou pagamento.

### Rationale

O app ainda precisa estar autenticado e o mapa gerenciado pode depender de rede; declarar essa
fronteira evita prometer um aplicativo completamente offline. O que precisa ser demonstravelmente
local são os dados, transições e efeitos da nova jornada.

### Alternatives considered

- Tornar autenticação, catálogo e tiles offline: rejeitado por estar fora do escopo.
- Aceitar chamadas de leitura ao dispatch real: rejeitado porque criariam dependência e possível
  redirecionamento externo mesmo sem escrita.
