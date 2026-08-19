# Research: Configurar Supabase

## 1. SDK, versões e módulos mínimos

**Decision**: usar `supabase-kt` 3.2.6 e `ktor-client-android` 3.3.1, a versão do Ktor usada pelo
próprio release. Essa é a linha estável mais recente comprovadamente compatível com o compilador
Kotlin 2.2.x integrado ao projeto. Adicionar somente o artefato central `supabase-kt`, sem Auth,
PostgREST, Storage, Realtime, Functions ou plugin de serialização do aplicativo.

**Rationale**: o módulo central contém `SupabaseClient` e `createSupabaseClient`; módulos de
produto apenas o estendem. O engine Ktor é obrigatório no Android e o engine Android é suficiente
porque Realtime/WebSockets está fora do escopo. O gate de compilação rejeitou 3.7.0: sua metadata
Kotlin 2.4.0 excede a versão 2.3.0 que o compilador integrado consegue ler. O release 3.2.6 usa
Kotlin 2.2.21 e Ktor 3.3.1, preservando o toolchain do app sem suprimir a verificação de metadata.
Fontes: [release 3.2.6](https://github.com/supabase-community/supabase-kt/releases/tag/3.2.6),
[módulo central](https://github.com/supabase-community/supabase-kt/blob/3.2.6/Supabase/README.md),
[compatibilidade Kotlin/Ktor do release](https://github.com/supabase-community/supabase-kt/blob/3.2.6/gradle/libs.versions.toml),
[quickstart Android](https://supabase.com/docs/guides/getting-started/quickstarts/kotlin) e
[compatibilidade K2 com bibliotecas](https://kotlinlang.org/docs/k2-compiler-migration-guide.html#compatibility-with-kotlin-libraries).

**Alternatives considered**: instalar PostgREST apenas para obter o cliente, rejeitado porque o
core é publicável sozinho e consultas são proibidas; usar Retrofit/Ktor manual, rejeitado porque
não entrega um cliente Supabase; usar 3.7.0, rejeitado pelo gate real de metadata; atualizar todo o
toolchain Kotlin/Compose para consumir 3.7.0, rejeitado por ampliar o escopo; suprimir a verificação
de metadata, rejeitado por ocultar incompatibilidade; adicionar BOM com um único módulo, rejeitado
por não alinhar múltiplos artefatos nesta etapa.

## 2. Compatibilidade Android e permissão

**Decision**: preservar minSdk 24 e habilitar core library desugaring com `desugar_jdk_libs` 2.1.5,
pois o SDK informa minSdk 26 sem desugaring. Adicionar somente a permissão normal `INTERNET`; não
adicionar `ACCESS_NETWORK_STATE` nem permissões de runtime.

**Rationale**: elevar minSdk excluiria usuários sem necessidade. Desugaring fornece as APIs Java
necessárias em versões antigas e o projeto já compila para Java 11. `INTERNET` é necessária para
que o cliente possa ser reutilizado por features remotas e não gera prompt. Construção do cliente
não acessa a rede. Fontes: [requisitos Android do SDK](https://github.com/supabase-community/supabase-kt/blob/3.2.6/README.md),
[desugaring Android](https://developer.android.com/studio/write/java8-support#library-desugaring) e
[engines Ktor](https://ktor.io/docs/client-engines.html).

**Alternatives considered**: elevar minSdk para 26, rejeitado por regressão de compatibilidade;
adiar `INTERNET`, rejeitado porque deixaria o cliente incapaz de cumprir seu propósito; adicionar
detecção de conectividade ou health check, rejeitado porque não há operação remota nesta feature.

## 3. Origem, nomes e precedência da configuração

**Decision**: os nomes canônicos e case-sensitive são `supabaseUrl` e `supabaseKey`. O Gradle
resolve primeiro `providers.gradleProperty(name)` e usa o `local.properties` ignorado do projeto
somente como fallback. Localmente, os valores ficam em `local.properties` ou no
`$GRADLE_USER_HOME/gradle.properties`; em CI, entram como
`ORG_GRADLE_PROJECT_supabaseUrl` e `ORG_GRADLE_PROJECT_supabaseKey`.

**Rationale**: `providers.gradleProperty` cobre `-P`, system property, ambiente CI e arquivos
Gradle com precedência documentada e compatibilidade com configuration cache. O fallback mantém
o fluxo project-specific do Android Studio e seu arquivo já está fora do Git. Uma fonte superior
presente, ainda que blank, deve falhar em vez de cair silenciosamente para outra credencial.
Fontes: [project properties e CI](https://docs.gradle.org/current/userguide/build_environment.html#sec:project_properties)
e [Provider API](https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api.provider/-provider-factory/gradle-property.html).

**Alternatives considered**: valores rastreados em `gradle.properties`, rejeitados por segurança;
aliases adicionais `SUPABASE_URL`/`SUPABASE_KEY`, rejeitados por duplicar contrato; plugin Secrets
Gradle, rejeitado porque duas propriedades e validação específica já são cobertas por APIs nativas;
passar valores reais com `-P` em CI, rejeitado por exposição em comando/processo.

## 4. Validação antecipada de URL e chave

**Decision**: materializar e validar ambos os valores durante a configuração do módulo, acumulando
falhas e lançando `GradleException` sem valores antes de qualquer variante gerar artefato.
`supabaseUrl` recebe `trim()`, deve ser URI absoluta, não opaca, HTTPS, com host e sem user-info;
não há DNS ou request. `supabaseKey` recebe `trim()` e usa allowlist:

- aceitar `sb_publishable_` case-sensitive com sufixo ASCII não vazio;
- rejeitar `sb_secret_` e formatos opacos desconhecidos;
- para JWT legado, exigir três segmentos Base64URL, decodificar somente o payload e aceitar apenas
  um claim `role` igual a `anon`;
- rejeitar `service_role`, múltiplos roles, payload inválido ou qualquer outro papel;
- nunca verificar assinatura, registrar chave/payload ou incluir o valor no diagnóstico.

**Rationale**: Supabase classifica publishable/anon como client-safe e secret/service_role como
credenciais elevadas que podem ignorar RLS. A allowlist impede empacotar uma chave administrativa;
decodificar payload é classificação local, não autenticação. Validação de disponibilidade exigiria
rede e pertence à primeira feature que executar I/O. Fonte: [API keys do Supabase](https://supabase.com/docs/guides/getting-started/api-keys).

**Alternatives considered**: buscar a substring `service_role` no JWT bruto, rejeitado porque o
payload está codificado; aceitar qualquer JWT/nonblank, rejeitado por risco crítico; limitar host a
`.supabase.co`, rejeitado porque bloquearia instância self-hosted; validar por request, rejeitado
por escopo.

## 5. BuildConfig e escape

**Decision**: habilitar `buildFeatures.buildConfig = true` e gerar `SUPABASE_URL` e
`SUPABASE_KEY` em `defaultConfig` para todas as variantes. Uma função local produz literal Java
seguro, escapando barra, aspas e controles; os valores nunca são impressos.

**Rationale**: `buildConfigField` é o mecanismo Android oficial e recebe expressão Java crua, por
isso Strings exigem aspas/escape. `defaultConfig` garante o mesmo contrato a debug, release e
futuras flavors. Fontes: [campos customizados](https://developer.android.com/build/gradle-tips#share-custom-fields-and-resource-values-with-your-app-code)
e [BuildConfigField](https://developer.android.com/reference/tools/gradle-api/9.1/com/android/build/api/variant/BuildConfigField).

**Alternatives considered**: `resValue`, rejeitado por espalhar configuração como recurso;
manifest placeholder, rejeitado porque nenhum componente Android consome os valores; interpolação
com aspas simples, rejeitada por gerar fonte inválida/injetável; validação runtime, rejeitada pela
clarificação que exige falha de qualquer build.

## 6. Ownership do cliente e arquitetura

**Decision**: criar um `internal object SupabaseClientProvider` em `core/network` com uma única
propriedade `client: SupabaseClient` lazy que chama `createSupabaseClient` usando somente os campos
validados de `BuildConfig`. Não instalar plugin e não executar request. Futuras Repositories recebem
o cliente por construtor; UI e ViewModels não leem `BuildConfig` nem criam clientes.

**Rationale**: o `object` garante uma instância por processo, evita recriação por Activity e não
exige `Application`, Hilt/Koin, AppContainer ou Repository vazio. Lazy evita trabalho no startup,
mas o cliente já está disponível por uma fronteira estável. Fonte: [inicialização Kotlin](https://supabase.com/docs/reference/kotlin/initializing).

**Alternatives considered**: armazenar somente Strings, rejeitado porque não entrega integração
pronta; criar em Activity ou por Repository, rejeitado por multiplicar engines; `Application` com
`lateinit`, rejeitada por acrescentar lifecycle/manifest sem necessidade; DI, rejeitada por YAGNI;
cliente top-level público, rejeitado por expor ownership sem fronteira nomeada.

## 7. Logs, chave pública e validação

**Decision**: não instalar logging Ktor nem emitir log de sucesso. Erros citam apenas o nome do
campo e a regra aceita. Reconhecer explicitamente que publishable/anon é extraível do APK e não é
segredo; proteção dos dados depende de RLS. Validar com builds debug/release, lint, casos negativos,
inspeção do Git/logs, abertura offline e regressão dos fluxos existentes, sem testes automatizados.

**Rationale**: externalizar evita commit acidental, mas não transforma chave cliente em segredo.
O objetivo de segurança é impedir chaves elevadas e vazamento desnecessário. O usuário não pediu
testes e a constituição impede sua geração automática.

**Alternatives considered**: mascarar e logar parte da chave, rejeitado por não agregar diagnóstico;
teste de conectividade, rejeitado porque mistura validade local com disponibilidade; testes
automatizados, rejeitados por falta de autorização.

## Resolution status

Todas as decisões do Technical Context foram resolvidas; não restam `NEEDS CLARIFICATION`.
