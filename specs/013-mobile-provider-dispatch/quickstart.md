# Quickstart: validar despacho de prestadores móveis

Este guia comprova a feature de ponta a ponta sem testes automatizados. Use um projeto Supabase de
desenvolvimento ou a stack local; nunca rode a matriz em produção nem use `service_role` no APK.

## 1. Pré-requisitos

- JDK/Android SDK do projeto e emulador/dispositivo API 24 e API 36.
- Supabase CLI autenticada ou Docker para stack local.
- propriedades Gradle já exigidas pelo app: Supabase e estilo MapLibre gerenciado.
- duas contas `CLIENT` e ao menos três contas `PROVIDER`, com providers `MOBILE` ativos.
- serviço de guincho ativo com `dispatch_kind=MOBILE_SEQUENTIAL` e ofertas ativas/preços distintos.
- FINE e Location Settings disponíveis nos dispositivos dos providers.

## 2. Banco reproduzível

```bash
supabase start
supabase db reset
supabase db lint
supabase migration list
```

Confirme no catálogo local/remoto:

- migration `20260823020000_mobile_provider_dispatch.sql` aplicada;
- `provider_discovery_locations`, `service_searches` e extensão de `appointments` presentes;
- exatamente um serviço de guincho marcado `MOBILE_SEQUENTIAL`; nenhum outro serviço alterado;
- candidatos no schema `private`;
- RLS habilitada e nenhuma escrita direta para `anon`/`authenticated`;
- RPCs de [mobile-dispatch-rpcs.md](contracts/mobile-dispatch-rpcs.md) como `SECURITY DEFINER`,
  `search_path` vazio e EXECUTE apenas para `authenticated`;
- índices GiST/uniques parciais presentes;
- job `expire-mobile-dispatch-requests` presente em `cron.job` e execuções saudáveis em
  `cron.job_run_details`.

O Postgres local/remoto precisa suportar agendamento em segundos. Se não suportar, interrompa a
validação: não reduza o requisito para cron de um minuto.

## 3. Build e inspeção de dependências

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug
./gradlew :app:dependencyInsight \
  --dependency realtime-kt \
  --configuration debugRuntimeClasspath
./gradlew :app:dependencyInsight \
  --dependency android-sdk-opengl \
  --configuration debugRuntimeClasspath
