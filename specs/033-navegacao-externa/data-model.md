# Modelo de dados: navegação externa

**Feature**: [spec.md](spec.md) | **Plano**: [plan.md](plan.md)

Os nomes novos abaixo são propostas de implementação. Todos os dados novos são transitórios, sem alteração de banco ou preferência persistida.

## Contexto do atendimento

Reutilizar `MobileDispatchSnapshot`, `MobileAppointment` e `AppointmentTrackingUiState`:

| Campo existente | Uso |
|---|---|
| `appointment.id`, `appointment.revision`, `snapshot.revision` | Identificar contexto e rejeitar resposta antiga ou pertencente a outro atendimento. |
| `dispatchKind`, `trackingAuthorized`, `appointment.status` | Determinar serviço e permissão de deslocamento pelas regras existentes. |
| `appointment.arrivedAt` | Encerrar a navegação PitTec até o cliente após chegada. |
| `guinchoTrip.pickup`, `guinchoTrip.destination` | Coordenadas confirmadas das duas etapas do guincho. |
| `clientLocation.coordinate` | Ponto vigente do atendimento PitTec, lido do acompanhamento. |

`isClient` e `historical` são propriedades do contexto da tela e entram na decisão do ViewModel. O tipo de serviço e a etapa são derivados; não precisam de novas colunas ou flags financeiras.

## Destino de navegação

Um valor transitório `ExternalNavigationTarget` contém:

| Campo | Regra |
|---|---|
| `appointmentId` | Mesmo atendimento da tela; usado apenas internamente. |
| `snapshotRevision` | Revisão do snapshot revalidado; nunca retroceder o estado já observado. |
| `appointmentRevision` | Revisão do atendimento; rejeitar atualização operacional mais antiga mesmo quando a revisão da busca não mudou. |
| `leg` | `GUINCHO_PICKUP`, `GUINCHO_DESTINATION` ou `PITTEC_CLIENT`. |
| `coordinate` | `MapCoordinate` presente e válido. |

O rótulo de destino pode ser derivado de `leg` para exibição local. Nenhum identificador interno ou rótulo com dados pessoais integra o intent.

**Validação**: Latitude e longitude finitas, nos intervalos `[-90, 90]` e `[-180, 180]`. Ausência de destino não pode virar coordenada padrão, centro de rua ou extremidade de polilinha. `(0, 0)` não recebe proibição geográfica artificial; não deve ser fabricado como substituto. A posição de origem não é campo obrigatório, pois é obtida pelo navegador.

| Contexto autorizado | Etapa | Destino |
|---|---|---|
| Guincho, `ACCEPTED` | Retirada, inclusive antes do início do transporte | `guinchoTrip.pickup` |
| Guincho, `IN_PROGRESS` | Transporte iniciado | `guinchoTrip.destination` |
| PitTec, `ACCEPTED`, `arrivedAt == null` | Ida ao cliente | `clientLocation.coordinate` |
| Cliente, histórico, outra modalidade, sem autorização ou outro estado | Sem navegação operacional | Nenhum |

## Opção de navegador

`ExternalNavigator` possui os valores `WAZE` e `GOOGLE_MAPS` e pertence ao limite compartilhado em `core/navigation`, junto do adaptador Android. Nome exibido e pacote são constantes; não vêm do backend. Uma lista de opções associa cada valor à disponibilidade observada no dispositivo. A disponibilidade é reconsultada no lançamento e não é persistida.

Um pedido `ExternalNavigationRequest` associa `requestId`, navegador e destino validado e pertence a `appointmenttracking`. O adaptador recebe somente navegador e coordenada, sem importar esse modelo de negócio. `requestId` é local e monotônico na vida do ViewModel, sem necessidade de persistência ou envio externo.

## Estado transitório da escolha

| Estado | Dados e comportamento |
|---|---|
| `Closed` | Sem seletor ou pedido pendente. |
| `Choosing` | Duas opções e disponibilidade, orientação quando nenhuma pode abrir. |
| `Preparing` | Navegador escolhido e geração da operação; desabilita repetição enquanto revalida contexto. |
| `ReadyToLaunch` | Pedido único, pronto para o efeito Android da rota visível. |
| `Failure` | Categoria e opções atuais para mensagem, cancelamento e nova escolha. |

**Transições**: `Closed → Choosing → Preparing → ReadyToLaunch → Closed`. Cancelamento leva a `Closed`. Destino ausente, contexto inválido ou falha de leitura/abertura levam a `Failure`; nova escolha passa por `Preparing` novamente. Um pedido é consumido antes do lançamento. `onHidden()` invalida a geração e descarta pedidos não consumidos; `onVisible()` restaura o atendimento, nunca relança o navegador.

**Invariantes**: Uma preparação por vez; resposta de geração cancelada é ignorada; nenhuma regressão de revisão; nenhuma alteração operacional do atendimento em qualquer transição. Estado do navegador e estado do atendimento são independentes.

**Falhas**: `DESTINATION_UNAVAILABLE`, `CONTEXT_CHANGED`, `CONTEXT_REFRESH_FAILED` e `APP_UNAVAILABLE` permitem orientação e nova tentativa. Sessão expirada segue o fluxo de autenticação existente. Não armazenar exceção Android, URI ou dados sensíveis no estado de apresentação.

A leitura pontual do cliente retorna `AppointmentClientLocationResult`: `Found`, `Unavailable` ou `Failure` com a classificação de falha já usada no projeto. A preparação tem limite de 4 segundos para as leituras; timeout preserva o atendimento e permite nova tentativa.

## Estado da posição e representação

Reutilizar `TrackingDataState` e `AppointmentProviderLocation`; manter revisão, horário e classificação de atualização existentes. `Live`, `Stale`, `Offline` e `Failure` não determinam o desenho do ícone, mas continuam determinando texto e acessibilidade.

| Participante | Ícone no acompanhamento com posição válida | Texto quando antiga ou indisponível |
|---|---|---|
| Guincheiro | `MapMarkerKind.LIVE` | Última posição conhecida/aguardando atualização; informar indisponibilidade sem ponto. |
| PitTec | `MapMarkerKind.PITTEC_LIVE` | Mesma informação, mantendo identidade PitTec. |
| Cliente | `MapMarkerKind.CLIENT_LOCATION` | Manter indicação existente quando posição antiga ou sem horário; informar ausência quando necessária. |

Não criar marcador sem coordenada válida. Atualização recente substitui a posição e encerra o aviso de desatualização correspondente. Estados de desconexão não podem fazer um ponto retido parecer uma leitura recente.
