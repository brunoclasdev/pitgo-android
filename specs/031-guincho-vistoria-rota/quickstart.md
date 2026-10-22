# Guia de validação — Vistoria ampliada e rota do Guincho

Este guia deve ser executado depois da implementação. Nesta fase foram produzidos somente documentos; os cenários abaixo não foram executados e não há evidência de build/deploy desta feature. Ver [fotos](contracts/inspection-api.md), [UI](contracts/inspection-ui.md), [rota](contracts/guincho-route-api.md) e [modelo](data-model.md).

## Pré-requisitos

- Java/Android SDK e toolchain do projeto; npx Supabase e Deno operantes, ADB e aparelhos autorizados.
- Projeto Dev separado, contas reais de teste já autorizadas de guincheiro/cliente e veículo de teste. Usar o fluxo de pagamento simulado próprio de Dev; não fabricar aprovação no banco nem movimentar pagamentos reais para validar a feature.
- Um serviço com destino confirmado e vistoria editável; outro registro antigo para conferir compatibilidade. Preparar um rascunho v1 original-only e uma versão antiga já aprovada pelas operações existentes.
- Migrations e função guincho-route implementadas, configuração de roteador válida no backend. Homol e Prod compartilham projeto; checar projeto-alvo antes de qualquer aplicação futura.
- Condições de desempenho da spec, espaço disponível e fotos da câmera padrão até 16 MP. Usar A70, A32, A33 e ASUS conforme disponíveis, mais janela >=600dp para restauração adaptativa.

## 1. Checks locais após implementação

Executar na raiz do repositório:

```bash
./gradlew :app:assembleDevDebug :app:assembleHomolDebug :app:assembleProdDebug :app:lintHomolDebug :app:validateEnvironmentIsolation --console=plain
npx --yes deno check supabase/functions/guincho-route/index.ts
adb devices -l
```

Esperado: builds e checks sem erros novos; warnings existentes registrados separadamente. Conferir que não surgiram suítes, fixtures, mocks ou upgrades gerais. A compilação não comprova sincronização, RLS, rotação ou percurso real.

## 2. Inventário e disponibilização em Dev

Os comandos deste bloco são instruções para a implementação autorizada, não ações executadas neste planejamento. Configurar PITGO_PROJECT_REF para o projeto Dev previamente identificado, sem colocar tokens ou senhas em arquivos do repositório.

```bash
npx supabase --version
npx supabase link --project-ref "$PITGO_PROJECT_REF"
npx supabase migration list --linked
npx supabase functions list --project-ref "$PITGO_PROJECT_REF"
npx supabase secrets list --project-ref "$PITGO_PROJECT_REF"
npx supabase db push --dry-run
```

Conferir nomes de PITTEC_ROUTING_BASE_URL/API_KEY e disponibilidade do roteador; não registrar valores de secrets nem URLs contendo coordenadas. Conferir migrations aditivas, guards, grants e policies exatas das variantes. Em T017/T050/T065, revisar **todo o conjunto pendente mostrado pelo dry-run**, inclusive migrations de outras histórias no checkout. Arquivos, dependências, guards e permissões precisam estar completos/revisados antes de qualquer aplicação; aguardar migrations incompletas sem removê-las ou pular dependências. Aplicações ao mesmo projeto são sequenciais, mesmo com desenvolvimento em paralelo. Depois da revisão e dentro da autorização de entrega:

```bash
npx supabase db push --linked
npx supabase functions deploy guincho-route --project-ref "$PITGO_PROJECT_REF"
```

Confirmar verify_jwt=true, handler autenticado e ausência de escrita direta por clientes. Inventário e smoke test autenticado devem distinguir função inexistente/configuração inválida de falta de posição. Não prometer rota porque a cotação já calcula distância: a função nova e a geometria precisam estar disponíveis nesse ambiente.

Para instalar APK Dev no aparelho de teste selecionado:

```bash
adb -s "$PITGO_DEVICE_SERIAL" install -r -t app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s "$PITGO_DEVICE_SERIAL" shell am start -W -n com.trustarx.pitgo/.MainActivity
```

Os flavors usam o mesmo applicationId; verificar ambiente e contas antes do uso. Homol/Prod somente após validação em Dev e autorização correspondente, repetindo inventário no alvo e instalando o APK correto. Não confundir CLI vinculada ao Dev com o backend compartilhado.

