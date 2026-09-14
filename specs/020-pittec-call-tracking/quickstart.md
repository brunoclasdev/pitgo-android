# Quickstart: validar chamada e acompanhamento do PitTec

## Objetivo

Validar a implementação da feature `020` no Android e em um Supabase local isolado, cobrindo busca
contínua, proposta, timeout, pagamento, privacidade, mapas e localização em segundo plano. Este roteiro
não cria testes automatizados, conforme a constituição.

## Pré-requisitos

- JDK e Android SDK compatíveis com o projeto; emulador/dispositivo API 24+;
- Docker e Supabase CLI para banco local;
- credenciais locais/sandbox exigidas pelo projeto, sem inserir service-role key no APK;
- ambiente Mercado Pago sandbox configurado para as Edge Functions;
- três contas cliente e ao menos três contas PitTec com perfis/serviços/preços válidos;
- dois dispositivos ou emuladores para o cenário de mapa/background.

Nunca execute `supabase db reset` contra um projeto remoto. Os comandos abaixo pressupõem o stack
local iniciado pelo CLI.

## Verificação estática

Na raiz do repositório:

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

Para validar migrations em banco descartável local:

```bash
supabase start
supabase db reset --local
supabase db lint --local
```

Confirme também que pg_cron contém os schedules de avanço das buscas e expiração das confirmações.
O deploy não deve considerar apenas um `NOTICE` de falha como sucesso operacional.

Para exercitar pagamento sandbox local, sirva as Edge Functions seguindo a configuração já usada no
projeto:

```bash
supabase functions serve mp-create-payment --env-file supabase/.env.local
supabase functions serve mp-webhook --env-file supabase/.env.local
```

Use somente um arquivo local ignorado pelo Git para secrets.

## Dados de preparação

1. Cadastre um serviço PitTec com preço positivo e associe-o aos três prestadores.
2. Deixe o PitTec A mais próximo, B em seguida e C depois, respeitando os raios atuais.
3. Habilite disponibilidade e localização foreground dos prestadores.
4. Garanta que o cliente não possui `service_searches` PitTec ativa.
5. Anote `request_id`, `appointment_id`, `quoted_price`, `quote_version` e revisões a cada cenário para
   conferir idempotência e vínculos.

## Cenários obrigatórios

### 1. Uma busca sobrevive a recusas e ausência de candidatos

1. Cliente toca uma vez em `Chamar PitTec` e repete o toque rapidamente.
2. Verifique que existe um único `request_id` e a tela informa que continua procurando.
3. PitTec A recusa; deixe a oferta de B expirar; C aceita.
4. Confirme que todas as tentativas pertencem ao mesmo request e que nenhuma etapa vira `EXHAUSTED`.
5. Repita sem prestadores disponíveis; depois habilite um PitTec sem reabrir a tela do cliente.
6. O worker deve criar a oferta na mesma solicitação. Cancelar pelo cliente deve impedir ofertas novas.

Evidência: FR-001 a FR-006, SC-001.

### 2. Dois aceites concorrentes

1. Deixe dois aparelhos PitTec receberem/recuperarem uma oferta concorrente válida.
2. Toque `Aceitar` quase simultaneamente.
3. Somente um appointment deve alcançar `AWAITING_CLIENT_CONFIRMATION`; o outro recebe oferta
   indisponível/revisão stale e não permanece reservado.

Evidência: FR-005, Edge Case de concorrência.

### 3. Mesmo preço para PitTec e cliente

1. Registre o valor mostrado ao PitTec antes do aceite.
2. Aceite e compare com o card do cliente.
3. Os valores e moeda devem ser idênticos a `appointments.quoted_price`/`BRL`; nenhuma estimativa
   fictícia ou `minimum_price` deve aparecer.
4. Adultere `amount`/`dispatch_kind` na chamada do checkout; o servidor deve ignorar/rejeitar a
   divergência e nunca criar cobrança por valor menor.

Evidência: FR-007, FR-008, FR-012.

### 4. Confirmação, recusa e prazo de 120 segundos

1. Após o aceite, confirme que ambos veem `AWAITING_CLIENT_CONFIRMATION` e o cliente vê contador.
2. Confirme dentro do prazo; verifique uma única transição para `AWAITING_PAYMENT` mesmo repetindo a
   request.
3. Em nova chamada, recuse: request/appointment devem terminar, PitTec ser liberado e não existir
   cobrança.
4. Em nova chamada, deixe expirar: o banco cancela mesmo com app fechado e rejeita confirmação tardia.
5. Dispute toque de confirmar com o worker de expiração no limite; o relógio/lock do servidor deve
   produzir apenas um resultado consistente.
6. Simule cancelamento do PitTec antes da decisão; o card some e o mesmo request volta à busca.

