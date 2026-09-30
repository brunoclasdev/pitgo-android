# Build Configuration Contract

## Flavor matrix

| Flavor | Dimension | Payment mode | Backend | Mercado Pago |
|---|---|---|---|---|
| `dev` | `environment` | `SIMULATED` | Dev isolado | Não inicializa fluxo externo nem cria transação |
| `homol` | `environment` | `INTEGRATED` | Homol isolado | Credenciais reais de homologação, uso controlado |
| `prod` | `environment` | `INTEGRATED` | Prod isolado | Credenciais reais de produção |

O `applicationId` é `com.trustarx.pitgo` em todos os flavors. Variantes esperadas: `devDebug`, `devRelease`, `homolDebug`, `homolRelease`, `prodDebug` e `prodRelease`.

## Local files

Cada arquivo fica em `config/<flavor>.properties`, é fornecido pela máquina/CI e não é versionado. O repositório deve conter somente um exemplo sem valores reais, se necessário.

Propriedades comuns:

```properties
environmentId=<dev-or-homol-or-prod>
supabaseProjectRef=<expected-environment-project-ref>
supabaseUrl=https://<environment-project>.supabase.co
supabaseKey=<publishable-or-anon-key>
mapStyleUrl=https://<managed-style-url>
mapStyleProviderName=<provider>
mapStyleAttribution=<attribution-with-OpenStreetMap>
mapStyleAttributionUrl=https://<provider-url>
```

Somente `homol.properties` e `prod.properties` incluem `mercadoPagoPublicKey` client-safe, com o mesmo valor nos dois arquivos. Access Tokens Mercado Pago, service-role keys, senhas e outros secrets privados não são propriedades do APK. Homol e Prod usam os secrets do mesmo projeto Supabase de produção, por exemplo:

```bash
npx supabase secrets set --project-ref <ref-do-ambiente> MP_ACCESS_TOKEN=...
```

## Validation

O plugin deve validar o arquivo selecionado antes do build da variante. Deve rejeitar arquivo ausente, URL não HTTPS, chave placeholder, service-role key, chave Mercado Pago inválida em `homol`/`prod` e qualquer tentativa de dar `INTEGRATED` ao flavor `dev`.

O build não deve imprimir valores de propriedades em mensagens de erro ou logs. Mensagens podem nomear a propriedade ausente e o ambiente.

`:app:validateEnvironmentIsolation` exige os três arquivos e valida identidade/flavor, host canônico `<supabaseProjectRef>.supabase.co`, Dev separado de produção e todas as propriedades públicas de Homol iguais às de Prod. A verificação deve terminar com erro sanitizado antes da distribuição se houver divergência. Builds individuais validam somente seu ambiente; sua compilação isolada não equivale à aprovação da verificação conjunta. Secrets permanecem no backend e não entram nessa validação Android.
