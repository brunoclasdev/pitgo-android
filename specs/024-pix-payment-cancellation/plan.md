# Implementation Plan: Pix e cancelamento do pagamento

**Branch**: `024-pix-payment-cancellation` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

## Summary

A entrega vai completar a experiência de pagamento Pix dentro da tela Android, garantindo que QR Code e Copia e Cola sejam exibidos apenas quando o snapshot estiver completo, mantendo a cobrança idempotente e limitada temporariamente a R$ 1,00. O cancelamento será unificado para atualizar o estado persistido antes de retornar à tela inicial PitTec; pagamentos aprovados seguirão o fluxo de reembolso sem apagar o histórico financeiro.

A implementação reutiliza o fluxo existente de PaymentCheckout, PaymentRepository, Edge Functions `mp-*`, RPCs de pagamento e estados `PaymentStatus`. Não será adicionada uma nova integração Pix nem uma nova dependência Android.

## Technical Context

**Language/Version**: Kotlin existente do projeto, Jetpack Compose e TypeScript/Deno nas Supabase Edge Functions

**Primary Dependencies**: Android Jetpack Compose, ViewModel, StateFlow, Coroutines, Supabase Kotlin client, Mercado Pago Android Core Methods para cartões e Orders API no backend

**Storage**: PostgreSQL/Supabase, tabelas de pagamentos, solicitações PitTec e atendimentos existentes

**Testing**: Build/lint Android, lint/migrations Supabase e validação manual orientada por cenários; testes automatizados não serão criados sem solicitação específica

**Target Platform**: Android suportado pelo projeto e Supabase remoto do projeto PitGo

**Project Type**: Aplicativo mobile Android com backend Supabase

**Performance Goals**: Exibir o Pix após a resposta válida da cobrança; sair de carregamento/erro em até 20 segundos; retornar à tela inicial em até 5 segundos após cancelamento confirmado

**Constraints**: Todo valor desta feature deve ser R$ 1,00; Access Token permanece somente no backend; operações de cobrança, cancelamento e reembolso devem ser idempotentes; pagamento aprovado não pode ser sobrescrito por cancelamento local

**Scale/Scope**: Uma cobrança ativa por solicitação, duas formas de pagamento já existentes, fluxo cliente PitTec e estados de pagamento/reembolso já existentes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Simplicidade**: PASS — reutiliza estados, repositórios, funções e contratos existentes; não adiciona SDK Pix ou camada nova.
- **II. Android MVVM**: PASS — UI renderiza `PaymentCheckoutUiState`; ViewModel coordena ações; Repository acessa Supabase.
- **III. Estado previsível**: PASS — QR disponível, verificação, erro, cancelamento e reembolso permanecem estados explícitos.
- **IV. Dados e segurança**: PASS — o valor de R$ 1,00 é validado no backend; secrets não entram no APK; snapshots são sanitizados.
- **V. Qualidade proporcional**: PASS — validação usa build, lint e cenários manuais já adequados ao risco; nenhuma dependência nova é necessária.

**Gate result**: PASS. Não há `NEEDS CLARIFICATION` pendente.

## Project Structure

### Documentation (this feature)

```text
specs/024-pix-payment-cancellation/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── payment-pix-cancellation.md
└── checklists/
    └── requirements.md
```

### Source Code

```text
app/src/main/java/com/trustarx/pitgo/features/payment/
├── data/PaymentDtos.kt
├── model/PaymentCheckoutUiState.kt
├── model/PaymentModels.kt
├── repository/PaymentRepository.kt
├── ui/PaymentCheckoutScreen.kt
└── viewmodel/PaymentCheckoutViewModel.kt

app/src/main/java/com/trustarx/pitgo/core/navigation/
└── PitGoNavHost.kt

app/src/main/java/com/trustarx/pitgo/features/pittec/
├── data/PitTecDtos.kt
├── ui/PitTecScreen.kt
└── viewmodel/PitTecViewModel.kt

supabase/functions/
├── _shared/payment-cancellation.ts
├── _shared/payment-response.ts
├── mp-create-payment/index.ts
├── mp-cancel-payment/index.ts
└── mp-refund-payment/index.ts

supabase/migrations/
└── [new migration only if the authoritative R$ 1,00/status transition requires schema or RPC change]
```

**Structure Decision**: Manter a organização por feature no Android e as Edge Functions compartilhadas para pagamento no Supabase. A navegação de retorno será ajustada no `PitGoNavHost`; a atualização de estado continuará no backend, por meio das funções e RPCs existentes.

## Phase 0: Research

Research was completed in [research.md](./research.md). Decisions cover:

1. Reutilização da Orders API e dos campos oficiais do Pix.
2. Cancelamento/reembolso baseado no estado persistido e idempotência.
3. Valor temporário de R$ 1,00 validado no backend.
4. Snapshot completo como contrato entre backend e Android.

## Phase 1: Design

Design artifacts:

- [data-model.md](./data-model.md): entidades, campos, valores temporários e transições.
- [contracts/payment-pix-cancellation.md](./contracts/payment-pix-cancellation.md): bodies e snapshots para criação, cancelamento e reembolso.
- [quickstart.md](./quickstart.md): cenários executáveis para QR, erro, cancelamento, reembolso e concorrência.

## Implementation Approach

1. Ajustar o estado e a apresentação Pix para validar QR base64 e Copia e Cola, exibir feedback de cópia e tratar resposta incompleta sem spinner infinito.
2. Garantir que a tela de pagamento use R$ 1,00 para apresentação e que a reserva/backend rejeite valores diferentes.
3. Unificar botão cancelar, voltar e retorno do fluxo PitTec em uma operação idempotente que aguarda a atualização persistida antes da navegação.
4. Manter cancelamento pré-aprovação como `CANCELLED` e pagamento aprovado como fluxo de reembolso (`REFUND_PENDING` → `REFUNDED`/`REFUND_FAILED`).
5. Recarregar a tela inicial e limpar o estado ativo local após cancelamento confirmado, sem mascarar erro de rede ou estado concorrente.
6. Validar build, lint, migrações e os cenários do quickstart.

## Constitution Check (Post-Design)

- **Simplicidade**: PASS — nenhuma nova dependência ou arquitetura foi introduzida.
- **MVVM e estado explícito**: PASS — apresentação fica na UI; transições ficam no ViewModel/backend.
- **Segurança**: PASS — QR pode ser exibido ao cliente autenticado; tokens e dados sensíveis continuam no backend.
- **Confiabilidade**: PASS — idempotência, revisão, polling e recuperação são preservados.
- **Escopo**: PASS — somente Pix completo, cancelamento correto, retorno PitTec e valor temporário de R$ 1,00.

**Post-design gate result**: PASS.

## Complexity Tracking

Nenhuma violação da constituição identificada; esta seção não requer entradas.
