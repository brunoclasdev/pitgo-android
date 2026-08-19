# Data Model: Configurar Supabase

## Overview

Esta feature não cria dados de negócio nem persistência. O modelo descreve a transformação de dois
inputs externos efêmeros em uma configuração validada de build e, depois, em um único cliente
runtime. Os tipos são conceituais: somente o provider do cliente será uma classe de produção; não
serão criados DTOs ou modelos redundantes para representar etapas exclusivas do Gradle.

## SupabaseBuildInput

Valores brutos fornecidos pelo ambiente de preparação.

| Field | Type | Required | Sources |
|---|---|---|---|
| `supabaseUrl` | String | Yes | Gradle project property; fallback local ignorado |
| `supabaseKey` | String | Yes | Gradle project property; fallback local ignorado |

Regras:

- nomes são exatos e case-sensitive;
- uma fonte Gradle presente tem precedência sobre o fallback local;
- valores são efêmeros e nunca pertencem a arquivo rastreado, log ou mensagem de erro;
- espaços externos são removidos; conteúdo interno não é transformado.

## ValidatedSupabaseConfig

Resultado conceitual da validação durante a configuração do build.

| Field | Type | Rules |
|---|---|---|
| `url` | Absolute HTTPS URI | Não opaca; host obrigatório; sem user-info |
| `key` | Client-safe String | Publishable atual ou JWT legado com papel `anon` |
| `keyKind` | `PUBLISHABLE` \| `LEGACY_ANON` | Derivado apenas para classificação no build; não precisa chegar ao runtime |

Invariantes:

- uma configuração possui exatamente uma URL e uma chave;
- `sb_secret_`, `service_role`, papel diferente, múltiplos roles e formato desconhecido são
  inválidos;
- JWT legado é somente decodificado para classificar `role`; assinatura não é validada;
- nenhum valor inválido gera `BuildConfig`, APK ou AAB;
- validação é sintática/local e não afirma que projeto ou chave existem no servidor.

## Runtime configuration

Dois campos imutáveis são gerados para todas as variantes:

| Field | Source | Visibility |
|---|---|---|
| `BuildConfig.SUPABASE_URL` | `ValidatedSupabaseConfig.url` | Interna ao módulo do app |
| `BuildConfig.SUPABASE_KEY` | `ValidatedSupabaseConfig.key` | Interna ao módulo do app; extraível do APK por design |

Código de UI, ViewModel e futuras features não devem acessar esses campos diretamente. A fronteira
runtime é o `SupabaseClientProvider`.

## SupabaseClientProvider

| Field | Type | Rules |
|---|---|---|
| `client` | `SupabaseClient` | Lazy, read-only, exatamente uma instância por processo |

Configuração do cliente:

- usa exclusivamente URL e chave validadas;
- instala zero plugins nesta feature;
- cria o engine requerido sem executar request;
- não persiste sessão, não autentica e não expõe URL/chave como API paralela;
- futuras Repositories recebem o cliente por construtor.

## State transitions

```text
RAW_INPUT
   ├── missing/invalid ──> BUILD_REJECTED
   └── valid ───────────> VALIDATED_CONFIG ──> BUILD_CONFIG_GENERATED
                                                   │
                                                   └── first provider access ──> CLIENT_READY
                                                                               │
                                                                               └── later access ──> SAME_CLIENT
```

- `BUILD_REJECTED` é terminal para a invocação Gradle e não produz app.
- `CLIENT_READY` não significa conexão verificada; nenhuma chamada remota ocorre.
- Não existem estados de loading, erro de rede, usuário, sessão ou dados nesta feature.

## Relationships

```text
ExecutionEnvironment 1 ─── 1 SupabaseBuildInput
SupabaseBuildInput    1 ─── 0..1 ValidatedSupabaseConfig
ValidatedSupabaseConfig 1 ─── 1 generated runtime configuration
RuntimeConfig        1 ─── 1 SupabaseClient per process
```

## Security boundary

- Publishable/anon identifica um cliente público; não é cofre nem autorização de usuário.
- A chave cliente pode ser recuperada do APK e não pode proteger dados sozinha.
- RLS e autenticação do backend permanecem obrigatórias nas features que acessarem dados.
- Secret/service-role possui privilégio elevado e nunca pode atravessar `BUILD_REJECTED`.
