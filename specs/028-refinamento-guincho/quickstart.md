# Quickstart: Guincho

Interfaces em [contratos](contracts/guincho-api.md), estados/preços em [modelo](data-model.md). A evidência da implementação está registrada ao final deste documento.

## Prerequisites

Cliente/veículo válido, dois providers MOBILE/Guincho com localização/disponibilidade/notificações, segundo cliente para concorrência, staff MANAGER ativo e usuário sem permissão. Backend de validação com rota e regras novas; escolher projeto explicitamente. Dev usa base própria; Homol compartilha Prod e envolve transações reais. Dashboard em `/Users/brunocesar/projetos-web/pitgo` integrado aos novos contratos.

Antes de cada checkpoint T015/T023/T030/T042, aplicar incrementalmente as migrations concluídas e implantar as funções da história no backend de validação selecionado, conferindo regras/jobs/schema cache e compatibilidade do APK. Registrar o projeto e as versões aplicadas; gerar tipos do dashboard T038 somente após atualizar esse backend. T045 corresponde ao rollout de entrega após validação e build, não ao primeiro preparo do ambiente.

## Commands

Na raiz Android, após alterações:

```sh
./gradlew :app:assembleDevDebug :app:assembleHomolDebug :app:assembleProdDebug
./gradlew :app:lintHomolDebug
npx supabase migration list --linked
npx supabase db push --linked
npx supabase functions deploy guincho-quote --project-ref <projeto-alvo>
adb devices -l
adb -s <cliente> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

Também implantar as mp-* alteradas; verificar regras/jobs/schema cache antes de APK. Não executar db push só para validar documentação.

No dashboard:

```sh
npm run typecheck
npm run lint
npm run build
```

## Cenários

1. Mapa antes de pagar; retirada/destino/veículo antes de selecionar; trocar ícones troca mini card. GPS negado, falta destino, offline/vazio geram ação recuperável sem cobrança.
2. Base isolada: preço 100, rota conhecida 10 km, tarifa 3, markup 10%, taxas 0%+5 → cliente 148 e prestador 130. Distância fracionária respeita round. Regra alterada antes exige recotação; depois de pago mantém valor.
3. Dev simula sem MP; Homol/Prod cobram total mostrado. Pending/failed não libera dados/oferta. Approved envia oferta somente ao escolhido; reabrir não duplica cobrança.
4. Dois clientes reservando o mesmo provider: só uma reserva válida. Aprovação tardia de cancelado gera compensação/refund, sem interferir no pedido do segundo cliente.
5. Aceite só pelo escolhido; recusa/timeout pago cancela e refunda integralmente sem procurar outro. Validar acesso indevido por outro cliente/provider.
6. Cheguei antes de Iniciar transporte, sem confirmação de início pelo cliente. Concluir só IN_PROGRESS. Reabrir durante espera mantém prestador na conclusão e card automático do cliente.
7. Cliente confirma: ambos retornam ao inicial; repetição/reconexão mantém resultado único e disponibilidade existente.
8. Problema exige motivo; DISPUTED libera novo pedido ao prestador e mantém histórico. Restore prioriza novo ativo.
9. Suporte MANAGER+ com justificativa; usuário sem permissão/razão vazia rejeitado. COMPLETE mantém pagamento; CANCEL_FULL_REFUND solicita amount completo incluindo taxas. Duplo toque não duplica; resolver caso antigo preserva novo atendimento.
10. Regressão PitTec/BROWSE/MOBILE_SEQUENTIAL anteriores, preços, pagamentos, histórico; datas null e todos os enums/restore/notificações devem funcionar.

## Evidência

Registrar flavor/projeto/devices/versões/valores/resultados sem tokens, documentos ou endereços em logs. SC-004: pelo menos 20 mudanças, incluindo aprovação de pagamento e confirmação de conclusão, com ambos os aplicativos em primeiro plano no atendimento e conexão contínua ao backend, sem falhas de requisição. Registrar tipo de rede e timestamps sincronizados da persistência no backend e renderização em cada aplicativo; calcular a latência até a última renderização, exigindo ≥19 de 20 em ≤10 s. Excluir dessa latência o processamento externo anterior à aprovação persistida; registrar falhas de rede separadamente e validar sua recuperação. Build/lint não substituem fluxo financeiro/dashboard. Encerrar reservas de validação e acompanhar refunds.

## Evidência da implementação — 30/09/2026

- Android: builds `assembleDevDebug`, `assembleHomolDebug` e `assembleProdDebug` concluídos. Lint Dev passou após repetição; a primeira execução sofreu falha interna do analisador Kotlin/FIR durante alterações em andamento. Verificação final executada após estabilização.
- Dashboard: `npm run typecheck`, `npm run lint` e `npm run build` passaram; rotas `/contestacoes` e `/contestacoes/[id]` incluídas. Alterações preexistentes do repositório web preservadas.
- Edge Functions: `npx --yes deno check` passou para `guincho-quote` e `mp-create-payment`. Cotação implantada em Dev via `npx supabase`.
- Dev (`ljnqlaiiwreuczbdghxn`): migrations até `20260930152000` aplicadas via `npx supabase db push`. Simulação habilitada somente neste projeto, fora das migrations; padrão de entrega permanece desabilitado.
- Validação manual SQL com rollback: descoberta, total 148/remuneração 130, idempotência da reserva, privacidade antes do pagamento, aprovação simulada, aceite, chegada, início e conclusão/confirmacão; contestação libera operação, suporte sem permissão rejeitado, reembolso integral simulado, primeira decisão preservada, novo atendimento mantido.
- Validação adicional com rollback: alteração de oferta rejeitada, amount adulterado não cria intenção, aprovação após expiração cancela e compensa integralmente; recuperação financeira antiga não toma prioridade do pedido novo. Não houve transação externa MP nestas validações nem persistência dos dados temporários.
- Correções aditivas após execução real: identidade de staff referencia Auth; reserva Guincho não exige aceite antecipado do PitTec; referências SQL compostas resolvidas; projeção de suporte usa `support_required_at`; preço revalidado na simulação; recuperação limitada ao checkout ativo. Migrations aplicadas não foram editadas.
- Prod/Homol (`eetbrkznuqbdrnybnyhf`): compatibilidade das 23 migrations verificada em transação com rollback. Antes do rollout, nenhum atendimento/busca operacional e nenhuma falha cron nos últimos 15 minutos.

### Validações ainda sem evidência ponta a ponta

A amostra SC-004 de 20 mudanças medidas entre banco e renderização dos dois apps ainda não foi coletada. A validação SQL e os builds não substituem essa medição nem o pagamento real MP e o reembolso externo. Por instrução expressa de 30/09/2026, a execução dos checkpoints manuais T015/T023/T030/T042/T046 fica com o usuário. Esses itens foram encerrados no escopo do agente como transferidos, sem afirmar execução ponta a ponta ou resultado de SC-004.

### Rollout e devices

- Prod/Homol: migrations até `20260930152000` aplicadas por `npx supabase db push`; schema anterior salvo localmente fora do Git. `guincho-quote` e `mp-create-payment` implantadas no projeto vinculado.
- Auditoria Dev/Prod: mesma última migration, uma regra ativa e job de expiração ativo; zero falhas cron recentes; escrita de cotação negada a anon/authenticated, RLS ativo. Flag de simulação true em Dev e false em Prod.
- Configuração faltante de rota em Prod identificada por HTTP 503 e corrigida com a URL já validada em Dev, preservando secrets de pagamento. Os quatro probes sem sessão (duas funções em cada projeto) passaram a retornar HTTP 401, sem criar operações MP. Upstream rodoviário respondeu `Ok`; o Python do sistema falhou por SSL, verificação repetida com Python 3.13 sem desabilitar TLS.
- Homol instalado e iniciado em ASUS `R1AIB60009675B2`, A70 `RQ8MB054C1J`, A33 `RQCTB04EDNW`, A32 `RX8T10LATDY`. ASUS/A70 exibiram entrada; A33 manteve a sessão de prestador disponível. A32 permaneceu com tela de sistema no primeiro levantamento.
- Dashboard implementado e validado localmente; publicação web não executada.

### Validação manual assumida pelo usuário

O usuário escolheu Homol e dispensou testes manuais pelo agente. Não foram efetuadas cobranças reais MP, não foi coletada a amostra SC-004 e não foi executada validação autenticada do painel pelo agente. Homol permanece em Prod com MP real; Dev mantém simulação. O último APK inclui histórico acessível também quando o prestador está na modalidade PitTec.

Builds finais Dev/Homol/Prod passaram após inclusão do histórico no perfil PitTec. Lint final Dev passou (2m13s). APK Homol final reinstalado nos quatro devices, preservando os dados do app. Nenhum hook after_implement configurado (`.specify/extensions.yml` ausente).

Ajuste de retirada automática: GPS recente e consulta de endereço ao abrir o Guincho, destino como único campo editável, retirada manual pelo mapa quando GPS indisponível. Sem migrations/deploy backend neste ajuste. Validação manual continua com o usuário.

Build Homol do ajuste de retirada automática concluído (`assembleHomolDebug`, 39s), instalado com sucesso e iniciado nos quatro devices conectados. Sem execução de testes manuais, conforme preferência do usuário.
