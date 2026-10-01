# Quickstart: Android Environment Flavors

**Configuração vigente (30/09/2026)**: Dev usa o projeto de desenvolvimento e simulação local. Homol espelha as propriedades públicas de Prod e usa o mesmo backend, conta Mercado Pago e pagamentos reais. Seções datadas abaixo registram etapas anteriores e não descrevem a configuração vigente quando citam Dev/Homol compartilhados. Um APK Android sem assinatura não pode ser instalado; use `homolDebug`, assinado automaticamente com a chave de debug, para testar sem chave de release.

**Exceção solicitada para pagamentos Homol (30/09/2026)**: o checkout Homol sorteia um valor em centavos entre R$ 1,00 e R$ 2,00 (inclusive), uma vez por entrada de checkout. O valor fica em SavedStateHandle; pagamentos já criados reutilizam o valor persistido no banco. Pix e cartão usam o mesmo valor exibido. Dev mantém simulação; o checkout Prod mantém a regra anterior.

A requisição Homol envia `checkout_environment=homol`. A Edge Function autenticada usa a reserva exclusiva de Homol, acessível apenas ao service_role, valida faixa/precisão e conserva os controles de proprietário, cotação, prazo e idempotência. `payments.checkout_environment` identifica a cobrança; a cotação comercial/remuneração não é recalculada. Para atendimentos direcionados, `appointments.payment_amount` registra o valor efetivamente cobrado e é usado pelos controles de aprovação e continuidade. O campo de ambiente identifica o fluxo do aplicativo; não substitui a autenticação da conta.

Migration `20260930153000_homol_random_checkout_amount.sql` aplicada com `npx supabase` nos dois bancos e Edge Function `mp-create-payment` publicada em ambos. Auditoria de permissões/contratos aprovada. Build Homol, compilação Kotlin Dev/Prod e lint Homol passaram; nenhum pagamento foi criado durante a validação. Para conferir manualmente, abrir um checkout Homol novo, verificar a faixa, recriar a tela e confirmar que o valor não muda; realizar Pix/cartão com autorização do usuário e conferir valor do pagamento e liberação do atendimento. Cobranças antigas conservam seus valores.

## Prerequisites

- Android SDK configurado para compile SDK 37 e JDK compatível com o projeto.
- Três arquivos locais `config/dev.properties`, `config/homol.properties` e `config/prod.properties` preenchidos com valores client-safe.
- Dois projetos Supabase: Dev separado e produção compartilhada por Homol/Prod, com migrations e Edge Functions no backend correspondente.
- Secrets privados configurados em cada projeto com `npx supabase secrets set`.
- Somente um flavor instalado por dispositivo; vários devices podem estar conectados, com seleção explícita por serial. Substituição exige assinatura compatível e versionCode não inferior ao instalado.

## Build configuration

```bash
./gradlew :app:assembleDevDebug
./gradlew :app:assembleHomolDebug
./gradlew :app:assembleProdDebug
```

Expected result: cada tarefa gera uma APK identificada pelo flavor e falha antes da compilação caso seu arquivo ou uma propriedade obrigatória seja inválida.

Para variantes de distribuição:

```bash
./gradlew :app:validateEnvironmentIsolation
./gradlew :app:assembleDevRelease
./gradlew :app:assembleHomolRelease
./gradlew :app:assembleProdRelease
```

## Installation and environment replacement

Instale `devDebug`, valide a identificação `Dev`, faça login no backend dev e confirme o fluxo simulado. Depois instale `homolDebug` sobre o mesmo dispositivo. Expected result: o APK anterior é substituído, o fingerprint muda, a sessão/estado financeiro do dev não é restaurado e o app usa o backend de produção. Trocar entre Homol e Prod também invalida a sessão local por identidade de flavor, embora o backend seja o mesmo.

## Payment validation

