# Quickstart: validar a melhoria de UX das telas do cliente

Guia manual de ponta a ponta (sem testes automatizados, por decisão da constituição do produto).
Use duas contas reais (uma `CLIENT`, uma `PROVIDER MOBILE` já configurada) e, quando possível, dois
dispositivos/emuladores físicos — o mesmo setup já usado para validar a feature 013.

## 1. Pré-requisitos

- Migration `client_addresses` (ver `contracts/client-address-rpcs.md`) aplicada no projeto
  Supabase de desenvolvimento (`supabase migration list` mostrando local/remoto em sincronia).
- Conta `CLIENT` sem endereço cadastrado (para testar o estado vazio) e outra com endereço já
  salvo (para testar edição).
- Conta `PROVIDER MOBILE` ativa, publicando localização (mesmo pré-requisito da feature 013).

## 2. Home acionável (US5 — FR-005, FR-006)

1. Logue com a conta `CLIENT` sem endereço: confirme que a linha de endereço na Home não é mais um
   texto estático — ela reage ao estado real (`get_my_client_address` → `NOT_FOUND`) e é tocável.
2. Toque na linha de endereço: o formulário de cadastro abre diretamente.
3. Cadastre um endereço com CEP válido (reaproveitando a busca de CEP já usada no cadastro do
   prestador) e volte à Home: a linha agora mostra o endereço salvo e continua tocável para editar.
4. Confirme que o card de veículo continua funcionando como hoje (`NoVehicleCard`/`VehicleCard`,
   já implementado — não deve haver regressão).

## 3. Perfil completo (US6 — FR-007, FR-008)

1. Abra a aba Perfil: confirme que endereço e veículo padrão aparecem (ou o aviso de ausência),
   cada um com ação de cadastrar/editar, sem sair da aba.
2. Troque o tema para Escuro, depois Claro, depois Padrão do sistema: cada escolha deve refletir
   imediatamente em toda a tela e persistir ao fechar/reabrir o app.
3. Confirme que Sair continua pedindo confirmação e funcionando como hoje.

## 4. Feedback durante a solicitação (US1 — FR-002, FR-003)

1. Como cliente, solicite o serviço de guincho e chegue ao estado de proposta encontrada.
2. Toque em "Solicitar" e observe: o botão mostra um indicador de carregamento/"processando" em
   vez de apenas ficar cinza sem explicação.
3. Force uma distância pequena entre cliente e prestador (mesma posição, como no teste manual da
   feature 013): confirme que a tela mostra um texto amigável ("muito próximo") em vez de "0,0 km".
4. Force o esgotamento da busca (nenhum prestador em 50 km): confirme que a tela de "nenhum guincho
   disponível" com "Tentar novamente" continua funcionando (já implementada — não deve regressar).
5. Deixe uma solicitação expirar (60 s) com ambos os apps abertos: confirme que a contagem
   regressiva chega a zero visivelmente, sem parecer travada (comportamento já existente — apenas
   confirmar ausência de regressão).

## 5. Mapa do cliente em tempo real (US2 — FR-011 a FR-013)

1. Com o atendimento aceito, abra a tela de acompanhamento como cliente.
2. Confirme que sua própria posição aparece centralizada no mapa, com um marcador visualmente
   distinto do marcador do guincho.
3. Movimente o dispositivo do prestador (ou simule publicação de nova posição): confirme que o
   marcador do guincho se desloca no mapa do cliente em poucos segundos, sem recarregar a tela.
4. Saia da tela (voltar) e reabra: confirme que uma nova leitura de posição do cliente é feita
   (não uma posição antiga parada).

## 6. Mapa do prestador em tempo real (US3 — FR-014, FR-015)

1. Como prestador, aceite um atendimento e abra a tela de atendimento: confirme que agora existe
   um mapa (hoje inexistente) mostrando sua posição e a do cliente, ambas visíveis sem precisar
   mover o mapa manualmente.
2. Movimente o dispositivo do prestador: confirme que o marcador dele se atualiza em tempo real,
   mantendo o marcador do cliente visível.
3. Arraste/dê zoom manualmente no mapa: confirme que uma nova posição do prestador não força a
   câmera de volta ao enquadramento automático.

## 7. Botão voltar sem travar (US4 — FR-016 a FR-018)

1. Com um atendimento aceito, como cliente, na tela de acompanhamento, toque em voltar: confirme
   que a navegação retorna à Home e **permanece** lá (sem ser redirecionado de volta
   automaticamente).
2. Repita o mesmo teste do lado do prestador, a partir da tela de atendimento, voltando para a
   Dashboard.
3. Fixe o app (mate o processo) com o atendimento ainda ativo e reabra do zero: confirme que o app
   leva automaticamente de volta à tela do atendimento — esse comportamento de retomada deve
   continuar funcionando apenas neste cenário (início de sessão), não como reação ao "voltar"
   manual do passo 1/2.
4. Aceite/recuse/cancele o atendimento enquanto a outra parte está numa tela diferente: confirme
   que a navegação automática dispara corretamente para essa mudança real de status (revisão nova),
   distinguindo do caso de "voltar" manual sem mudança nenhuma.

## 8. Critério de encerramento

A feature está pronta quando todos os cenários acima passam nos dois papéis (cliente e
prestador), sem regressão nos comportamentos já confirmados como existentes em `research.md` #8
(contagem regressiva, busca esgotada, Home sem veículo), e sem nenhuma chamada de rede nova além
das duas RPCs de endereço do cliente e das já existentes da feature 013.
