# Quickstart: Validar vistoria fotográfica do Guincho

**Status em 2026-10-14**: implementação integrada; builds e análise estática aprovados. Oito migrations aplicadas ao backend compartilhado Homol/Prod com autorização explícita. Homol mais recente instalado no ASUS AI2202, A70, A33 e A32; quatro fotos READY com objetos válidos e aprovação real confirmados. Retorno do cliente após aprovação implementado; nova execução desse evento no APK atualizado e demais cenários de aceite ainda pendentes. Resultados efetivos ao final deste documento.

## Pré-requisitos

- Implementação e migrations da feature concluídas; contratos em [API](contracts/guincho-inspection-api.md) e [UI](contracts/guincho-inspection-ui.md).
- JDK/SDK Android configurados como no projeto, arquivos locais `config/dev.properties`, `config/homol.properties` e `config/prod.properties` válidos, sem expor seu conteúdo em logs. Dev usa backend próprio e pagamento simulado; Homol/Prod compartilham backend e pagamentos reais.
- Duas contas participantes (cliente com veículo e prestador móvel de Guincho) em dois aparelhos/emuladores com câmera funcional. Outra conta para verificar negação de acesso; funcionário MANAGER+ para consulta de contestação no dashboard.
- Bucket e novas migrations aplicados ao ambiente de validação escolhido. Não supor que compilar APK aplica migrations ou cria bucket.
- Para verificar compatibilidade, usar um pedido que já tenha chegada registrada antes da ativação e outro anterior à ativação ainda a caminho. Criá-los pelo fluxo normal do ambiente de validação, sem gerar fixtures/suítes automáticas.

## Compilação e verificação estática

Na raiz Android, depois da implementação:

```sh
cd /Users/brunocesar/projetos-mobile/android/pitgo
./gradlew :app:assembleDevDebug :app:assembleHomolDebug :app:assembleProdDebug --console=plain
./gradlew :app:lintHomolDebug :app:validateEnvironmentIsolation --console=plain
git diff --check
adb devices -l
```

Os flavors compartilham application ID. A instalação substitui a versão anterior; escolher o aparelho/ambiente explicitamente durante a validação:

```sh
adb -s <serial> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s <serial> shell am start -n com.trustarx.pitgo/.MainActivity
```

Esses comandos de instalação são instruções futuras; não foram executados por este planejamento.

Para validação isolada do schema em Supabase local descartável já configurado, sem usar `--linked` ou reset de banco remoto:

```sh
npx supabase start
npx supabase db push --local
npx supabase migration list --local
```

Essa etapa valida o backend local; não significa que o app Dev aponta para ele. O build exige endpoint HTTPS configurado. O teste completo deve usar o backend Dev configurado e suas migrations correspondentes, sem substituir URLs automaticamente. Aplicação em backend compartilhado Homol/Prod fica para a etapa de entrega explicitamente autorizada.

No dashboard, após integrar a consulta:

```sh
cd /Users/brunocesar/projetos-web/pitgo
npm run typecheck
npm run lint
npm run build
```

Não criar novas suítes automatizadas sem solicitação. Resultados de build/lint não comprovam aceitação funcional; registrar os cenários seguintes separadamente.

## Cenários funcionais em Dev

