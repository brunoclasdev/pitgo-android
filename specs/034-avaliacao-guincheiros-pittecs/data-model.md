# Modelo de dados: avaliação de atendimento

## Entidade: ServiceRating

| Campo | Tipo lógico | Regra |
|---|---|---|
| id | UUID | Chave primária gerada pelo servidor |
| appointment_id | UUID | Obrigatório; referência ao atendimento; único para impedir mais de uma avaliação |
| client_id | UUID | Autor derivado do atendimento e da sessão autenticada |
| provider_id | UUID | Prestador derivado do atendimento; representa Guincho ou PitTec |
| rating | inteiro | Obrigatório, faixa de 1 a 5 |
| comment | texto anulável | Opcional; máximo de 500 pontos de código Unicode, espaços inclusos; texto vazio após btrim vira null |
| created_at | instante UTC | Gerado no servidor; representa envio definitivo |

Tabela implementada: `public.service_ratings`. As duas modalidades usam `public.appointments.provider_id` → `public.providers.id` e `client_id` → `public.profiles.id`. Cliente e prestador são derivados do atendimento; não são aceitos como autoridade no comando do app.

## Relações

- Um atendimento concluído elegível tem zero ou uma avaliação.
- Cada avaliação pertence a exatamente um cliente, um prestador e um atendimento.
- Prestadores agregam avaliações de seus atendimentos; modalidade Guincho/PitTec é obtida do atendimento, sem duplicar tabelas por tipo.
- A descrição permanece privada; resumo agregado não contém identificadores de clientes nem texto.

## Regras e elegibilidade

- Somente o cliente dono de um atendimento com status `COMPLETED` pode criar a avaliação.
- Atendimentos cancelados, contestados ou em andamento são inelegíveis. O backend valida o estado no instante da gravação.
- Nota é obrigatória e inteira, de 1 a 5; não há valor padrão.
- Descrição é opcional e tem no máximo 500 caracteres.
- Uma chave única em `appointment_id` garante no máximo uma avaliação mesmo com requisições simultâneas ou retry.
- A tabela não concede escrita direta ao cliente; a escrita ocorre somente pelo RPC autorizado. Não há operação de atualização ou exclusão de avaliação: o registro é append-only após o envio confirmado. Uma segunda submissão retorna estado já avaliado sem alterar conteúdo.
- Resumo disponível aos demais clientes: média e quantidade. Sem avaliações, quantidade zero e média ausente.
- Comentário visível somente ao autor, prestador associado e suporte autorizado. Outros clientes não podem inferir identidade do autor por contrato público.

## Estados de apresentação

O estado da tela deve ser explícito: pendente, enviando, enviado, falha recuperável ou já avaliado. A conclusão do atendimento não depende desse estado e nunca é revertida por dispensar ou falhar uma avaliação.
