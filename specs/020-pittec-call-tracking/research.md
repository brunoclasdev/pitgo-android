# Phase 0 Research: Chamada e acompanhamento do PitTec

## 1. Máquina de estados autoritativa

**Decision**: Modelar no servidor o fluxo PitTec como
`SEARCHING → AWAITING_PROVIDER → AWAITING_CLIENT_CONFIRMATION → AWAITING_PAYMENT → ACCEPTED → IN_PROGRESS`,
com `CANCELLED` e `COMPLETED` como estados terminais. `appointments.PENDING` continua sendo a oferta
ao PitTec; após o aceite, o appointment permanece reservado em
`AWAITING_CLIENT_CONFIRMATION`/`AWAITING_PAYMENT` e só chega a `ACCEPTED` depois do pagamento.

**Rationale**: A infraestrutura atual de localização, rota e chegada já entende `ACCEPTED` como
atendimento apto ao deslocamento. Fazer esse estado significar "proposta confirmada e pagamento
aprovado" satisfaz os gates novos sem ampliar coordenadas exatas para estados intermediários. Estados
persistidos permitem que cliente e PitTec restaurem a mesma etapa depois de fechar o app.

**Alternatives considered**:

- Derivar confirmação e pagamento de Booleans locais enquanto o appointment já está `ACCEPTED`:
  rejeitado porque libera rastreamento cedo, não restaura de modo confiável e permite divergência
  entre participantes.
- Manter a decisão da feature `019`, que evitava uma RPC e um estado intermediário: rejeitado porque
  os requisitos novos de prazo de dois minutos, versão de proposta e aprovação server-side tornam a
  persistência obrigatória. A feature `020` supersede essa decisão pontual.

## 2. Busca contínua sem estado `EXHAUSTED`

**Decision**: Quando uma rodada não encontrar candidato elegível, manter a mesma
`service_searches` em `SEARCHING`, preservar seu ponto e definir `next_dispatch_at`. Um worker
agendado com pg_cron seleciona buscas vencidas em lote usando `FOR UPDATE SKIP LOCKED`, chama a lógica
de avanço existente e reagenda as que continuam sem candidato. Restore/polling também executa a
expiração e o avanço lazy como recuperação caso o cron não esteja disponível.

Recusa, indisponibilidade ou timeout cancela somente a oferta atual e volta a mesma solicitação para
`SEARCHING`. Preservam-se os raios, ordenação, elegibilidade e prazo de oferta existentes. Um PitTec
que já recusou ou deixou a oferta expirar não recebe novamente a mesma solicitação; novos prestadores
e prestadores elegíveis ainda não tentados entram nas varreduras seguintes.

**Rationale**: A lógica atual termina em `EXHAUSTED` ao esgotar uma varredura e depende do cliente
consultar o snapshot para progredir. Reagendamento server-side cumpre a continuidade mesmo com a tela
fechada, evita busy loop e não altera as regras comerciais de seleção.

**Alternatives considered**:

- Fazer loop no `PitTecViewModel`: rejeitado porque para com o processo Android, duplica concorrência
  e não constitui fonte de verdade.
- Reofertar continuamente a quem já recusou: rejeitado por experiência ruim e por alterar a semântica
  de uma recusa; reoferta poderá ser modelada no futuro como uma nova tentativa, se requisitada.

## 3. Preço único e versionado

**Decision**: Manter `dispatch_candidates.quoted_price` e `appointments.quoted_price` como a cadeia
autoritativa já congelada a partir de `provider_services.price`. Expor no snapshot uma proposta
`{ amount, currency: "BRL", version }`: o PitTec vê o valor da oferta antes de aceitar e o cliente vê
o mesmo valor depois do aceite. Acrescentar `appointments.quote_version`, iniciando em 1, e registrar
a versão confirmada pelo cliente.

Uma mudança real de preço nunca altera silenciosamente uma proposta aceita: invalida a confirmação,
incrementa a versão e volta a `AWAITING_CLIENT_CONFIRMATION` com novo prazo. No fluxo corrente o preço
fica imutável durante o appointment; uma nova oferta/appointment cria sua própria proposta.

