# Implementation Plan: Acompanhamento pós-pagamento e cobrança PitTec

**Branch**: `[025-pittec-payment-tracking]` | **Date**: 2026-09-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/025-pittec-payment-tracking/spec.md`

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

O fluxo deve calcular no backend o valor do serviço mais o adicional por quilometragem, aplicar 40% e taxas configuráveis somente ao total do cliente, e manter projeções financeiras separadas entre cliente e PitTec. Após o pagamento aprovado, ambos devem acompanhar o deslocamento, a chegada e o início do serviço com estados sincronizados. A implementação aproveitará o despacho PitTec, o snapshot protegido por papel, o checkout Mercado Pago Orders e o acompanhamento de localização já existentes.

## Technical Context

**Language/Version**: Kotlin/JVM conforme catálogo Gradle atual; SQL PostgreSQL e TypeScript nas Edge Functions

**Primary Dependencies**: Android SDK, Jetpack Compose, ViewModel/StateFlow, Supabase PostgREST/Realtime, Mercado Pago Orders, MapLibre/OSM conforme módulos existentes

**Storage**: PostgreSQL/Supabase; propostas congeladas em `appointments`; pagamentos em `payments`; regras em `pittec_pricing_rules`

**Testing**: compilação `:app:assembleDebug`, validação SQL/RPC e roteiro manual em `quickstart.md`; testes automatizados não serão adicionados sem solicitação explícita da constituição

**Target Platform**: Android suportado pelo projeto e Supabase remoto

**Project Type**: aplicativo mobile Android nativo com backend Supabase

**Performance Goals**: snapshot e transições refletidos aos participantes em até 10 segundos; cálculo de preço não deve depender de chamada externa de rota

**Constraints**: servidor é autoridade para preço e pagamento; cliente não recebe componentes financeiros do PitTec; localização precisa indicar estado desatualizado; valores em BRL e centavos

**Scale/Scope**: uma solicitação PitTec ativa por cliente e no máximo um atendimento ativo por PitTec, preservando os limites já existentes

## Constitution Check

*GATE: PASS*

- **Simplicidade**: reutiliza os módulos `pittec`, `mobiledispatch`, `payment` e `appointmenttracking`; adiciona somente campos, projeções e ajustes necessários.
- **Android/MVVM**: UI continua renderizando estado; ViewModels chamam Repositories; novos dados de preço serão mapeados nos DTOs/modelos existentes.
- **Estado previsível**: estados de proposta, pagamento, chegada e acompanhamento permanecem explícitos e idempotentes.
- **Dados e segurança**: cálculo e autoridade de cobrança permanecem no backend; RLS/RPCs impedem vazamento entre cliente e PitTec; logs não conterão dados sensíveis.
- **Qualidade proporcional**: a validação usa compilação, SQL/RPC e roteiro manual; não cria testes automatizados sem solicitação.

## Project Structure

### Documentation (this feature)

```text
specs/025-pittec-payment-tracking/
├── plan.md              # This file ($speckit-plan command output)
├── research.md          # Phase 0 output ($speckit-plan command)
├── data-model.md        # Phase 1 output ($speckit-plan command)
├── quickstart.md        # Phase 1 output ($speckit-plan command)
├── contracts/           # Phase 1 output ($speckit-plan command)
└── tasks.md             # Phase 2 output ($speckit-tasks command - NOT created by $speckit-plan)
```

### Source Code (repository root)
```text
app/src/main/java/com/trustarx/pitgo/features/
├── mobiledispatch/       # snapshot, despacho e estado do cliente
├── pittec/               # proposta, aceite e projeção do PitTec
├── payment/              # checkout e status do pagamento
├── appointmenttracking/  # mapa e estado pós-pagamento
└── providertracking/     # publicação da localização do PitTec

supabase/migrations/
└── 20260923000000_pittec_pricing_distance_and_total.sql

supabase/functions/
└── mp-create-payment/    # permanece usando a reserva server-side

specs/025-pittec-payment-tracking/
├── research.md
├── data-model.md
├── contracts/pricing-and-dispatch.md
└── quickstart.md
```

**Structure Decision**: manter a organização por feature do aplicativo e as funções SQL versionadas no Supabase. A migração nova corrigirá a autoridade do valor e acrescentará a distância congelada; o Android apenas consumirá projeções de papel e não calculará a cobrança.

## Phase 0: Research

1. Reutilizar `pittec_pricing_rules` e `private.pittec_calculate_pricing`, ampliando a regra com a tarifa por quilômetro.
2. Corrigir a sobrescrita posterior de `reserve_order_payment`, que atualmente usa `quoted_price` para PitTec em vez de `customer_total_amount`.
3. Ajustar o snapshot para não enviar markup/taxas ao cliente e para enviar o detalhamento permitido ao PitTec.
4. Remover o valor fixo de `1.00` do fluxo real do checkout PitTec; o backend continua sendo a autoridade final.

## Phase 1: Design

1. Criar a migração `20260923000000_pittec_pricing_distance_and_total.sql` com colunas de distância/preço congelado, regra configurável e funções atualizadas.
2. Atualizar DTOs e modelos PitTec para diferenciar a visão do cliente da visão do PitTec e validar valores positivos, moeda, versão e prazo.
3. Atualizar a tela de proposta do cliente para exibir somente o total; atualizar a tela do PitTec para exibir serviço, quilometragem e valor do PitTec.
4. Atualizar o checkout para usar o total recebido da proposta e preservar a validação de contexto, versão e expiração.
5. Integrar o snapshot ao acompanhamento pós-pagamento, preservando localização, chegada, início e retomada já previstos.
6. Validar compilação, contratos, SQL e os cenários do quickstart nos quatro dispositivos Android conectados.

## Constitution Check — pós-design

*GATE: PASS*

- Não há nova camada arquitetural ou dependência externa necessária.
- A fórmula financeira e a autoridade do pagamento permanecem no backend.
- As projeções por papel minimizam exposição de dados.
- O Android permanece em Kotlin/Compose/MVVM e renderiza estados explícitos.
- O plano não inclui testes automatizados não solicitados nem altera fluxos fora do escopo.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
Nenhuma violação da constituição foi identificada; esta seção não se aplica.
