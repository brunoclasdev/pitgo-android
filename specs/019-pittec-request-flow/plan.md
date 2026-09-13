# Implementation Plan: Fluxo correto de solicitação do PitTec

**Branch**: `019-pittec-request-flow` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/019-pittec-request-flow/spec.md`

## Summary

Corrige o fluxo real de solicitação do PitTec: remove o card de estimativa fictícia (resquício do
protótipo mockado `017`) que hoje dispara a busca automática sem cobrar nada e sem nunca revelar ao
cliente qual PitTec foi encontrado; insere um card "PitTec encontrado" (nome + distância) assim que
um candidato é localizado, permitindo confirmar ou recusar antes de qualquer cobrança; e move o
pagamento real (Pix/cartão, `018`) do ponto onde ele hoje é acionado (antes mesmo de abrir o mapa,
a partir da Home) para um novo card de valores mostrado **depois** que o prestador aceita, exigindo
confirmação e aprovação do pagamento antes de o serviço ser considerado iniciado. Backend
(`private.pittec_dispatch_snapshot`) passa a expor nome e distância do candidato atual ao cliente
antes do aceite (hoje só expõe ao próprio prestador); o gate de pagamento sai de
`start_pittec_dispatch` e entra em `arrive_pittec_appointment`.

## Technical Context

**Language/Version**: Kotlin (Android, versão estável já usada no projeto)

**Primary Dependencies**: Jetpack Compose, Material Design 3, ViewModel + StateFlow, Navigation
Compose, Supabase Kotlin client (Postgrest RPC + Realtime) — todas já em uso; nenhuma dependência
nova.

**Storage**: Postgres (Supabase) — `service_searches`, `appointments`, `private.dispatch_candidates`,
`payments` (todas já existentes, `018`/`015`). Nenhuma tabela nova.

**Testing**: Nenhum teste automatizado solicitado (governança do projeto) — build/lint e roteiro
manual (`quickstart.md`) são os gates de qualidade.

**Target Platform**: Android nativo (API mínima já definida no projeto).

**Project Type**: Mobile app com backend compartilhado (Supabase) — mesmo projeto usado pelo app
iOS; mudanças de RPC afetam os dois clientes.

**Performance Goals**: Sem meta nova além das já vigentes no app (resposta de RPC em segundos,
Realtime com fallback por polling já existente em `PitTecViewModel`).

**Constraints**: A tarifa cobrada continua sendo o valor fixo de plataforma já definido em `018`
(não varia por prestador/distância, `016`). Nenhuma migration deve alterar comportamento de outras
modalidades (guincho móvel `013` permanece com seu próprio gate de pagamento, inalterado).

**Scale/Scope**: Um fluxo de cliente (tela do PitTec) e três funções SQL ajustadas/estendidas no
backend compartilhado; nenhuma tela nova além dos dois cards já descritos na spec.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Simplicidade/KISS/YAGNI**: PASS — reaproveita integralmente a tela, o ViewModel, o Repository
  de PitTec e a tela/ViewModel/Repository de pagamento já existentes (`018`); nenhuma camada, padrão
  ou dependência nova é introduzida. O card "PitTec encontrado" reaproveita o mesmo tipo
  `PitTecProvider` já usado pós-aceite; a busca sequencial e a expansão de raio existentes não são
  reescritas.
- **II. Android nativo moderno com MVVM**: PASS — Compose + ViewModel + StateFlow + Repository,
  fluxo UI → ViewModel → Repository inalterado; nenhum Use Case necessário (a lógica de negócio
  nova é uma extensão pontual do estado já modelado em `PitTecDispatchPhase`).
- **III. Estado previsível**: PASS — os dois novos momentos ("candidato encontrado, aguardando
  confirmação" e "valores confirmados, aguardando pagamento") são modelados como novos casos
  explícitos de `PitTecDispatchPhase` (sealed interface já existente), não como Booleans soltos.
- **IV. Dados, segurança e falhas explícitas**: PASS — o valor cobrado nunca é inventado no client
  (vem do mesmo fluxo real de pagamento de `018`); a guarda de pagamento migra de uma função SQL
  `SECURITY DEFINER` para outra, mantendo o mesmo padrão de verificação server-side (nunca apenas
  client-side). Nenhum dado sensível novo é logado.
- **V. Qualidade proporcional**: PASS — o card de estimativa fictícia (código morto/resquício de
  `017`) é removido diretamente por estar relacionado ao escopo desta mudança; nenhuma refatoração
  maior e não relacionada é feita.

Nenhuma violação — sem entradas em Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/019-pittec-request-flow/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── pittec-request-flow-rpcs.md
└── tasks.md             # Phase 2 ($speckit-tasks, not created here)
```

### Source Code (repository root)

```text
supabase/
└── migrations/
    └── <timestamp>_pittec_request_flow.sql   # extends pittec_dispatch_snapshot (campo
                                               # `candidate` pré-aceite), move o gate
                                               # PAYMENT_REQUIRED de start_pittec_dispatch
                                               # para arrive_pittec_appointment

app/src/main/java/com/trustarx/pitgo/
├── features/pittec/
│   ├── model/PitTecModels.kt                 # + PitTecCandidate; + fases de
│   │                                          #   PitTecDispatchPhase (CandidateFound,
│   │                                          #   ConfirmingValues)
│   ├── data/PitTecDtos.kt                    # + mapeamento do novo campo `candidate`
│   ├── repository/PitTecRepository.kt        # decodifica `candidate`; nenhuma RPC nova
│   │                                          #   chamada por este repository
│   ├── viewmodel/PitTecViewModel.kt          # remove o `initialRequestId` vindo de um
│   │                                          #   pagamento prévio; adiciona
│   │                                          #   confirmCandidate()/declineCandidate()/
│   │                                          #   confirmValues(); só chama onAccepted()
│   │                                          #   depois do pagamento aprovado
│   └── ui/
│       ├── PitTecScreen.kt                   # remove EstimateGatedDispatchOverlay/
│       │                                      #   EstimateCard fictício; adiciona os
│       │                                      #   cards "PitTec encontrado" e "Valores"
│       └── PitTecDispatchComponents.kt        # + FoundCandidateCard, + ValuesCard
│                                              #   (reaproveitando os componentes de
│                                              #   pagamento de `018` dentro do segundo)
├── features/payment/                         # reaproveitado como está (nenhuma mudança
│                                              #   de contrato/UI própria)
└── core/navigation/PitGoNavHost.kt           # remove a navegação Home → PaymentCheckout
                                               #   → PitTec; Home passa a ir direto para
                                               #   PitTec (pagamento agora é interno à tela)
```

**Structure Decision**: Nenhuma estrutura nova — tudo dentro de `features/pittec` (já existente) e
uma única migration SQL aditiva compartilhada. `features/payment` não muda internamente; só muda
quem o invoca e quando.

## Complexity Tracking

*Sem violações — seção não aplicável.*
