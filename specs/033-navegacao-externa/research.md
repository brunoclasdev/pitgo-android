# Pesquisa: navegação externa e indicação de localização

**Data**: 2026-10-22 | **Feature**: [spec.md](spec.md)

## 1. Integração com os navegadores

**Decisão**: Usar `Intent.ACTION_VIEW` com pacote fixo. Waze recebe `waze://?ll=latitude,longitude&navigate=yes`, pacote `com.waze`. Google Maps recebe `https://www.google.com/maps/dir/?api=1&destination=latitude,longitude&travelmode=driving&dir_action=navigate`, pacote `com.google.android.apps.maps`. Construir parâmetros com `Uri.Builder` e representação decimal independente do idioma; omitir a origem.

**Motivo**: As interfaces oficiais permitem entregar o destino sem SDK, chave ou consulta adicional de geocodificação. O Maps pode apresentar navegação ou prévia conforme a disponibilidade da origem. Configuração inicial e permissões pertencem ao navegador. O contrato detalhado está em [contracts/external-navigation.md](contracts/external-navigation.md).

**Alternativas consideradas**: `google.navigation:q=...&mode=d` também é oficial, mas a URL de direções explicita melhor a possibilidade de prévia. Busca por endereço ou `geo:` não garante o caminho preparado. SDK embarcado, loja e fallback automático para navegador web não atendem ao escopo da escolha explícita.

