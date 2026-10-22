# Contrato: RPCs de avaliação

Os contratos abaixo estão implementados na migration `20261022100000_service_ratings.sql`. Todas as operações, incluindo consulta do resumo, exigem sessão autenticada. O app chama os RPCs via Repository Supabase existente.

## `submit_service_rating(p_appointment_id, p_rating, p_comment)`

- Entrada: UUID do atendimento, nota inteira 1–5, texto opcional de até 500 caracteres.
- O servidor deriva cliente/prestador e modalidade do atendimento; não recebe IDs de cliente ou prestador.
- Valida sessão, autoria, status `COMPLETED`, modalidade elegível e inexistência de avaliação anterior.
- Sucesso novo: retorna `{outcome: "submitted", context: {...}}`. O contexto contém `appointment_id`, `provider_name`, `can_rate`, `submitted` e `rating` (`id`, `rating`, `comment`, `created_at`).
- Repetição depois de uma gravação confirmada: retorna `already_submitted` e o estado salvo autorizado ao autor; nunca sobrescreve. A tabela não permite escrita direta do cliente, UPDATE ou DELETE; este RPC é a única via de criação e não expõe operação de edição/remoção.
- Entrada inválida, atendimento inelegível ou falta de autorização: erro de domínio sem persistência.
- Falha de transporte: app mantém seleção e texto; ao tentar novamente, o servidor resolve de forma segura a possível confirmação ambígua.

## `get_provider_rating_summary(p_provider_id)`

- Saída: `count` e `average` (nulo quando count é zero).
- Não retorna avaliações individuais, texto, cliente, appointment_id ou outro dado identificável.
- Pode ser consultado no contexto do perfil público de provider por clientes do app.

## `get_service_rating(p_appointment_id)`

- Saída privada: nota, texto opcional, instante de criação e estado avaliado.
- Autorizações permitidas: cliente autor, prestador vinculado ao atendimento e suporte autenticado segundo a política existente.
- Para usuário sem autorização, não revela se existe comentário nem a identidade de seu autor.
- O retorno ao cliente também fornece o estado necessário ao histórico para ocultar nova submissão.

## Integração de domínio

- Consultas de histórico de atendimento devem informar se há avaliação e se o cliente pode avaliar; qualquer elegibilidade retornada é apenas para apresentação, pois o RPC de envio revalida.
- A ação de dispensar fecha a UI sem RPC e sem alterar o atendimento.
- A UI deve distinguir erro sem confirmação de sucesso confirmado. Se a resposta indicar `already_submitted`, apresentar como concluído, nunca como falha que permitiria alterar os dados.
- Nenhuma chave privilegiada ou segredo Supabase será usada no app.

## Histórico compartilhado

`list_my_service_appointments(p_before, p_before_id, p_limit)` retorna snapshots paginados dos atendimentos de Guincho e dos atendimentos PitTec concluídos do participante autenticado. Cada snapshot inclui `rating_state: {submitted, can_rate}` e `created_at`; a consulta não inclui o comentário. A página padrão contém 20 registros com cursor por `created_at` e ID do atendimento.

## Comentários recebidos pelo prestador

`list_my_provider_ratings(p_before, p_before_id, p_limit)` resolve o prestador por `providers.account_id = auth.uid()` e retorna somente suas avaliações, sem aceitar um ID de prestador como entrada. Cada item contém `id`, `rating`, `comment` e `created_at`; página padrão de 20 itens, limite de 50, cursor por data e ID. Clientes e sessões anônimas não podem usar este contrato.
