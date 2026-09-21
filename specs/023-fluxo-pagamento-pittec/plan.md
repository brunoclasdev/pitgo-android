# Implementation Plan: Fluxo de pagamento do PitTec

**Branch**: 023-fluxo-pagamento-pittec | **Date**: 2026-09-18 | **Spec**: [spec.md](spec.md)

## Summary

O fluxo atual inicia o pagamento antes da busca PitTec e usa um único valor de proposta. Esta
feature move a cobrança para depois do aceite do prestador: a busca continua sem cobrança, o
backend congela o valor base do prestador, calcula 40% sobre esse valor e adiciona as taxas da
regra comercial vigente, e cada papel recebe somente sua visão financeira. O cliente confirma a
proposta de 15 minutos, escolhe um cartão salvo ou cadastra um novo cartão, e o atendimento só
avança após pagamento aprovado.

A implementação reutilizará os estados, RPCs, canais privados, PaymentRepository, SDK de cartões e
executor de Orders existentes. O backend persistirá a composição financeira e a versão da regra
para que confirmação, cobrança, retomada e auditoria usem exatamente o mesmo valor.

## Technical Context

**Language/Version**: Kotlin/Android SDK no app; SQL/PostgreSQL e TypeScript/Deno nas Edge Functions

**Primary Dependencies**: Jetpack Compose, ViewModel/StateFlow, Supabase Auth/PostgREST/Realtime,
Mercado Pago Orders e SDK Android CoreMethods já presentes

**Storage**: PostgreSQL/Supabase compartilhado; tabelas existentes de PitTec, pagamentos, cartões
salvos e uma nova tabela de regras de preço

**Testing**: ./gradlew :app:assembleDebug :app:lintDebug; deno check nas Edge Functions;
lint/migrations Supabase quando Docker/CLI estiverem disponíveis; matriz manual em homologação

**Target Platform**: Android suportado pelo projeto e projeto Supabase de homologação

**Project Type**: Mobile app + backend serverless compartilhado

**Performance Goals**: Após o aceite, apresentar a proposta ao cliente em até 10 segundos em
condições normais; após aprovação, refletir o início para os dois participantes em até 10 segundos

**Constraints**: Nunca expor o valor do prestador ao cliente ou o total do cliente ao prestador;
calcular em valores decimais exatos; não criar cobrança antes do aceite; uma única cobrança válida
por proposta; proposta expira em 15 minutos; segredos e dados de cartão permanecem server-side

**Scale/Scope**: Fluxo PitTec existente, um atendimento e uma proposta ativa por solicitação,
pagamentos Orders e cartões salvos; sem novo painel administrativo e sem alterar o fluxo móvel
Guincho fora dos pontos compartilhados

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Simplicidade**: PASS. Reutiliza dispatch, pagamentos, cartões e reconciliação existentes;
  adiciona apenas a composição de preço necessária e a tela/estado ausentes.
- **II. Android/MVVM**: PASS. A UI PitTec emitirá eventos; ViewModel controlará proposta,
  confirmação e checkout; Repository centralizará RPCs e funções.
- **III. Estado previsível**: PASS. Serão usados estados explícitos para proposta, confirmação,
  pagamento, expiração e falha; o fluxo não dependerá de booleanos independentes.
- **IV. Dados e segurança**: PASS. Valores serão filtrados por papel no snapshot server-side;
  tokenização e Access Token continuarão fora do app; idempotência e transições permanecerão no
  backend.
- **V. Qualidade proporcional**: PASS. A mudança é aditiva e localizada em PitTec/pagamento,
  sem nova dependência; verificações serão proporcionais ao risco financeiro.

## Project Structure

### Documentation

    specs/023-fluxo-pagamento-pittec/
    ├── plan.md
    ├── research.md
    ├── data-model.md
    ├── quickstart.md
    └── contracts/
        └── pittec-payment-flow.md