| Cenário | Ações | Resultado esperado |
|---|---|---|
| Chegada | Contratar, aceitar, aprovar pagamento simulado; guincheiro toca Cheguei | Chegada registrada, ACCEPTED, fotos pendentes; transporte e conclusão bloqueados |
| Quatro pontos | Fotografar frente/traseira/laterais e conferir prévias | Pontos identificados e progresso somente após confirmação remota; extras opcionais |
| Ponto faltante | Tentar enviar com três pontos | Indicação do ponto faltante; envio bloqueado |
| Refazer/desmarcar | Refazer ponto salvo antes do envio; desmarcar detalhe ou descartar upload pendente | Nova captura não substitui a anterior até salvar; material READY preservado; rascunho coerente |
| Câmera externa | Abrir câmera, girar tela, voltar, cancelar captura e encerrar/reabrir app | Sem perder fotos confirmadas; referência pendente recuperada ou nova captura orientada; sem sucesso falso |
| Envio | Salvar quatro pontos e enviar conjunto | Versão fixa SUBMITTED; cliente vê pendência sem expandir mapa |
| Revisão | Abrir miniaturas/ampliação; simular falha em uma foto | Identificação dos pontos; retry; aprovação bloqueada enquanto houver foto sem apresentação bem-sucedida |
| Correção | Cliente solicita correção com motivo; guincheiro corrige e reenvia | Motivo obrigatório, nova versão, anterior preservada; início bloqueado |
| Aprovação | Cliente aprova versão vigente | Fotos aprovadas; transporte continua ACCEPTED, service_started_at ausente, botão Iniciar transporte disponível ao guincheiro |
| Retomada após OK | Fechar/reabrir ambos antes de iniciar | Aprovação mantida, aguarda guincheiro, sem nova aprovação nem início automático |
| Início explícito | Guincheiro toca Iniciar transporte | IN_PROGRESS; ambos acompanham; só agora pode solicitar conclusão |
| Conclusão | Guincheiro conclui; cliente confirma | Confirmação final separada das fotos; retorno normal; histórico completo |
| Cancelamento | Cancelar durante captura/revisão; repetir incluindo expiração existente em ambiente isolado | Ações de escrita encerradas; fotos confirmadas disponíveis; sem OK inventado |
| Histórico | Consultar concluído, cancelado e contestado como ambos os participantes | Versões, fotos extras/não enviadas, autoria, captura, decisão e correções corretas; aprovada destacada |
| Perfil do veículo alterado | Alterar cadastro depois de contratar e consultar vistoria | Veículo documentado continua sendo o snapshot contratado |
| Legado | Retomar pedido já chegado antes da ativação | Fluxo anterior preservado; ausência de vistoria explicitada |
| Pedido antigo ainda a caminho | Registrar sua primeira chegada após ativação | Vistoria obrigatória, sem dispensa pela data da reserva |
| Troca de ambiente | Substituir Dev por Homol com captura/download pendente, sem contratar ou pagar em Homol | Fingerprint invalida sessão, intenções e cache; nenhuma foto ou operação de Dev é reutilizada |
| Acessibilidade | Fonte ampliada, tela pequena, claro/escuro; navegar voltar | Ações alcançáveis, pontos identificados por texto, sem interferir na navbar |

## Contratos e segurança

Executar verificações com sessões reais do ambiente isolado e registrar status/resultados, sem colocar JWTs, caminhos privados ou imagens em logs públicos:

1. Chamar `start_guincho_transport` e wrappers antigos `start_mobile_appointment`/`complete_mobile_appointment` antes do OK: nenhum libera transporte/conclusão. Guincho requerido em app antigo também não recebe dispensa.
2. Repetir chegada, reserva, confirmação, envio, decisão e início com a mesma intenção: no máximo um resultado, sem versões/decisões duplicadas. Reutilizar chave com outro payload deve falhar.
3. Duas sessões do cliente tentam aprovação/correção simultâneas: somente uma decisão vence. Cliente tenta aprovar versão anterior: conflito e versão vigente apresentada, sem aprovação silenciosa.
4. Cancelar enquanto upload/confirm está em andamento: nenhuma foto não confirmada é mostrada como salva e nenhuma aprovação é produzida; fotos previamente READY permanecem consultáveis.
5. Consultar manifesto e Storage como terceira conta: negado. Tentar sobrescrever/apagar objeto enviado/aprovado e alterar tabela diretamente como participante: negado. Conferir policies efetivas, não só as novas.
6. Sair da conta com download/câmera pendentes e entrar com outra: cancelar trabalho antigo, limpar referência/cache da sessão e não exibir imagens anteriores.
7. Em contestação, MANAGER+ abre galeria/foto via dashboard: auditoria identifica ação, conta e caso/foto. Conta sem permissão, foto de outro caso ou falha de auditoria: sem download. Nunca permitir aprovação/edição pelo suporte.
8. Conferir permissões de leitura após reembolso pendente/falho/concluído e após resolução da contestação; consultar fotos não muda pagamento nem decisão financeira.
9. Regressão: convite → aceite → pagamento → deslocamento segue vigente, sem dados pessoais liberados antes da aprovação financeira. PitTec, atendimento fixo e outros fluxos mantêm suas transições.

## Medições de sucesso

