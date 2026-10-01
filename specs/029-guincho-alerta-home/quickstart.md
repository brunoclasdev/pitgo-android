# Quickstart: Validação manual de alerta e Home do guincheiro

Este roteiro descreve validação futura pelo usuário; não declara cenários executados. Não criar testes automatizados, mocks ou fixtures sem solicitação.

## Fluxo vigente após a correção de 30/09/2026

Este trecho substitui os cenários antigos que condicionavam o convite ao pagamento:

1. Contratar um guincho na seleção do mapa: cliente aguarda aceite; guincheiro recebe um convite global/notificação com sua remuneração, sem dados pessoais/endereços do cliente.
2. Aceitar abre checkout do cliente e mantém prestador aguardando pagamento. Recusar/expirar encerra a solicitação sem criar cobrança. A confirmação é idempotente e o prestador continua reservado enquanto o cliente paga.
3. No Homol, conferir R$ 1,00–R$ 2,00 no checkout e na cobrança real. Criar/pagar somente durante validação autorizada pelo usuário. Verificação pendente mantém feedback explícito, sem segunda cobrança; aprovação recebida do MP leva ambos ao tracking.
4. Conferir mapa enquadrando ponto de coleta/cliente e guincho, atualizado pelo canal existente de localização. "Cheguei" muda para serviço iniciado em uma operação atômica; finalizar exige confirmação do cliente e retorna ambos à Home.
5. Sair/reabrir em convite, espera por pagamento, deslocamento e confirmação final deve recuperar o estágio correto. Na Home, mover o mapa pausa acompanhamento por 30 segundos; após o intervalo recentraliza automaticamente.

Backend: migrations `20260930154000` e `20260930155000`. O novo app usa `create_guincho_request_before_payment`; `create_guincho_request` continua compatível com versões já publicadas. O normalizador de Orders reconhece estados pendentes independentemente do valor nominal reportado e não os usa como prova de liquidação. O reconciliador conserva consultas de pagamentos recentes ainda ativos mesmo com flag de suporte; casos anteriores cancelados/em disputa/concluídos com essa flag ficam preservados para tratamento financeiro separado. Nenhum estorno, nova cobrança ou convite de teste foi acionado pelo agente.

Verificação da correção: build Homol, compilação Kotlin Dev/Prod e lint Homol concluíram com `BUILD SUCCESSFUL`; contratos/permissões foram conferidos em transações com rollback e novamente após aplicar ambas as migrations via `npx supabase`. `mp-create-payment` e `mp-reconcile-payments` foram publicados nos dois projetos. Homol foi instalado nos três dispositivos conectados. Cenários funcionais acima continuam para validação manual do usuário.

## Pré-requisitos

Dois aparelhos, um cliente com veículo e um prestador móvel de Guincho cadastrado. Para alerta, prestador disponível, localização e notificações habilitadas; serviço de localização ativo ao validar segundo plano. Usar Dev para pagamento simulado. Homol e Prod efetuam pagamento real e requerem decisão consciente do usuário. Não precisa de migration nem secrets novos.

## Compilação na fase de implementação

Na raiz do projeto:

```sh
./gradlew :app:assembleDevDebug :app:assembleHomolDebug :app:assembleProdDebug
./gradlew :app:lintHomolDebug
adb devices -l
adb -s <serial> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s <serial> shell am start -n com.trustarx.pitgo/.MainActivity
```

Instalar somente quando solicitado. Os flavors compartilham pacote e substituem a instalação anterior. Não interpretar este roteiro como autorização de pagamento ou execução manual pelo agente.

## Cenários