**Rationale**: Evita os valores simulados do dashboard do prestador e garante que a confirmação se
refira exatamente ao preço cobrado. `BRL` é a única moeda exigida; uma tabela de moedas seria YAGNI.

**Alternatives considered**:

- Calcular novamente no Android ou usar `services.minimum_price`: rejeitado porque pode divergir do
  valor congelado para o prestador.
- Aceitar `amount` enviado pelo APK como fonte de cobrança: rejeitado por integridade e segurança.

## 4. Confirmação e expiração em dois minutos

**Decision**: Criar a RPC idempotente `respond_to_pittec_quote`, restrita ao cliente dono, com
`request_id`, `appointment_id`, `quote_version`, decisão e `expected_revision`. Ela trava na ordem
canônica `service_searches → appointments`, compara estado, versão e prazo usando `now()` do banco e
retorna um outcome explícito. Confirmação válida leva a `AWAITING_PAYMENT`; recusa encerra a chamada e
libera o PitTec.

Um worker expira confirmações vencidas e o mesmo check roda de forma lazy em RPCs de leitura/decisão.
Timeout cancela a solicitação e o appointment, libera o prestador e rejeita qualquer confirmação
tardia. O app usa `server_now` e `client_confirmation_deadline` apenas para desenhar o contador; ele
não decide a validade.

**Rationale**: Relógio e transação do servidor eliminam diferenças de horário, corridas entre timer e
toque e reativação indevida após reconexão. `expected_revision` impede aceitar um card substituído.

**Alternatives considered**:

- Timer local que chama cancelamento após 120 segundos: rejeitado porque deixa de rodar se o app for
  encerrado e pode aceitar uma decisão tardia.
- Reutilizar `response_deadline`: rejeitado porque esse campo representa o prazo da oferta ao PitTec,
  um relógio independente.

## 5. Pagamento como gate transacional do deslocamento

**Decision**: Para `PITTEC_AUTO_SEQUENTIAL`, `mp-create-payment` recebe somente a referência da
solicitação e os dados do meio de pagamento; resolve no servidor cliente, appointment ativo,
`quoted_price`, `quote_version` e estado `AWAITING_PAYMENT`. O valor e o `dispatch_kind` enviados pelo
app não são autoridade. `payments` passa a vincular `appointment_id` e `quote_version` para permitir
validação inequívoca.

Toda atualização para `APPROVED`, seja criação, refresh ou webhook, é persistida e reconciliada por
uma única transação server-side. Somente um pagamento que coincida em request, cliente, appointment,
valor e versão confirmada promove appointment e busca a `ACCEPTED`; a mesma transação materializa o
destino fixo do atendimento. Aprovação tardia de uma solicitação terminal não a reativa e segue a
recuperação/estorno já existente. `arrive_pittec_appointment` mantém a verificação completa como
defesa em profundidade.

**Rationale**: A implementação atual confia no valor do APK e considera qualquer pagamento aprovado
com o mesmo request suficiente. A decisão elimina cobrança adulterada e torna o começo do tracking
independente de qual aplicativo recebeu primeiro a notificação.

**Alternatives considered**:

- O cliente mudar localmente para tracking ao receber aprovação: rejeitado porque o PitTec pode não
  receber a mesma informação e o backend ainda autorizaria estados incoerentes.
- Criar outro provedor/meio de pagamento: rejeitado; a feature reutiliza o Mercado Pago existente.

## 6. Privacidade e destino fixo

**Decision**: Não criar `client_current_locations` no aceite do PitTec. Na promoção para `ACCEPTED`,
copiar uma única vez a coordenada original de `service_searches` para o contexto do appointment.
Rejeitar `publish_client_location` para dispatch PitTec, evitando que o GPS posterior do cliente
sobrescreva o destino. Publicação/leitura da posição do PitTec, contexto de rota e tópicos privados de
localização continuam autorizados apenas em `ACCEPTED`/`IN_PROGRESS` e enquanto o participante está
vinculado.

**Rationale**: O ponto solicitado é o destino, não uma trilha do cliente. Materializá-lo somente após
o pagamento evita que coordenadas exatas sejam compartilhadas no aceite ou na confirmação.

**Alternatives considered**:

