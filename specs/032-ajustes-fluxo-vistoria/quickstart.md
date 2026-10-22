# Quickstart: Validação dos ajustes na vistoria

> Escopo atual (correção explícita do usuário em 2026-10-21): somente aviso interno, sem push/Firebase. Os pré-requisitos e cenários FCM abaixo documentam a execução anterior e estão superados. Para validar o fluxo atual, usar os passos da seção “Correção do aviso e layout no A33”.

Este guia será executado após implementação. Os resultados abaixo são esperados, não evidências de execução já realizada. Contratos: [avisos](contracts/client-inspection-alerts.md), [recuperação/navegação](contracts/inspection-recovery-navigation.md); entidades em [data-model.md](data-model.md).

## Pré-requisitos

- Android SDK/JDK compatíveis com o projeto, dependências resolvidas e configurações locais client-safe de Dev/Homol/Prod.
- Dois aparelhos ou emuladores: cliente e guincheiro, contas participantes distintas; terceiro usuário para verificar autorização. Para push, Android com Google Play services/Google APIs, rede e notificações permitidas. Pelo menos um aparelho Android 13+ para permissão de notificação.
- Firebase Android app para `com.trustarx.pitgo`, FCM HTTP v1 habilitado, `google-services.json` público no source set de cada flavor e projeto Dev isolado. Homol/Prod seguem configuração compartilhada atual, mantendo vínculo de ambiente.
- No backend Dev: migrations da vistoria 030/031 e desta feature aplicadas, Storage privado, RPCs autenticadas, worker `guincho-inspection-push`, pg_net/pg_cron/Vault e credenciais protegidas de envio. Verificar só presença/status de configuração, sem imprimir valores.
- Atendimento de Guincho do backend Dev em etapa editável e com condições de revisão atendidas pelas regras existentes. Um fluxo inteiramente simulado no aparelho não comprova trigger, RPC ou push remoto; usar dados do backend de desenvolvimento apropriados, sem criar cobrança real para obter o cenário.

## Build e instalação

Na raiz do repositório:

