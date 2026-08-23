# Contract: navegação e estados de UI

## Rotas centralizadas

### Sign Up com origem fechada

```text
SignUpOrigin = DEFAULT | PROVIDER
SignUpPattern = sign-up?origin={origin}
```

- “Criar conta” na Welcome usa `DEFAULT`;
- “Cadastrar empresa” na Welcome usa `PROVIDER`;
- valor ausente/desconhecido é tratado como `DEFAULT`, nunca como rota arbitrária;
- a origem não altera campos nem força tipo PF/PJ; apenas escolhe o próximo destino depois que a
  sessão central confirmar a conta.

### Cadastro de empresa

```text
ProviderRegistration = provider-registration
```

- sem argumentos;
- somente composto quando `AppSessionState.Authenticated`;
- incluído na aparência de superfície do app;
- `providerId`, owner, e-mail e CNPJ não são argumentos.

## Entradas

| Origem | Ação | Destino |
|---|---|---|
| Welcome | Criar conta | Sign Up `DEFAULT` |
| Welcome | Cadastrar empresa | Sign Up `PROVIDER` |
| Sign Up PROVIDER | sessão autenticada | ProviderRegistration como raiz autenticada |
| Perfil | Área da empresa | ProviderRegistration |

O fluxo PROVIDER é avaliado antes do onboarding de veículo naquela transição. A preferência de
onboarding existente não é apagada por esta feature.

## Saídas e back stack

- Voltar em formulário intacto retorna à origem.
- Voltar com draft alterado abre “Descartar cadastro?”; confirmar limpa o ViewModel antes de sair.
- Durante Submitting, saída destrutiva fica bloqueada e a UI informa que o cadastro está em andamento.
- `AlreadyRegistered` e `Success` mostram resumo dentro do mesmo destino.
- “Concluir/Voltar ao perfil” remove ProviderRegistration do back stack e volta à instância de
  Perfil; se não houver Perfil anterior (origem Sign Up), navega Perfil como raiz autenticada.
- Android Back depois da conclusão não reabre formulário ou Sign Up.
- Expiração/troca de sessão limpa o draft e pede refresh ao `SessionViewModel`; o guard global decide
  Welcome ou SessionGate.

## Contrato de renderização

```text
Loading
StartupFailure(reason)
Editing(content)
AlreadyRegistered(summary)
Success(summary)
SessionExpired
```

### Editing

- três headings em ordem: empresa, endereço e serviços;
- categorias e serviços usam `LoadState = Loading | Empty | Content | Failure`;
- serviço é desabilitado até categoria válida;
- ofertas usam `localId` estável, podem ser adicionadas/removidas sem teto do app e aparecem como
  itens da `LazyColumn`, não como listas aninhadas;
- submit requer ao menos uma oferta válida;
- estado Submitting desabilita inputs, add/remove, seletores e ação principal;
- falha temporária preserva draft em memória e mostra banner com retry;
- erro de campo usa supporting text + semântica e solicita foco no primeiro erro visual.

### Existing/Success

- exibir nome, contato comercial, endereço e todas as ofertas;
- indicar se o cadastro acabou de ser criado ou já existia;
- CNPJ é mascarado e IDs internos não aparecem;
- oferta gratuita é exibida a partir de `price=0`;
- oferecer uma ação clara de retorno ao Perfil.

## Responsividade e acessibilidade

- `Scaffold` + `TopAppBar`, sem bottom navigation no formulário/resumo;
- única `LazyColumn`, `imePadding`, largura máxima de 600dp e conteúdo em uma coluna;
- sem altura fixa, ellipsis ou scroll horizontal em campos/cards;
- áreas de toque mínimas de 48dp e ação principal com mínimo de 56dp;
- headings semânticos, erros com `error(...)`, banners/resultado com live region e loading com texto;
- controles de gratuito, remover e retry possuem rótulo textual; nada depende apenas de cor;
- IME Next nos campos single-line, newline em descrições multiline, dropdowns sem teclado;
- com fonte 200%, paisagem e teclado aberto, todos os campos/ações permanecem alcançáveis.
