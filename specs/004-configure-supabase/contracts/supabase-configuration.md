# Contract: Supabase Build Configuration and Runtime Client

## Purpose

Definir a fronteira verificável entre o ambiente local/CI, o build Android e o cliente Supabase
compartilhado. Este contrato não define tabela, endpoint, usuário, sessão ou operação de rede.

## Input contract

| Canonical name | Required | Local source | CI source |
|---|---|---|---|
| `supabaseUrl` | Yes | `local.properties` ou Gradle user home | `ORG_GRADLE_PROJECT_supabaseUrl` |
| `supabaseKey` | Yes | `local.properties` ou Gradle user home | `ORG_GRADLE_PROJECT_supabaseKey` |

Os nomes são case-sensitive. `providers.gradleProperty` tem precedência; `local.properties` só é
lido como fallback quando a propriedade Gradle correspondente está ausente. Uma propriedade de
maior precedência presente mas blank é inválida e não autoriza fallback silencioso.

Valores reais não podem ser colocados no `gradle.properties` rastreado, código, recursos, specs,
argumentos/logs de CI ou artefatos de diagnóstico. Em CI, variáveis devem ser masked/protected e
existir apenas no processo do build.

## Normalization and validation contract

### `supabaseUrl`

1. Remover somente espaços externos.
2. Rejeitar ausente ou vazio.
3. Interpretar localmente como URI, sem DNS ou request.
4. Exigir URI absoluta, não opaca, esquema HTTPS case-insensitive, host não vazio e ausência de
   user-info.
5. Preservar o restante do conteúdo normalizado, inclusive eventual `/` final.

### `supabaseKey`

1. Remover somente espaços externos.
2. Rejeitar ausente ou vazio.
3. Aceitar `sb_publishable_` case-sensitive com sufixo ASCII `[A-Za-z0-9_-]+` não vazio.
4. Rejeitar `sb_secret_` e qualquer prefixo opaco desconhecido.
5. Para chave legada, exigir três segmentos Base64URL, decodificar apenas o payload e aceitar
   exatamente um claim `role` com valor `anon`.
6. Rejeitar `service_role`, papel diferente, múltiplos claims de papel ou payload malformado.
7. Não validar assinatura JWT e não imprimir chave, segmentos ou payload.

## Failure contract

Qualquer falha interrompe a configuração do módulo para debug, release e futuras variantes antes
de APK/AAB. Se houver mais de um erro, todos podem ser listados, mas nenhum valor é incluído.

Mensagens devem seguir estes significados:

| Condition | Safe diagnostic |
|---|---|
| Campo ausente/blank | Identificar o nome e as fontes aceitas |
| URL inválida | Informar que `supabaseUrl` exige URL HTTPS absoluta com host |
| Chave inválida/elevada | Informar que `supabaseKey` aceita publishable/anon e proíbe secret/service-role |

Não há fallback de desenvolvimento, placeholder executável nem estado de erro em runtime.

## Build output contract

Após sucesso, todas as variantes recebem exatamente:

- `BuildConfig.SUPABASE_URL`: String normalizada e escapada como literal Java seguro;
- `BuildConfig.SUPABASE_KEY`: String normalizada e escapada como literal Java seguro.

O gerador deve escapar barra invertida, aspas, quebras, tabs e caracteres de controle. Os campos
não são recursos XML nem manifest placeholders.

## Runtime contract

- **Owner**: `core/network/SupabaseClientProvider`.
- **Output**: `client: SupabaseClient`, lazy e read-only.
- **Cardinality**: exatamente uma identidade de cliente por processo após o primeiro acesso.
- **Input**: somente os dois campos validados de `BuildConfig`.
- **Plugins installed**: nenhum.
- **Side effects at construction**: nenhum request, autenticação, consulta ou persistência.
- **Future consumers**: Repositories por injeção de construtor; nunca Composable ou ViewModel
  criando/lendo configuração diretamente.

## Android contract

- O manifest declara somente `android.permission.INTERNET` para rede.
- A permissão é normal e não produz prompt.
- minSdk permanece 24 com core library desugaring.
- Nenhuma tela, rota, string ou comportamento visual muda.

## Invariants

- Um ambiente válido fornece um par e produz um cliente.
- Configuração inválida produz zero artefato do aplicativo.
- Chave elevada produz zero artefato, mesmo que a URL seja válida.
- O cliente não prova conectividade; offline não invalida a configuração.
- A chave publishable/anon é pública no APK; RLS é a fronteira de dados.
- Nenhum valor completo aparece em Git, mensagens ou logs.
