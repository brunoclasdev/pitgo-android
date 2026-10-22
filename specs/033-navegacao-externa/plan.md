# Implementation Plan: Navegação externa e indicação de localização

**Branch**: `feature/rota-externa` | **Date**: 2026-10-22 | **Spec**: [spec.md](spec.md)

**Input**: `specs/033-navegacao-externa/spec.md`. O identificador da feature é `033-navegacao-externa`; o script resolve esse identificador como `BRANCH`, enquanto a branch Git verificada é `feature/rota-externa`.

## Summary

Oferecer uma ação “Abrir navegação” aos prestadores autorizados, com escolha explícita entre Waze e Google Maps. Reutilizar as coordenadas vigentes da retirada, do destino final do guincho ou do atendimento PitTec, validar o contexto no ViewModel e executar intents Android com pacote fixo na fronteira da UI. Manter o compartilhamento existente e recarregar o atendimento no retorno. Nos mapas do acompanhamento, escolher o ícone pelo participante e conservar a informação textual de posição antiga ou indisponível, sem emitir o marcador de relógio.

## Technical Context

**Language/Version**: Kotlin/Compose Compiler 2.2.10 declarados no catálogo; AGP 9.1.1; compatibilidade Java 11.

**Primary Dependencies**: Android SDK (`Intent`, `PackageManager`, `Uri`), Compose BOM 2026.06.01/Material 3, Lifecycle 2.10.0, Navigation Compose 2.9.8, MapLibre 13.5.0 e repositórios Supabase já usados. Versões são as registradas no projeto, sem atualização ou nova dependência nesta feature.

**Storage**: Estado transitório no ViewModel. Reutilização dos snapshots existentes; nenhuma tabela, migration, preferência persistida ou serviço de backend novo.

**Testing**: Compilação Homol Debug e validação manual de fluxos, falhas, localização e acessibilidade. Não criar testes automatizados, mocks ou fixtures, conforme a constituição.

**Target Platform**: Android nativo, minSdk 24, targetSdk 36, compileSdk 37. Conferir visibilidade no Android 11+ e comportamento nos aparelhos de validação disponíveis, incluindo A32.

**Project Type**: Aplicativo Android em um módulo `app`, organizado por feature e recursos compartilhados em `core`.

**Performance Goals**: Até duas ações desde o acompanhamento, inclusive com resumo recolhido. Aplicativo externo visível em até 5 segundos após a escolha em pelo menos 19 de 20 tentativas com navegadores configurados, incluindo a revalidação do atendimento. A abertura independe da geometria e da origem disponível no PitGo.

**Constraints**: Sem contexto Android retido pelo ViewModel; sem transições de atendimento causadas pelo navegador; dados mínimos no intent; seleção explícita por abertura; erros acionáveis; layout responsivo e fonte ampliada. Preservar rastreamento autorizado, intervalos e permissões atuais.

**Scale/Scope**: Dois navegadores, dois perfis de prestador e três destinos operacionais. Mapas de atendimento nas visões de cliente/prestador; nenhum endpoint novo ou impacto de escala no backend além de leituras pontuais por escolha.

## Constitution Check

*GATE avaliado antes da pesquisa e novamente após o desenho.*

| Princípio | Antes da pesquisa | Após o desenho | Evidência |
|---|---|---|---|
| I. Simplicidade e requisito atual | PASS | PASS | Intents nativos; reaproveitamento de snapshots, ícones e serviço; sem SDK ou persistência nova. |
| II. Android nativo e MVVM | PASS | PASS | Regras e estado no ViewModel; dados pelos repositórios existentes; UI executa apenas o efeito Android. |
| III. Estado previsível e experiência | PASS | PASS | Estados exclusivos do seletor; pedido consumido uma vez; Material oficial; dois toques sem expandir resumo; fonte ampliada. |
| IV. Dados, segurança e falhas | PASS | PASS | Destino validado, contexto revalidado, pacote fixo, exceções recuperáveis, ausência de dados pessoais e coordenadas nos logs. |
| V. Qualidade proporcional | PASS | PASS | Compilação e aceite manual; sem novos testes/mocks, camadas sem uso ou refatoração externa ao escopo. |

**Resultado**: Todos os gates aprovados; nenhuma exceção à constituição e nenhuma decisão pendente.

## Project Structure

### Documentation (this feature)

```text
specs/033-navegacao-externa/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── external-navigation.md
└── checklists/
    └── requirements.md
```

`tasks.md` será produzido por `$speckit-tasks`, após este planejamento.

### Source Code (repository root)

```text
app/src/main/
├── AndroidManifest.xml                              # consultas dos dois pacotes
├── java/com/trustarx/pitgo/
│   ├── core/navigation/
│   │   └── ExternalNavigationLauncher.kt              # novo: integração Android sem regra de atendimento
│   ├── core/map/
│   │   ├── model/MapSurfaceModels.kt                  # tipos e coordenadas existentes
│   │   └── ui/ParticipantMapStyleController.kt         # ícones padrão já disponíveis
│   └── features/
│       ├── appointmenttracking/
│       │   ├── model/AppointmentTrackingModels.kt      # estado da escolha e marcadores por papel
│       │   ├── repository/AppointmentTrackingRepository.kt # leitura pontual da posição PitTec
│       │   ├── viewmodel/AppointmentTrackingViewModel.kt   # elegibilidade, revalidação e pedido único
│       │   └── ui/
│       │       ├── AppointmentTrackingScreen.kt       # histórico, efeito Android e ação PitTec
│       │       └── ExternalNavigationChooser.kt        # novo: seletor compartilhado pelos dois conteúdos
│       ├── guincho/ui/GuinchoTrackingContent.kt        # ação, marcador e avisos
│       ├── pittec/ui/PitTecMockTrackingContent.kt      # coerência do consumidor visual existente
│       └── providertracking/service/ProviderLocationTrackingService.kt # reutilizado
└── res/values/strings.xml                             # textos e acessibilidade
```

