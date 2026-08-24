# Data Model: Busca de endereço por CEP

## Visão geral

A feature não cria entidade persistida. Ela acrescenta uma tentativa de consulta ao estado em
memória do cadastro empresarial e aplica um resultado validado ao `AddressDraft` existente.
Consulta de CEP e verificação/geocodificação são processos diferentes:

```text
CEP digitado ── Buscar CEP ──> PostalCodeLookupState
                                      │
                                      └── Found ──> AddressDraft editável
                                                           │
                                                           └── fluxo existente de verificação e cadastro
```

## Modelos do domínio de consulta

### PostalAddressResult

Resultado canônico aceito do ViaCEP.

| Campo | Tipo | Regra |
|---|---|---|
| `postalCode` | String | exatamente oito dígitos; igual ao CEP solicitado |
| `street` | String? | trim; vazio vira null |
| `complement` | String? | trim; vazio vira null |
| `neighborhood` | String? | trim; vazio vira null |
| `city` | String | trim, obrigatório e não vazio |
| `state` | String | uppercase; uma das 27 UFs |

Campos administrativos retornados pelo serviço não pertencem ao modelo.

### PostalCodeLookupResult

Resultado fechado produzido pelo Repository:

```text
PostalCodeLookupResult
├── Found(address: PostalAddressResult)
├── NotFound
└── Failure(reason: PostalCodeLookupFailure)
```

`PostalCodeLookupFailure` possui somente:

- `OFFLINE`;
- `TIMEOUT`;
- `SERVICE_UNAVAILABLE`;
- `UNEXPECTED_RESPONSE`.

Nenhum Throwable, status bruto, URL ou corpo é armazenado no estado de UI.

### PostalCodeLookupState

Estado renderizável dentro de `ProviderEditingContent`:

```text
PostalCodeLookupState
├── Idle
├── Loading(attemptId, postalCode)
├── Found(attemptId, postalCode, appliedFields)
├── NotFound(attemptId, postalCode)
└── Failure(attemptId, postalCode, reason)
```

Regras:

- `attemptId` é monotônico durante a vida do ViewModel;
- apenas uma tentativa pode estar `Loading`;
- estado/resultados só são válidos para o CEP armazenado no próprio estado;
- `Failure` é recuperável e não invalida o CEP;
- `NotFound` cria erro do campo para o mesmo CEP;
- `Found.appliedFields` registra quais campos realmente receberam valor não vazio.

### CepAddressField e CepAutofillProvenance

`CepAddressField` é enum fechado:

- `STREET`;
- `COMPLEMENT`;
- `NEIGHBORHOOD`;
- `CITY`;
- `STATE`.

`CepAutofillProvenance(postalCode, fields)` relaciona o CEP resolvido ao conjunto de campos
preenchidos pela resposta. O conjunto continua associado ao CEP mesmo que a pessoa corrija um dos
valores, para que a troca de CEP não carregue dados do endereço anterior. Campos nunca retornados
pelo serviço permanecem manuais e não são limpos pela proveniência.

## Extensão do estado do cadastro

`ProviderEditingContent` passa a conter:

- `postalCodeLookup: PostalCodeLookupState = Idle`;
- `cepAutofillProvenance: CepAutofillProvenance? = null`;
- os campos atuais `draft`, catálogo, verificação, erros, foco e submissão permanecem.

`AddressDraft` não ganha duplicação de valores. Seus campos atuais são a única fonte para a UI e
para a validação:

| Campo | Origem possível | Obrigatoriedade |
|---|---|---|
| `postalCode` | pessoa; normalizado para oito dígitos | obrigatório |
| `street` | ViaCEP ou pessoa | obrigatório |
| `number` | somente pessoa | obrigatório |
| `complement` | ViaCEP ou pessoa | opcional |
| `neighborhood` | ViaCEP ou pessoa | obrigatório |
| `city` | ViaCEP ou pessoa | obrigatório |
| `state` | ViaCEP ou seletor | obrigatório |

Latitude/longitude não são alteradas pela busca. A aplicação de um resultado invalida o
`AddressVerificationState` existente porque o endereço textual mudou.

