# Implementation Plan: Mercado Pago Orders

**Branch**: `feature/implementacao-mercadopago` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

**Input**: `specs/022-mercadopago-orders/spec.md`. O identificador retornado pelo Spec Kit é `022-mercadopago-orders`; a branch Git efetiva permanece a indicada acima.

## Summary

Migrar novas cobranças para Checkout API Orders com processamento automático, uma transação por tentativa e Pix de 30 minutos. Preservar Customers/Cards e obter token novo para cartão salvo. Manter Payments exclusivamente para resolver cobranças legadas. Reservar tentativas antes de HTTP, reconciliar resultados incertos e processar reembolsos sem depender do app aberto.

Reutilizar payments, RPCs, Realtime e MVVM. Acrescentar inbox privada e um executor durável, acordado por um único cron de minuto somente quando houver trabalho vencido. Isso atende FR-016 sem reintroduzir a cadência de um segundo recentemente mitigada.

## Technical Context

**Language/Version**: Kotlin/Compose compiler 2.2.10 conforme catálogo, Java 21 no ambiente, TypeScript em Supabase Edge Runtime/Deno, PostgreSQL 15 na configuração local.
**Primary Dependencies**: Existentes: AGP 9.1.1, Compose BOM 2026.06.01, Supabase Kotlin 3.2.6, Ktor 3.3.1, Mercado Pago Android SDK 1.0.0, supabase-js@2. Manter versões; HTTP via fetch, HMAC via Web Crypto, pg_cron/pg_net/Vault.
**Storage**: PostgreSQL; expansão aditiva de payments e inbox privada. Nenhum PAN, CVV ou token de cobrança persistido.
**Testing**: Compilação/lint Android, tipos Deno, lint SQL e cenários manuais. Não gerar suítes ou fixtures sem solicitação, conforme constituição.
**Target Platform**: Android minSdk 24, compileSdk 37, targetSdk 36 e backend Supabase compartilhado deste repositório.
**Project Type**: Aplicativo móvel + serviços de pagamento.
**Performance Goals**: SC-006: feedback em até 10 s em 95% dos cenários. Chamada externa do checkout limitada a 7 s. Webhook persiste/ACK em até 5 s normalmente. Executor: 20 itens, concorrência 2, orçamento 40 s e timeout HTTP de 7 s.
**Constraints**: Uma obrigação financeira ativa por contexto; resultado incerto bloqueia nova cobrança; três envios de reembolso no máximo, consultas não contam. Sem cron subminuto, nova plataforma, alterações de preços ou regras de atendimento.
**Scale/Scope**: Dois fluxos (móvel/PitTec), dois meios (Pix/cartão), legado Payments e Orders. Lotes limitados e índices por vencimento; não presumir volume de produção não medido.

## Constitution Check

| Gate | Antes da pesquisa | Após o design |
|---|---|---|
| I: simplicidade e escopo | Passa | Passa: tabela financeira reutilizada, um executor e inbox necessária à entrega durável |
| II: Android/MVVM | Passa | Passa: ViewModel/Repository existentes; dados PCI tratados pelo componente nativo SDK |
| III: estados/experiência | Passa | Passa: verificação e reembolso explícitos; Material, textos acessíveis e tema existentes |
| IV: segurança/transições | Passa | Passa: RLS/grants mínimos, segredo interno, valor exato e aplicação atômica |
| V: qualidade/performance | Passa | Passa: sem testes gerados, upgrades genéricos ou polling agressivo |

Sem exceções. Habilitação da conta e credenciais são pré-requisitos de homologação, não resultados comprovados por este planejamento.

## Project Structure

### Documentation (this feature)

```text
specs/022-mercadopago-orders/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── payment-interfaces.md
│   └── mercadopago-orders.md
└── checklists/requirements.md
```

