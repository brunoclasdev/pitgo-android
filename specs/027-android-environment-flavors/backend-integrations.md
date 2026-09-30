# Integrações dev/homol — 30/09/2026

**Registro histórico**: após esta configuração, Homol foi alterado para usar o backend de Prod. O projeto descrito abaixo atende somente Dev. O arquivo privado citado foi renomeado para `config/dev.secrets.env`; nele `MP_ORDERS_ENABLED=false`. As funções e jobs permanecem no projeto Dev para testes de backend, enquanto o APK Dev usa pagamentos simulados. Para Homol, valem as funções, secrets e dados do projeto Prod.

## Estado aplicado

Projeto alvo: `pitgo-database-dev` (`ljnqlaiiwreuczbdghxn`), compartilhado por dev/homol. Nenhuma alteração foi feita no backend de produção. Nenhuma cobrança, tokenização de cartão ou reembolso real foi executado.

- Dez Edge Functions do repositório implantadas e confirmadas ACTIVE via `npx supabase functions deploy --use-api` e `functions list`, com verificação JWT conforme supabase/config.toml.
- Secrets personalizados enviados por arquivo privado ignorado `config/homol.secrets.env`: MP_ACCESS_TOKEN, MP_WEBHOOK_SECRET, MP_ORDERS_ENABLED, MP_RECONCILE_SECRET, GEOCODE_REFRESH_SECRET, MAPTILER_API_KEY e PITTEC_ROUTING_BASE_URL. SUPABASE_* são fornecidos pela plataforma.
- Credenciais Mercado Pago existentes em supabase/.env.local reutilizadas provisoriamente, conforme a configuração compartilhada da sessão; leitura `/users/me` retornou HTTP 200. A chave MapTiler da configuração de mapas existente foi verificada por geocodificação HTTP 200.
- Secrets dos workers de reconciliação e geocodificação gerados especificamente para o destino. Vault tem project_url do dev, MP_RECONCILE_SECRET e geocode_refresh_secret, sincronizados com os workers.
- Autenticação sincronizada com o arquivo versionado: hook before_user_created habilitado, senha mínima 8, cadastro por e-mail sem confirmação. O PostgreSQL local foi alinhado com os projetos remotos (major_version=17). Config diff final: zero diferenças em propriedades declaradas; oito propriedades remotas não declaradas preservadas.
- Regra ativa de precificação PitTec copiada de produção, sem copiar atendimentos/pagamentos. Identidade da sequência preservada.
- Nove jobs configurados, com horários/funções equivalentes aos de produção, utilizando somente o Vault do destino. Cron habilitado: 128 execuções observadas, todas succeeded. Worker financeiro retorna sem HTTP quando não há trabalho pendente, por desenho.
- Storage conferido: produção e destino sem buckets e sem políticas customizadas de Storage; não há arquivos/buckets da aplicação a implantar neste momento.

## Roteamento provisório

Sem endpoint contratado informado, foi adotado provisoriamente `https://router.project-osrm.org/route/v1/driving` exclusivamente no backend de testes. O endpoint retornou HTTP 200, code=Ok e uma rota para coordenadas públicas sintéticas. A validação exigiu cliente TLS atualizado; a falha inicial do cliente Python do sistema não foi contornada desabilitando TLS. Substituir por endpoint próprio/contratado quando fornecido. API OSRM: https://project-osrm.org/docs/v26.4.0/http

## Evidências de validação

- Cadastro inválido bloqueado pelo hook (400).
- CLIENT e PROVIDER cadastrados com sessão imediata; get_my_account_context retornou os tipos corretos (200).
- Catálogo retornou somente categorias ativas conforme RLS (200).
- Endpoints de pagamento/cartão/cancelamento/reembolso e pittec-route aceitaram a sessão e rejeitaram entradas inválidas (400), antes de qualquer operação financeira.
- Geocodificação de provider em endereço público sintético retornou VERIFIED (200).
- Workers recusaram chamadas sem o secret (401) e processaram lotes vazios autorizados (200).
- Webhook recusou assinatura inválida (401); evento sintético assinado de tipo não suportado retornou IGNORED (200), sem alimentar a fila financeira.
- Todas as contas temporárias e seus perfis/provas foram removidos ao final. Conferidos zero usuários, perfis, papéis, provas, cartões e pagamentos restantes no destino.

Scripts/evidências locais protegidos em `/private/tmp/pitgo-integrations-20260930`; scripts temporários fora do app e do Git. O teste não comprova cobrança real, retorno real do Mercado Pago nem atendimento completo com cálculo de rota pela Edge Function. Essas validações continuam pendentes de cenário operacional/conta/valor e participantes de teste.

## Pendência externa: registro do webhook Mercado Pago

Callback de homol implantado e assinatura validada:

`https://ljnqlaiiwreuczbdghxn.supabase.co/functions/v1/mp-webhook`

Registrar esse callback na aplicação Mercado Pago apropriada, evento Order (Mercado Pago), e manter MP_WEBHOOK_SECRET correspondente. A URL atual de produção não foi alterada. Com credenciais da mesma aplicação para homol/prod, trocar o callback global pode desviar notificações de produção; é necessário confirmar uma aplicação exclusiva de homol ou definir uma estratégia de encaminhamento antes de alterar o painel.

Não há sessão/ferramenta autenticada do painel Mercado Pago disponível neste ambiente; o Access Token existente permitiu leitura da API, mas não comprova acesso à configuração global de notificações. A reconciliação periódica do destino já foi configurada como mecanismo de recuperação, sem afirmar que notificações reais foram registradas. A documentação exige configurar a URL no painel: https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/notifications

## Operação futura

Dev usa a simulação Android e não deve invocar esses endpoints financeiros. Homol é integrado e pode realizar transações reais quando utilizado normalmente pelo usuário. Dados remotos de dev/homol são compartilhados; não carregar usuários ou dados pessoais de produção para esses testes.

Aplicar secrets/funções/config uma vez por projeto, sempre passando `--project-ref ljnqlaiiwreuczbdghxn`, sem usar o link atual de produção implicitamente. Arquivos de secrets devem permanecer ignorados e nunca ser incluídos no APK ou impressos em logs.
