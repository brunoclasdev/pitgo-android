# Research: Alerta e Home do guincheiro

## 1. Reutilizar o fluxo pago

**Decision**: Consumir restoreAnyDispatchKind e ProviderIncomingRequestViewModel; alerta apenas GUINCHO_DIRECT/PENDING pago autorizado.

**Rationale**: O serviço ProviderLocationTrackingService já contempla Guincho nas notificações. PitGoNavHost ainda navega automaticamente para ProviderRequest nas ofertas não PitTec, contrariando a clarificação de card sobre a tela atual. O ajuste precisa ocorrer na apresentação e no escopo do observador, sem novo fluxo de pagamento.

**Alternatives considered**: Novo endpoint ou observador por tela duplicariam autoridade e trabalho. Reutilizar a Home do PitTec inteira mudaria perfil e comportamento além do requisito.

## 2. Card acima das rotas

**Decision**: Um host de card no escopo da sessão autenticada do guincheiro; compartilhar o ViewModel do pedido. Navegar somente após resposta válida ou restauração de atendimento já aceito.

**Rationale**: Perfil e histórico precisam receber o mesmo alerta sem perder a rota. O snapshot e revisão existentes validam resposta e contagem. Impedir duplicação com a tela ProviderRequest legado e distinguir entrada histórica de retomada operacional.

**Alternatives considered**: Navegação obrigatória para Home e aviso sem ações foram descartados pela escolha A do usuário.

## 3. Posição atual e câmera

**Decision**: DeviceLocationRepository.currentPreciseFix e preciseUpdates na Home visível, MapLibreMapHost e ParticipantMapStyleController existentes. Posição com mais de 35 segundos fica desatualizada, usando convenção do tracking; validar precisão pelo critério já usado para posição precisa. Não usar fallback de cliente de até sete dias como posição atual do guincheiro.

**Rationale**: A localização da Home deve existir mesmo com prestador indisponível, sem ativar publicação operacional por conta própria. Diferenciar gesto real de callback idle evita desligar acompanhamento após recentralização programática.

**Alternatives considered**: Novo SDK de mapa ou seguir sempre a câmera causaria dependências e impediria exploração manual.

## 4. Notificação e deduplicação

**Decision**: Manter canal e IDs existentes; setOnlyAlertOnce nas atualizações, timeout pelo prazo restante e marcador por providerId/appointmentId em DataStore para silenciar repostagem após reinício. Serializar postagem para um pedido; marcar somente quando permitido e efetivamente postado. Limpar notificações e estado visível na saída/troca de conta, preservando o marcador mínimo enquanto o pedido continuar vigente, inclusive ao entrar novamente na mesma conta. Eliminar o marcador somente após encerramento ou expiração.

**Rationale**: Hoje o serviço associa postagem à revisão e o notifier não usa setOnlyAlertOnce, permitindo som repetido. ID estável sozinho evita empilhamento, mas não resolve a repetição sonora. Nenhum dado de cliente precisa ser armazenado para corrigir isso.

**Alternatives considered**: Deduplicação apenas em memória falha após recriação do processo. Novo canal pode perder preferências. Push remoto acrescentaria backend e credenciais sem requisito atual.

**Fonte oficial**: [Android — Create a notification](https://developer.android.com/develop/ui/compose/notifications/create-notification), consultada em 2026-09-30: permissões, canais, atualização e remoção. Som/destaque continuam sujeitos ao sistema e preferências.

## 5. Limite de entrega

**Decision**: Aproveitar o serviço em primeiro plano existente; não prometer entrega após force-stop ou quando serviço/processo estiver morto. Recuperar estado autoritativo na abertura; incluir condição de serviço ativo na validação em segundo plano.

**Rationale**: A documentação interna do notifier declara notificação local, sem FCM. O escopo exige equivalência ao PitTec, não introdução de push remoto.

**Alternatives considered**: FCM e tela de chamada em tela cheia mudariam infraestrutura e experiência solicitada.

## 6. Validação

**Decision**: Compilação/lint na implementação e roteiro manual para o usuário. Não executar chamadas de pagamento reais como validação do planejamento.

**Rationale**: O usuário reservou testes manuais para si. Dev permite o fluxo simulado; Homol/Prod usam pagamentos reais e a mesma regra de liberação.

**Alternatives considered**: Criar suítes de teste ou tratar cenários descritos como já executados contrariaria as instruções.