## 3. Catálogo e conjunto mínimo — SC-001/007/008

1. Em atendimento Dev aceito/pago pelo fluxo autorizado, registrar Cheguei. Conferir 17 quadros, quatro obrigatórios e 13 opcionais; nomear cada pneu separadamente e distinguir painel de instrumentos de painel completo.
2. Capturar somente dianteira, traseira e laterais. Enviar e aprovar como cliente; opcionais vazias não impedem. Confirmar retorno do cliente à transação e espera pelo início manual do guincheiro.
3. Em outro rascunho, preencher opcionais deixando uma principal ausente: envio permanece bloqueado e informa o ponto faltante.
4. Incluir detalhe livre com legenda, nomeados opcionais e substituição. Conferir unicidade por ponto nomeado e múltiplos detalhes livres. Confirmar que o conjunto enviado/histórico mantém todas as fotos efetivamente incluídas.
5. Conferir versão antiga aprovada e reserva v1: consulta, aprovação vigente e original-only continuam válidos. Cliente legado recebe point conhecido; app novo preserva nomes das categorias.

## 4. Captura, orientação e responsividade — SC-002/003/004/009

Antes dos ensaios, concluir a instrumentação T030 para fotos/ações e T046 para rota; T061 apenas revisa esses eventos e resultados. Usar Android Studio System Trace/Perfetto com eventos apoiados em relógio monotônico do aparelho, como SystemClock.elapsedRealtimeNanos. Registrar ferramenta/versão e método realmente usados. Não usar relógio civil nem subtrair relógios de aparelhos diferentes; intervalos atravessando reinicialização do aparelho precisam ser identificados como inválidos e repetidos, sem ocultar falhas de integridade.

| Medição | Evento inicial | Evento final |
| --- | --- | --- |
| Retorno com miniatura — SC-002 | Callback de captura aceita recebido pelo app | Primeiro frame do álbum com miniatura no ponto correto |
| Resposta visual — SC-004 | Ação recebida: rolar, abrir ponto ou capturar | Primeiro frame com feedback correspondente, sem aguardar salvamento |
| Salvamento — SC-004 | Callback de captura aceita | Salvamento remoto confirmado ou reconciliado como READY |
| Retomada — SC-005 | Reabertura/recuperação de rede com sessão e contexto aptos | Início automático da operação de envio retomável |
| Percurso — SC-006 | Início confirmado ou desvio confirmado | Primeiro frame com o percurso correspondente; incluir espera por admissão/cooldown |
| Carregamento — SC-009 | Abertura do álbum/imagem | Primeiro frame com miniaturas inicialmente visíveis/original ampliado |

Sucesso de download ou decodificação não equivale ao primeiro frame exibido: correlacionar eventos de apresentação com frames no trace. Manter somente categoria, número anônimo da amostra e duração nas evidências, sem fotos, coordenadas, caminhos privados ou credenciais. Para SC-007 entre aparelhos, usar observação com referência temporal comum, por exemplo gravação conjunta das telas das contas de teste, sem subtrair seus relógios e sem incluir conteúdo sensível nas evidências do repositório.

Calcular por critério/tipo de operação a proporção dentro da meta e p95 pelo método nearest-rank: ordenar n durações e selecionar a posição ceil(0,95 × n), contada a partir de 1. Preservar mínimos e condições da spec; não misturar início de rota com recálculo nem resposta de UI com salvamento. Tentativas que falharem/expirarem contam como fora da meta; registrar falhas separadamente, sem removê-las do denominador, e não alegar p95 aprovado se falhas impedirem atingir a proporção exigida.

1. Distribuir pelo menos 40 capturas entre aparelhos, com fotos verticais/horizontais; medir callback → álbum/miniatura e callback → salvamento confirmado.
2. Executar pelo menos 20 sequências com rotação durante câmera, retorno, preparação e envio. Conferir ponto, orientação, quantidade e etapa; callback não relança câmera nem duplica imagem.
3. Com item salvando, fotografar outro ponto, rolar e abrir foto existente. Executar 40 ações, inclusive álbum com 17 pontos salvos; medir resposta visual sem esperar a rede.
4. Conferir normalização, pico de memória e ausência de crash/ANR em imagens maiores, sem exigir foto horizontal em tela horizontal. Em >=600dp/multiwindow, conferir apresentação vertical do conteúdo e restauração mesmo que a Activity gire.
5. Abrir álbum 20 vezes e ampliar 20 fotos; medir miniaturas visíveis/original com frio e quente identificados. Upload/conclusão de outra foto não deve reiniciar todos os downloads.
6. Como cliente, observar miniaturas primeiro e preparação dos originais para revisão. Falha de original bloqueia aprovação mesmo que sua thumb tenha carregado; outras imagens continuam consultáveis. Abrir uma imagem deve ter prioridade sobre pré-carga.

