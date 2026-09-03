# Contract: Resolução do serviço implícito no cadastro de prestador

Esta funcionalidade não expõe nenhuma API nova ao backend. O único contrato relevante é a leitura
estendida do catálogo (campo adicional) e a garantia de que o envio final ao RPC existente continua
igual. Documentado aqui para deixar explícito o que a implementação pode/deve assumir.

## Leitura do catálogo (estendida)

Consulta direta à tabela `public.services` (sem RPC), já usada pelo cadastro hoje.

### Antes

Campos lidos: `id`, `category_id`, `name`, `description`.

### Depois

Campos lidos: `id`, `category_id`, `name`, `description`, `dispatch_kind`.

Nenhuma mudança de RLS ou de coluna no banco — `dispatch_kind` já existe e já é legível pelo mesmo
papel que hoje lê `name`/`description` nesta consulta.

## Resolução do serviço implícito de uma modalidade

Entrada: `dispatch_kind` alvo (`MOBILE_SEQUENTIAL` para Móvel, `PITTEC_AUTO_SEQUENTIAL` para
PitTec).

Regra:

1. Buscar até 2 serviços ativos com esse `dispatch_kind` (categoria e serviço já existentes).
2. Se exatamente 1 for encontrado: esse é o serviço implícito da modalidade.
3. Se 0 forem encontrados: a modalidade não pode ser usada para cadastro nesta instalação — cadastro
   bloqueado com falha compreensível.
4. Se 2 ou mais forem encontrados: catálogo está em estado inconsistente para essa modalidade —
   cadastro bloqueado com falha compreensível, sem tentar adivinhar qual usar.

Esta resolução roda ao selecionar a modalidade Móvel/PitTec (e novamente, silenciosamente, antes do
envio) — nunca ao selecionar Fixo.

## Envio do cadastro (`create_provider_registration`) — inalterado

O RPC e seu contrato de entrada/saída não mudam. O que muda é apenas como o array de ofertas
(`p_offers`) é montado no app antes da chamada:

| Modalidade | Como a oferta enviada é montada |
|---|---|
| Fixo | como hoje: uma ou mais ofertas escolhidas manualmente pelo prestador |
| Móvel | uma oferta com o `service_id` do serviço implícito resolvido; `price`/`duration_minutes` vêm do que o prestador digitou; `is_free` conforme o prestador marcar |
| PitTec | uma oferta com o `service_id` do serviço implícito resolvido; `price = 0`, `is_free = true`, `duration_minutes` com um valor padrão interno (não exibido, sem significado de cobrança) |

Todas as validações de formato já existentes no RPC (categoria/serviço ativos, duração entre 1 e
10080 minutos, preço coerente com `is_free`) continuam se aplicando sem exceção — a oferta montada
automaticamente para Móvel/PitTec já é construída para satisfazê-las.

## Erros

Nenhum código de erro novo é necessário. O bloqueio por serviço implícito ausente/duplicado (ver
acima) é tratado inteiramente no app antes de chamar o RPC, reaproveitando a mensagem de
"catálogo indisponível/alterado" já usada pelo cadastro para outras inconsistências de catálogo
(`ProviderFailure.CatalogChanged`).
