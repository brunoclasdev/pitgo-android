# Phase 1 Data Model: Fluxo correto de solicitação do PitTec

## Status: extensão aditiva de entidades já existentes

Nenhuma tabela nova. `payments`/`saved_cards` (`018`) e `service_searches`/`appointments`/
`private.dispatch_candidates` (`015`) são reaproveitados como estão — apenas um campo novo na
função de snapshot e o ponto onde a guarda de pagamento é verificada.

## `private.pittec_dispatch_snapshot` — novo campo `candidate`

| Campo | Tipo | Quando aparece |
|---|---|---|
| `candidate.provider_name` | `text` | `appointment.status = 'PENDING'` e o chamador é o cliente dono da busca. |
| `candidate.approximate_distance_meters` | `integer`, arredondado a 500m | Mesma condição acima. |

Fonte dos dados: a própria função já lê `candidate_row` (via `search_row.current_candidate_id`) e o
nome do prestador (via `appointment_row.provider_id`) para montar `offer` (hoje só visível ao
prestador) — `candidate` é a mesma leitura, exposta ao cliente em vez do prestador, só enquanto
`PENDING` (nunca depois de `ACCEPTED`, quando o campo `provider`, já existente, passa a valer).

`candidate` e `provider` são mutuamente exclusivos por status — nunca preenchidos ao mesmo tempo:

```text
PENDING          → candidate preenchido, provider = null
ACCEPTED/IN_PROGRESS/COMPLETED/CANCELLED → provider preenchido, candidate = null
qualquer outro   → ambos null
```

## Guarda de pagamento — realocada, não nova

| RPC | Antes (`015`/`018`) | Depois (`019`) |
|---|---|---|
| `start_pittec_dispatch` | Exige `payments.request_id = p_search_id` `APPROVED` | **Sem guarda** — busca é gratuita para iniciar |
| `arrive_pittec_appointment` | Sem guarda | Exige `payments.request_id = <search_id da appointment>` `APPROVED` antes de permitir `ACCEPTED → IN_PROGRESS` |

`payments.request_id` continua sendo o mesmo `search_id` usado desde o início da busca — nenhuma
mudança de chave, só de quando a cobrança acontece em relação ao ciclo de vida da busca.

## Modelos Kotlin (client) — extensões de `PitTecModels.kt`

```kotlin
/** Candidato localizado, ainda não aceito pelo prestador — visível só ao cliente. */
data class PitTecCandidate(
    val providerName: String,
    val approximateDistanceMeters: Int,
) {
    fun isValid() = providerName.isNotBlank() && approximateDistanceMeters >= 0
}
```

`PitTecDispatchSnapshot` ganha `val candidate: PitTecCandidate? = null`, mutuamente exclusivo com o
`provider` já existente (nunca ambos não nulos ao mesmo tempo — refletindo a regra do backend acima).

`PitTecDispatchPhase` (sealed interface já existente) ganha dois novos casos, substituindo o
`Active` genérico nesses dois momentos específicos (o `Active` continua cobrindo os demais estados,
como `SEARCHING` sem candidato ainda):

```kotlin
/** `AWAITING_PROVIDER` com um candidato específico — aguardando o cliente confirmar interesse. */
data class CandidateFound(
    val snapshot: PitTecDispatchSnapshot, // snapshot.candidate garantidamente não nulo aqui
    val pending: PitTecPending? = null,
) : PitTecDispatchPhase

/** `ACCEPTED` — sinaliza que a tela de pagamento (`018`) deve ser aberta antes de prosseguir. */
data class ConfirmingValues(
    val snapshot: PitTecDispatchSnapshot, // snapshot.provider garantidamente não nulo aqui
) : PitTecDispatchPhase
```

**Reuso de `018`, sem duplicar UI/ViewModel de pagamento**: em vez de embutir estado de pagamento
dentro de `PitTecDispatchPhase`, `ConfirmingValues` é só o sinal para `PitTecScreen`/`PitTecRoute`
navegar para a mesma rota `PitGoRoutes.Payment` (`PaymentCheckoutRoute`/`PaymentCheckoutViewModel`,
inalterados) já usada hoje — a diferença é **apenas o momento do disparo**: hoje a navegação para
`PitGoRoutes.Payment` acontece a partir da Home, antes da busca; a partir desta spec, acontece a
partir da própria tela do PitTec, quando `ConfirmingValues` é observado. Em `onApproved`, a
navegação segue para `AppointmentTracking` como já acontece hoje. A navegação automática anterior
(disparada assim que `status` entrava em `ACCEPTED`/`IN_PROGRESS`) passa a esperar por esse ciclo
completo em vez de pular direto para o acompanhamento.

## Máquina de estados (visão do cliente)

```text
None ──(Solicitar)──▶ Locating ──▶ StartingSearch ──▶ Active(SEARCHING)
Active(SEARCHING) ──(candidato localizado)──▶ CandidateFound
CandidateFound ──(cliente confirma)──▶ Active(AWAITING_PROVIDER, mesmo candidato, aguardando prestador)
CandidateFound ──(cliente recusa)──▶ Active(SEARCHING)  [cancela o appointment atual; advance tenta o próximo]
Active(AWAITING_PROVIDER) ──(prestador aceita)──▶ ConfirmingValues
Active(AWAITING_PROVIDER) ──(prestador recusa/expira)──▶ Active(SEARCHING)  [já existente]
ConfirmingValues ──(pagamento aprovado)──▶ navega para AppointmentTracking (já existente)
ConfirmingValues ──(cliente desiste)──▶ cancela o appointment (mesma regra de cancelamento pós-aceite já existente)
Active(*) ──(nenhum prestador disponível)──▶ NoProvider  [já existente]
```

Nenhuma transição nova além das listadas — `NoProvider`/`Terminal`/`Failure` (já existentes)
continuam alcançáveis a partir de qualquer ponto, sem mudança.
