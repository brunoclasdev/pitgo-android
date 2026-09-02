# Phase 0 Research: PitTec sob demanda

## 1. Fronteira do produto

### Decision

Representar PitTec com `services.dispatch_kind = PITTEC_AUTO_SEQUENTIAL` e
`providers.location_mode = PITTEC`. PitTec continua sendo um prestador comum
(`PROVIDER`), não um terceiro papel de conta.

### Rationale

O projeto já separa papel, modalidade e forma de despacho. A nova modalidade
controla disponibilidade/rastreamento, enquanto o novo tipo de despacho permite
seleção automática sem alterar o fluxo do Guincho, que hoje apresenta opções ao
cliente antes de enviar a oferta.

### Alternatives considered

- Reutilizar `MOBILE_SEQUENTIAL`: rejeitado porque mudaria o contrato e a UX do
  Guincho.
- Criar papel `PITTEC`: rejeitado porque duplicaria cadastro, autorização e
  painel de um prestador.
- Identificar PitTec apenas pelo nome/slug: rejeitado por acoplar regra de
  negócio a texto mutável.

## 2. Despacho automático e concorrência

### Decision

Criar RPCs dedicados para iniciar/cancelar o PitTec e estender as transições
compartilhadas de resposta/cancelamento. O início cria a busca, escolhe o
prestador mais próximo e cria o agendamento `PENDING` na mesma transação. Cada
recusa, cancelamento do prestador ou expiração seleciona o próximo candidato não
tentado nos raios 10, 25 e 50 km. Há uma única busca PitTec ativa por cliente;
as regras de simultaneidade dos serviços legados não são alteradas.

### Rationale

O uso de `FOR UPDATE`, revisão e chave idempotente no banco evita dois clientes
reservarem o mesmo prestador e impede respostas atrasadas de alterarem a oferta
atual. A unicidade específica do PitTec elimina telas de retomada ambíguas sem
ampliar a regra aos outros serviços. O algoritmo atual do despacho móvel recicla
candidatos após esgotar a lista, o que
contraria a regra PitTec de nunca repetir uma recusa.

### Alternatives considered

- Fazer a sequência no ViewModel: rejeitado por não ser seguro sob concorrência
  e por parar quando o cliente vai para background.
- Alterar o helper atual para nunca repetir: rejeitado porque pode mudar o
  comportamento vigente do Guincho.
- Enviar para vários prestadores simultaneamente: rejeitado pelo requisito de
  tentativa estritamente sequencial.

## 3. Estados do atendimento

### Decision

Reutilizar estados persistidos:

- `SEARCHING`: preparando/selecionando o próximo PitTec;
- `AWAITING_PROVIDER` + agendamento `PENDING`: oferta de 60 s enviada;
- `ACCEPTED`: prestador a caminho;
- `IN_PROGRESS`: prestador confirmou “Cheguei”;
- `COMPLETED`, `CANCELLED`, `EXPIRED`, `NO_PROVIDER`: estados terminais.

Para PitTec, `complete` só aceita `IN_PROGRESS`. O instante de “Cheguei” é
registrado em `appointments.arrived_at`.

### Rationale

Os estados já participam de RLS, Realtime, retomada e UI. A semântica é
compatível e evita uma migração ampla de enums/check constraints. Um timestamp
específico mantém auditoria da ação sem inventar um estado redundante.

### Alternatives considered

- Adicionar `ARRIVED`: rejeitado por duplicar o papel de `IN_PROGRESS` e exigir
  mudanças transversais sem benefício ao usuário.
- Concluir diretamente de `ACCEPTED`: rejeitado porque tornaria a confirmação de
  chegada não obrigatória.

## 4. Privacidade da localização

### Decision

Antes do aceite, `list_nearby_pittecs` retorna até 200 marcadores com coordenadas
estáveis ajustadas a uma grade aproximada de 500 m, precisão declarada e ID
opaco por resposta. Não retorna nome, `provider_id` ou coordenada exata. A
seleção usa a coordenada real exclusivamente no servidor.