## 5. Retomada e resultado incerto — SC-003/005/007/008

Realizar pelo menos 20 tentativas, incluindo cinco fechamentos/reaberturas, cobrindo:

- Conexão interrompida antes da reserva, após reserva, após original, após thumb e após confirmação remota. Restaurar e verificar mesmos IDs/payload e uma foto vigente por ponto.
- Fechar/encerrar o PitGo com foto aceita pendente, reabrir primeiro no mapa inicial da mesma conta e conferir início automático da retomada <=5 s quando autorizado/conectado. Não exigir abrir álbum ou tocar em Retomar.
- Reabrir sem conexão e restabelecer durante uso; fila retoma automaticamente. Não exigir que fotos sejam enviadas enquanto app permanece fechado.
- Deixar uma substituição sem thumb/confirm: foto anterior continua selecionada. Submissão v1/v2 não pode passar pela reserva pendente; a confirmação v1 não deve promover uma captura v2 incompleta.
- Perder resposta após confirmação e remover apenas arquivo local de teste já confirmado: reconciliação reconhece READY sem nova captura. Não apagar material de usuário real para esse cenário.
- Cancelar câmera; fazer arquivo temporário tornar-se indisponível antes de salvamento em contexto de teste; conferir falha individual e preservação das demais fotos.
- Logout/troca de conta/ambiente durante envio: geração cancela commit tardio e limpa somente apresentação/cache de leitura; journals/arquivos de capturas aceitas não confirmadas continuam privados por conta/ambiente. Capturar offline na conta A, sair, entrar na B e conferir ausência de consulta/envio da pendência de A; voltar à A no ambiente original e confirmar retomada automática sem recaptura após revalidar permissões. Trocar ambiente também não apaga pendências do anterior. Remoção exige salvamento confirmado ou descarte explícito permitido. Expiração de sessão suspende até autenticação válida.
- Outra sessão fecha a versão enquanto há item local sem reserva: item fica bloqueado com explicação, sem anexar a versão fechada nem iniciar correção automaticamente.

## 6. Rota e recálculo — SC-006/008

1. Aprovar vistoria sem iniciar: percurso de transporte não se ativa. Iniciar pelo guincheiro e confirmar; rota/destino aparecem mesmo que o GPS ainda seja a mesma revisão conhecida.
2. Conferir que rota segue vias até o destino contratado, não até retirada/cliente/base. Mover-se em trajeto real autorizado e observar marcador sem chamadas upstream a cada fix.
3. Fazer desvios reais com leituras precisas; conferir três fixes/4 s e limiar definido, atualização automática e destino invariável. Posicionamento antigo/impreciso ou oscilação não deve disparar séries de recálculos.
4. Medir pelo menos 20 inícios e 20 desvios confirmados. Contar desde confirmação/detecção, incluindo espera por admissão; não reiniciar o cronômetro após cooldown.
5. Interromper rede/GPS durante percurso: preservar referência e aviso, sem linha direta falsa, novo início ou alteração de aprovação. Recuperar e reabrir: manter etapa/destino corretos.
6. Repetir refresh concorrente em sessões autorizadas: um lease/cálculo admitido por vez e intervalo >=5 s entre tentativas. Resposta de lease velho não sobrescreve rota nova; fix mais novo durante HTTP não causa starvation.
7. Encerrar/cancelar pelo fluxo existente enquanto há pedido em voo: cleanup invalida o lease, UI encerra rota ativa e resposta tardia não recria cache. Conferir regressão das rotas/contratação de PitTec e cotação do Guincho.

## 7. Permissões e privacidade

Com contas de teste e ferramenta HTTP autorizada, conferir chamadas dos contratos sem gravar JWT/payload em logs: sem sessão → 401; conta alheia → 403/negação de Storage; participante em etapa incompatível → 409. Testar leitor v1, paths original/thumb exatos e bloqueio de UPDATE/DELETE/upsert. Saber o appointment_id ou o prefixo não concede acesso; lease/cache não deve devolver rota a um solicitante alheio.