1. **Mapa inicial**: entrar como guincheiro disponível e depois indisponível; verificar posição centrada, marcador próprio, perfil/disponibilidade/histórico acessíveis. Mover mapa, aguardar posição e confirmar que enquadramento permanece; Recentralizar retoma acompanhamento.
2. **Sem localização**: negar permissão, desligar localização e interromper atualizações; conferir mensagem/recuperação, ausência de posição fictícia e identificação de posição desatualizada. Reativar e conferir retorno ao mapa atual.
3. **Antes do pagamento**: cliente escolhe guincho e mantém checkout pendente; prestador não recebe card ou informações pessoais. Reproduzir falha/recusa no ambiente adequado.
4. **Card global**: concluir pagamento simulado em Dev com prestador no Perfil e depois no Histórico; card aparece com dados e valor do prestador sem mudar rota. Conferir prazo original, não reiniciado ao sair/voltar.
5. **Resposta**: aceitar abre tracking; outra contratação recusada retorna à Home seguindo cancelamento existente. Toque repetido não cria segunda resposta. Expiração remove ações e alerta.
6. **Segundo plano**: liberar novo pedido com prestador em segundo plano e depois bloqueado; conferir notificação genérica, toque recuperando pedido e ausência de informações pessoais na tela bloqueada. Interromper o serviço e reabrir o aplicativo: recuperar pedido vigente sem prometer entrega durante a interrupção. Repetir atualizações/recriação do serviço dentro do prazo: nenhum novo som para o mesmo pedido.
7. **Notificação antiga e conta**: cancelar pedido antes do toque; aplicativo informa indisponibilidade sem permitir resposta. Sair/trocar conta limpa notificação e posição e não revela pedido anterior. Sair e entrar novamente na mesma conta com pedido ainda vigente: recuperar card sem novo som, preservando apenas o marcador mínimo até encerramento/expiração.
8. **Retomada/conclusão**: abrir atendimento aceito, em execução e aguardando cliente; apresentar estágio correto. Confirmar conclusão pelo cliente e conferir Home com mapa sem card residual.
9. **Layout/regressão**: fonte ampliada, telas pequenas, temas claro/escuro; card rolável e ações legíveis com marcador/Recentralizar visíveis na Home com mapa; Perfil e Histórico preservados sem exigir esses controles. Home do cliente, fixo e PitTec conservam seus fluxos. Validar falha de estilo/mapa e reconexão sem alertas duplicados.

## Medição

Para SC-001/002/004, registrar pelo menos 20 ocorrências de cada critério com versões, rede, condições de permissões e relógios sincronizados; pelo menos 19 devem cumprir 10 segundos. Medir pedido desde a liberação registrada até o card/notificação visível; posição desde a abertura até a centralização. Falhas externas e force-stop ficam anotados separadamente. Registrar resultados manuais, inclusive falhas; não presumir aprovação a partir de build.

Consultar [contrato](contracts/guincho-home-alert.md) e [modelo](data-model.md) para regras sem duplicar dados pessoais em evidências.

## Revisão de implementação — 2026-09-30

- FR-001/002/006/007: card global somente para GUINCHO_DIRECT pago/PENDING; prazo original, resposta serializada e remuneração do prestador derivada da projeção existente. O relógio do pedido usa offset calculado apenas na recepção do snapshot.
- FR-003/004/005: notificação genérica, IDs mínimos no Intent, consulta autoritativa ao tocar, deduplicação durável por conta/pedido com TTL, atualizações silenciosas e remoção na resposta/expiração. Saída/troca de conta limpa apresentação, preservando apenas o marcador até expirar.
- FR-008 a FR-013: mapa reutilizado, localização precisa durante Home visível, marcador atual/desatualizado, gesto distinguido de movimento programático, Recentralizar e recuperação; card ocupa região medida separada e controles inferiores têm rolagem para preservar área do mapa. Perfil reutiliza controles existentes de disponibilidade/permissões/bateria e histórico permanece acessível.
- FR-014/015/016: observador do guincheiro no escopo da sessão, cancelado ao ocultar/sair; retomada paga abre tracking nos estados existentes, conclusão bilateral retorna à Home. Fluxo histórico mantém a distinção existente para não encerrar a visualização histórica automaticamente. Cliente, fixo e PitTec mantêm seus ramos.
- Código novo não registra nomes, veículos, endereços, coordenadas ou tokens em logs. Não há migrations, Edge Functions ou mudanças de cobrança. Não foram gerados testes ou dados de teste, nem efetuados pagamentos ou instalação em aparelhos.
- A revisão de código não substitui os cenários de campo: SC-001 a SC-006 e suas amostras de 20 ocorrências continuam com o usuário.

### Verificações concluídas

Número do destino: campo separado "Número do imóvel", com suporte a número, sufixo (123A) e s/n. A confirmação exige preencher o campo. O endereço completo é a sugestão/ponto escolhido seguido do número, separado por vírgula; a prévia, a confirmação e o draft persistido usam esse mesmo texto, que também chega ao guincheiro no fluxo existente após aprovação do pagamento. A edição do trajeto preserva o número. Não é necessária migration porque destination_address já armazena o endereço completo. Na validação manual, escolher uma sugestão, informar o número, conferir a prévia, editar o trajeto e conferir o destino exibido ao prestador.