## Erros de campo

`ProviderFieldError` ganha `POSTAL_CODE_NOT_FOUND`. Os erros existentes continuam tipados.

Regras de exibição:

- CEP vazio: `REQUIRED`;
- CEP com quantidade inválida: `INVALID_POSTAL_CODE`;
- CEP sintaticamente válido e inexistente: `POSTAL_CODE_NOT_FOUND`;
- falha temporária: pertence a `PostalCodeLookupState.Failure`, não ao mapa de campos;
- campo obrigatório vazio: `REQUIRED` junto ao próprio controle;
- formato, seleção, tamanho e indisponibilidade conhecidos usam razões distintas;
- `TEXT_TOO_LONG` é traduzido com o limite específico do campo.

## Transições

| Origem | Evento | Destino | Efeito no draft |
|---|---|---|---|
| Idle/Found/NotFound/Failure | `Buscar CEP` válido | Loading | nenhum |
| qualquer estado | `Buscar CEP` inválido | estado atual/Idle | sem rede; erro e foco no CEP |
| Loading | mesmo botão novamente | Loading | no-op |
| Loading | Found compatível | Found | aplica cinco campos presentes; nunca altera número |
| Loading | `erro=true` | NotFound | nenhum; marca CEP não encontrado |
| Loading | falha temporária | Failure | nenhum; preserva todos os dados |
| Loading | resposta incompatível | Failure(UnexpectedResponse) | nenhum |
| Loading | CEP ou campo derivável editado | Idle | cancela/invalida tentativa; preserva edição atual |
| Found | CEP alterado | Idle | limpa campos da proveniência; preserva número e campos manuais |
| NotFound | CEP alterado | Idle | remove erro não encontrado |
| Failure | Retry | Loading com novo ID | preserva CEP e draft |
| qualquer estado | início do submit | não Loading | não admite consulta ativa |
| qualquer estado | sessão perdida/trocada | estado raiz SessionExpired | cancela job e remove draft da UI |

Uma resposta só pode realizar a transição se `attemptId`, CEP capturado, estado `Loading` e CEP atual
forem simultaneamente iguais. Isso impede aplicação tardia inclusive em A → B → A.

## Aplicação atômica do resultado

Para cada valor não vazio retornado:

1. normalizar/validar a resposta completa;
2. copiar todos os valores aceitos em uma única atualização do `StateFlow`;
3. registrar os campos aplicados em `CepAutofillProvenance`;
4. remover somente erros que ficaram válidos após a atualização;
5. invalidar a verificação/geocodificação anterior;
6. focar o primeiro campo obrigatório ainda vazio ou, se completo, o número.

Se qualquer invariável de resposta falhar, nenhum campo é alterado.

## Validação e foco do formulário

A ordem canônica de validação segue a tela:

1. modalidade;
2. nome;
3. descrição (somente limite);
4. e-mail;
5. telefone;
6. CNPJ;
7. CEP;
8. rua;
9. número;
10. complemento (somente limite);
11. bairro;
12. cidade;
13. UF;
14. verificação existente, quando aplicável ao fluxo atual;
15. existência de oferta;
16. campos de cada oferta na ordem visual.

No primeiro submit inválido, todos os erros são gravados. Depois disso, editar um campo revalida
somente ele: o erro é substituído ou removido apenas quando a entrada se torna válida. A UI deriva o
índice do foco da mesma lista ordenada de linhas que renderiza, rola o item, aguarda sua composição e
então solicita foco/semântica.

## Lifecycle, segurança e privacidade

- draft, tentativa e proveniência ficam somente no ViewModel do destino;
- recomposição e rotação não reiniciam a chamada;
- não há `SavedStateHandle`, DataStore, Room ou cache em disco;
- consulta transmite somente o CEP na URL HTTPS;
- não há headers Supabase, cookies, CNPJ, e-mail, número ou endereço parcial;
- CEP, URL, resposta e exceção bruta não são registrados;
- consulta não cria nem altera registro no Supabase.