- Continuar publicando o GPS do cliente na tela compartilhada: rejeitado por FR-030 e por exigir um
  comportamento de background que está fora do escopo.
- Expor o destino exato logo que o PitTec aceita: rejeitado por FR-022.

## 7. Rastreamento em segundo plano do PitTec

**Decision**: Reutilizar `ProviderLocationTrackingService`, já declarado como foreground service de
localização, com notificação persistente, `START_STICKY`, lease em DataStore e intervalo de 10
segundos. O backend só muda o destino da publicação de discovery para appointment quando o estado
autoritativo alcançar `ACCEPTED`; em cancelamento/conclusão, o lease e as atualizações específicas do
appointment terminam. Não solicitar localização em segundo plano ao cliente.

**Rationale**: O serviço existente já implementa o mecanismo Android requerido e as permissões do
manifest. A feature precisa corrigir seu gatilho e seus estados de erro, não criar outro serviço nem
adicionar `ACCESS_BACKGROUND_LOCATION`.

**Alternatives considered**:

- Worker periódico: rejeitado porque não garante atualização a cada 10 segundos e não substitui um
  foreground service de localização.
- Serviço adicional exclusivo de PitTec: rejeitado por duplicar lease, notificação e aquisição GPS.

## 8. Sincronização e recuperação

**Decision**: Realtime privado continua enviando invalidações nos tópicos
`mobile-dispatch:<search_id>`, `provider-dispatch:<provider_id>`, `payment:<request_id>` e, após
pagamento, `appointment-location:<appointment_id>`. Cada evento dispara novo snapshot; `revision`
descarta estado antigo. Polling de 5 segundos permanece ativo em todos os estados não terminais,
inclusive confirmação e pagamento. A observação do prestador deve restaurar qualquer dispatch kind,
e a política de leitura de pagamento deve usar helper `SECURITY DEFINER` mínimo, como o padrão já
adotado para dispatch.

**Rationale**: Eventos como invalidação reduzem acoplamento ao payload e o polling cobre perda de
websocket, reinício do processo e eventual ausência do cron. Hoje o polling do cliente para justamente
nos estados intermediários e o observer genérico do prestador restaura apenas mobile dispatch.

**Alternatives considered**:

- Confiar somente no Realtime: rejeitado porque reconexão e eventos perdidos são cenários explícitos.
- Colocar toda a entidade no broadcast: rejeitado por privacidade e risco de versões divergentes.

## 9. Enquadramento e cálculo de distância

**Decision**: Reutilizar `AppointmentTrackingScreen` nos dois perfis. No primeiro snapshot válido,
enquadrar o marcador do PitTec e o local fixo do atendimento. Atualizações posteriores movem o
marcador e recalculam distância direta (Haversine) sem forçar câmera depois de pan/zoom manual; uma
ação de reenquadrar volta a mostrar ambos. A última localização permanece visível com timestamp e
estado desatualizado. Distância de rota existente só pode ser exibida se corresponder à revisão da
posição atual; caso contrário, usa-se a distância direta.

**Rationale**: A especificação define distância entre posições e exclui navegação, rota e ETA. A
tela e controladores atuais já têm dois marcadores, framing e fallback; precisam de política de câmera
consistente, não de um novo mapa.

**Alternatives considered**:

- Reenquadrar em toda atualização: rejeitado porque luta contra a interação manual e causa saltos.
- Tornar a Edge Function de rota requisito para atualizar distância: rejeitado por estar fora do
  escopo e introduzir latência/falha desnecessária.

## 10. Estratégia de validação

**Decision**: Não criar testes automatizados nesta feature. Validar compilação/lint, schema local e
um roteiro manual que cubra concorrência, timeout, pagamento, segurança de preço, privacidade,
reconexão, mapa e foreground service.

**Rationale**: A constituição proíbe gerar testes automaticamente sem pedido explícito. Os riscos são
principalmente integrações entre Android, Supabase Realtime, pg_cron e Mercado Pago, portanto o
roteiro ponta a ponta descrito em `quickstart.md` é o gate proporcional solicitado.

**Alternatives considered**:

- Adicionar suites unitárias/UI novas: rejeitado nesta fase por ausência de solicitação explícita.
