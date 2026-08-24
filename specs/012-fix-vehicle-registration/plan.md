# Implementation Plan: Corrigir cadastro de veículos e erros por campo

**Branch**: `012-fix-vehicle-registration` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-fix-vehicle-registration/spec.md`

## Summary

Restaurar `create_vehicle` e `set_primary_vehicle` por meio de uma migration aditiva, mantendo
`auth.uid()` como única origem do proprietário e exigindo uma conta `CLIENT` ativa. As operações
continuarão transacionais e serializadas por conta, de modo que o primeiro veículo seja principal
e uma troca manual nunca deixe dois veículos ativos como principais.

No Android, manter o fluxo MVVM existente e fortalecer três pontos: normalização/validação local
com mensagens específicas, classificação sanitizada dos erros PostgREST em erro de campo ou falha
geral, e foco precedido por rolagem explícita ao primeiro campo inválido. A conclusão do onboarding
será movida para depois do salvamento confirmado; onboarding irá para Home e cadastro iniciado na
Garagem retornará à Garagem, sempre removendo o formulário concluído da pilha.

## Technical Context

**Language/Version**: Kotlin 2.2.10, Java 11, Gradle Kotlin DSL e PostgreSQL/PL/pgSQL do Supabase

**Primary Dependencies**: Android SDK, Jetpack Compose BOM 2026.06.01, Material 3,
Lifecycle/ViewModel 2.10.0, Navigation Compose 2.9.8, Coroutines, supabase-kt/Postgrest 3.2.6,
kotlinx.serialization JSON 1.9.0 e DataStore Preferences 1.2.1; nenhuma dependência nova

**Storage**: Supabase PostgreSQL (`public.vehicles`, `public.profiles`, `public.account_roles`) e
DataStore existente somente para a pendência local do onboarding

**Testing**: sem testes automatizados, conforme solicitação e constituição; validação com Supabase
CLI local, inspeção SQL, `:app:assembleDebug`, `:app:lintDebug` e fluxos manuais em dispositivo

**Target Platform**: Android minSdk 24, targetSdk 36 e compileSdk 37; smartphones em retrato e
paisagem, teclado aberto e fonte ampliada

**Project Type**: aplicativo Android nativo de módulo único com backend declarativo por migrations
Supabase, organizado por feature/core

**Performance Goals**: primeira indicação de campo inválido visível e focada em até 1 segundo;
95% dos salvamentos válidos concluem ou retornam falha acionável em até 5 segundos; uma única RPC
por toque aceito

**Constraints**: Marca e Modelo são os únicos obrigatórios; sem `service_role`, `user_id` no
payload, DML direto, retry automático, alteração de migration aplicada, nova dependência,
persistência local do formulário ou testes automatizados

**Scale/Scope**: duas RPCs, uma migration nova, treze campos do formulário existente, um fluxo de
onboarding, uma tela de Garagem e os resumos de conta/Home já existentes

## Constitution Check

*GATE inicial e pós-design: aprovado sem exceções.*

- **I — Simplicidade/KISS/YAGNI**: reutiliza tabela, RPCs, Repository, ViewModel, formulário,
  DataStore e navegação existentes. Não adiciona Use Case, DataSource, framework de DI, nova tela,
  cache ou dependência.
- **II — Android/MVVM**: Compose continua stateless; o ViewModel controla validação e submissão; o
  Repository permanece a única fronteira Supabase; Navigation Compose decide o destino pela origem.
- **III — Estado/UX**: operação, erros de campo, foco e origem são tipos explícitos no `StateFlow`.
  Todos os erros conhecidos são apresentados juntos e falhas gerais ficam em banner separado.
- **IV — Dados/segurança**: RPCs `SECURITY DEFINER` usam `search_path=''`, derivam ownership de
  `auth.uid()`, conferem papel `CLIENT`, mantêm RLS e expõem execução somente a `authenticated`.
  A classificação Android usa apenas códigos/tokens permitidos e nunca registra o erro bruto.
- **V — Qualidade proporcional**: a migration é aditiva e reproduzível; locking transacional
  protege a unicidade do principal; compilação, lint, SQL local e cenários manuais cobrem o risco
  sem criar testes não solicitados.

O design pós-Phase 1 mantém todos os gates aprovados. A migration nova é necessária porque o
estado remoto não contém as funções apesar de uma migration histórica registrá-las; alterar a
migration antiga quebraria a natureza incremental e auditável do banco.

## Project Structure

### Documentation (this feature)

```text
specs/012-fix-vehicle-registration/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── vehicle-form-ui.md
│   └── vehicle-rpcs.md
└── tasks.md                         # criado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/
├── core/
│   ├── auth/repository/SessionRepository.kt
│   ├── navigation/PitGoNavHost.kt
│   ├── preferences/VehicleOnboardingRepository.kt
│   └── vehicle/
│       ├── data/VehicleDtos.kt
│       ├── model/VehicleModels.kt
│       ├── repository/VehicleRepository.kt
│       └── validation/VehicleValidation.kt
└── features/
    ├── garage/
    │   ├── ui/VehicleFormScreen.kt
    │   └── viewmodel/
    │       ├── GarageViewModel.kt
    │       └── VehicleFormViewModel.kt
    └── onboarding/
        ├── ui/VehicleOnboardingScreen.kt
        └── viewmodel/VehicleOnboardingViewModel.kt

app/src/main/res/values/strings.xml

supabase/migrations/
└── 20260823000000_restore_vehicle_rpcs.sql
```

**Structure Decision**: manter o módulo único e as responsabilidades já consolidadas. Modelos,
DTOs, validação e Repository de veículo permanecem em `core/vehicle` porque são consumidos por
Garagem, Home, conta e onboarding. Estado e UI do formulário continuam na feature `garage`. A
única adição de backend é uma migration posterior às já aplicadas; migrations históricas não serão
editadas.

## Complexity Tracking

Nenhuma violação constitucional foi identificada.
