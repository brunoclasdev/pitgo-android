# Research: Catálogo de serviços por categoria e mapa PitTec

## Decision 1: Reutilizar o catálogo relacional existente

- **Decision**: usar `service_categories` como categoria principal, `services.category_id` como relação única e `provider_services` como vínculo da oferta. `provider_categories` continua sendo a projeção existente do cadastro.
- **Rationale**: as tabelas já têm unicidade por nome, estado ativo, RLS, RPC de cadastro e leitura paginada no app. Criar uma taxonomia paralela duplicaria dados e quebraria a busca atual.
- **Alternatives considered**: guardar uma lista de serviços em JSON por provider; rejeitado porque perderia unicidade, filtros e integridade referencial existentes.

## Decision 2: Categoria única no cadastro fixo

- **Decision**: o estado do cadastro terá uma categoria principal compartilhada pelas ofertas do prestador fixo; a seleção de serviço filtra somente essa categoria.
- **Rationale**: corresponde à decisão de clarificação e mantém a identidade comercial da empresa coerente. Serviços implícitos de Móvel/PitTec continuam fora desse fluxo.
- **Alternatives considered**: permitir categorias diferentes por oferta; rejeitado nesta feature porque ampliaria o modelo e mudaria a descoberta de prestadores sem requisito.

## Decision 3: Seed inicial e expansão autorizada

- **Decision**: uma migration insere/atualiza as sete categorias e até 15 especialidades selecionáveis por FIXED em cada uma, excluindo da contagem os serviços implícitos preservados. Especialidades de Guincho/PitTec usam BROWSE e contratação fixa. RPCs da feature permitem somente staff autorizado adicionar, editar, ativar ou desativar itens.
- **Rationale**: a carga inicial é reproduzível e versionada, enquanto a gestão futura não exige nova versão do aplicativo para cada serviço. A permissão usa `private.dashboard_access_at_least` e registra ação no audit log existente.
- **Alternatives considered**: deixar o cliente editar tabelas diretamente; rejeitado por segurança e por risco de quebrar o catálogo compartilhado.

## Decision 4: Revalidação no servidor

- **Decision**: o app filtra localmente para UX, mas o RPC de criação valida categoria única, serviço ativo, categoria ativa, duplicidade e correspondência de categoria imediatamente antes de gravar.
- **Rationale**: evita que uma seleção desativada ou alterada entre carregamento e envio seja aceita; mantém o backend como fonte de verdade.
- **Alternatives considered**: confiar apenas na validação do ViewModel; rejeitado por concorrência e por dados recebidos do app não serem confiáveis.

## Decision 5: Painel PitTec compacto e expansível

- **Decision**: para `ACCEPTED`, o mapa fica no conteúdo principal e o resumo é compacto por padrão; tocar no resumo expande os detalhes e revela cancelar. Atualizações de localização não reenquadram o mapa após interação manual.
- **Rationale**: resolve a obstrução visual mantendo o status acessível e preservando uma ação crítica sem sacrificar a área do mapa.
- **Alternatives considered**: remover o card por completo; rejeitado porque esconderia status e cancelamento. Card grande permanente; rejeitado porque causa o problema relatado.

## Pontos de integração confirmados

- RPC vigente: `create_provider_registration` em `20260902000000_pittec_on_demand.sql`.
  A nova migration substitui somente essa função, preservando provas, validações,
  inserções e comportamento idempotente. Nenhuma migration histórica foi editada.
- Gestão: `private.dashboard_access_at_least('MANAGER')` e
  `public.record_dashboard_action`; grants de leitura existentes continuam intactos.
- Tela real: `AppointmentTrackingScreen`, compartilhada por Guincho e PitTec.
  O resumo inferior é aplicado somente ao dispatch PitTec.
- A altura medida do resumo reduz fisicamente o viewport do MapView; a atribuição fica
  acima dessa área. Expansão é rolável e limitada a 45% da altura da tela.
- O host compartilhado precisou aceitar coordenadas para enquadramento por bounds,
  porque o zoom aproximado por distância não garante inclusão da geometria da rota.
  Chamadores antigos mantêm o comportamento com lista vazia por padrão.
- A carga usa correspondência por nome normalizado e `ON CONFLICT DO NOTHING`, sem
  reescrever IDs, ofertas ou decisões anteriores de desativação. Serviços legados fora
  das listas não são excluídos; o total em bases existentes deve ser conferido na validação
  Supabase completa antes da publicação.
