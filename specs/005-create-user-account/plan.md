# Implementation Plan: Criar conta de usuário

**Branch**: `005-create-user-account` | **Date**: 2026-08-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-create-user-account/spec.md`

## Summary

Implementar uma tela Compose de cadastro aberta pela Welcome, com escolha de pessoa física ou
jurídica, nome dinâmico, e-mail, telefone, CPF/CNPJ, senha e confirmação. O ViewModel manterá estado
imutável, validação/máscaras brasileiras — incluindo CNPJ alfanumérico vigente —, foco no primeiro
erro, proteção contra toque duplo, descarte e falhas tipadas. Um `SignUpRepository` usará Auth e
PostgREST do cliente Supabase existente: o signup envia metadata normalizada, um trigger
transacional cria `public.profiles` com o mesmo UUID, e uma consulta/recuperação idempotente garante
o perfil antes de abrir a Home. Migration versionada aplicará checks, uniques, grants e RLS de
acesso somente ao próprio perfil; nenhuma chave administrativa entrará no Android.

## Technical Context

**Language/Version**: Kotlin 2.2.10 integrado ao projeto; bytecode Java/Kotlin 11; JDK 17 para o
build; SQL PostgreSQL na migration Supabase

**Primary Dependencies**: Android Gradle Plugin 9.1.1, Gradle 9.3.1, Compose BOM 2026.06.01,
Material 3, Lifecycle/ViewModel 2.10.0, Navigation Compose 2.9.8; `supabase-kt`, `auth-kt` e
`postgrest-kt` 3.2.6; Ktor Android 3.3.1; Kotlin Serialization plugin 2.2.10 e JSON runtime 1.9.0;
desugaring 2.1.5 já configurado para minSdk 24

**Storage**: Supabase Auth (`auth.users`) e PostgreSQL `public.profiles` com UUID PK/FK 1:1,
trigger, checks de normalização/DV/XOR, uniques, RLS e grants; metadata Auth somente como entrada
não confiável de criação/recuperação; nenhuma persistência local nova

**Testing**: `supabase db reset`/lint, build/lint Android e validação manual end-to-end conforme
`quickstart.md`; nenhum teste automatizado, conforme solicitação e constituição

**Target Platform**: aplicativo Android nativo single-module, minSdk 24, targetSdk 36 e compileSdk
37; smartphones, tema claro/escuro, fonte até 200%, teclado e TalkBack

**Project Type**: aplicativo mobile Android com backend gerenciado Supabase e migrations SQL
versionadas no mesmo repositório

**Performance Goals**: feedback visual em até 1 segundo após envio válido; UI responsiva sem I/O na
Main Thread; no máximo uma operação remota simultânea por tela; uma consulta de confirmação e, só
quando necessário, uma inserção idempotente de recuperação

**Constraints**: Confirm email desabilitado e senha mínima 8 no ambiente; exatamente CPF ou CNPJ;
CNPJ numérico e alfanumérico oficial; zero senha/token/documento completo ou Throwable remoto em
logs; somente chave publishable/anon no APK; Home somente com sessão e perfil confirmados; RLS do
próprio UUID; sem login visível, recuperação de senha, login social, edição de perfil, cache, DI ou
service-role

**Scale/Scope**: uma tela/rota, dois tipos de pessoa, seis entradas de texto visíveis por vez, um
Repository, um ViewModel, uma tabela e uma migration inicial; sem listas, paginação, uploads,
Realtime ou sincronização em background

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Gate evaluation before research

- **Simplicidade e YAGNI — PASS**: o escopo prevê somente cadastro, perfil e navegação. Login,
  recuperação, edição, Hilt/Koin, cache, Use Case pass-through e módulos Supabase sem uso ficam fora.
- **Android nativo moderno e MVVM — PASS**: Compose apenas renderiza/emitirá eventos; ViewModel
  controla estado/validação; Repository encapsula Auth/PostgREST; migration encapsula integridade e
  segurança definitivas.
- **Estado previsível — PASS**: Editing, Submitting, Failure e Success serão exclusivos; um único
  campo de documento impede CPF/CNPJ simultâneos e senha não é persistida.
- **Dependências justificadas — PASS**: Auth cria/persiste sessão; PostgREST confirma e recupera o
  perfil; Serialization tipa o payload/resposta. Cada módulo possui uso atual demonstrado e mantém
  as versões compatíveis já validadas na feature 004.
- **Dados, segurança e falhas — PASS**: validação local e SQL, trigger transacional, confirmação e
  recuperação idempotente, grants mínimos, RLS, chaves de cliente e erros sanitizados cobrem os
  riscos sem service-role no app.
- **Material, responsividade e acessibilidade — PASS**: a tela reutiliza tema/tokens e componentes
  Material, uma rolagem com IME, foco/erro, alvos mínimos, semântica e layout adaptativo.
- **Qualidade proporcional — PASS**: nenhum teste automatizado é criado; migration reset/lint,
  build/lint, matriz manual e inspeção de segurança são proporcionais ao risco.

## Project Structure

### Documentation (this feature)

```text
specs/005-create-user-account/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── account-registration.md
│   └── profiles-schema.md
└── tasks.md                         # criado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
build.gradle.kts                             # aplica plugin Serialization somente no app

