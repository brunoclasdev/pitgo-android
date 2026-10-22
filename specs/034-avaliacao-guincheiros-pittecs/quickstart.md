# Quickstart de validação: avaliação

## Pré-requisitos

- App Android configurado com ambiente Supabase autorizado e migrations locais aplicadas.
- Uma conta de cliente e contas de prestador Guincho e PitTec.
- Um atendimento concluído de cada modalidade, além de um atendimento em andamento ou cancelado.
- Seguir [contrato dos RPCs](contracts/service-rating-rpcs.md) e [modelo de dados](data-model.md).

## Validação ponta a ponta

1. Inicie um cronômetro ao abrir uma avaliação Guincho concluída ainda sem nota. Confira nota sem seleção inicial e texto opcional.
2. Envie nota 1 sem texto. Ao confirmar o envio, pare o cronômetro e verifique se o tempo foi de até dois minutos. Consulte novamente pelo histórico: deve aparecer avaliado, sem opção para alterar ou enviar outra.
3. Repita com PitTec usando nota 5 e descrição. Confira que a avaliação está associada ao atendimento e prestador PitTec corretos.
4. Feche a avaliação antes do envio em outro atendimento concluído. Confirme que o atendimento continua concluído; reabra pelo histórico e complete a avaliação.
5. Interrompa a conexão durante o envio. A tela deve manter nota/texto e oferecer retry. Após reconexão, confirme que uma gravação já feita no servidor retorna como avaliada e não gera segunda linha.
6. Tente avaliar atendimento em andamento, cancelado e contestado. O servidor deve negar sem gravar.
7. Consulte o resumo do prestador com uma conta de cliente diferente. Confirme média e quantidade corretas e ausência de comentário/autor.
8. Consulte a descrição com autor, prestador atendido e suporte autorizado; em seguida, tente com outro cliente e confirme que o texto não é retornado.
9. Consulte um prestador sem avaliações: deve aparecer Ainda sem avaliações, sem nota zero ou média inventada.

## Verificações de build

Na raiz do repositório, com o ambiente de desenvolvimento Android configurado:

- `./gradlew :app:assembleHomolDebug` — deve compilar o aplicativo.
- Aplique a migration no backend de desenvolvimento/homologação usando o fluxo já adotado pelo projeto antes do roteiro manual.
- O plano não cria uma nova suíte automatizada, em conformidade com a constituição do projeto.

## Validação executada em 2026-10-22

- Build `:app:assembleHomolDebug`: sucesso.
- Migration e regras de autorização exercitadas no banco de homologação em transações revertidas: envio nas duas modalidades, resposta `already_submitted` preservando a nota, histórico, comentário vazio/500 caracteres Unicode, recusa de nota inválida/501 caracteres/atendimento cancelado, resumo sem avaliações e leitura privada pelo autor/prestador/suporte. Outros clientes e acesso direto à tabela foram recusados. As avaliações usadas nessas consultas foram revertidas.
- Migration `20261022100000` publicada em homologação pelo CLI `npx supabase db query --linked`, com registro em `supabase_migrations.schema_migrations` na mesma transação. O `db push` não conseguiu autenticar a conexão direta.
- ASUS: build instalada; histórico compartilhado acessível pelo perfil, formulário sem seleção inicial e envio desabilitado, além da apresentação definitiva de uma avaliação registrada. Sem novo crash no processo validado.

## Perfil do guincheiro

1. Abra Perfil no guincheiro. Confira a média e a quantidade em Suas avaliações; sem notas, exibir Ainda sem avaliações.
2. Toque em Ver comentários. Confira nota, data e texto de suas avaliações; registros sem descrição devem indicar Sem comentário.
3. Volte da lista e confira que o perfil continua selecionado.
4. Confirme que clientes e sessões anônimas não conseguem usar `list_my_provider_ratings`, e que um prestador recebe somente registros vinculados ao próprio cadastro.

Validação do complemento: build HomolDebug aprovada; RPC publicada em homologação como migration `20261022103000`; consulta limitada ao prestador autenticado, paginação sem repetição e recusas de cliente/anônimo verificadas em transação revertida. No A33, média/quantidade, abertura da lista e retorno à aba Perfil foram conferidos.