Autocomplete do destino: a partir de três caracteres, a busca aguarda 350 ms sem nova digitação e solicita até cinco sugestões do MapTiler em português, restritas ao Brasil e priorizando a retirada. A chave permanece na Edge Function `guincho-address-search`, que exige uma sessão de cliente READY; nenhuma SDK extra foi adicionada. Selecionar uma sugestão preenche o endereço e desloca o mapa para suas coordenadas. Alterar o texto invalida o ponto anterior; confirmar exige uma sugestão selecionada ou um ponto escolhido no mapa com endereço correspondente. Ajustar o mapa resolve novamente o endereço, com fallback Android e identificação explícita por coordenadas se os geocodificadores falharem. Respostas de consultas anteriores não substituem uma seleção mais recente. Na validação manual, conferir sugestões, número/cidade, seleção e coordenadas, editar texto após selecionar, arrastar o mapa, ausência de resultados, perda de rede e edição do trajeto antes de contratar. Não há alteração de schema ou regra de cobrança.

Verificações do autocomplete: `npx --yes deno check supabase/functions/guincho-address-search/index.ts` aprovado; compilação Dev/Prod, assemble Homol e lint Homol aprovados após os ajustes finais. Edge Function publicada via `npx supabase` em Prod/Homol e Dev, com presença da chave MapTiler confirmada sem exposição de valores. Homol instalado com preservação de dados e aberto nos três aparelhos conectados. Cenários funcionais permanecem para validação manual do usuário.

Atualização de layout e status: na Home, o card de solicitação ocupa a área de conteúdo do Scaffold, preservando a navbar no bottomBar. O checkout usa notificações privadas de pagamento e consulta de recuperação a cada 2 segundos; os status de atendimento e a solicitação visível do prestador também são consultados a cada 2 segundos. A publicação de localização mantém sua cadência própria. O reconciliador financeiro passa a verificar trabalho elegível a cada 10 segundos. Pagamentos pendentes normais, sem cancelamento, compensação ou suporte, nos primeiros 30 minutos recebem nova consulta ao Mercado Pago após 10 segundos. Falhas e casos antigos mantêm o backoff existente. O intervalo de criação até aprovação observado no último pagamento antes da alteração foi de aproximadamente 72 segundos; isso não representa a duração da confirmação bancária. Na validação manual, conferir o card sem mover a navbar, retornar do banco ao checkout e verificar a passagem para o mapa após aprovação, além de aceite, chegada e conclusão bilateral. O prazo total depende também do Mercado Pago e da conectividade.

A disponibilidade exibida na Home acompanha o snapshot atualizado da sessão, incluindo desativação pelo serviço de localização. Ao sair da conta, a disponibilidade é alterada antes de encerrar a autenticação; o comando assíncrono que encerra o serviço não repete essa alteração, evitando atingir a conta seguinte durante troca de usuário.

Correção da descoberta de guinchos: a tela do cliente consulta novamente a lista a cada cinco segundos enquanto visível, preservando a seleção e a cotação do prestador que continua disponível. A retomada do publicador envia START idempotente ao serviço, sem confiar apenas no estado mantido em memória após encerramento pelo Android. A aquisição inicial de GPS dispõe de 30 segundos. Conflito de publicador entre dois aparelhos da mesma conta encerra somente o publicador perdedor, sem desativar a disponibilidade compartilhada. Para validação manual, ativar a disponibilidade com localização precisa; dois prestadores no mapa exigem duas contas diferentes. A busca mantém os critérios de localização recente, raio e ausência de atendimento em andamento.

Atualização solicitada em 2026-09-30: a Home usa navbar inferior compartilhada com PitTec. O botão Recentralizar foi removido; o mapa retoma o acompanhamento automaticamente após 30 segundos, reiniciando esse intervalo após gesto manual e usando somente posição atual válida. Nome do prestador e disponibilidade ocupam linhas separadas, evitando quebra letra por letra. As referências anteriores ao botão e à pausa indefinida após gesto foram substituídas por essa orientação do usuário; na validação manual, conferir a retomada automática após 30 segundos.

- Comando final: `./gradlew :app:assembleDevDebug :app:assembleHomolDebug :app:assembleProdDebug :app:lintHomolDebug --console=plain`.
- Resultado: `BUILD SUCCESSFUL`, em 1m45s, após o último ajuste de layout. APKs Debug dos três ambientes gerados; lint Homol concluído, com avisos e sem erros impeditivos. Relatório: `app/build/reports/lint-results-homolDebug.html`.
- Recentralizar ocupa uma linha separada do mapa para não sobrepor o marcador em telas pequenas; o card global também ocupa sua própria região medida.
- `git diff --check` concluído sem problemas de whitespace. As 25 tarefas de implementação foram concluídas. Cenários funcionais e medições SC-001 a SC-006 não foram executados; seguem no roteiro acima para validação manual do usuário.