1. Em `dev`, iniciar checkout/PitTec e executar aprovação, recusa, expiração e cancelamento. Confirmar a evolução das telas sem chamadas para Mercado Pago ou Edge Functions de pagamento.
2. Em `homol`, executar uma transação controlada autorizada e confirmar que o pagamento aparece no projeto Supabase e na conta Mercado Pago de produção.
3. Em `prod`, executar somente o cenário operacional autorizado e confirmar o destino de produção.
4. Remover uma propriedade obrigatória e executar novamente o build. Confirmar falha clara identificando ambiente/propriedade, sem imprimir o valor.
5. Em dev, iniciar cenário local sem criar solicitação remota, aprovar e percorrer deslocamento, início do atendimento e conclusão; repetir offline. Confirmar zero mutações de atendimento remoto e zero chamadas financeiras.
6. Em cópias temporárias dos arquivos locais, repetir o project ref/endpoint de produção em Dev e executar `:app:validateEnvironmentIsolation`: deve falhar. Alterar qualquer propriedade pública de Homol em relação a Prod também deve falhar. Repetir com project ref divergente do host ou identidade divergente do flavor; restaurar os arquivos ao final. Não registrar os valores nas evidências.
7. Comparar instalação limpa, atualização legada sem fingerprint, troca dev→homol e atualização no mesmo ambiente; limpar somente estado incompatível antes de restaurar autenticação.

## Provisionamento por ambiente

Arquivos Android contêm apenas propriedades públicas; arquivos `config/*.secrets.env` ficam ignorados e contêm somente secrets do backend. Dev tem projeto próprio; Homol usa os mesmos secrets já configurados no projeto de Prod. Usar referências explícitas em cada operação, conferindo o projeto antes de executá-la:

```bash
npx supabase db push --db-url "$PITGO_ENV_DATABASE_URL"
npx supabase functions deploy --project-ref "$PITGO_ENV_PROJECT_REF"
npx supabase secrets set --env-file "config/$PITGO_ENVIRONMENT.secrets.env" --project-ref "$PITGO_ENV_PROJECT_REF"
```

A URL privada deve ser fornecida pelo ambiente local protegido, sem incluí-la em documentação ou logs. Implantar a mesma cadeia de migrations em cada projeto, revisar os dados/seeds e ajustar os destinos de cron/Vault e webhook para o backend correto. Homol/Prod compartilham intencionalmente o projeto e as credenciais reais de produção; Dev usa o projeto de desenvolvimento e pagamentos simulados. Credenciais Mercado Pago e IDs de conta só podem ser confirmados operacionalmente no backend; a validação Android não comprova sua titularidade.

Dev oferece cenários locais para checkout e continuação PitTec; a aprovação abre o mapa e permite avançar chegada, início e conclusão. Cartões têm identificação de simulação e não recebem PAN/CVV. O Pix de teste contém texto sem valor financeiro, nunca um código pagável. Ao concluir, o pagamento continua aprovado no histórico e deixa de ser recuperável como atendimento ativo; cancelar aplica apenas um reembolso local simulado. Os estados simulados vivem no processo e são reiniciados quando ele termina, sem restaurar atendimento remoto.

## Validação do espelho Homol/Prod em 30/09/2026

- `:app:validateEnvironmentIsolation`, `:app:assembleHomolDebug` e `:app:assembleHomolRelease` passaram com a configuração local vigente. A validação rejeitou Homol com uma propriedade pública diferente de Prod e Dev apontando para o backend de Prod; os arquivos foram restaurados e a validação final passou.
- O `BuildConfig` gerado de `homolDebug` contém as mesmas configurações públicas de backend, mapa e Mercado Pago de `prodDebug`; ambos usam pagamento integrado. Apenas a identidade do flavor difere.
- `app-homol-debug.apk` tem assinatura automática de debug válida e nome `PitGo Homol`. `app-homol-release-unsigned.apk` foi gerado sem assinatura de distribuição e não pode ser instalado no Android enquanto não for assinado.
- O APK `homolDebug` foi instalado e aberto em `R1AIB60009675B2`, `RQ8MB054C1J`, `RQCTB04EDNW` e `RX8T10LATDY`; os quatro aparelhos apresentaram `MainActivity` em primeiro plano.
- Nenhuma transação Mercado Pago foi executada. O checkout real, webhook e persistência de uma cobrança ainda exigem teste operacional autorizado na conta de produção.