- SC-001: nenhum início automático ou conclusão antes de início explícito com vistoria aprovada.
- SC-002: pelo menos 10 guincheiros e 10 clientes representativos; 90% de cada grupo sem ajuda, captura/envio até 5 min e revisão até 2 min nas condições da spec. Medir etapas separadamente; espera pelo outro participante não entra no tempo de execução da própria etapa.
- SC-003: pelo menos 20 transições de envio/correção/aprovação; 95% visíveis ao outro participante em até 10 s, com aplicativos visíveis e conexão contínua. Registrar início na confirmação da ação para o autor e fim na exibição ao outro, com relógios sincronizados; registrar falhas de rede separadamente.
- SC-004/005/006: 100% dos cenários de histórico, retomada, repetição e acesso correspondentes sem perda de fotos confirmadas, versão incorreta, duplicação ou acesso indevido.

## Registro da validação

Na implementação, acrescentar data, ambientes, versões, aparelhos, comandos executados, resultados e limitações reais. Não marcar aceitação a partir da existência deste roteiro. Este planejamento não aplicou migrations, instalou aplicativos, executou cobrança/reembolso ou validou visualmente o fluxo.

## Implementação — baseline 2026-10-14

- Android iniciou com somente a documentação 030 não rastreada. As oito migrations propostas não existem no histórico e seus nomes estão livres. ARRIVE inicia serviço na migration 154000; guardas compartilhadas e valor Homol serão preservados.
- Ignore files Android e dashboard cobrem build, ferramentas e segredos; não há Dockerfile/artefato publicável que exija outro ignore.
- Dashboard contém alterações prévias em autenticação, Home, ignores e database.ts; preservar integralmente essas alterações e acrescentar apenas os contratos desta feature. AGENTS.md e guia Next.js local de Route Handlers lidos.
- Nesta sessão, psql e docker não foram encontrados no PATH; a validação isolada será verificada antes de declarar T037 concluída. Há três aparelhos autorizados conectados e um não autorizado; nenhuma instalação foi efetuada neste levantamento.

## Resultados da implementação — 2026-10-14

| Verificação executada | Resultado efetivo |
|---|---|
| Android: `:app:assembleDevDebug :app:assembleHomolDebug :app:assembleProdDebug :app:lintHomolDebug :app:validateEnvironmentIsolation` | PASS no comando final, BUILD SUCCESSFUL, 125 tarefas. Log local `/tmp/pitgo-inspection-verified-build.log`; relatório `app/build/reports/lint-results-homolDebug.html`. APKs gerados em `app/build/outputs/apk/{dev,homol,prod}/debug/`. |
| Dashboard: `npm run typecheck`, `npm run lint`, `npm run build` | PASS nos três comandos finais; build inclui o proxy autenticado de fotos. Log local `/tmp/pitgo-inspection-dashboard-final.log`. |
| `git diff --check` nos dois repositórios | PASS. Alterações anteriores do dashboard preservadas. |
| Oito migrations: parser PostgreSQL `pglast.parse_sql` e `parse_plpgsql` | PASS após a revisão final. Verifica sintaxe; não comprova aplicação, resolução de referências, RLS ou execução de funções. |
| `npx supabase --version` | CLI 2.120.0 disponível via npx. |
| `npx supabase status` | Falhou: Docker e Podman ausentes. |
| `npx supabase db lint --local --schema public,private --level error --fail-on error` | Falhou: conexão recusada em 127.0.0.1:54322; nenhum Supabase local em execução. |
| `npx supabase db query --linked --project-ref ljnqlaiiwreuczbdghxn …` e `npx supabase projects list` | Consultas sem retorno, interrompidas após espera limitada. Não há resultado de consulta ou validação remota. Nenhum DDL foi enviado. |
| Câmera, revisão entre participantes e métricas SC | NÃO EXECUTADO. Nenhum APK instalado; nenhum pagamento/reembolso realizado. |

O projeto vinculado pela CLI é `eetbrkznuqbdrnybnyhf`, compartilhado por Homol/Prod. O Dev configurado é `ljnqlaiiwreuczbdghxn`. Na primeira fase acima não foi executado `db push` remoto nem alterado o vínculo; a aplicação autorizada posterior está registrada abaixo. Para concluir T037, disponibilizar Docker/Podman e executar os cenários locais deste roteiro; para T040, disponibilizar o backend Dev com as migrations e os participantes necessários.

Foi localizado posteriormente o cliente psql em `/usr/local/opt/libpq/bin/psql`, mas nenhum servidor local. Uma tentativa de instalar PostgreSQL via Homebrew foi interrompida; as duas dependências adicionadas (`docbook`, `docbook-xsl`) foram removidas e nenhum serviço de banco foi iniciado.