Após o aceite, o backend copia a posição solicitada do cliente para
`client_current_locations` e autoriza as tabelas de localização do atendimento
somente aos dois participantes. Para PitTec, a posição exata do prestador deixa
de ser publicada na descoberta enquanto o atendimento estiver ativo. Dados
transitórios são apagados ao finalizar.

### Rationale

Isso satisfaz a visualização prévia sem permitir identificação, correlação ou
reconstrução simples da residência/local exato do prestador. O ponto estável
evita que atualizações sucessivas com ruído aleatório sejam combinadas para
estimar a posição real.

### Alternatives considered

- Enviar a coordenada exata e arredondar no Android: rejeitado porque o dado
  sensível já teria saído do servidor.
- Aplicar ruído aleatório a cada consulta: rejeitado porque múltiplas amostras
  podem ser promediadas e os marcadores “saltam” no mapa.
- Mostrar apenas uma contagem: rejeitado porque não atende ao mapa solicitado.

### References

- PostGIS `ST_SnapToGrid`: https://postgis.net/docs/ST_SnapToGrid.html

## 5. Disponibilidade e mudança de modalidade

### Decision

Adicionar `providers.pending_location_mode`. Sem atendimento ativo, a mudança é
imediata. Durante um atendimento, o modo escolhido fica pendente e é aplicado
atomicamente quando o atendimento se torna terminal. FIXED e MOBILE continuam
com o comportamento atual fora desse caso.

Um PitTec só é elegível quando: conta ativa, modalidade efetiva PITTEC,
disponível, ponto recente e preciso, oferta ativa para o serviço e nenhum
atendimento/oferta incompatível em andamento.

### Rationale

O campo pendente cumpre a decisão funcional de não interromper o atendimento e
não obriga a UI a rejeitar a escolha do usuário. Centralizar elegibilidade no
banco mantém descoberta e despacho consistentes.

### Alternatives considered

- Bloquear mudança até o fim: mais simples, mas contradiz a expectativa de que
  a escolha feita passe a valer depois.
- Alterar imediatamente: rejeitado porque poderia parar localização/alertas no
  meio do atendimento.

## 6. Alertas em background e tela bloqueada

### Decision

Generalizar o serviço em primeiro plano existente para operar quando um
prestador disponível estiver em MOBILE ou PITTEC. Em PITTEC ele assina o canal
privado de despacho, restaura o estado e usa polling de 5 s como fallback. Uma
nova oferta produz notificação local em canal separado de importância alta,
som, vibração quando permitida e `PendingIntent` para o atendimento. ID e revisão
deduplicam alertas.

Ativar disponibilidade PitTec exige `POST_NOTIFICATIONS` nas versões aplicáveis
e canal de ofertas habilitado. Não será solicitada `ACCESS_BACKGROUND_LOCATION`:
o FGS visível cobre a disponibilidade ativa. Reinício do aparelho, processo
forçadamente encerrado e force-stop não possuem garantia nesta versão; a
expiração da localização remove o prestador da seleção.

### Rationale

O produto já mantém um FGS `START_STICKY` durante disponibilidade móvel. Estender
esse componente entrega foreground, background e tela bloqueada sem introduzir
Firebase, token de push, novos dados pessoais e infraestrutura operacional. A
limitação fica explícita e é compatível com o escopo esclarecido.

### Alternatives considered

- FCM data messages: adiados; seriam necessários caso a garantia futura inclua
  processo morto/reinício, mas ampliam backend, dependências e política de token.
- Notificação de baixa prioridade: rejeitada porque não produz alerta visível e
  sonoro adequado.

### References

- Android notification channels: https://developer.android.com/develop/ui/compose/notifications/channels

## 7. Percurso e distância

### Decision

