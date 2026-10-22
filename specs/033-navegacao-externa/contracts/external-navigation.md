# Contrato de navegação externa e acompanhamento

**Feature**: [spec.md](../spec.md) | **Modelos**: [data-model.md](../data-model.md)

## Interface oferecida ao prestador

1. No acompanhamento autorizado, mostrar “Abrir navegação”, acessível sem expandir o resumo. Ausência temporária de rota interna ou de posição do prestador não bloqueia essa ação.
2. Abrir diálogo Material com Waze e Google Maps. Ambas as opções são identificadas por nome, com indicação textual das indisponíveis e opção de cancelar. Se nenhuma pode abrir, informar essa condição.
3. Ao escolher uma disponível, revalidar contexto e destino e impedir toques repetidos. Aplicativo indisponível ou destino inválido gera mensagem compreensível e preserva o atendimento.
4. Lançar somente o aplicativo escolhido. Fechar o seletor no sucesso; no retorno, apresentar o atendimento vigente. Cancelar, abrir ou voltar não executa ações operacionais.

A elegibilidade completa é a tabela do modelo de dados. A restrição de cliente e histórico é aplicada antes de preparar o pedido, inclusive se uma ação antiga ainda estiver na tela.

## Contrato Android de saída

| Aplicativo | Pacote fixo | URI de destino |
|---|---|---|
| Waze | `com.waze` | `waze://?ll={latitude},{longitude}&navigate=yes` |
| Google Maps | `com.google.android.apps.maps` | `https://www.google.com/maps/dir/?api=1&destination={latitude},{longitude}&travelmode=driving&dir_action=navigate` |

- Usar `Intent.ACTION_VIEW` e `setPackage` com os valores acima. Construir parâmetros com APIs de URI e números independentes do idioma; a vírgula separa latitude e longitude.
- Manifesto declara somente esses dois nomes sob `<queries>`. Consultar o intent exato com `resolveActivity` ao apresentar disponibilidade e antes do lançamento.
- Não enviar origem, nome do cliente, endereço textual, telefone, placa, imagens, valores ou identificadores do atendimento. Apenas coordenadas e parâmetros constantes necessários à rota.
- Falha na resolução, `ActivityNotFoundException` ou `SecurityException` produz `APP_UNAVAILABLE`, nova consulta das opções e mensagem local. Não abrir outro aplicativo, navegador web ou loja automaticamente.
- Chamada executada enquanto a rota está visível. Não finalizar a Activity PitGo nem limpar sua pilha de navegação para abrir o navegador. Consumo único impede repetição por recomposição ou retorno.

Formatos apoiados nas [interfaces oficiais pesquisadas](../research.md). A origem, o cálculo e eventuais confirmações pertencem ao navegador. O caminho pode diferir do PitGo. Sem origem disponível, a experiência externa pode solicitar sua definição mantendo o destino preenchido.

## Revalidação e continuidade

`AppointmentTrackingViewModel` lê `appointmentSnapshot(appointmentId)` no repositório existente. Para PitTec, faz também leitura pontual pelo repositório de acompanhamento usando `get_appointment_client_location`. Não criar endpoint, migration ou gravação para esta funcionalidade.

Validar identificador, autorização, modalidade, etapa, revisão e coordenada. Resposta atrasada não substitui snapshot mais recente. Falha de leitura exige orientação e nova tentativa, sem reutilizar silenciosamente um contexto não confirmado. Mudança válida de etapa/destino usa o estado vigente; cancelamento ou perda da permissão interrompe o pedido. Não existe bloqueio transacional que impeça alteração remota após a leitura.

Retorno Android não contém resultado de viagem e não representa chegada. O ciclo de vida existente recarrega o acompanhamento; o serviço de localização autorizado permanece independente das consultas da tela.

## Marcadores e mensagens

Nos mapas do atendimento de cliente e prestador, `LIVE` identifica guincho, `PITTEC_LIVE` identifica PitTec e `CLIENT_LOCATION` identifica cliente, inclusive para a última posição válida. Nenhum desses produtores emite `STALE` para representar idade.

Posição antiga mantém aviso textual acessível e descrição coerente com última posição conhecida; ausência de posição gera orientação sem marcador artificial. Uma posição recente retira o aviso correspondente. Preservar a classificação existente de 30 segundos e os demais estados de falha.

## Observação e aceite

O adaptador devolve somente abertura solicitada com sucesso ou falha recuperável. Isso não comprova que o navegador desenhou o caminho. A validação manual confere destino e rota nos seis casos previstos e continuidade do acompanhamento pelo cliente. Eventual diagnóstico usa apenas aplicativo/categoria de erro, sem registrar intent, URI, coordenadas ou dados pessoais.