### Revisão final de invariantes (T036)

- Chegada grava flag e rascunho sob bloqueio de busca → atendimento. Aprovação mantém ACCEPTED; START explícito e COMPLETE exigem aprovação da versão vigente. Transições novas também recusam prazo de atendimento vencido, antes da próxima execução do cancelamento agendado.
- Pagamento conserva `payment_can_release`, quote version e `coalesce(payment_amount, customer_total_amount)`, inclusive o valor específico de Homol. Wrappers antigos continuam passando pela transição protegida.
- Reservas, confirmações, envios e decisões usam intenção persistida; confirmação READY com operação diferente é recusada. Revisão/versão e bloqueios serializam alterações; sucessora única preserva fotos e decisão anteriores.
- FKs vinculam versão, foto e manifesto ao mesmo pedido; triggers protegem fotos READY, versões decididas e manifestos enviados. Troca de ponto só desmarca o anterior após confirmar a nova imagem.
- Bucket privado limita tamanho/MIME e caminho reservado. Policies restritivas impedem bypass por policies permissivas antigas; participantes não sobrescrevem nem apagam. Policies anon não chamam funções privadas sem permissão, preservando outros buckets.
- Dashboard exige MANAGER+, caso correspondente e auditoria antes do download administrativo. Galeria e proxy não aprovam fotos nem alteram resolução financeira.
- Logout/troca de fingerprint invalidam operações e arquivos; STOP da câmera preserva a captura. Navegação de retomada reconhece a rota de vistoria para não expulsar o prestador durante a captura.

Esses achados foram conferidos no código. Comportamento efetivo de RLS, concorrência, Storage e recuperação continua dependente de T037/T040.

### Correspondência e aceite

FR-001 a FR-018 estão implementados nos arquivos relacionados em `tasks.md`: captura guiada, versionamento, decisão do cliente, início explícito, histórico e suporte auditado. T001–T036, T038–T039 e consolidação T041 estão concluídas. T037 e T040 permanecem abertas por falta de execução real dos cenários.

SC-001 e SC-004–SC-006 possuem guardas e fluxos implementados, mas não têm resultado funcional medido. SC-002 (10 guincheiros + 10 clientes) e SC-003 (20 transições) não foram medidos. Nenhum SC foi considerado aprovado a partir de compilação ou inspeção estática.

### Preparação da distribuição (T041)

Responsável pela publicação do PitGo: primeiro validar em Dev e concluir T037/T040; coordenar APK e dashboard compatíveis com a aplicação ordenada das oito migrations. A ativação no backend compartilhado afeta Homol e Prod, inclusive aplicativos antigos. Atendimentos já chegados conservam o fluxo anterior; primeira chegada após ativação exige vistoria.

Texto preparado para notas da versão e instruções do APK: “Agora o guincheiro registra fotos da frente, traseira e laterais do veículo ao chegar. O cliente pode aprovar ou pedir correção; as fotos e decisões ficam no histórico. Atualize o PitGo para registrar ou aprovar as fotos do veículo. Essa etapa será obrigatória antes de iniciar o transporte. Após a aprovação, o guincheiro toca em Iniciar transporte.”

A versão mínima compatível e o link público de atualização devem ser definidos pelo responsável pela distribuição. O Gradle atual declara versionName 1.0 / versionCode 1; esses valores também identificam builds anteriores e não distinguem esta implementação. Não usá-los como evidência de compatibilidade nem anunciar um link fictício. A ativação remota autorizada em 2026-10-14 e a distribuição direta aos aparelhos estão registradas abaixo; não houve publicação pública nem deploy do dashboard.

## Aplicação e instalação autorizadas — 2026-10-14

Pedido explícito: aplicar migrations com `npx supabase`, gerar o build e instalar Homol nos aparelhos conectados.