```

Esperado: build/lint aprovados, Supabase Realtime 3.2.6 e MapLibre OpenGL 13.5.0 resolvidos, sem
nova biblioteca de jobs, push, banco local ou mapa.

## 4. Publicação do provider

1. Entre como provider `MOBILE`; marque Disponível a partir do Dashboard visível.
2. Conceda localização precisa e mantenha POST_NOTIFICATIONS concedida no primeiro cenário.
3. Coloque app em background/apague a tela por mais de um minuto.
4. Observe notificação/Active Apps, status in-app ao retornar e uma única linha atual no banco.
5. Mova o dispositivo e confirme publicações próximas de 10 s e disponibilidade no cliente em até
   15 s.
6. Verifique que não existe tabela/linha de histórico.

Repetir:

- negar POST_NOTIFICATIONS em API 33+: publicação continua, com explicação da visibilidade limitada;
- conceder somente localização aproximada: disponibilidade móvel não inicia e UI aponta solução;
- revogar FINE, desligar localização e ficar offline: após 30 s o provider sai da seleção;
- reconectar: somente o fix atual é publicado, sem replay de trajeto;
- swipe da task: FGS continua; force-stop/user stop/reboot: não reinicia invisivelmente e o heartbeat
  fica stale; abertura visível oferece retomada;
- duas instalações da mesma conta: lease recente rejeita o segundo tracker sem alternar posições.

## 5. Ordenação e raios

Posicione providers elegíveis aproximadamente a 9, 11, 24, 26, 49 e 51 km do cliente. Use preços
distintos e o mesmo serviço exato.

1. Na conta CLIENT, toque no card de guincho.
2. Conceda FINE e inicie a busca.
3. Confirme primeira proposta dentro de 5 s e menor distância elegível.
4. Toque Próximo repetidamente, inclusive duplo toque e perda de conexão.
5. Confirme não repetição e progressão confirmada 10 km → 25 km → 50 km.
6. Confirme que o provider a 51 km não aparece e o vazio só ocorre após esgotar 50 km.
7. Para empate de distância, confirme desempate determinístico por provider ID.
8. Torne o candidato corrente indisponível/stale antes de solicitar; ele deve ser invalidado e a
   busca avançar.

Use `EXPLAIN ANALYZE` na função/consulta interna de seleção em ambiente descartável e confirme que
o índice GiST participa do `ST_DWithin` com 100 providers elegíveis.

## 6. Solicitação, preço e concorrência

1. Em uma proposta, anote o preço e toque Solicitar duas vezes.
2. Confirme exatamente um appointment `PENDING`, um candidato `REQUESTED` e deadline de 60 s.
3. Altere o preço da oferta no catálogo; snapshot cliente/provider continua com o preço congelado.
4. Provider recusa: resultado registra `PROVIDER_REJECTED` e o cliente recebe o próximo candidato.
5. Repita e deixe expirar com ambos apps fechados: cron marca `EXPIRED` e avança; aceite após o
   deadline é recusado mesmo antes da próxima execução do cron.
6. Faça duas contas clientes disputarem o mesmo provider: o índice permite somente uma solicitação
   `PENDING/ACCEPTED/IN_PROGRESS`; a outra busca recebe candidato diferente/indisponível.
7. Provider aceita: sai de novas buscas, discovery é removida e tracking privado fica disponível
   somente aos dois participantes.

## 7. Atendimento e cancelamento

- Inicie e conclua: busca vira `COMPLETED`, preço não muda, tracking privado é apagado e provider
  volta à discovery somente após novo fix elegível.
- Cliente tenta cancelar sem motivo: erro inline; com motivo: fluxo termina e não busca outro.
- Provider tenta cancelar sem motivo: erro inline; com motivo: lease é liberada e a mesma busca do
  cliente continua sem repetir candidatos.
- Confirme autor/motivo persistidos e não expostos a terceiros.

## 8. Realtime, restart e autorização

Em cada estado `SEARCHING`, `OPTION_FOUND`, `AWAITING_PROVIDER`, `ACCEPTED` e `IN_PROGRESS`:

1. feche/reabra e mate/recrie o processo;
2. autentique a mesma conta;
3. confirme redirecionamento ao snapshot correto, sem duplicar busca/request;
4. desligue/ligue rede e confirme resnapshot após reconnect;
5. injete/observe eventos de revisão antiga e confirme que a UI não regride;
6. troque de conta e confirme que proposta, IDs e coordenadas somem antes da nova tela.

Com tokens de anon, outro CLIENT e outro PROVIDER, confirme que não é possível ler/alterar busca,
candidato, appointment ou posição móvel que não pertença à relação autorizada. Broadcast bruto não
deve conter preço, coordenadas, contato ou ponto do cliente.

## 9. UI, mapa e acessibilidade

- MOBILE usa carro; FIXED preserva pin de endereço.
- Atualizações do carro não recriam MapView/style nem recentralizam após gesto.
- Falha de estilo/tiles mantém proposta/status/actions textuais.
- TalkBack lê nome, localidade, distância, preço e ações fora do mapa; countdown não é anunciado a
  cada segundo.
- Testar 320dp, retrato, paisagem, tablet/fold e fontScale 2.0: botões não cortam, cancelamento
  rola acima do IME e nenhuma ação essencial depende de cor/ícone/mapa.

## 10. Critério de encerramento

A feature só está pronta quando todos os cenários acima passam, as métricas SC-001 a SC-014 da
[spec](spec.md) são registradas na matriz manual, e não há coordenadas históricas, acesso cruzado,
aceite tardio, duplicação ou regressão de revisão.
