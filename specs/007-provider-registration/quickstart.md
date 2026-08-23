# Quickstart: validar cadastro de empresa prestadora

## 1. Pré-requisitos

- Android SDK/JDK compatíveis com o projeto.
- Docker e Supabase CLI para validação local do schema.
- `supabaseUrl` e uma chave publishable/anon em `local.properties` ignorado ou propriedades Gradle.
- Provider de e-mail habilitado no Supabase; para cadastro e sessão imediata locais,
  `enable_confirmations = false`, como já definido em `supabase/config.toml`.
- Ao menos uma categoria e um serviço relacionado no catálogo local para o cenário positivo. A
  migration não deve seedar dados de produção inventados; use dados administrativos/fixtures locais.

Não executar `supabase db push` remoto como parte desta validação. Aplicar migrations remotas é uma
ação posterior deliberada.

## 2. Banco local e build

Na raiz do repo:

```bash
supabase start
supabase db reset
supabase db lint
./gradlew :app:assembleDebug :app:lintDebug
```

Esperado:

- migrations recriam schema sem erro;
- lint não encontra função insegura/policy inválida;
- Android compila sem dependência nova;
- nenhuma credencial ou CNPJ aparece na saída.

Se o ambiente remoto já possuir as tabelas não versionadas, revisar o preflight da migration antes
de aplicá-la: provider singular/plural, FK de categoria e `neighbornhood`. Ambiguidade deve abortar,
nunca apagar ou mesclar dados automaticamente.

## 3. Preparar catálogo local

Pelo Supabase Studio local, usando papel administrativo:

1. crie pelo menos duas categorias com nomes distintos;
2. crie dois serviços em uma categoria e um serviço na outra;
3. não crie catálogo pelo aplicativo.

Confirme que uma sessão autenticada consegue apenas consultar catálogo e não inserir/alterar
categoria ou serviço.

## 4. Fluxos de entrada

### Nova conta com intenção de provider

1. Na Welcome, toque “Cadastrar empresa”.
2. Conclua o cadastro de conta.
3. Confirme que a sessão autenticada abre o cadastro de empresa, sem pedir ou mostrar owner ID.
4. Volte sem editar: deve retornar de forma previsível.

### Conta existente

1. Entre normalmente.
2. Abra Perfil e toque “Área da empresa”.
3. Para uma conta sem provider, deve aparecer o formulário com e-mail comercial pré-preenchido a
   partir do e-mail Auth e editável.
4. Alterar o contato comercial não deve alterar o e-mail de login.

## 5. Cadastro positivo

Preencha empresa e endereço válidos, incluindo CNPJ vigente, CEP de oito dígitos e UF. Deixe
coordenadas vazias. Configure:

- uma oferta paga com preço de duas casas e duração válida;
- uma oferta diferente marcada “Serviço gratuito”.

Confirme o cadastro.

Esperado:

- loading desabilita campos, adicionar/remover e botão principal;
- apenas uma confirmação é processada mesmo com toques repetidos;
- o resumo mostra empresa, endereço e duas ofertas, sem ID interno ou CNPJ completo;
- provider usa o mesmo UUID da conta;
- existe exatamente um endereço com owner derivado;
- a oferta gratuita persiste preço zero e a paga preserva centavos;
- provider, endereço e ofertas existem juntos.

Finalize e confirme que Perfil permanece na área autenticada e reabre o mesmo resumo por “Área da
empresa”, em vez de oferecer uma segunda criação.

## 6. Catálogo e múltiplas ofertas

1. Escolha a primeira categoria e confirme que só aparecem seus serviços.
2. Troque a categoria e confirme que o serviço anterior é limpo.
3. Adicione várias ofertas; remova itens intermediários e depois todas.
4. Tente enviar sem oferta: a tela deve pedir ao menos uma e focar o primeiro erro.
5. Adicione novamente e tente selecionar serviço já usado: a UI impede/orienta e o submit também
   rejeita duplicidade.