- A espera anterior foi identificada na leitura da credencial pelo Chaves do macOS. Após liberação, a credencial existente foi usada somente em memória, sem gravá-la no repositório/logs.
- `npx supabase db push --linked --dry-run --skip-vault` confirmou somente as oito migrations da vistoria pendentes. `npx supabase db push --linked --skip-vault --yes` aplicou todas, de `20261014120000` a `20261014131000`, com saída 0. Projeto `eetbrkznuqbdrnybnyhf` (Homol/Prod compartilhados); Dev não foi alterado. Log local `/tmp/pitgo-inspection-migration-push.log`.
- Consulta remota por `npx supabase db query --linked` confirmou as oito versões no histórico, RLS nas três tabelas, bucket privado JPEG de até 5 MiB, oito policies de Storage, enriquecimento do snapshot e guarda de início. Evidência `/tmp/pitgo-inspection-deployed.log`.
- `npx supabase db lint --linked --schema public,private --level error --fail-on error` não reportou erros nas funções da vistoria. O comando global retornou 1 por `42702`, referência ambígua a `a.id` em `public.request_payment_cancellation`, função anterior que esta feature não modificou. Pendência registrada sem alterar regras financeiras fora do escopo. Log `/tmp/pitgo-inspection-remote-lint.log`.
- Depois da aplicação, `./gradlew :app:assembleHomolDebug :app:validateEnvironmentIsolation --console=plain` passou. APK `app/build/outputs/apk/homol/debug/app-homol-debug.apk`, 69.311.653 bytes, SHA-256 `ceb788fed4933ecf642a3fa8a0d40b13161311beecf78ae5b67e3f74d323536c`. Log `/tmp/pitgo-homol-deploy-build.log`.
- `adb -s <serial> install -r` retornou Success nos quatro aparelhos: ASUS_AI2202 (`R1AIB60009675B2`), SM_A705MN (`RQ8MB054C1J`), SM_A336M (`RQCTB04EDNW`) e SM_A325M (`RX8T10LATDY`). Nenhum aparelho exigiu desinstalação/limpeza de dados.
- Conferência posterior confirmou SHA-256 idêntico ao APK Homol nos quatro aparelhos. `am start -W` retornou Status: ok e o processo do aplicativo estava ativo em todos. O SM_A325M reconectou via USB durante a primeira conferência; a repetição após reconexão passou. Registro `/tmp/pitgo-homol-device-installations.json`. Abertura do app não representa aceite funcional da vistoria.

A aplicação remota e a instalação não substituem os cenários autorizados/negados, concorrência, câmera e avaliação entre participantes de T037/T040; essas tarefas permanecem abertas. Nenhum pagamento ou reembolso foi realizado nesta entrega.

## Correção após conferência nos aparelhos — 2026-10-14

Cliente e guincheiro exibiam “Não foi possível concluir esta ação” no acompanhamento antes da chegada. A inspeção das telas e de `MobileDispatchEnvelopeDto.domainOrNull()` identificou que o RPC de acompanhamento retorna o snapshot diretamente; a conversão desse formato para `MobileDispatchSnapshotDto` não repassava `inspection`, embora o formato com `snapshot` aninhado já o preservasse. Isso acionava a proteção de resumo ausente e ocultava Cheguei.

Correção: repassar `inspection = inspection` nessa conversão, mantendo validação e bloqueios existentes. Não houve alteração de banco nem remoção de guardas.

- `:app:assembleHomolDebug :app:lintHomolDebug :app:validateEnvironmentIsolation` passou, BUILD SUCCESSFUL em 1m54s; `git diff --check` passou. Log `/tmp/pitgo-inspection-envelope-fix-build.log`.
- Homol corrigido instalado com `adb install -r` nos dois aparelhos então conectados, A70 (`RQ8MB054C1J`, cliente) e A33 (`RQCTB04EDNW`, guincheiro). SHA-256 do APK instalado confirmado em ambos: `4443ed6336be625161b5453beeaf3c6c70d22f2500920b0a073edd3373d0f634`.
- Abertura retornou Status: ok; screenshots e hierarquia de UI confirmaram ausência da mensagem nos dois e botão CHEGUEI visível no prestador. Evidência local `/tmp/pitgo-inspection-envelope-fix-devices.json`. Não foi acionada chegada, aprovação, início, conclusão ou pagamento durante essa conferência.
- Os dois aparelhos desconectados permanecem com o build anterior. A conferência resolve o erro relatado de acompanhamento; não representa a execução integral de T037/T040.

## Captura e quadros com miniaturas — 2026-10-14