Conferir backup_rules/data_extraction_rules para a árvore privada, FileProvider sem concessão por pasta e invalidação de geração durante normalização/download. A limpeza de cache/apresentação no logout não apaga pendências; callbacks/Jobs antigos não as alteram nem recriam arquivos. Logs e métricas não contêm imagens, placas, nomes, caminhos privados, coordenadas ou URL/JWT/secrets.

## Registro de evidências

### Execução iniciada — 2026-10-14

- Branch `feature/refinamento-guincho`; 11 arquivos Android já modificados pelo trabalho de GPS foram identificados antes da implementação e preservados. Snapshots SHA-256 da sessão permitem conferir alterações de documentação/base; spec e checklist permanecem intactos.
- Baseline: builds Dev/Homol/Prod, lintHomolDebug e validateEnvironmentIsolation aprovados (`BUILD SUCCESSFUL`, 2m33s, 125 tarefas). Log local: `/tmp/pitgo-031-baseline.log`. Java 21.0.10; CLI via `npx supabase` 2.121.0.
- Aparelhos disponíveis no início: A70, A32 e A33; ASUS ausente. Inventário remoto confirmou `pittec-route`, `guincho-quote` e nome `PITTEC_ROUTING_BASE_URL` em Dev. Nenhum valor de secret foi usado nas evidências. Link inicial apontava para o projeto compartilhado; vínculo Dev deve ser confirmado antes de qualquer aplicação.
- Integração confirmada: `private.inspection_lock/inspection_require_draft/inspection_result`, manifesto e regras de pagamentos da feature 030; `startTransport` em AppointmentTrackingViewModel; SessionRepository/ConnectivityObserver no container e raiz NavHost. Nenhum fluxo financeiro foi alterado ou exercitado para este baseline.
- Dev: as oito migrations da feature 030 estavam ausentes e foram aplicadas, seguidas das dez migrations aditivas desta feature (catálogo, guards, reads/commands v2, Storage, três de rota, projeção v1 e snapshot de pontos nomeados), sempre via `npx supabase`, com dry-run e sem alterar arquivos históricos já aplicados. Função `guincho-route` publicada com verificação de JWT; `npx deno check` aprovado.
- Segurança exercitada em Dev: HTTP 401 para rota sem sessão, apikey/anon sem identidade de usuário e leitura direta da tabela privada. Consulta transacional em papel authenticated confirmou negação a participante alheio e ao RPC reservado a service_role, com rollback e sem criação de atendimento/pagamento/foto. Grants lidos confirmam ausência de acesso anon e escrita/refresh de rota exclusivos de service_role.
- Lint SQL em public/private: nenhum erro nas funções de vistoria/rota desta feature. Foi identificado um achado fora do escopo em `public.request_payment_cancellation`, cujo código não foi alterado; a análise incluindo extensions também lista funções PostGIS preexistentes. Não interpretar o exit code da CLI como ausência desses achados.
- Contas Dev: três usuários, dois prestadores e nenhum guincho ativo no inventário. Credenciais/contas para ensaio foram solicitadas, sem resposta até este registro. Não houve criação de usuários, cobrança real, pagamento/aprovação fabricados ou ensaio de serviço com contas de produção.
- Instrumentação adicionada antes dos ensaios: duração monotônica de retorno/miniatura, ação/feedback, original e salvamento (com identificação local de boot); o percurso só registra conclusão após identificação da geometria no frame nativo do MapLibre. O runner tolera até 10 s de handoff para câmera antes de pausar, preservando o journal e sem garantia de execução com app encerrado.
- SC-001–009: medições completas e fluxos positivos entre cliente/guincheiro **pendentes**, incluindo 40 capturas, 20 rotações/interrupções, 20 retomadas, 20 inícios/desvios, revisão/histórico e acessibilidade no álbum autenticado. Verificações estáticas, compilações e negativas de permissão não substituem essas amostras; nenhum p95 funcional foi aprovado.

### Entrega técnica e instalação — 2026-10-14

