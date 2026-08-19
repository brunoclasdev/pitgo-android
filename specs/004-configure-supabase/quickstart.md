# Quickstart Validation: Configurar Supabase

## Prerequisites

- JDK 17 e Android SDK Platform 37 configurados.
- Um `supabaseUrl` HTTPS e uma chave **publishable** (`sb_publishable_...`) ou legacy `anon`.
- Nunca usar ou compartilhar chave `sb_secret_...` ou `service_role`.
- Dispositivo/emulador Android API 24+ para regressão manual.

## Configure local values

Adicionar ao `local.properties` já ignorado, preservando `sdk.dir` existente:

```properties
supabaseUrl=https://<project-ref>.supabase.co
supabaseKey=sb_publishable_<client-value>
```

Não copiar valores reais para `gradle.properties`, specs, código, recursos, comandos registrados ou
mensagens. Como alternativa local, usar as mesmas propriedades no
`$GRADLE_USER_HOME/gradle.properties`, fora do repositório.

Em CI, cadastrar valores masked/protected com estes nomes:

```text
ORG_GRADLE_PROJECT_supabaseUrl
ORG_GRADLE_PROJECT_supabaseKey
```

O contrato completo está em [supabase-configuration.md](contracts/supabase-configuration.md).

## Positive build validation

Com valores válidos configurados:

```bash
./gradlew clean assembleDebug assembleRelease lintDebug
```

Resultado esperado:

- `BUILD SUCCESSFUL`.
- APKs debug e release gerados.
- nenhuma atualização de Kotlin/Compose, módulo funcional Supabase ou plugin de serialização.
- `SupabaseClientProvider.client` compila como o único cliente compartilhado.
- nenhum request ou log contendo URL/chave ocorre durante build ou inicialização.

## Negative build matrix

Executar cada caso somente com valores sintéticos. Propriedades `-P` podem ser usadas para fixtures
não sensíveis que sobrescrevem o fallback local; nunca colocar a chave real na linha de comando.

| Case | Synthetic input | Expected result |
|---|---|---|
| URL ausente/blank | `supabaseUrl` omitida ou espaços | Build interrompido; cita apenas o campo |
| URL sem scheme | `project.supabase.co` | Build interrompido |
| URL insegura | `http://project.supabase.co` | Build interrompido |
| URL sem host/opaca | URI HTTPS malformada | Build interrompido |
| URL com user-info | `https://user@project.supabase.co` | Build interrompido |
| Chave publishable | `sb_publishable_validation_only` | Build permitido sem request |
| Chave secret | `sb_secret_validation_only` | Build interrompido sem ecoar valor |
| JWT legacy anon | fixture JWT com payload `role=anon` | Build permitido sem validar assinatura |
| JWT service role | fixture JWT com payload `role=service_role` | Build interrompido sem ecoar payload |
| Formato desconhecido | String não vazia fora da allowlist | Build interrompido |

Para cada falha, validar debug e release. A saída deve mencionar correção/fonte aceita e não conter
input completo, payload ou segmento da chave.

## Source precedence and normalization

1. Configure fixtures diferentes em uma propriedade Gradle e em `local.properties`.
2. Confirme que a propriedade Gradle vence.
3. Configure a fonte superior como blank e confirme falha, sem fallback silencioso.
4. Adicione espaços externos a URL/chave válidas e confirme build bem-sucedido.
5. Confirme que conteúdo interno não foi alterado.

## Repository and log inspection

Sem imprimir o valor, pesquise a chave real por stdin:

```bash
printf '%s\n' "$ORG_GRADLE_PROJECT_supabaseKey" |
  git grep --files-with-matches --fixed-strings -f - -- .
```

Resultado esperado: nenhuma saída. Também revisar:

```bash
git status --short
git diff --check
git ls-files local.properties
```

`local.properties` não deve estar rastreado. Referências textuais a `sb_secret_`/`service_role` em
specs e validação são regras/fixtures sintéticas, não credenciais.

## Runtime regression and offline behavior

1. Instalar o APK debug e abrir o app.
2. Percorrer Welcome → Home → RequestService e usar Back.
3. Confirmar que não há nova tela, loading, erro, permissão de runtime ou mudança visual.
4. Encerrar e reabrir o processo; o app permanece funcional.
5. Desativar rede e repetir a abertura: configuração continua válida e nenhum health check falha.
6. Confirmar em inspeção de código que acessos repetidos retornam o mesmo cliente lazy e nenhum
   módulo Auth/PostgREST/Storage/Realtime/Functions foi instalado.

## Scope confirmation

A validação termina com o cliente central disponível. Não são esperados autenticação, sessão,
tabela, DTO, consulta, escrita, upload, assinatura Realtime, função remota, Repository, UI de erro,
persistência ou teste automatizado.
