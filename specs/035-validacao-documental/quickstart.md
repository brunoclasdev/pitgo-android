# Quickstart: Validação do cadastro documental

Este roteiro deve ser executado depois da implementação. Os comandos não foram executados para compilar funcionalidade ainda inexistente durante o planejamento.

## Pré-requisitos

- SDK/JDK configurados conforme o projeto; acesso aos ambientes dev/homol, dispositivo ou emulador com câmera funcional e internet.
- Novas migrations desta feature revisadas e aplicadas em ambiente isolado. Conferir lista pendente antes de aplicar; a migration local não versionada de guincho não faz parte deste roteiro.
- Edge Function `registration-evidence-confirm` implantada apenas no ambiente de validação, com autenticação e segredo restrito ao servidor quando necessário.
- Contas sintéticas: cliente novo, prestador novo com dois veículos de atendimento, cliente aprovado iniciando prestador, cliente/prestador anteriores ao corte (incluindo um inativo), staff ativo MANAGER e conta sem privilégio. Não usar documentos pessoais reais em validação técnica.
- Acesso à chamada autenticada de RPCs e Storage (dashboard em integração ou cliente HTTP seguro), sem tokens em comandos compartilhados ou histórico. Contratos em [registration-api.md](contracts/registration-api.md).

## Preparação e build

Na raiz do repositório:

```sh
git status --short
supabase migration list --local
./gradlew :app:assembleDevDebug :app:assembleHomolDebug
./gradlew :app:installDevDebug
```

Inspecionar a origem de cada migration antes de aplicá-la ao ambiente de validação pelo procedimento já adotado pelo projeto. Não usar `supabase db reset` em banco compartilhado nem `db push` sem revisar as migrations pendentes. A conexão local e propriedades por flavor devem seguir configuração existente, sem editar ou imprimir secrets.

## Corte em ambiente isolado

Preparar schema, helpers e instaladores sem executar o snapshot legado ou trocar o trigger antes da migration final. Implantar as Edge Functions compatíveis com o marcador privado de corte, sem cache da decisão e negando em falha de consulta. Aplicar `20261105114000_registration_atomic_cutover.sql`: backfill/snapshot, troca de trigger e ativação de guards SQL ocorrem na mesma transação, com serialização das escritas concorrentes de signup/membership. Verificar uma tentativa de cadastro concorrente ao corte: modalidade preexistente mantém dispensa, modalidade criada depois exige análise. Conferir falha/rollback em banco isolado: nenhuma ativação parcial pode permanecer. Não executar corte remoto durante o planejamento.

## Cenários de aceite

1. **Primeira etapa**: criar cliente e prestador com nome, sobrenome, e-mail, telefone, documento (prestador: CNPJ; cliente: CPF ou CNPJ), senha e confirmação. Não solicitar endereço/veículo antes da criação básica. Tentar CPF/CNPJ/e-mail duplicado, documento inválido e CPF no cadastro de prestador e confirmação divergente; nenhum cadastro duplicado deve surgir. Sair e entrar: obter sessão restrita, não falha de perfil nem acesso à Home comercial.
2. **Bloqueio efetivo**: com cada modalidade pendente, tentar navegação direta e chamadas autenticadas de discovery, criação de pedido, pagamento e disponibilidade. Esperado: recusa antes de qualquer efeito de negócio. Repetir pelo contexto v1 e caminho administrativo antigo de ativação; nenhum bypass. Cadastro, consulta de pendências e dados necessários ao onboarding continuam disponíveis.
3. **Cadastro complementar**: preencher dados vigentes de cada modalidade; interromper e reabrir/outro dispositivo. Somente dados/fotos confirmados são retomados. Prestador com dois veículos deve ter dois slots veiculares e quatro pessoais; cliente só quatro pessoais.
4. **Captura e upload**: tirar cada foto, revisar, substituir antes de enviar e testar cancelamento de câmera/arquivo inválido/mais de 10 MiB/falta de rede. Tentar submissão com uma reserva não confirmada: recusar. Após resposta de upload perdida, repetir com mesma operação e confirmar o objeto existente sem duplicação.
5. **Privacidade**: tentar ler objeto de outra conta, alterar path/reserva/titular e baixar sem JWT. Esperado: negar. Após submeter, tentar atualizar/deletar imagem e editar snapshot: negar. Conferir exclusão do backup Android e limpeza dos temporários no logout; não encontrar documentos, CPF ou credenciais nos logs.
6. **Submissão**: com todas as informações/fotos, enviar. Deve passar a IN_REVIEW e continuar bloqueado na modalidade. Login, novo upload e formulário salvo nunca ativam.
7. **Dashboard/contrato**: com staff MANAGER ativo, listar por situação/modalidade com paginação e abrir detalhe/fotos da versão correta. Sem staff ou com staff inativo, negar consulta e decisão. Aprovação sem conferência completa ou sobre versão antiga deve falhar.
8. **Correção**: solicitar correção identificando uma foto e motivo. Usuário vê pendência, cria nova versão e substitui a foto; dados/evidências mantidos não precisam ser recriados. Reenvio volta a IN_REVIEW, histórico anterior permanece e nenhum resultado antigo aprova a nova versão. Conferir no modelo: as fotos mantidas usam os mesmos IDs de arquivo e caminhos, com novos vínculos na versão corrente; vínculos da versão anterior não mudam. A foto substituída recebe novo arquivo e só troca o vínculo do novo rascunho.
9. **Aprovação e concorrência**: conferir todas as evidências e aprovar com revisão esperada. Atualizar estado no app: liberar apenas modalidade aprovada. Disparar duas decisões com a mesma revisão: uma confirma e outra recebe conflito; repetir a vencedora com mesma chave retorna resultado prévio, sem novo evento de ativação.
10. **Modalidades independentes**: cliente aprovado inicia prestador, envia documentos e recebe pedido de correção. Em todas as fases, deve continuar contratando como cliente e não atender como prestador. Após aprovação específica, ambas as modalidades ficam disponíveis respeitando restrições operacionais.
11. **Legado e migração**: contas anteriores ao corte mantêm situação sem nova documentação; a inativa não é reativada. Cliente legado que inicia prestador após o corte passa por análise. Conta nova sem case não é dispensada. Confirmar manutenção das exceções de signup do staff.
12. **Integrações**: confirmar que discovery omite prestador pendente; criação de pagamento é negada antes de chamar gateway para cliente pendente. Webhook/reconciliação legítimos de pagamentos anteriores continuam funcionando sem JWT móvel.