Após o relato de fotos não salvas, os logs dos dois aparelhos não continham a exceção de processamento: o catch anterior descartava seu detalhe. Consulta de status por `npx supabase db query --linked` encontrou uma versão DRAFT e nenhuma foto reservada, READY ou objeto enviado. A falha antecedia o upload. A inspeção do código e do SDK local identificou divisão por `BitmapFactory.Options.inSampleSize`, cujo valor inicial é zero; o processamento agora inicializa o fator em 1 antes do cálculo de redução.

- A captura passou a usar quadros em duas colunas para frente, traseira e laterais; a prévia local aparece no próprio ponto, pode ser ampliada e informa “Aguardando salvar”. “Salvar foto”/“Descartar” ficam fixos no rodapé enquanto há prévia. O estado “Foto salva” e o progresso continuam dependendo de confirmação remota. Fonte ampliada/telas estreitas usam uma coluna; telas maiores podem usar quatro.
- Revisão/histórico também usam miniaturas; ampliação mantém autoria, datas e descrição. Fotos adicionais continuam opcionais.
- Uma prévia pendente não é apagada ao tocar em outro ponto. Recarregamento de miniaturas usa uma geração própria após refresh/troca de versão; imagens anteriores voltam a carregar depois de salvar outra. Recuperação de upload local reutiliza a reserva e operação de confirmação existentes. Diagnósticos de falha em debug registram somente etapa, classe da exceção e status HTTP, sem imagens, caminhos, IDs, JWTs ou payloads.
- Build final `:app:assembleHomolDebug :app:lintHomolDebug :app:validateEnvironmentIsolation` passou, BUILD SUCCESSFUL em 1m8s. `git diff --check` passou. Log `/tmp/pitgo-inspection-photo-grid-final-build.log`.
- Homol final instalado com Success no A70 e A33 conectados; APK SHA-256 `94dc8c080baa437d5e782b31c17fdd345fce92158dc47144ea6d2c185989426c`. A reinstalação verificou ausência de captura local pendente antes de substituir o APK.
- Conferência visual do A33 confirmou os quatro quadros, respectivos pontos e acesso à captura. Não foi criada imagem fictícia para o histórico de um atendimento real. Até a última consulta, continuavam zero fotos no servidor; nova captura/salvamento foi solicitada ao usuário para confirmar a execução completa. Não considerar upload real validado apenas pela correção ou compilação.

Nenhuma migration ou dado financeiro foi alterado nesta correção. T037/T040 continuam pendentes nos cenários que não foram executados.

## Retorno ao álbum e salvamento automático — 2026-10-14

Novo relato: fotos ainda não salvas e necessidade de retornar ao álbum após capturar. A consulta real por `npx supabase db query --linked` continuou mostrando uma versão DRAFT e nenhuma reserva/foto/objeto. Somente A70 (cliente) conectado; A33 (guincheiro) não disponível para ler os eventos da câmera. Reconexão solicitada, sem confirmação recebida nesta etapa.

- Ao receber sucesso da câmera, o ViewModel normaliza a imagem e inicia salvamento automático no rascunho. Consulta a revisão atual, confere a versão vinculada à captura e faz reserva → upload → confirmação. A prévia é conservada para retry se houver falha; progresso/“Foto salva” continuam dependentes do backend. Capturas canceladas não fazem upload.
- Álbum permanece como destino de retorno. A navegação agora conserva a entrada restaurada da vistoria durante Initializing/Verifying, mostrando somente loading até a sessão válida. O código anterior podia remover essa entrada em um novo processo criado após abrir a câmera, perdendo seu SavedStateHandle/registro do resultado; esse risco foi identificado por revisão de código, sem comprovação nos logs do A33 desconectado. Demais estados de autenticação continuam encerrando o acesso conforme suas regras.
- Intenção guarda a versão de captura; callback duplicado é ignorado durante processamento/salvamento. Descrição opcional de avaria fica antes da captura. Logs debug registram estágios camera_requested, camera_return_ok/cancelled, image_processed, reserve_begin, upload_begin, confirm_begin e photo_saved sem conteúdo sensível.
- `:app:assembleHomolDebug :app:lintHomolDebug :app:validateEnvironmentIsolation` passou, BUILD SUCCESSFUL em 1m34s; `git diff --check` passou. Log `/tmp/pitgo-inspection-camera-return-build.log`.
- APK Homol SHA-256 `9f1257bf5b8fffff04906bf2df968343d522ae5aadc6264794a2d8462cdbb751`; instalado com Success no A70. Hash instalado conferido e abertura Status: ok; evidência `/tmp/pitgo-inspection-camera-return-device.json`. APK disponível em `app/build/outputs/apk/homol/debug/app-homol-debug.apk` para atualizar o guincheiro após reconexão.

