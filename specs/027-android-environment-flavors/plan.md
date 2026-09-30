# Implementation Plan: Android Environment Flavors

**Branch**: `027-android-environment-flavors` | **Date**: 2026-09-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/027-android-environment-flavors/spec.md`

## Summary

O aplicativo terá uma única dimensão Gradle chamada `environment`, com os flavors `dev`, `homol` e `prod`. Cada variante carregará sua configuração de um arquivo local separado e ignorado pelo Git; o build falhará quando uma configuração obrigatória estiver ausente ou inválida. O `applicationId` continuará igual nos três flavors porque apenas um ambiente deve estar instalado por vez.

O código receberá uma identidade de ambiente derivada do flavor e uma abstração de gateway de pagamento. `dev` usará um gateway simulado que percorre os estados do checkout sem chamar Mercado Pago ou Edge Function de pagamento. `homol` e `prod` usarão o gateway integrado e o mesmo backend Supabase, com as mesmas credenciais públicas Mercado Pago. Secrets privados permanecerão nos projetos Supabase, nunca empacotados no APK.

## Technical Context

**Language/Version**: Kotlin, Java 11, Gradle Kotlin DSL

**Primary Dependencies**: Android Gradle Plugin já adotado, Jetpack Compose/Material 3, ViewModel, Coroutines, DataStore, Supabase Kotlin SDK, Mercado Pago SDK e MapLibre já presentes no módulo `app`

**Storage**: DataStore e armazenamento local gerenciado pelo Supabase Auth; nenhum banco local novo será criado

**Testing**: Compilação das seis combinações flavor/build type necessárias e validação manual nos dispositivos; testes automatizados não serão adicionados sem solicitação explícita, conforme a constituição

**Target Platform**: Android API 24+, target SDK 36, compile SDK 37

**Project Type**: Aplicativo móvel Android nativo

**Performance Goals**: Seleção do gateway e leitura da identidade sem trabalho de rede; simulação de pagamento deve responder no fluxo da UI sem bloquear a Main Thread

**Constraints**: Uma instalação por vez; nenhum secret privado no APK, código versionado ou log; falha de configuração antes de operação financeira; `dev` não pode depender de configuração remota para virar integrado

**Scale/Scope**: Um módulo Android existente, dois backends Supabase (Dev separado; Homol/Prod compartilhados), dois comportamentos de pagamento e os recursos compartilhados de sessão, preferências e diagnóstico

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Simplicidade e entrega orientada ao requisito | Uma dimensão e três flavors resolvem o requisito sem aplicativos ou módulos paralelos. | PASS |
| II. Android nativo moderno com MVVM | A integração permanece no módulo existente, com gateway/repository e estado exposto por ViewModel/StateFlow. | PASS |
| III. Estado previsível e experiência consistente | A identidade do ambiente é imutável por build; a troca invalida estado incompatível antes da sessão ser usada. | PASS |
| IV. Dados, segurança e falhas explícitas | Arquivos locais fornecem apenas configuração client-safe; secrets de backend ficam no Supabase; validação ocorre antes da operação. | PASS |
| V. Qualidade proporcional e evolução sustentável | Reutiliza o checkout e o cliente Supabase existentes; não cria testes, camadas ou persistência sem necessidade atual. | PASS |

## Project Structure

### Documentation (this feature)

```text
specs/027-android-environment-flavors/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── build-configuration.md
│   └── payment-environment.md
└── tasks.md                 # criado pelo $speckit-tasks
```

### Source Code (repository root)

```text
app/
├── build.gradle.kts                         # dimensão, flavors, arquivos de configuração e BuildConfig
├── src/
│   ├── main/java/com/trustarx/pitgo/
│   │   ├── core/environment/
│   │   │   ├── EnvironmentConfig.kt
│   │   │   ├── EnvironmentStateRepository.kt
│   │   │   └── EnvironmentDiagnostics.kt
│   │   ├── core/app/PitGoAppContainer.kt    # injeta gateway selecionado
│   │   └── features/payment/repository/
│   │       ├── PaymentGateway.kt
│   │       ├── IntegratedPaymentGateway.kt
│   │       ├── SimulatedPaymentGateway.kt
│   │       └── PaymentRepository.kt
│   ├── main/res/                            # recursos comuns
│   ├── dev/res/values/strings.xml           # nome/identificação dev
│   ├── homol/res/values/strings.xml         # nome/identificação homol
│   └── prod/res/values/strings.xml          # nome/identificação prod
└── ...
config/
├── dev.properties                           # ignorado, fornecido localmente
├── homol.properties                          # ignorado, fornecido localmente
└── prod.properties                           # ignorado, fornecido localmente

supabase/
├── functions/                               # mesmo código versionado, implantado por projeto
└── migrations/                              # aplicado separadamente em cada backend
```

**Structure Decision**: Manter um único módulo `app` e organizar a nova responsabilidade em `core/environment` e `features/payment/repository`. Os flavors fornecem somente constantes de build e recursos de identificação; regras de pagamento ficam em gateways substituíveis pelo container existente. Dev usa um projeto Supabase próprio; Homol e Prod compartilham o projeto de produção, sem duplicar funções ou migrations no repositório.

## Phase 0 — Research

As decisões e fontes locais estão em [research.md](./research.md). Os pontos pesquisados foram: compatibilidade da configuração atual do Gradle, composição de flavors com o `applicationId` existente, limite de segurança entre configuração pública e secrets, seleção do gateway de pagamento e invalidamento do estado persistido ao trocar de backend.

## Phase 1 — Design

Após aprovação simulada, `features/pittec/repository/SimulatedPitTecFlowRepository.kt` manterá um snapshot local e transições explícitas para deslocamento, atendimento e conclusão. O ViewModel reutilizará a apresentação existente e selecionará o estado local somente em dev. A simulação não mutará o despacho remoto nem enviará UUIDs simulados a RPCs que exigem pagamento real. Os cenários locais também estarão disponíveis sem uma solicitação remota prévia, evitando deixar um atendimento real aguardando pagamento.

Cada arquivo terá `environmentId` e `supabaseProjectRef` esperados. A tarefa Gradle `:app:validateEnvironmentIsolation` verificará os três arquivos em conjunto: correspondência de identidade com flavor, correspondência entre project ref e host canônico `<ref>.supabase.co`, Dev separado e todas as propriedades públicas de Homol iguais às de Prod. Será obrigatória antes da distribuição. Builds individuais validarão apenas seu arquivo; a validação conjunta confere o espelhamento.

- [data-model.md](./data-model.md) define a identidade do ambiente, configuração client-safe, modo de pagamento e escopo do estado persistido.
- [contracts/build-configuration.md](./contracts/build-configuration.md) define os arquivos, propriedades e critérios de falha do build.
- [contracts/payment-environment.md](./contracts/payment-environment.md) define o contrato comum dos gateways e os limites entre simulação e integração.
- [quickstart.md](./quickstart.md) define a validação executável das variantes, isolamento de backend e fluxo de pagamento.

## Complexity Tracking

Nenhuma violação da constituição foi identificada. A abstração `PaymentGateway` tem uso atual demonstrável porque separa os dois comportamentos exigidos pela feature e impede que a variante `dev` alcance o gateway integrado.

## Post-Design Constitution Check

PASS. O design mantém MVVM e o container existente, centraliza a seleção de dependências, valida configuração antes de distribuir uma variante, não empacota secrets privados e preserva o fluxo integrado fora de `dev`. Não há dependência nova planejada.