Criar a Edge Function autenticada `pittec-route`. Ela verifica se o usuário é
participante de um atendimento `ACCEPTED`/`IN_PROGRESS`, lê coordenadas exatas no
servidor e consulta por HTTPS um serviço gerenciado compatível com OSRM Route
v1. O segredo e URL ficam em Supabase Secrets. A resposta é normalizada para
GeoJSON `[longitude, latitude]`, distância em metros e revisão da localização.

Persistir somente um `appointment_route_snapshots` por atendimento, sobrescrito
quando o prestador se move pelo menos 50 m e o snapshot tem pelo menos 15 s. Uma
requisição por atendimento fica em voo; novas atualizações são combinadas. Em
erro/timeout, o app calcula distância haversine e desenha uma linha direta
tracejada, claramente indicada como estimativa.

### Rationale

O proxy impede expor credenciais e coordenadas a um endpoint escolhido pelo
cliente, fornece a mesma rota aos dois participantes e limita custo. O snapshot
único evita histórico de deslocamento, enquanto o fallback mantém o fluxo útil
quando o roteador falha.

### Alternatives considered

- Chamada direta do APK: rejeitada por expor segredo, permitir abuso e dar
  resultados divergentes aos participantes.
- Hospedar OSRM nesta entrega: rejeitado por custo operacional desnecessário.
- Persistir todas as rotas: rejeitado por privacidade e ausência de requisito de
  histórico.
- Atualizar a cada coordenada: rejeitado por custo, bateria e concorrência.

### References

- OSRM Route API v1: https://project-osrm.org/docs/v5.24.0/api/
- Supabase Edge Functions: https://supabase.com/docs/guides/functions/quickstart
- MapLibre `LineLayer`: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.style.layers/-line-layer/-line-layer.html

## 8. Organização Android

### Decision

Criar `features/pittec` para a tela e máquina de estados do cliente. Estender os
modelos/repositórios de `features/mobiledispatch` para o transporte compartilhado
e adaptar `appointmenttracking`, `providertracking`, cadastro e painel. Criar
`core/routing` apenas para contrato e acesso ao snapshot/Edge Function.

Os mapas continuam stateless: recebem marcadores, polilinha e callbacks. O
ViewModel combina snapshots do despacho, posições e rota em um único `UiState`.

### Rationale

Essa divisão acompanha a estrutura feature-first existente, evita duplicar a
infraestrutura Supabase/Reatime e mantém regras específicas fora de componentes
genéricos.

### Alternatives considered

- Novo módulo Gradle: rejeitado; o projeto é pequeno e de módulo único.
- Colocar todo PitTec em `mobiledispatch`: rejeitado porque a descoberta e a UX
  do cliente são próprias e tornariam o pacote existente menos coeso.

## 9. Realtime, recuperação e segurança

### Decision

Continuar com canais privados Supabase, payloads de invalidação mínimos e RPC de
snapshot como fonte de verdade. Eventos carregam apenas ID/revisão/estado, nunca
coordenadas. Cliente e prestador restauram a única busca ativa ao abrir/deep
link; desconexão usa polling limitado até Realtime recuperar.

Todas as mutações sensíveis validam `auth.uid()`, papel, participante, revisão,
estado anterior e idempotência. Tabelas privadas não permitem DML direto. Logs
omitem coordenadas, tokens e payloads completos.

### Rationale

Esse padrão já existe no repositório e tolera eventos perdidos ou fora de ordem.
RLS mais autorização dentro do RPC fornece defesa em profundidade.

### Alternatives considered

- Confiar no evento como estado: rejeitado porque Realtime não é uma fila de
  entrega garantida.
- Canal público: rejeitado por exposição de metadados de atendimento.

### References

- Supabase Realtime Authorization: https://supabase.com/docs/guides/realtime/authorization

## 10. Fora do escopo consciente

- FCM e garantia após reboot/force-stop.
- Navegação curva-a-curva ou instruções por voz.
- Histórico de rotas/localização.
- Chat, preço exibido, cobrança, avaliação ou descrição de especialidade.
- Testes automatizados novos, salvo decisão explícita posterior.