Pendente: instalar esta versão no A33 e observar uma captura real, retorno ao álbum, registro READY, objeto privado e miniatura recuperada após reabertura. Não declarar resolução funcional do salvamento apenas pelo build. Nenhuma migration, aprovação, início de transporte, pagamento ou reembolso foi executado nesta etapa.

## A33 reconectado, salvamento real e retorno após aprovação — 2026-10-14

- A33 reconectado e atualizado com o build de retorno da câmera (`9f1257…`). Antes da instalação foram encontradas duas imagens JPEG apenas no cache local; preservadas no aparelho e em backup privado local com permissões restritas, sem importá-las/atribuir-lhes pontos ou aprová-las automaticamente.
- O usuário realizou as quatro capturas, enviou a versão e aprovou pelo cliente. Consulta real com `npx supabase db query --linked` confirmou FRONT/REAR/LEFT/RIGHT em READY, quatro objetos presentes, MIME JPEG, tamanho e owner correspondentes. Nova consulta confirmou versão APPROVED, atendimento ACCEPTED e `service_started_at` ausente. Evidência local `/tmp/pitgo-inspection-real-photo-approval.log`.
- Conferência do A33 após captura confirmou retorno ao álbum, quatro indicações “Foto salva” e quatro arquivos baixados. A câmera Samsung usa confirmação “OK” antes de devolver o resultado. Não foi implementada uma promessa de retorno no disparo sem essa confirmação; o fluxo observado retorna após confirmar na câmera. Nenhuma foto de teste técnico foi inserida pelo agente: as quatro imagens acima foram capturadas pelo usuário.
- Pedido adicional: ao aprovar, cliente deve voltar ao acompanhamento da transação. Implementado evento único somente após RPC de aprovação confirmar versão e estado APPROVED. Navegação retorna ao acompanhamento do mesmo appointmentId, reutilizando a entrada anterior quando correspondente. Falhas mantêm a revisão; consulta posterior de fotos aprovadas não dispara esse evento. Transporte continua dependendo de Iniciar transporte pelo guincheiro.
- Build/lint/isolamento finais passaram, BUILD SUCCESSFUL em 1m52s. Log `/tmp/pitgo-inspection-approval-return-build.log`. Homol instalado com Success nos A70/A33, ambos com hash conferido: `fc8d54286f07afc2f4874ac8812c488e5aa9a7e4dee4afbb587a849961bf4409`. Abertura Status: ok nos dois; evidência `/tmp/pitgo-inspection-approval-return-devices.json`.
- Após a última reabertura, A70 estava na tela de entrada; login solicitado ao usuário para conferir a transação no cliente. No A33, acompanhamento mostra “Fotos aprovadas”; houve diálogo do sistema referente a Google TV, fechado sem alteração no PitGo. A aprovação real ocorreu antes da instalação deste último ajuste de navegação: não declarar nova execução de aprovação/retorno com este APK sem um novo cenário permitido.
- Na conferência final, A70 já estava na Home autenticada e A33 no mapa inicial do prestador; ambos mantinham o APK conferido. Não foram acionados nova contratação, nova aprovação ou início para forçar um teste da navegação. A consulta ao servidor e a tela anterior confirmaram o estado aprovado real; a mudança do retorno após uma nova aprovação foi validada por código/build e continua aguardando essa próxima execução funcional.

US1 (salvamento real dos quatro pontos) e aprovação da US2 possuem agora evidência funcional em Homol. Correção, métricas, concorrência, demais acessos e cenários Dev de T037/T040 continuam pendentes. Não foram executados início de transporte, conclusão, pagamento ou reembolso pelo agente; nenhuma migration adicional foi necessária.

## Atualização dos quatro aparelhos — 2026-10-14

- Build Homol e isolamento de ambientes passaram. Instalação com `adb install -r` retornou Success no ASUS AI2202 e Samsung A70, A33 e A32, preservando dados. Hash instalado conferido nos quatro: `fc8d54286f07afc2f4874ac8812c488e5aa9a7e4dee4afbb587a849961bf4409`. Evidências locais `/tmp/pitgo-latest-homol-install-build.log` e `/tmp/pitgo-latest-homol-installed-devices.json`.