gradle/
└── libs.versions.toml                       # Auth, PostgREST e Serialization alinhados

app/
├── build.gradle.kts                         # plugin e dependências necessárias
└── src/main/
    ├── java/com/trustarx/pitgo/
    │   ├── core/
    │   │   ├── navigation/
    │   │   │   ├── PitGoNavHost.kt         # Welcome → SignUp → Home/back stack
    │   │   │   └── PitGoRoutes.kt          # rota sign-up centralizada
    │   │   └── network/
    │   │       └── SupabaseClientProvider.kt # instala Auth e Postgrest uma vez
    │   └── features/signup/
    │       ├── data/
    │       │   └── ProfileDtos.kt           # payload/row serializáveis internos
    │       ├── model/
    │       │   ├── SignUpModels.kt          # draft, estado, erros e resultados
    │       │   └── SignUpValidation.kt      # normalização, CPF/CNPJ/e-mail/telefone
    │       ├── repository/
    │       │   └── SignUpRepository.kt      # Auth + perfil + recovery + error mapping
    │       ├── ui/
    │       │   ├── SignUpScreen.kt          # Route/Screen e componentes locais
    │       │   └── SignUpVisualTransformations.kt
    │       └── viewmodel/
    │           └── SignUpViewModel.kt
    └── res/
        ├── drawable/                         # back e visibilidade reutilizáveis
        └── values/strings.xml                # textos, erros, estados e a11y

supabase/
├── config.toml                               # configuração local gerada pelo CLI
└── migrations/
    └── <timestamp>_create_profiles.sql       # schema, checks, trigger, grants e RLS
```

**Structure Decision**: manter o módulo Android único e criar a feature `signup` apenas com os
grupos que possuem responsabilidade atual. O Repository recebe o cliente por composição simples,
sem framework DI nem Use Case. Validação é feature-local; DTOs não vazam para UI. A migration é a
fonte versionada de backend e fica fora de `app/`. O callback `onCreateAccount` já existente na
Welcome será conectado no NavHost, sem refatorar a tela. Helpers visuais continuam privados; o
vetor de back pode ser renomeado para uso genérico em vez de duplicado.

### Post-design constitution re-check

- `research.md` resolveu módulos/versões, API de sessão 3.2.6, fluxo híbrido trigger + recovery,
  schema, CNPJ alfanumérico, RLS/grants, erros sanitizados, estado, UI, navegação e migrations.
- `data-model.md` separa rascunho, request normalizado, metadata não confiável, identidade, perfil,
  DTOs, resultados e estados sem modelos ou campos futuros.
- Os contratos fixam pré-requisitos Auth, sequência Repository, idempotência, esquema exato
  `profiles`, names de constraints, trigger e políticas verificáveis.
- `quickstart.md` cobre migration, build/lint, PF/PJ, CNPJ legado/novo, validação, IME/a11y,
  conflitos, rede, rollback, recovery, back stack, RLS e ausência de vazamento.
- Nenhuma UI acessa Supabase; nenhum service-role, Throwable remoto, senha persistida, módulo
  desnecessário, teste não solicitado ou permissão nova é introduzido.

**Resultado pós-design**: PASS. PostgREST/Serialization e a policy INSERT são complexidade
justificada exclusivamente pela recuperação idempotente esclarecida; não há violação constitucional.

## Complexity Tracking

Nenhuma violação constitucional requer exceção.