Evidência: FR-008 a FR-012, FR-020, FR-026, SC-003, SC-007, SC-009.

### 5. Pagamento é o único gate do deslocamento

1. Confirme a proposta e exercite pagamento `PENDING`, `REJECTED` e falha de rede.
2. Em todos, ambos permanecem aguardando pagamento; RPCs/tópicos de localização do appointment são
   negados e o mapa de deslocamento não abre.
3. Aprove uma tentativa com request, cliente, appointment, valor e versão correspondentes.
4. Verifique que webhook/refresh persiste `APPROVED`, promove busca e appointment a `ACCEPTED` uma só
   vez e materializa o destino fixo.
5. Feche os dois apps antes da aprovação; ao reabrir, ambos devem restaurar `ACCEPTED` e abrir o mesmo
   appointment.
6. Cancele uma chamada e depois entregue webhook aprovado tardio: o estado terminal não pode reabrir;
   a tentativa segue a recuperação financeira existente.

Evidência: FR-009, FR-020, FR-021, FR-022, FR-027, SC-002, SC-007.

### 6. Destino fixo e privacidade

1. Antes do pagamento, tente ler `get_appointment_client_location`,
   `get_appointment_provider_location`, `get_pittec_route_context` e assinar o tópico de localização
   como cada participante; todos devem negar/não expor coordenadas exatas.
2. Após aprovação, confirme que o destino equivale ao ponto registrado na criação.
3. Mova o dispositivo do cliente e reabra a tela; o ponto e o marcador não mudam.
4. Tente `publish_client_location` no appointment PitTec; o backend deve rejeitar/ignorar sem alterar o
   destino.
5. Cancele/conclua e confirme que leituras/publicações específicas deixam de funcionar.

Evidência: FR-022, FR-023, FR-029, FR-030.

### 7. Mapa nos dois perfis

1. Com pagamento aprovado, abra tracking em cliente e PitTec.
2. Os dois mapas devem mostrar `PitTec` e `Local do atendimento` no enquadramento inicial.
3. Simule deslocamento com posições válidas; marcador e distância direta atualizam sem reload.
4. Confirme que uma rota com revisão antiga não mantém distância obsoleta.
5. Faça pan/zoom: atualizações não devem puxar a câmera continuamente. Toque `Reenquadrar` e confirme
   que ambos os marcadores voltam aos bounds.
6. Sobreponha os pontos e valide distância zero ou `Chegou`, mantendo identificação acessível.
7. Aumente fonte e use leitor de tela para confirmar que estado, participante e distância não dependem
   apenas de cor.

Evidência: FR-013 a FR-017, FR-024, SC-005, SC-006, SC-008.

### 8. Latência, stale e background do PitTec

1. Durante o deslocamento, registre `captured_at` de pelo menos 20 posições e o instante renderizado no
   cliente; ao menos 95% devem aparecer em até 10 segundos sob conectividade normal.
2. Bloqueie o aparelho PitTec e coloque o app em segundo plano. A notificação persistente permanece e
   o cliente continua recebendo posições.
3. Desligue GPS, retire permissão necessária e corte a rede separadamente.
4. Cliente mantém a última posição como desatualizada; PitTec recebe mensagem acionável; nenhuma
   coordenada é inventada.
5. Restaure a condição e confirme retomada automática. Cliente nunca deve ser solicitado a habilitar
   localização em segundo plano.
6. Cancele/conclua; notificação/lease/publicação do appointment devem terminar.

Evidência: FR-018, FR-019, FR-023, FR-025, FR-028, FR-029, SC-004, SC-010.

### 9. Realtime, restauração e regressão

1. Em cada estado não terminal, derrube/reconecte WebSocket e reinicie o processo Android.
2. O polling/restore deve recuperar a maior revisão sem duplicar busca, aceite, confirmação ou
   pagamento.
3. Confirme que o dashboard PitTec recupera ofertas `PITTEC_AUTO_SEQUENTIAL` e que o tópico de
   pagamento é legível apenas pelo cliente dono.
4. Execute smoke test de mobile dispatch/guincho: aceite, localização e gate de pagamento dessas
   modalidades mantêm o comportamento anterior.
5. Execute o fluxo PitTec pós-chegada existente para verificar que `IN_PROGRESS`, conclusão e
   avaliação não sofreram alteração funcional.

Evidência: FR-020, FR-021 e limites de escopo.

## Critério de encerramento

- build e lint Android sem erros novos;
- migrations aplicam do zero e sobre o schema anterior no ambiente local;
- todos os cenários obrigatórios passam, com atenção especial às corridas e ao preço server-side;
- nenhum secret ou dado sensível aparece no APK/logs;
- nenhuma regressão observada em mobile dispatch ou no fluxo posterior à chegada;
- os 10 critérios mensuráveis da spec têm evidência registrada no roteiro.