### Source Code

    app/src/main/java/com/trustarx/pitgo/features/pittec/
    ├── data/PitTecDtos.kt
    ├── model/PitTecModels.kt
    ├── repository/PitTecRepository.kt
    ├── ui/PitTecDispatchComponents.kt
    └── viewmodel/PitTecViewModel.kt

    app/src/main/java/com/trustarx/pitgo/features/payment/
    ├── data/PaymentDtos.kt
    ├── model/PaymentModels.kt
    ├── model/PaymentCheckoutUiState.kt
    ├── repository/PaymentRepository.kt
    └── viewmodel/PaymentCheckoutViewModel.kt

    app/src/main/java/com/trustarx/pitgo/features/paymentmethods/
    ├── repository/SavedCardRepository.kt
    ├── ui/PaymentMethodsScreen.kt
    └── viewmodel/PaymentMethodsViewModel.kt

    supabase/migrations/
    └── <timestamp>_pittec_customer_pricing.sql

    supabase/functions/
    ├── mp-create-payment/index.ts
    └── _shared/payment-reconciliation.ts

**Structure Decision**: manter organização por feature e o fluxo UI → ViewModel → Repository →
Supabase/Edge Function. A migration compartilhada estenderá appointments e criará a regra de
preço; o app PitTec consumirá snapshots role-aware e delegará criação/reconciliação financeira ao
fluxo de pagamentos existente.

## Phase 0: Research

As decisões estão consolidadas em [research.md](research.md). Foram resolvidos:

1. fonte do preço base e congelamento da proposta;
2. fórmula e arredondamento monetário;
3. separação de dados entre cliente e prestador;
4. integração com Orders/idempotência/reconciliação;
5. expiração, liberação do prestador e retomada;
6. reutilização do SDK de cartões com cadastro inline.

## Phase 1: Design

O modelo de dados está em [data-model.md](data-model.md), o contrato de snapshots/RPCs/checkout
está em [contracts/pittec-payment-flow.md](contracts/pittec-payment-flow.md), e os cenários
runnable estão em [quickstart.md](quickstart.md).

### Backend design

- Remover o pagamento prévio do caminho PitTec.
- Ao aceitar, congelar provider_amount, versão da regra, adicional, taxas, total do cliente,
  moeda, versão da proposta e expiração de 15 minutos.
- Filtrar o snapshot pela identidade autenticada: prestador recebe apenas valor base; cliente
  recebe composição do total; nenhum papel recebe o valor exclusivo do outro.
- Confirmar a proposta apenas com revisão de versão e prazo; transicionar para
  AWAITING_PAYMENT sem criar cobrança.
- Reservar a cobrança Orders usando o total do cliente, a proposta confirmada e uma chave
  idempotente persistida. Timeout continua em VERIFYING e o executor existente reconcilia.
- Após aprovação válida, a transição atômica libera ACCEPTED; falha, expiração ou cancelamento
  não inicia o atendimento. Expiração libera o prestador e encerra a solicitação.

### Android design

- PitTec inicia a busca sem navegar para o checkout.
- O snapshot AWAITING_CLIENT_CONFIRMATION renderiza o total e seus componentes somente para o
  cliente; a visão do prestador continua mostrando apenas seu valor.
- A confirmação chama o Repository com appointment, quote version e revision.
- O checkout reutiliza PaymentRepository/PaymentCheckoutViewModel, recebe o total congelado pelo
  servidor, lista cartões salvos e permite abrir a tokenização inline para um cartão novo.
- O estado de pagamento permanece único e recuperável por snapshot/realtime/polling; a navegação
  para tracking só ocorre depois de APPROVED.
- Erros de expiração, conflito de preço, cartão recusado, offline, timeout e sessão expirada
  recebem mensagens acionáveis sem detalhes técnicos.

### Constitution Check after design

- **PASS**: nenhuma dependência nova, nenhuma credencial no app e nenhuma tabela acessível
  diretamente pelo cliente para compor preço.
- **PASS**: a separação financeira ocorre antes da serialização do snapshot, e não apenas por
  ocultação de componentes da UI.
- **PASS**: o fluxo móvel existente permanece com seus contratos atuais; alterações compartilhadas
  serão limitadas a branches PitTec e ao caminho de pagamento comum já usado pelo app.
- **PASS**: a migration é forward-only, preserva pagamentos históricos e associa a regra aplicada
  à proposta para auditoria e retomada.

## Complexity Tracking

Nenhuma violação constitucional identificada; a tabela de exceções não é necessária.
