# Pesquisa: avaliação de guincheiros e PitTecs

## Decisões

### Persistência e autoridade

**Decisão**: Persistir avaliações em uma tabela PostgreSQL única, ligada ao atendimento existente. Um RPC autenticado fará o envio; o servidor obtém cliente e prestador do próprio atendimento e confirma modalidade, estado concluído e autoria antes de gravar. Uma restrição única por atendimento impede duplicatas.

**Racional**: Guincho e PitTec compartilham a regra de avaliação e os fluxos existentes já usam RPCs Supabase para operações de domínio. Uma única entidade mantém a regra equivalente nas duas modalidades e evita confiar em identificadores de cliente/prestador enviados pelo app. A restrição no banco cobre chamadas concorrentes e reenvios após falha de rede.

**Alternativas consideradas**: Tabelas separadas por modalidade duplicariam validação e consulta agregada. Escrita direta na tabela pelo app exporia mais superfície de autorização e deixaria a validação de estado mais fácil de contornar.

### Privacidade de avaliações

**Decisão**: Expor um contrato público de resumo contendo apenas média e quantidade, e um contrato autenticado separado para descrição, autorizado ao cliente autor, ao prestador vinculado e ao suporte com a autorização já usada pelo backend. Não expor a tabela diretamente aos clientes.

**Racional**: A especificação permite divulgar apenas estatísticas agregadas e restringe texto e identidade. A separação de RPCs minimiza os campos disponíveis em cada contexto. A documentação Supabase recomenda proteger tabelas expostas com RLS e revisar cuidadosamente funções privilegiadas; também alerta que views podem ignorar RLS por padrão. [Supabase: Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security) e [Supabase: Database Functions](https://supabase.com/docs/guides/database/functions).

**Alternativas consideradas**: Uma view ampla com dados de avaliações exigiria configuração de segurança e projeção cuidadosas. A tabela diretamente consultável aumentaria o risco de revelar comentários ou identidade por engano.

### Integração móvel

**Decisão**: Seguir UI → ViewModel → Repository → Supabase RPC, Kotlin/Compose e StateFlow já adotados. Oferecer a avaliação depois da conclusão sem prender a navegação; no histórico, buscar elegibilidade e estado avaliado junto aos dados do atendimento.

**Racional**: O app já usa repositories Kotlin para RPC e tem histórico de guincho e acompanhamento de dispatch. O fluxo não deve alterar nem atrasar os RPCs existentes de conclusão. A versão atual do app declara Kotlin via Compose compiler 2.2.10, Supabase Kotlin 3.2.6, AGP 9.1.1 e Gradle wrapper 9.3.1 e SDK Android compile 37 / target 36.

**Alternativas consideradas**: Integrar a avaliação no comando de conclusão acoplaria feedback opcional ao fluxo obrigatório. Persistência local não é necessária para manter uma avaliação pendente, pois a elegibilidade vem do atendimento concluído no backend.

### Comentário e entrada

**Decisão**: Nota inteira de 1 a 5, exigida e sem seleção inicial; texto opcional limitado a 500 caracteres. Após confirmação, avaliação imutável. Em erro de rede, manter os dados digitados e permitir nova chamada; se o primeiro envio tiver sido confirmado no servidor, a restrição única retorna estado já avaliado sem criar ou substituir dados.

**Racional**: Converte os requisitos da spec em regras de validação no app e no banco, preservando a experiência de retry sem duplicidade.

**Alternativas consideradas**: Permitir edição exigiria histórico de versões e política de alteração não solicitados e conflitaria com a decisão de clarificação.

## Restrições e lacunas resolvidas

- O backend existente tem conclusão e listagem de histórico de guincho, além de conclusão de PitTec, todos por RPC.
- A autorização de suporte deve reutilizar o mecanismo server-side existente para casos de disputa/inspeção; não criar um papel ou acesso administrativo paralelo.
- Confirmado: Guincho e PitTec usam `public.appointments.provider_id` → `public.providers.id`; o autor é `public.appointments.client_id` → `public.profiles.id`. A modalidade vem de `public.service_searches.dispatch_kind`.
- Não foi identificada necessidade de dependência, Edge Function, push, cache local ou alteração de estado do atendimento.
- A especificação não exige testes automatizados e a constituição do projeto proíbe gerá-los automaticamente. A validação executável será documentada em quickstart e pode ser seguida manualmente nos ambientes configurados.

## Confirmações da implementação

- O histórico existente de Guincho é reutilizável para snapshots das duas modalidades. O novo RPC `list_my_service_appointments` mantém os registros de Guincho e acrescenta atendimentos PitTec concluídos. A rota e os componentes existentes foram mantidos; o texto passou a ser Histórico de atendimentos.
- O suporte usa `private.dashboard_access_at_least('MANAGER')`, com registro de acesso em `public.record_dashboard_action`.
- Wrappers de snapshots preservam os contratos operacionais existentes e adicionam somente elegibilidade/estado da avaliação; o resumo público não carrega comentários.