## Evidências históricas da implementação inicial

- `:app:tasks --offline`: configuração Gradle aceita e seis variantes disponíveis.
- `:app:validateEnvironmentIsolation --offline`: passou usando três configurações sintéticas distintas, exclusivamente para verificação.
- Arquivos locais de configuração: ignorados por Git e Docker.
- Cenários negativos: backend duplicado, environmentId incorreto, project ref/host divergente e chave Supabase privada foram rejeitados. Os arquivos sintéticos foram restaurados ao final.
- Compilação final: seis variantes debug/release passaram, com `--offline --max-workers=2`, após validação conjunta. Lint dos três flavors debug: zero erros e 103 avisos por flavor. APKs de release sem assinatura; todas as configurações usadas eram sintéticas.
- Consulta read-only via `npx --no-install supabase projects list --output json`: apenas um projeto PitGo ativo identificado (`pitgo-database`); não há três projetos mapeados aos ambientes. Configurações públicas definitivas ainda não fornecidas; nenhuma transação executada. A evidência sintética não representa validação financeira nem integração operacional.
- Integração existente preservada por delegação ao PaymentRepository; criação, recuperação, observação, cancelamento e reembolso mantêm suas implementações remotas. A comprovação operacional em homol/prod permanece bloqueada pela ausência de configurações/contas confirmadas.
- Configurações sintéticas removidas após as verificações. APKs de compilação transferidos para `/private/tmp/pitgo-flavor-compilation-only`, fora dos caminhos usuais de distribuição.
- Pendentes T012, T019, T027 e T031: identificação/isolamento em dispositivo, cenários dev/offline, inspeção runtime de UI/logs e matriz de substituição/reinício/sessão. Exigem configuração real de backend e seleção dos dispositivos; pagamentos reais exigem contexto de conta/valor autorizado.
- Revisão final: `git diff --check` passou; propriedades e secrets ignorados por Git/Docker; rastreabilidade FR-001–FR-017 e SC-001–SC-009 conferida em tasks.md. Critérios operacionais pendentes não são considerados comprovados.

## Comandos de inspeção

```bash
git diff --check
./gradlew :app:lintDevDebug
./gradlew :app:lintHomolDebug
./gradlew :app:lintProdDebug
```

Verificar também que `git status --short` não lista os arquivos `config/*.properties` e que logs de build/app não contêm chaves, tokens ou secrets.

## Atualização de 29/09/2026

A configuração foi extraída do documento local `dados-database-pitgo.rtf`: dev/homol usam `Pitgo-database-dev`, prod usa `Pitgo-database`. Os três arquivos públicos locais estão ignorados e não contêm secret key ou senha de banco. Propriedades de mapa e chave pública Mercado Pago foram mantidas da configuração local existente para homol/prod; titularidade das contas e destinos de webhook ainda precisam de confirmação operacional. Nenhuma cobrança, migration ou deploy foi executado nesta atualização. As evidências anteriores com configurações sintéticas são históricas.

Como dev/homol compartilham o banco, dados de autenticação e domínio remoto são comuns aos dois. A troca de flavor ainda invalida a sessão local; dev continua sem operações financeiras. Executar o provisionamento apenas uma vez por projeto, e não uma vez por flavor.

Validação desta atualização: `validateEnvironmentIsolation` e os três builds debug passaram com configurações reais. Não houve instalação nos dispositivos nem validação de transação Mercado Pago.

## Cópia inicial de banco em 30/09/2026

A estrutura e o catálogo de produção foram importados e conferidos em dev/homol, sem dados pessoais/financeiros, secrets ou cron. Backup e evidências: [database-copy.md](database-copy.md). Integrações externas e dados operacionais de teste permanecem pendentes.

## Provisionamento confirmado em 30/09/2026

Funções, autenticação, secrets, Vault, precificação e cron configurados no dev/homol. Smoke checks passaram, sem cobranças; consultar [backend-integrations.md](backend-integrations.md) para evidências e pendências externas.