13. **Recuperação de senha**: solicitar redefinição para conta pendente e aprovada; resposta não revela se e-mail existe. Abrir retorno no aplicativo, informar senha válida e confirmação e realizar novo login. Estado de análise, documentos e capacidades permanecem iguais. Testar link inválido/expirado/reutilizado, falha de rede, limite de envio e reinício do app durante o retorno; sessão de recuperação não libera marketplace nem permite decidir cadastro.

## Evidências da validação

Registrar ambiente, versão, cenário, IDs sintéticos e resultado, sem copiar imagens, CPF ou tokens para logs/relatórios. Conferir relações e decisões conforme [data-model.md](data-model.md). Para segurança, verificar negativas no servidor, não apenas botões escondidos.

Medir consulta de cadastro/fila sem imagens em homol com 1.000 casos sintéticos (meta p95 até dois segundos); não gerar script/fixture automaticamente nesta fase. Em rodada com dez participantes, ao menos nove devem completar etapa básica em três minutos e explicar o próximo passo. Registrar limitações reais do dispositivo/rede, se houver.

A entrega da implementação é validada somente após build e cenários aplicáveis concluídos; o planejamento não declara esses resultados como executados.

Validação por incremento: na US3 executar cenários 7 e 9 pela interface de RPC e consulta de contexto, e a parte de 10 anterior à correção; ainda não exigir tela de acompanhamento ou comando de correção. Após US4 executar integralmente 8–10 no aplicativo. Na US1 incluir cenário 13 além de 1, 2 e 11.

## Resultado da execução em 2026-11-05

- `./gradlew :app:assembleDevDebug :app:assembleHomolDebug`: executado com sucesso nas duas variantes.
- `deno check`: executado nas 12 funções de servidor novas/modificadas, incluindo geocodificação e cancelamento compartilhado; sem erros de tipos após os ajustes.
- 17 migrations analisadas com parser PostgreSQL; corpos PL/pgSQL também analisados substituindo apenas declarações de tipos de tabela por `record` em memória, pois o parser isolado não tem catálogo. Isso valida sintaxe, não existência de colunas, semântica, políticas ou execução transacional.
- Decodificador ImageScript executado localmente com codificação/decodificação de imagem sintética, sem usar documentos reais.
- XML de manifesto, FileProvider e recursos validado; referências e formatação conferidas.
- Nenhum servidor PostgreSQL/Supabase respondeu na porta local 54322. Não foram aplicadas migrations, criadas contas remotas ou publicadas Edge Functions. Os cenários integrados com Auth/Storage, concorrência, permissões e corte permanecem pendentes.
- Não foi feita a rodada com dez participantes ou medição p95 com mil casos. Dispositivos conectados não foram modificados; as variantes usam o mesmo applicationId e uma instalação poderia substituir o ambiente em uso.

Pendências correspondentes: T022, T035, T040, T045, T048, T049 (parte de banco), T050 e T051. Os checkboxes desses itens permanecem abertos.

## Publicação na nuvem autorizada pelo usuário

Projeto: `pitgo-database` (`eetbrkznuqbdrnybnyhf`), produção. Executado com `npx supabase` após autorização explícita para aplicar as migrations.

- Histórico remoto reconciliado em staging com as migrations já aplicadas da master, sem alterar ou reaplicar a migration local de guincho.
- As 17 migrations originais foram aplicadas em duas etapas: 16 preparatórias, publicação de 12 Edge Functions necessárias e, por último, ativação transacional.
- Antes da aplicação, todas as definições foram executadas no schema remoto em transação com ROLLBACK. Foram preservadas as validações atuais de provider_kind, preços centralizados, prova de endereço e documentos veiculares.
- Corrigida uma ambiguidade de alias SQL encontrada no teste real de rascunho por meio da migration adicional `20261105115000_registration_draft_sql_alias_fix.sql`. Total publicado: 18 migrations.
- Verificação posterior: fluxo ativo, bucket privado, revisão negada a anon, tabelas privadas negadas a authenticated, trigger de cadastro instalado e 21 modalidades existentes preservadas como legadas.
- Teste com conta temporária em transação revertida: signup, contexto restrito, bloqueio do marketplace, salvamento de rascunho e coexistência CLIENT/PROVIDER passaram. Nenhuma conta de teste deve permanecer após o ROLLBACK.
- Validações completas de upload real, recuperação por e-mail, revisão humana, concorrência entre revisores, UI e medições com participantes continuam separadas desta publicação.