6. Se uma categoria não tiver serviços, confirme estado vazio e seletor de serviço desabilitado.
7. Remova/indisponibilize um serviço no catálogo administrativo depois de selecioná-lo e antes do
   submit: nenhum registro parcial deve ser criado; a UI pede nova seleção após recarregar.

Não há teto definido pelo app. Uma lista extensa deve continuar rolando de forma lazy e manter cada
oferta associada à sua chave local.

## 7. Validação local

Verifique, uma tentativa por caso:

- nome, e-mail, telefone ou CNPJ vazio;
- e-mail inválido e contato com máscara/espaços;
- CNPJ numérico inválido e CNPJ alfanumérico oficial válido;
- CEP com quantidade incorreta e UF fora das 27 opções;
- número `s/n`, bloco ou lote aceito;
- somente latitude, somente longitude e valores fora de -90/90 ou -180/180;
- preço vazio, zero sem gratuito, negativo, separador inválido e mais de duas casas;
- duração vazia, fracionada, zero e acima de 10.080.

Esperado: nenhuma chamada de criação em formulário inválido, todos os erros específicos visíveis e
foco/scroll no primeiro erro em ordem visual.

## 8. Atomicidade, concorrência e resposta perdida

### Falha antes do commit

Provoque uma violação definitiva no catálogo entre seleção e submit. Depois da falha, confira que
não foi criado provider, endereço nem oferta para aquela conta.

### Duas confirmações concorrentes

Dispare toques rápidos ou duas chamadas autenticadas equivalentes. Esperado: lock por usuário,
exatamente um agregado e resposta `ALREADY_REGISTERED` para a chamada posterior.

### Resposta perdida após commit

Interrompa a conexão imediatamente após confirmar e tente novamente quando voltar. Esperado: a RPC
reconhece o agregado completo, não sobrescreve dados, não duplica endereço/ofertas e a UI abre o
resumo como sucesso reconhecido.

Se houver provider legado sem endereço ou oferta, esperado é falha neutra de consistência; o app não
completa nem apaga esse registro silenciosamente.

## 9. Conflitos e falhas de rede

- Use e-mail comercial já usado: mensagem neutra no e-mail, sem revelar empresa.
- Use CNPJ já usado: mensagem neutra no CNPJ, sem revelar documento/empresa.
- Fique offline antes de carregar catálogo: estado de falha com Retry.
- Fique offline durante submit: draft não sensível permanece em memória e o CTA permite retry.
- Simule timeout/5xx: mensagem compreensível, sem SQLSTATE, constraint, URL ou payload.

Revise Logcat: não deve conter CNPJ completo, IDs de owner, token, Authorization, payload JSON,
`PostgrestRestException.message/details` ou stack trace com request.

## 10. Sessão e RLS

Com duas contas A e B:

1. A cria provider e ofertas.
2. B não consegue selecionar provider/endereço/ofertas de A e não consegue executar DML direto.
3. B não consegue passar ID de A porque a RPC não possui argumento de owner.
4. Expire/logout durante o formulário de B: campos, CNPJ, catálogo e ofertas somem antes da Welcome
   ou SessionGate.
5. Uma resposta tardia iniciada por B não pode atualizar a tela após entrar como A.

Confirme também que `anon` não possui SELECT/DML e que `authenticated` possui somente SELECT nas
tabelas + EXECUTE na RPC.

## 11. Acessibilidade e configuração

Validar em telefone compacto, paisagem e fonte 200%, com teclado aberto:

- todos os campos, seletores, ofertas, Retry e CTA alcançáveis;
- nenhuma label truncada nem scroll horizontal;
- TalkBack anuncia headings, erros, seleção, gratuito, remover, loading e resultado;
- alvos têm pelo menos 48dp e a ação principal 56dp;
- back com draft mostra confirmação; back durante envio não abandona operação incerta;
- rotação mantém o draft na mesma instância de ViewModel; encerramento de processo não restaura
  CNPJ/endereço, conforme a decisão de privacidade.

## 12. Fora desta validação

Não validar como parte da feature: criação/edição de catálogo, aprovação de provider, publicação,
agenda, imagens, cobrança, geocodificação, busca de CEP, localização, múltiplos endereços ou edição
posterior.