**Fontes oficiais consultadas**: [Waze Deep Links](https://developers.google.com/waze/deeplinks), [Maps URLs](https://developers.google.com/maps/documentation/urls/get-started), [Google Maps Intents](https://developer.android.com/guide/components/google-maps-intents).

## 2. Disponibilidade e falhas de abertura

**Decisão**: Declarar somente os dois pacotes em `<queries>` no manifesto. Consultar a resolução do intent exato ao mostrar as opções e novamente ao lançar. Tratar `ActivityNotFoundException` e `SecurityException` como falhas recuperáveis, mantendo o atendimento e permitindo nova escolha.

**Motivo**: A interface precisa informar disponibilidade no Android 11+. A consulta pode ficar desatualizada entre escolha e abertura. A seleção permanece explícita mesmo com apenas um aplicativo disponível.

**Alternativas consideradas**: `QUERY_ALL_PACKAGES` expõe uma superfície desnecessária. Intents sem pacote podem abrir navegador ou outro aplicativo e perder a escolha do profissional. Verificar apenas a existência do pacote não confirma que a ação é resolvida.

**Fontes oficiais consultadas**: [Declaração de visibilidade](https://developer.android.com/training/package-visibility/declaring), [Interação entre aplicativos](https://developer.android.com/training/package-visibility/use-cases).

## 3. Elegibilidade, destino e atualização concorrente

**Decisão**: Centralizar a regra no `AppointmentTrackingViewModel`, reutilizando `MobileDispatchSnapshot`, `trackingAuthorized`, `MobileAppointmentStatus`, `GuinchoTripSummary` e a posição do cliente retornada pelo acompanhamento. Propagar `historical` até a regra. Revalidar o snapshot pela leitura existente `appointmentSnapshot()` antes de criar o pedido de lançamento; para PitTec, atualizar também a leitura da posição do cliente, expondo uma operação pontual no repositório existente. Falha na revalidação informa impossibilidade de confirmar o atendimento e permite tentar novamente.

**Motivo**: Guincho utiliza `trip.pickup` em `ACCEPTED` e `trip.destination` em `IN_PROGRESS`. PitTec utiliza o ponto do atendimento em `clientLocation`, com `ACCEPTED` e `arrivedAt == null`. Destino inválido não recebe substituto. A geração da escolha, o identificador e a revisão do atendimento impedem aplicar respostas atrasadas ou repetir lançamentos. A consulta é de estado, sem depender do cálculo da rota interna.

**Alternativas consideradas**: Usar o fim da polilinha condicionaria a abertura ao roteamento e poderia reaproveitar um percurso antigo. Geocodificar novamente o endereço perderia a coordenada confirmada. Capturar o destino apenas ao abrir o seletor ignoraria mudanças de etapa. Criar RPC de navegação ou gravar preferência não é necessário.

**Evidência local**: `features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt`, `features/appointmenttracking/repository/AppointmentTrackingRepository.kt`, `features/mobiledispatch/model/MobileDispatchModels.kt`, `features/guincho/ui/GuinchoTrackingContent.kt`. Todos os caminhos de código são relativos a `app/src/main/java/com/trustarx/pitgo/`.

## 4. Estado de tela, retorno e localização em segundo plano

**Decisão**: Manter o seletor e a preparação como estado imutável do ViewModel. Um pedido de abertura tem identificador de consumo único e é consumido pela rota Compose visível, que executa a chamada Android e comunica o resultado. ViewModel não guarda `Context` ou `Activity`. Ao retornar, usar a recarga existente de `onVisible()`; nenhum pedido consumido é restaurado ou relançado.

**Motivo**: `LifecycleStartEffect` chama `onHidden()` ao sair para o navegador, cancelando observação e limpando o estado da tela. O serviço `ProviderLocationTrackingService` já publica em primeiro plano, independentemente dessa observação: intervalo de movimento de 2 segundos e heartbeat parado de 10 segundos, sujeitos às condições existentes. Abrir navegador não deve chamar parada do serviço nem transições operacionais.

**Alternativas consideradas**: Executar intents em recomposição ou restaurar um pedido já consumido pode abrir duas vezes. Novo serviço, polling ou permissão de localização não tem necessidade atual. Não se promete callback do navegador comprovando chegada ou desenho da rota.

## 5. Marcadores sem relógio e informação de atualização

**Decisão**: Nos produtores de marcadores do acompanhamento, escolher o ícone pelo participante, independentemente da idade: guincho `LIVE`, PitTec `PITTEC_LIVE`, cliente `CLIENT_LOCATION`. Manter `TrackingDataState`, horários e limite existente de 30 segundos. Preservar os textos do acompanhamento e acrescentar onde faltar aviso explícito, especialmente no conteúdo de guincho. Em `Offline`/`Failure` com última posição, informar também que ela é a última posição conhecida.

**Motivo**: `STALE` seleciona `ic_map_stale` no `ParticipantMapStyleController`. Trocar apenas sua imagem pelo ícone genérico confundiria o PitTec com guincho. O componente também atende `GuinchoProviderHomeScreen`, cujo mapa de descoberta fica fora desta alteração. A camada legada pode permanecer para esse consumidor; os mapas de atendimento deixam de emitir `STALE`. Ajustar o consumidor já existente `PitTecMockTrackingContent` para coerência visual sem gerar novos mocks.

**Alternativas consideradas**: Apagar a camada compartilhada afetaria a descoberta. Apagar o marcador esconderia a última posição útil. Transformar o estado em `Live` ocultaria a idade da informação.

## 6. Privacidade e validação

**Decisão**: Enviar somente coordenadas e parâmetros fixos do navegador, sem dados pessoais ou identificadores do atendimento. Falhas podem ter diagnóstico de depuração com tipo de aplicativo e categoria do erro, sem URI ou coordenadas. Validar com compilação e cenários manuais; não gerar testes automatizados, mocks ou fixtures.

**Motivo**: Atende FR-014 e à constituição. O sucesso do `startActivity` comprova a abertura solicitada, enquanto o caminho e o destino exigem conferência manual em aplicativos configurados. Medir SC-002 com 20 tentativas distribuídas entre os seis casos, contando desde a escolha até o aplicativo ficar visível; pelo menos 19 devem levar até 5 segundos, incluindo a revalidação.

**Alternativas consideradas**: Telemetria nova, armazenamento de escolhas e automação de aplicativos externos ampliariam o escopo sem necessidade.

## Resultado

As decisões de integração, estados, destinos, visibilidade, ciclo de vida e marcadores estão resolvidas. Não há bloqueio de produto ou dependência nova a selecionar. O desenho continua na arquitetura atual e não exige alteração ou publicação de backend.