**Structure Decision**: Manter a regra dentro de `appointmenttracking`, onde já se reúnem os dois tipos de atendimento. Um pequeno adaptador em `core/navigation` constrói, verifica e abre intents; o seletor tem reutilização real entre guincho e PitTec. Os itens listados como reutilizados não exigem alteração. Não criar Use Case, Repository ou módulo só para repassar a abertura.

## Phase 0: Pesquisa concluída

[research.md](research.md) registra integração oficial, visibilidade de pacotes, destinos, concorrência, retorno, compartilhamento e marcadores. Todas as incógnitas técnicas foram resolvidas. Não há seleção de dependência externa nova.

## Phase 1: Desenho

### Elegibilidade e destino

- `!isClient`, `!historical`, sessão válida, snapshot do mesmo atendimento e `trackingAuthorized == true` são obrigatórios. A restrição do histórico entra no ViewModel, e não somente na visibilidade do botão.
- Guincho: `ACCEPTED` leva a `guinchoTrip.pickup`; `IN_PROGRESS` leva a `guinchoTrip.destination`. Permanecer na retirada durante vistoria não permite antecipar o destino final.
- PitTec: somente `ACCEPTED` com `arrivedAt == null`, usando a posição válida vigente do cliente no atendimento. Outros tipos de serviço não recebem a ação.
- No toque na opção, suspender novos toques, ler o snapshot por `appointmentSnapshot()` e, para PitTec, a posição pelo repositório de acompanhamento. Reutilizar os RPCs atuais. Se a leitura falhar, não abrir com contexto sem confirmação; mostrar mensagem e permitir nova tentativa. Reaplicar regras à resposta e ignorar retornos de uma geração descartada.
- Atualização de etapa/destino usa o ponto vigente; cancelamento ou perda de autorização interrompe a abertura com aviso. A revalidação é uma leitura pontual, sem alegação de atomicidade com mudanças posteriores no servidor.

### Estado, UI e fronteira Android

- Modelar `Closed`, `Choosing`, `Preparing`, `ReadyToLaunch` e `Failure` como alternativas explícitas no estado da tela; pedidos carregam identificador único. Detalhes e invariantes em [data-model.md](data-model.md).
- A ação compacta permanece acessível no mapa sem expandir o resumo; reutilizar espaçamentos e componentes Material do projeto, reservar espaço para não cobrir controles/avisos, respeitar insets, rolagem e fonte ampliada. Abrir um `AlertDialog` com Waze, Google Maps e cancelamento. Mostrar ambas as opções, marcando indisponíveis; com nenhuma disponível, exibir orientação e fechamento.
- A rota Compose consulta disponibilidade pelo adaptador, envia os resultados ao ViewModel e executa somente pedidos aprovados enquanto visível. Consumir o pedido antes de iniciar o aplicativo, para que retorno ou recomposição não o repitam. Falha devolve mensagem e possibilidade de escolher outro aplicativo.
- `ExternalNavigationLauncher` recebe contexto apenas durante chamadas, além do navegador e da coordenada, constrói o intent conforme o [contrato](contracts/external-navigation.md), verifica resolução e captura falhas de abertura. `ExternalNavigator` e o resultado de abertura ficam nesse limite compartilhado; o contexto de atendimento permanece na feature. O adaptador não importa modelos de atendimento nem conhece suas regras de pagamento. ViewModel não recebe nem guarda `Context`/`Activity`.
- Ao voltar, `onVisible()` restaura o estado vigente. `onHidden()` pode limpar estado transitório e cancelar consultas da tela, preservando os dados do serviço. Nenhum código da abertura chama chegada, início, vistoria, pagamento, conclusão ou parada de rastreamento.

### Marcadores e mensagens

- Alterar `AppointmentTrackingUiState.markers` para escolher `PITTEC_LIVE` ou `LIVE` pelo perfil mesmo com posição antiga; em `GuinchoTrackingContent`, usar `LIVE` para a última posição válida. Cliente mantém `CLIENT_LOCATION`.
- Manter o estado real de atualização e o limite de 30 segundos. Acrescentar informação de última posição conhecida a falhas/offline quando houver um ponto e oferecer aviso explícito de indisponibilidade quando não houver. Compartilhar o componente textual existente quando houver reutilização útil.
- Ajustar `PitTecMockTrackingContent`, consumidor de apresentação já existente, para manter o ícone PitTec. Não produzir novos dados simulados ou infraestrutura de mock.
- Preservar o suporte legado de `STALE` no controlador compartilhado, usado também pelo mapa de descoberta do guincheiro. A remoção do relógio é garantida nos produtores do acompanhamento, sem trocar o ícone de todas as categorias indiscriminadamente.

### Contratos, dados e operação

[data-model.md](data-model.md) define os modelos transitórios e suas validações; [contracts/external-navigation.md](contracts/external-navigation.md) define seleção, intents e retorno. Nenhum identificador ou dado pessoal do atendimento é enviado. Não há migration, Edge Function ou deploy de backend para esta feature.

### Validação e encerramento do desenho

[quickstart.md](quickstart.md) cobre os seis destinos/aplicativos, estados bloqueados, revalidação concorrente, aplicativos indisponíveis, localização ausente, erros, retorno, compartilhamento, relógio e acessibilidade. A implementação deverá compilar em Homol; o aceite deve registrar condições e resultados manuais, sem criar testes automatizados. Gates reavaliados após o desenho: todos aprovados.

## Complexity Tracking

Nenhuma violação ou complexidade adicional a justificar.
