# Research: Android Environment Flavors

## 1. Configuração Gradle existente

**Decision**: Estender `app/build.gradle.kts` com uma dimensão `environment` e os flavors `dev`, `homol` e `prod`, preservando o `applicationId` comum.

**Rationale**: O módulo já centraliza validação de Supabase, MapLibre e chave pública do Mercado Pago e gera constantes `BuildConfig`. A dimensão única evita combinações desnecessárias e o `applicationId` comum garante que a instalação seguinte substitua a anterior.

**Alternatives considered**: Três módulos Android separados aumentariam duplicação e manutenção. Suffixes de `applicationId` permitiriam instalações simultâneas, contrariando a decisão de produto.

**Sources**: `app/build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`.

## 2. Arquivos de configuração e secrets

**Decision**: Usar `config/dev.properties`, `config/homol.properties` e `config/prod.properties`, todos ignorados pelo Git. O Gradle lê o arquivo correspondente ao flavor e valida URLs, chaves públicas e identificadores antes de gerar a variante.

**Rationale**: O projeto já usa propriedades locais e rejeita chaves Supabase service-role e placeholders. A separação por arquivo evita misturar endpoints e permite que CI ou uma máquina de build forneça somente o ambiente pretendido.

**Security boundary**: Supabase publishable/anon key, URLs e Mercado Pago public key são valores client-safe e podem ser empacotados quando necessários. Access Tokens do Mercado Pago, service-role keys, senhas e secrets administrativos não entram nos arquivos consumidos pelo APK; ficam como secrets do backend de cada projeto e são configurados com `npx supabase secrets set`.

**Alternatives considered**: Colocar valores no código ou em `gradle.properties` versionado viola a constituição. Colocar todos os ambientes em um único arquivo facilita o cruzamento acidental de credenciais.

## 3. Seleção do pagamento

**Decision**: Introduzir `PaymentGateway` como contrato estreito para criação, consulta, cancelamento, reembolso e observação de pagamento. O `IntegratedPaymentGateway` delega ao comportamento atual de `PaymentRepository`; o `SimulatedPaymentGateway` executa estados locais determinísticos sem Supabase Functions ou SDK Mercado Pago.

**Rationale**: O `PaymentRepository` atual chama `mp-create-payment`, `mp-cancel-payment`, `mp-refund-payment`, RPCs e Realtime diretamente. Uma seleção explícita no `PitGoAppContainer` torna impossível que a variante `dev` use acidentalmente o caminho integrado e preserva `homol`/`prod`.

**Alternatives considered**: Condicionar cada chamada com `if (BuildConfig.FLAVOR == ...)` espalharia regra de ambiente pela UI e pelos ViewModels. Um backend remoto de simulação permitiria dependência de rede e ainda poderia atingir integrações indevidas.

**Refinamento aprovado**: Contrato e implementações ficam em `features/payment/repository`, conforme os grupos permitidos pela constituição. Como o despacho remoto exige pagamento aprovado no servidor, a continuação dev será local em `features/pittec/repository/SimulatedPitTecFlowRepository.kt`, com cenários pré-carregados e fases de deslocamento, atendimento e conclusão. Documentar uma limitação do despacho não substitui implementar essas telas simuladas.

## 4. Isolamento do estado

**Decision**: Persistir um fingerprint do ambiente e validar o fingerprint no início do app. Quando ele mudar, invalidar sessão local, recuperação de pagamento, estado de tracking e preferências específicas do fluxo antes de permitir a navegação autenticada.

**Rationale**: Como o `applicationId` é comum, Android preserva o diretório de dados ao substituir o APK. O repositório de sessão já possui limpeza local, e preferências de tracking/onboarding já são recursos separados; a feature precisa impedir que dados de um backend sejam enviados ao outro.

**Alternatives considered**: Limpar todos os dados do aplicativo em toda troca perderia preferências não relacionadas, como tema. Ignorar a troca reutilizaria tokens e estados financeiros incompatíveis.

**Refinamento aprovado**: Instalação limpa inicializa a identidade; dados legados sem fingerprint passam por invalidação antes de registrar a identidade. O fingerprint inclui ambiente, backend e versão do escopo. Pela decisão de 30/09/2026, `:app:validateEnvironmentIsolation` exige Dev separado e Homol com todos os valores públicos iguais aos de Prod; builds individuais não exigem arquivos dos outros ambientes.

## 5. Identificação observável

**Decision**: Expor apenas o identificador e o rótulo do ambiente em `BuildConfig`, usar o rótulo no nome visível da variante e disponibilizar a identidade na superfície de diagnóstico, sem imprimir valores de configuração.

**Rationale**: QA precisa distinguir a versão em segundos, enquanto logs e telas não devem revelar endpoints sensíveis, tokens ou payloads financeiros.

**Alternatives considered**: Exibir a URL ou conteúdo dos arquivos de configuração aumentaria risco sem ajudar a identificar a variante.