- Verificação final do código entregue: `:app:assembleDevDebug :app:assembleHomolDebug :app:assembleProdDebug :app:lintHomolDebug :app:validateEnvironmentIsolation` aprovados em 2m52s, 125 tarefas; log local `/tmp/pitgo-031-device-build.log`. `npx --yes deno check supabase/functions/guincho-route/index.ts` e `git diff --check` aprovados. Nenhuma dependência, suíte de testes, mock ou fixture foi adicionada. Spec/checklist permanecem com os hashes originais; os oito arquivos de GPS sem integração nesta feature também permanecem idênticos ao snapshot inicial.
- Projetos: Dev `ljnqlaiiwreuczbdghxn` e Homol/Prod compartilhado `eetbrkznuqbdrnybnyhf`. As dez migrations aditivas estão aplicadas nos dois, com listas local/remota sem divergências. Em Homol, revisão do dry-run precedeu aplicação serial via `npx supabase`; `guincho-route` está ACTIVE com `verify_jwt=true`. O vínculo da CLI ao encerrar aponta para Homol/Prod compartilhado.
- Compatibilidade positiva em Homol, somente leitura de um histórico existente: v1 continua com quatro pontos obrigatórios; v2 retorna os quatro pontos históricos no snapshot nomeado. Decisão/contadores e quantidade de fotos coincidem; v1 não expõe campos novos. Caminho de Storage não reservado foi negado. Evidência local agregada, sem IDs/fotos: `/tmp/pitgo-031-legacy-homol.json`.
- APK final Dev instalado no A32, abertura retornou `Status: ok` e hash instalado conferido. Depois foi substituído pela versão Homol, pois os flavors compartilham applicationId. SHA-256 Dev: `a2ad58e4fa51b86f61f2531f05220c7092ceb5b5d203163c8f9bca65afd69b91`.
- APK Homol final instalado no A70 (Android 11), A32 e A33 (Android 13), preservando dados. Hash de cada `base.apk` instalado coincide com `app/build/outputs/apk/homol/debug/app-homol-debug.apk`: `fad22002ba6d5dd707ff5f26ba027b2d06647acd8c940ca57bdfc87a78b44fc1`. Comando de abertura aceito e processo ativo nos três, sem `FATAL EXCEPTION` no trecho recente de AndroidRuntime. O primeiro `am start -W` do A70 excedeu 40 s; a conferência posterior de abertura/processo/hash passou, sem assumir desempenho aprovado. Evidência local: `/tmp/pitgo-031-install-homol-final.json`.
- ASUS não conectado; instalação não realizada nele. A32 estava bloqueado e A33 sem conta autenticada, limitando a inspeção visual. Não houve desbloqueio por credenciais, limpeza de dados ou operação financeira.
- Progresso: **54/65 tarefas concluídas**. T050 permanece parcial: backend/Deno/APK disponíveis, mas smoke positivo autenticado com etapa/GPS válidos e roteador ainda não executado. T065 permanece parcial: aplicação e instalação em Homol realizadas, mas a validação funcional Dev que condiciona o encerramento da entrega ainda está pendente. T018/T036/T051/T058/T059/T060/T061/T063/T064 também aguardam os ensaios completos descritos acima. Nenhum resultado de SC-001–009 ou p95 é declarado aprovado.

### Correção do retorno da câmera no A70 — 2026-10-15