tasks.md será produzido por $speckit-tasks.

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/
├── features/payment/{data,model,repository,ui,viewmodel}/
├── features/paymentmethods/{data,model,repository,ui,viewmodel}/
├── features/mobiledispatch/
├── features/pittec/
└── core/navigation/
app/src/main/res/values/strings.xml
supabase/
├── config.toml
├── migrations/                  # novas migrações, sem editar histórico aplicado
└── functions/
    ├── _shared/                 # operações MP/reconciliação compartilhadas
    ├── mp-create-payment/
    ├── mp-save-card/
    ├── mp-remove-card/
    ├── mp-refund-payment/
    ├── mp-webhook/
    ├── mp-cancel-payment/       # novo: intenção de cancelamento do checkout
    └── mp-reconcile-payments/   # novo: executor interno durável
```

**Structure Decision**: Evoluir features existentes; sem framework de pagamentos, fila externa ou tabela de orders redundante para uma transação por tentativa.

## Phase 0 — Research

Concluída em [research.md](research.md): endpoints, estados, cartão salvo, recuperação, notificações, devolução, execução sem app e legado. Nenhuma dúvida de produto bloqueia o design.

## Phase 1 — Design

1. Reservar tentativa com lock de contexto e preço do servidor antes de HTTP. Chave estável; busca vazia após timeout não libera nova cobrança.
2. Webhook, resposta de criação e executor aplicam resultado por operação transacional comum. Validar valor/moeda/referências. Evitar reaplicação de APPROVED no trigger PitTec.
3. Persistir intenção de cancelamento/reembolso na mesma transação do atendimento. Inbox antes de ACK; executor com lease/revisão, sem lock SQL durante HTTP.
4. Android recebe contexto de cartão autorizado, usa SDK 1.0.0 para token novo, persiste request_id em SavedStateHandle e recupera pagamento no servidor após nova sessão. Realtime invalida snapshot; fallback com tela ativa a cada 10 s, sem polling paralelo.
5. Backfill identifica PAYMENTS; novas tentativas usam ORDERS. Leitores, webhook e executor tratam ambas antes de ativar novas criações.

Detalhes: [modelo](data-model.md), [interfaces](contracts/payment-interfaces.md), [contrato externo](contracts/mercadopago-orders.md).

### Sequência de entrega e gates

- Schema aditivo e funções de domínio; backfill sem criar cobranças ou alterar resultados financeiros.
- Backend com ambos os protocolos; configurar Order e manter Payment para legado, cron e segredos em homologação.
- Android com service_id, token de cartão e snapshots. Clientes sem contexto obrigatório recebem erro acionável; não aceitar preço arbitrário para compatibilidade.
- Ativar Orders somente após [quickstart.md](quickstart.md), incluindo cartão anterior, recuperação sem app e legado. Incompatibilidade da conta bloqueia ativação, não remove FR-004.
- Em incidente, suspender novas criações por configuração; manter leitura, webhook e devolução de ambas as origens. Sem rollback destrutivo ou conversão de Orders existentes em Payments.

### Riscos delimitados

- Fluxo móvel hoje cobra antes da busca e aceita amount do app: incluir service_id e calcular preço do catálogo no servidor. start_mobile_dispatch valida serviço/cliente, quitação integral líquida, sync_state=CURRENT e ausência de cancelamento, devolução ou conflito financeiro. Aprovação com app fechado é registrada; busca móvel inicia no retorno do cliente com localização atual.
- Trigger PitTec pode interpretar reaplicação de APPROVED como contexto inválido após aceite: corrigir guard de mudança de estado e broadcast.
- Busca vazia não prova ausência de cobrança: manter verificação e sinalizar suporte após 24 h; continuar conciliação horária sem nova cobrança.
- Saldo, prazo do processador e indisponibilidade impedem promessa de devolução imediata. Distinguir aceite da solicitação de crédito confirmado.
- CLI Supabase e Deno não estão no PATH deste ambiente; os comandos do quickstart são validação futura, não executada nesta fase.

## Complexity Tracking

Nenhuma violação. Inbox e executor são necessários para FR-008/010/016; tarefas em memória não preservam intenção após encerramento do processo.
