# Implementation Plan: Cadastro de empresa prestadora

**Branch**: `007-provider-registration` | **Date**: 2026-08-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-provider-registration/spec.md`

## Summary

Implementar um destino autenticado de cadastro e consulta da empresa prestadora em Jetpack
Compose/MVVM. O formulário reúne dados da empresa, um endereço brasileiro e uma lista dinâmica de
ofertas escolhidas do catálogo existente. O Android valida e mantém o rascunho apenas em memória;
um único Repository carrega o catálogo e chama uma função PostgreSQL via Supabase. Essa RPC deriva
o proprietário de `auth.uid()`, valida novamente todo o agregado, serializa concorrência por conta e
cria `providers`, `addresses` e todas as linhas de `provider_services` na mesma transação. Uma
empresa já existente é carregada como resumo em vez de ser sobrescrita.

## Technical Context

**Language/Version**: Kotlin 2.2.10; SQL/PLpgSQL em PostgreSQL 15 local (Supabase)

**Primary Dependencies**: Android SDK/AGP 9.1.1, Jetpack Compose BOM 2026.06.01, Material 3,
Lifecycle/ViewModel/StateFlow 2.10.0, Navigation Compose 2.9.8, Supabase Kotlin 3.2.6
(Auth + Postgrest), Ktor Android 3.3.1 e Kotlin Serialization 1.9.0. Nenhuma dependência nova.

**Storage**: Supabase Auth e PostgreSQL (`profiles`, `providers`, `addresses`,
`service_categories`, `services`, `provider_services`); nenhum rascunho persistido localmente

**Testing**: Sem criação de testes automatizados, conforme a constituição. Verificação proporcional
por `supabase db reset`, `supabase db lint`, `./gradlew :app:assembleDebug :app:lintDebug` e roteiro
manual em [quickstart.md](quickstart.md)

**Target Platform**: Android 7.0+ (minSdk 24), targetSdk 36, smartphones compactos e paisagem

**Project Type**: Aplicativo Android nativo com migrations/RPCs Supabase versionadas no mesmo repo

**Performance Goals**: Manter interação a 60 fps; compor ofertas por lista lazy e chave estável;
carregar cada página do catálogo uma vez por instância do destino; processar apenas uma confirmação
por vez

**Constraints**: Somente sessão autenticada; uma empresa por conta; proprietário nunca editável;
salvamento atômico e idempotente; nenhuma chamada de criação por oferta; nenhum limite de quantidade
imposto pelo app; sem geocodificação, mapa, consulta de CEP, cache local ou logs de CNPJ/payload/erro
bruto

**Scale/Scope**: Um destino protegido com formulário e resumo, três seções, cinco tabelas de domínio,
um Repository, um ViewModel, uma RPC de criação e uma lista de ofertas limitada apenas pelo catálogo
e pelos limites técnicos do transporte/banco

## Constitution Check

### Gate pré-design

- **I — Simplicidade/KISS/YAGNI: PASS**. A feature adiciona apenas um Repository porque há acesso
  real ao Supabase, uma RPC porque três conjuntos precisam de atomicidade e dois compartilhamentos
  brasileiros porque passam a ter dois consumidores reais. Não há Use Case, DataSource extra,
  framework de DI, cache ou dependência nova.
- **II — Android moderno/MVVM: PASS**. Compose, Material 3, Navigation Compose, ViewModel,
  StateFlow, Coroutines e o `PitGoAppContainer` existente permanecem como arquitetura oficial. O
  fluxo é UI -> ViewModel -> Repository -> Supabase.
- **III — Estado/experiência: PASS**. Estados mutuamente exclusivos são sealed types; a tela é
  stateless, rolável, acessível e responsiva; loading, vazio, falha, edição, envio, existente e
  sucesso são explícitos.
- **IV — Dados/segurança: PASS**. A sessão central continua sendo a única fonte de autenticação;
  `auth.uid()` é a autoridade no banco; DML direto é revogado; RLS protege leituras; validações são
  duplicadas no cliente e no banco; erros são convertidos para tipos sanitizados.
- **V — Qualidade proporcional: PASS**. Não serão gerados testes não solicitados. A implementação
  deve compilar, manter o comportamento fora do escopo e usar verificação manual de banco, fluxo,
  acessibilidade e privacidade.
- **Restrições de produto/navegação: PASS**. Rotas e intenção pós-cadastro ficam centralizadas,
  nenhuma rota carrega IDs ou PII, e os componentes Material existentes não serão recriados.

### Gate pós-design

**PASS**. Os artefatos de Phase 1 mantêm uma única fronteira de escrita, não adicionam arquitetura
especulativa e detalham ownership, transação, RLS, estados, acessibilidade, retry e limpeza de sessão.
Nenhuma exceção à constituição foi necessária.

## Project Structure

### Documentation (this feature)

```text
specs/007-provider-registration/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── navigation-ui.md
│   └── supabase-provider-registration.md
└── tasks.md                              # criado apenas por $speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/
├── core/
│   ├── app/PitGoAppContainer.kt                         # registrar Repository
│   ├── common/validation/BrazilianInputValidation.kt    # validação BR compartilhada
│   ├── navigation/PitGoRoutes.kt                        # rota e origem tipada do signup
│   ├── navigation/PitGoNavHost.kt                       # guards e transições
│   └── ui/BrazilianVisualTransformations.kt             # telefone/CNPJ/CEP compartilhados
├── features/
│   ├── profile/ui/ProfileScreen.kt                      # entrada autenticada Área da empresa
│   ├── signup/
│   │   ├── model/SignUpValidation.kt                    # delegar validação compartilhada
│   │   └── ui/SignUpScreen.kt                           # usar transformações compartilhadas
│   └── providerregistration/
│       ├── data/ProviderRegistrationDtos.kt
│       ├── model/ProviderRegistrationModels.kt
│       ├── repository/ProviderRegistrationRepository.kt
│       ├── ui/ProviderRegistrationScreen.kt
│       ├── validation/ProviderRegistrationValidation.kt
│       └── viewmodel/ProviderRegistrationViewModel.kt
└── res/values/strings.xml

supabase/
└── migrations/20260820000000_provider_registration.sql
```

**Structure Decision**: Manter a organização feature-first já adotada. A feature contém somente
model, data, repository, validation, viewmodel e UI necessários. Validação e transformação de
telefone/CNPJ saem de `signup` para `core` porque passam a ser reutilizadas de fato; não será criado
um framework genérico de formulários. O schema e a RPC ficam em uma migration Supabase, sem Edge
Function ou serviço backend adicional.