```bash
./gradlew :app:validateEnvironmentIsolation
./gradlew :app:assembleDevDebug :app:assembleHomolDebug :app:assembleProdDebug
./gradlew :app:lintDevDebug :app:lintHomolDebug :app:lintProdDebug
adb devices
adb -s "$PITGO_CLIENT_SERIAL" install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s "$PITGO_PROVIDER_SERIAL" install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

Usar seriais explícitos definidos no ambiente local. Mesma applicationId: instalar outro flavor substitui o anterior; nunca misturar ambiente por acidente. Builds falham claramente se faltar configuração necessária de Firebase/ambiente, sem expor secretos. Nenhum teste automatizado novo é exigido; não executar deploy nem pagamentos Homol/Prod por este guia sem uma tarefa que os autorize.

## Cenário A — Aviso fora da vistoria

1. Autenticar cliente e guincheiro; confirmar registro da instalação do cliente pela resposta sem token, com ambiente correto.
2. Manter cliente no mapa do atendimento; guincheiro captura os quatro pontos obrigatórios, aguarda salvamento e envia. Cliente vê aviso “Há uma vistoria aguardando seu aceite”, ação “Revisar vistoria” e abre a versão enviada. Sua tela só muda ao tocar na ação.
3. Repetir com cliente no Perfil ou Histórico, depois com Home do aparelho aberta (app em segundo plano). No último caso, conferir notificação do aparelho e abertura correta com a Activity já existente e com início frio normal. Encerramento forçado não é condição de aceite de entrega.
4. Repetir com cliente já na vistoria: atualizar conteúdo sem duplicar destino. Repetir push/reconectar: no máximo um alerta interruptivo por versão/instalação. Após uma postagem bem-sucedida, dispensar a notificação e reentregar a mesma mensagem; repetir deixando a notificação expirar visualmente antes da reentrega. Nos dois casos, a notificação não reaparece nem emite novo alerta, mas a pendência interna permanece. Solicitar correção e reenviar: nova versão gera aviso novo.
5. Recusar `POST_NOTIFICATIONS` e desabilitar canal: aviso interno continua e não bloqueia aceite; a tentativa impedida não é registrada como publicação bem-sucedida, permitindo a primeira postagem se a permissão for concedida e a pendência continuar vigente. Reabrir conectado após envio offline: pendência vigente aparece fora da vistoria.
6. Aprovar/corrigir/cancelar com aviso aberto; verificar remoção interna e cancelamento externo quando receber invalidação. Tocar aviso antigo deve mostrar situação real, sem aprovar versão inválida. Sem entrega de invalidação, notificação externa expira visualmente em 5 min; a pendência ainda real continua no app.
7. Trocar conta e flavor: nenhum trabalho/aviso da associação anterior é apresentado. Terceiro usuário não lista nem lê o aviso/fotos de outro cliente. Rotacionar token/reautenticar: registro se reconcilia sem entrega duplicada.

Para SC-001, observar pelo menos 20 envios em cada contexto (mapa/outra tela/segundo plano), conexão contínua e permissões ativas; registrar instante da confirmação do guincheiro e instante de percepção do aviso. Meta: ≥19/20 em até 10 s por contexto. Aceitação FCM isolada não comprova percepção. Registrar separadamente casos com restrição de entrega, sem incluí-los como sucesso da meta.

## Cenário B — Salvar novamente a mesma captura

1. Capturar um ponto e desativar rede antes de concluir o salvamento; conferir miniatura/captura preservada e mensagem com “Tentar salvar novamente”. Restabelecer rede e acionar uma vez: mesma foto/ponto é salva, sem câmera e sem duplicação.
2. Repetir falha em preparo, envio do original, miniatura e confirmação; para controlar resposta perdida sem adicionar código de teste à aplicação, usar interrupção de conexão/proxy no ambiente de desenvolvimento ou depuração interativa. Conferir resultado remoto antes de afirmar em qual etapa falhou.
3. Criar condição controlada de pouco espaço no aparelho de desenvolvimento, sem apagar dados pessoais: raw permanece, liberar espaço e repetir. Nova falha mantém recuperação disponível.
4. Rotacionar, sair/voltar e reabrir aplicativo pela mesma conta: journal recupera a captura; retomada automática e manual não geram duas fotos. Tocar repetidamente enquanto salvamento está ativo não dispara operação conflitante.
5. Em instalação depurável, reproduzir perda de derivado preservando raw: a mesma captura deve reparar arquivos. Se já havia reserva e os bytes precisarem mudar, verificar abandono confirmado antes da nova reserva, sem perda da fonte, mudança de ponto ou exigência de desfazer manualmente.
6. Só quando não existir fonte recuperável, oferecer recaptura explícita; versão encerrada ou não editável mostra impedimento. Nenhum desses erros autoriza envio incompleto. Opcional incluída pendente bloqueia envio até recuperação ou retirada explícita.

SC-003: em todos os casos recuperáveis, após corrigir a causa, uma ação retoma a mesma captura; reconciliação remota confirma um único resultado selecionado. Sem capturar/registrar bytes, paths privados ou imagens como logs de diagnóstico.

## Cenário C — Enviar e voltar ao mapa

1. Enviar vistoria completa: após confirmação, abrir automaticamente mapa do mesmo pedido e mostrar estado vigente. Voltar não reabre a tela removida; consulta explícita das fotos continua disponível.
2. Repetir após correção e entrando na vistoria sem mapa anterior. Conferir ausência de duplicata do mapa e preservação do pedido.
3. Envio recusado ou sem conexão mantém a tela e as fotos. Se servidor confirmar mas resposta se perder, recuperação usa o mesmo `operationId` e versão, reconhece `EXISTING` e retorna uma vez, mesmo se polling já mostrar `SUBMITTED`.
4. Abrir por consulta uma versão já enviada/aprovada: permanecer na vistoria. Atualização remota sozinha não fecha consulta. Rotacionar durante envio e após confirmação não perde retorno pendente nem cria navegação repetida.
5. Aceitar como cliente: retorno atual ao mapa continua funcionando; transporte só começa após guincheiro tocar em “Iniciar transporte”. Se o cliente decidir antes de o guincheiro voltar ao mapa, mostrar decisão vigente.

SC-004: em 20 envios iniciais/reenvios confirmados, ≥19 retornam ao mapa em até 2 s após confirmação, sem toque. Falhas reais não produzem retorno de sucesso. SC-005 exige conteúdo preservado e nenhuma transição indevida em todos os casos.

## Conferência do backend e regressão

Conferir unicidade do aviso por versão, entregas restritas a vínculos atuais, claim/ACK do worker, retry transitório, token revogado e erro de credencial visível como falha operacional. Testar replay do submit sem novo aviso; recuperação após falha do disparo imediato deve usar cron. Testar backend/worker indisponível sem travar salvamento, mapa ou consulta manual da vistoria.

Verificar álbum/fotos ampliadas, catálogo obrigatório/opcional, retomada automática e autorização de versões antigas. Revalidar notificações do prestador/PitTec e serviço de localização existentes. Com leitor de tela e fonte ampliada, ação de revisar e retry devem permanecer identificáveis e operáveis; informação não depende só de cor.

Ao registrar evidências, usar identificadores de casos e tempos/categorias, sem credenciais, token FCM, fotos, URLs assinadas ou dados pessoais. Marcar cenários não executados como pendentes; não inferir conformidade apenas de build/lint. Sucesso integrado de segundo plano exige entrega real FCM no aparelho.

## Execução — 2026-10-21

- Baseline: `:app:validateEnvironmentIsolation :app:assembleDevDebug` passou (1 min 5 s). Alterações locais anteriores preservadas.
- Pontos de integração confirmados: Container, sessão/fingerprint, journal versão 1 e replay SQL. O submit atual descarta seu resultado; correção será feita no fluxo existente.
- Configuração Firebase não encontrada no checkout nem em variáveis FCM/Google; provisionamento e entrega real permanecem pendentes. Três aparelhos ADB estão conectados; os cenários de usuário ainda não foram executados.

### Implementação e backend Dev

- Recuperação da mesma captura implementada com classificação de fonte recuperável, mutex por atendimento e reparação de reserva sem descartar a fonte. O retorno ao mapa usa somente confirmação da intenção própria de envio, com replay estável e estado consumível restaurável.
- Aviso global implementado com consulta autenticada fora da tela de vistoria. Receptor FCM, Worker, ledger persistente e cancelamento por conta/ambiente integrados; dispensa/expiração não remove a marca de postagem.
- Quatro migrations `20261021120000`–`20261021123000` passaram no parser PostgreSQL e em execução transacional com rollback no backend Dev; depois foram aplicadas atomicamente e registradas no histórico Dev. Na execução inicial, o worker `guincho-inspection-push` foi publicado somente nesse projeto.
- Conferência remota: 4 migrations registradas; `anon` sem execução do leitor de aviso; `authenticated` sem execução do claim worker e com execução da listagem; identidade alheia simulada em transação revertida recuperou zero avisos. Claim foi exercitado em rollback, sem produzir entrega. Requisição HTTP ao worker sem segredo retornou 401.
- Build Debug sem Firebase usa `INSPECTION_PUSH_CONFIGURED=false`; isso permite validar código e aviso interno, mas não comprova notificação externa. Release e `-PrequireInspectionPush=true` exigem configuração real. JSONs Firebase, `INSPECTION_FIREBASE_PROJECT_ID`, `INSPECTION_PUSH_SECRET` e `FCM_SERVICE_ACCOUNT_JSON` continuam pendentes; a chave de serviço deve ser provisionada somente no backend.
- Nenhuma instalação, logout, troca de flavor ou interação com atendimento foi executada nos aparelhos Homol conectados. Não há emulador/AVD Dev configurado. Cenários A/B/C completos, acessibilidade em execução e medições SC-001–SC-005 permanecem pendentes; verificações de schema/tipos não os substituem.

### Resultado dos checks finais

- `assembleDevDebug`, `assembleHomolDebug`, `assembleProdDebug`, `lintDevDebug`, `lintHomolDebug`, `lintProdDebug` e `validateEnvironmentIsolation`: passaram na execução com `--no-daemon --max-workers=2 -Dorg.gradle.jvmargs='-Xmx4096m -Dfile.encoding=UTF-8'` (5 min 11 s). Os três relatórios de lint possuem zero erros; avisos não impeditivos permanecem no projeto. Não foram criadas suítes automatizadas.
- Depois do último ajuste que publica o estado de processamento antes da consulta de rede, os três APKs foram recompilados e a validação de isolamento passou novamente.
- Worker TypeScript: `deno check` passou. Migrations também exercitadas em transação revertida no Dev antes da aplicação; permissões e histórico verificados após commit. `git diff --check` passou.
- Guarda de configuração: `:app:preDevDebugBuild -PrequireInspectionPush=true` falhou como esperado com a mensagem de ausência de `app/src/dev/google-services.json`. Esta verificação não conclui T005: os arquivos reais ainda não existem.
- Estado final desta execução: 31/38 tarefas concluídas. Pendentes T005 (Firebase por flavor), T021 (completar credenciais/configuração do despacho Dev), T022 (entrega real e cenários de aviso), T030 (recuperação em aparelho), T034 (navegação/tempos em aparelho), T036 (revisão manual integrada/acessibilidade) e T037 (medições SC-001–SC-005). Não foram declarados resultados de cenários não executados.

### Publicação adicional em Homol — 2026-10-21

- Solicitação explícita posterior do usuário: publicar o backend em Homol também, usando `npx supabase`. Conferidos `config/homol.properties`, Dev e Prod: Homol utiliza o projeto `eetbrkznuqbdrnybnyhf`, compartilhado com Prod e distinto de Dev. A implantação alcança esse backend compartilhado; o vínculo lógico continua separado por ambiente.
- CLI `npx supabase` versão 2.121.0. Dependências da vistoria, `pg_net`, `pg_cron` e URL de destino foram conferidas antes da publicação. As quatro migrations desta feature foram executadas primeiro em transação revertida e depois aplicadas atomicamente com registro no histórico, sem aplicar outras migrations locais pendentes. Vault `INSPECTION_PUSH_ENVIRONMENT=shared`, sem substituir configuração existente; ambiente dos avisos derivado da cobrança vinculada.
- Função publicada com `npx supabase functions deploy guincho-inspection-push --project-ref eetbrkznuqbdrnybnyhf --no-verify-jwt --use-api`; conferida como `ACTIVE`, versão 1. Autenticação própria por segredo preservada: requisição POST sem segredo retornou HTTP 401.
- Verificação remota confirmou as quatro migrations, cron ativo, RLS nas três tabelas, leitor bloqueado para `anon`, claim bloqueado para `authenticated`, listagem permitida a `authenticated` e claim permitido a `service_role`.
- Ainda ausentes neste backend: Vault `INSPECTION_FIREBASE_PROJECT_ID` e `INSPECTION_PUSH_SECRET`; Secrets da função `INSPECTION_PUSH_SECRET` e `FCM_SERVICE_ACCOUNT_JSON`. A publicação está concluída, mas a entrega FCM continua pendente de provisionamento e validação real. Não foram copiados segredos de Dev, criados pagamentos nem alteradas sessões nos aparelhos. T005/T021/T022 e demais cenários manuais continuam pendentes; contagem 31/38 preservada.

### Correção do aviso e layout no A33 — 2026-10-21

- Esclarecimento explícito do usuário: aviso simples dentro do aplicativo; push não faz parte do requisito. Retirados Firebase Messaging, Google Services, WorkManager deste fluxo, registro de instalações, receptor/worker Android e publicação de notificação do sistema. A configuração Firebase deixou de ser pré-requisito de build/Release. Notificações preexistentes do prestador e localização continuam fora desta alteração.
- Diagnóstico no A33 (`192.168.0.16:5555`): captura mostrou o card global por baixo da barra de status, acima do Scaffold das telas, consumindo altura e reduzindo o mapa. Logs capturados não tinham exceção fatal; a causa era a posição do aviso no layout. O diálogo Material agora se apresenta fora da Column de navegação, sem alterar as medidas do NavHost ou das telas existentes. Mantida a Column original necessária ao aviso de chamado do prestador.
- O aviso oferece “Revisar vistoria” e “Agora não”; consulta a cada 5 s somente com cliente autenticado, conexão e app visível. A dispensa fica separada por conta/versão e é restaurável na Activity sem ser apagada pela inicialização transitória da sessão. Erro de abertura permanece explícito e é limpo na dispensa. O acesso à revisão segue revalidação autenticada no backend.
- A migration adicional `20261021130000_guincho_inspection_in_app_alerts.sql` passou primeiro por execução transacional revertida e depois foi aplicada com histórico em Dev e no backend compartilhado Homol/Prod, usando `npx supabase`. Cron de push e gatilho de disparo removidos; eventos deixaram de fazer fanout, dispatcher virou no-op e RPCs de registro/worker tiveram acesso revogado. Avisos e registros históricos preservados. Função Edge `guincho-inspection-push` removida dos dois projetos; lista remota confirmou ausência. Leitores autenticados continuam autorizados e leitores anônimos bloqueados.
- `assembleHomolDebug`, `lintHomolDebug` e `validateEnvironmentIsolation` passaram (7 min 7 s); relatório de lint com zero erros. Após os ajustes finais de restauração/dispensa e preservação da Column original, `assembleHomolDebug` e isolamento passaram novamente (1 min 9 s). Manifest mesclado e fontes/dependências ativos sem Firebase/WorkManager deste fluxo. `git diff --check` passou. Nenhuma suíte nova foi criada.
- Validação visual inicial no A33: diálogo centralizado sem deslocar cabeçalho/mapa; “Agora não” fechou o aviso e manteve “Aguardando aprovação das fotos” no atendimento. Na conferência da versão final, o atendimento já passou para aprovado e depois avançou de estado durante a observação; não havia mais diálogo de aceite pendente. Nenhuma ação de aprovação, início de transporte ou pagamento foi executada por esta validação. Não foi completado o teste da ação “Revisar vistoria” com pendência vigente na versão final, nem uma nova série de envios/tempos ou os demais cenários manuais.
- APK Homol final instalado com `adb install -r`, preservando dados, e aberto no A33, A32 (`RX8T10LATDY`), A70 (`192.168.0.17:5555`) e ASUS (`192.168.0.7:5555`); instalação e abertura bem-sucedidas nos quatro. Logs do processo final do A33 não apresentaram exceção fatal.
- Os antigos itens de provisionamento/entrega FCM e suas pendências deixaram de se aplicar ao escopo corrigido. As contagens históricas 31/38 acima descrevem a implementação anterior; não representam o aceite atual dos cenários manuais ainda não executados.