- Reprodução observada: após confirmar foto, tela com spinner e consultas de vistoria/dispatch canceladas repetidamente, sem crash. Debugger confirmou o ciclo `GuinchoInspectionRoute → SessionViewModel.refresh` provocado pelo erro SESSION mantido no ViewModel ao remontar a tela. Raw e journal permaneciam preservados em AWAITING_CAMERA.
- Causa da falha inicial confirmada com breakpoint, inclusive ao cancelar a câmera: `captureResult → checkAccount → accountId` encontrava Auth em `SessionStatus.Initializing`. O código-fonte local do SDK Auth 3.2.6 (`androidMain/.../setupPlatform.kt`) confirma a transição ao background; ActivityResult chega antes de ON_START restaurar a sessão. Não era prova de sessão expirada.
- Correção: aguardar `auth.awaitInitialization()` com timeout antes de validar a conta/persistir o resultado e antes das consultas do álbum; manter horário da captura congelado desde o callback. Revalidação transitória pausa o runner sem ocultar a fila como se houvesse logout. O launcher e ViewModel permanecem registrados durante o gate de autenticação; erro de sessão é consumido antes de pedir refresh, com proteção contra repetição. O watchdog não interrompe uma captura cujo callback está sendo persistido. Logout/troca real continuam sujeitos a conta/generation e UI privada permanece gated.
- Homol atualizada no A70 (Android 11), sem limpar dados. SHA-256 do APK e do base.apk instalado: `0e81ff3ffb57ae15d9befd7a54eab3f3e95c4bd90ca0d4b9e49e2e01453c58b7`. Build Homol, lintHomolDebug e validateEnvironmentIsolation aprovados em 1m48s, 49 tarefas; `git diff --check` aprovado. Log local `/tmp/pitgo-a70-camera-fix-final-build.log`.
- Evidência funcional parcial: fotos já capturadas pelo usuário foram recuperadas pela ação explícita da interface; nenhuma aprovação/envio de vistoria, cobrança ou transição de serviço foi executada. Abrir/cancelar a câmera passou a gerar DISCARDED, sem SESSION. Uma nova captura confirmada pelo usuário no APK corrigido voltou ao álbum e chegou a SAVED automaticamente; UI passou a exibir 4/4 principais salvas. Logs da execução final: zero FATAL EXCEPTION e zero `stage=session_refresh_requested` no álbum. Cancelamentos de câmera sem foto não foram usados como evidência visual nem enviados como fotos.
- Amostra pontual, sem aprovação de p95: callback → frame da miniatura 284 ms; callback → confirmação de salvamento 8.177 ms, ambos no relógio monotônico do mesmo boot. Não equivale às 40 capturas/20 rotações e demais amostras de SC-001–009. Logs e screenshots ficaram somente em `/tmp/pitgo-a70-camera-final-logcat.txt` e `/tmp/pitgo-a70-final-settled.png`, sem fotos/identificadores/credenciais adicionados ao repositório. As tarefas de homologação integral continuam pendentes.

### Datas do percurso e atualização dos quatro aparelhos — 2026-10-15

- A70 reconectado via descoberta mDNS/ADB Wi-Fi. O servidor possuía geometria de 357 pontos, destino contratado consistente, revisão/distância/precisão válidas e localização recente, sem falha upstream registrada. No app, o aviso de percurso indisponível foi reproduzido.
- Causa confirmada por debugger em `GuinchoRouteRepository.refresh`: `DateTimeParseException` ao ler `2026-10-15T04:11:06+00:00`, classificada como SERVICE_UNAVAILABLE. O DTO usava `Instant.parse` diretamente, incompatível com esse offset no runtime do A70. Todos os horários do DTO de rota agora reutilizam `parseSupabaseInstant`, já existente e usado no PitTec; datas/serialização inválidas passam a ser falha CONTRACT sem retry infinito. Nenhuma alteração SQL/Edge Function foi necessária.
- O APK também contém o ajuste do endereço de novos destinos: número inserido após a rua e antes do restante do endereço, tanto na confirmação quanto no payload salvo. Snapshots de serviços já contratados não foram reescritos.
- Build Homol, lintHomolDebug e validateEnvironmentIsolation aprovados em 1m30s, 49 tarefas; `git diff --check` aprovado. Log local `/tmp/pitgo-guincho-route-time-build.log`. APK Homol instalado com dados preservados em A70 por Wi-Fi, A33, A32 e ASUS por USB; comandos de abertura aceitos. SHA-256 conferido contra base.apk em todos: `313d2ffc2e99f06e284aa279f81935ed97786a1a55aee3d693f470f14bb5e412`. Evidência `/tmp/pitgo-route-time-all-installs.json`.
- Na verificação posterior, o A70 abriu o mapa do prestador sem FATAL EXCEPTION. Já não havia atendimento de guincho IN_PROGRESS no backend; portanto não foi possível confirmar visualmente o percurso corrigido em transporte. Não foi iniciado/fabricado atendimento, pagamento, aprovação ou deslocamento para produzir essa evidência. Validação de nova rota e desvios permanece parcial/pendente conforme T051/T063/T064.

Registrar data, ambiente/projeto, APK/SHA-256, aparelhos/Android, ferramenta/método de medição, contagens, proporção dentro da meta e p95 das amostras por SC/tipo de operação, sequência realmente exercitada e falhas/limitações. Estado deste guia: **implementação, checks locais, verificações parciais de segurança/legado, instalação e retorno pontual da câmera executados; homologação funcional integral e medições completas pendentes**. Separar checks de compilação, testes manuais de fluxo, segurança e medições de desempenho; não marcar todos como aprovados apenas porque o build passou.
