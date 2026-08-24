# Contract: Consulta ViaCEP

## Escopo

Este contrato define a única operação externa adicionada pela feature. Ela consulta um endereço para
assistir o preenchimento do draft; não autentica, não persiste dados e não valida posse ou localização
geográfica.

## Operação

```http
GET https://viacep.com.br/ws/{postalCode}/json/
Accept: application/json
```

### Pré-condições

- `postalCode` contém exatamente oito caracteres ASCII entre `0` e `9`;
- o host, scheme e caminho base são constantes do app;
- nenhum query parameter, body, cookie ou header de autenticação é enviado;
- valor inválido é rejeitado localmente e não chega à rede;
- existe no máximo uma chamada ativa por ViewModel.

## Resposta encontrada

Campos consumidos:

```json
{
  "cep": "01001-000",
  "logradouro": "Praça da Sé",
  "complemento": "lado ímpar",
  "bairro": "Sé",
  "localidade": "São Paulo",
  "uf": "SP"
}
```

Mapeamento:

| ViaCEP | Domínio | Formulário |
|---|---|---|
| `cep` | `postalCode` normalizado | CEP |
| `logradouro` | `street` | Rua |
| `complemento` | `complement` | Complemento |
| `bairro` | `neighborhood` | Bairro |
| `localidade` | `city` | Cidade |
| `uf` | `state` | UF |

`cep`, cidade e UF válidos são obrigatórios para aceitar a resposta. Rua, complemento e bairro
vazios viram null e não substituem valor manual. Campos desconhecidos são ignorados. Número nunca
é lido ou escrito por esta operação.

## CEP inexistente

O endpoint retorna HTTP 200 com um campo `erro` verdadeiro. O decoder aceita as formas observadas e
compatíveis:

```json
{ "erro": "true" }
```

```json
{ "erro": true }
```

Ambas produzem `NotFound`. Outros valores não são tratados como inexistência automaticamente; o
restante do contrato ainda precisa ser válido.

## Compatibilidade e confiança

Uma resposta 2xx só produz `Found` quando:

1. o JSON é um objeto decodificável;
2. `erro` não é verdadeiro;
3. o CEP retornado normalizado é idêntico ao solicitado;
4. cidade não está vazia;
5. UF pertence à allowlist brasileira.

Se uma condição falhar, o resultado é `UnexpectedResponse` e nenhum dado do draft muda.

## Status e falhas

| Sinal | Resultado |
|---|---|
| 200 + contrato válido | `Found` |
| 200 + `erro=true` | `NotFound` |
| timeout de request/conexão/socket, 408 ou 504 | `Failure(TIMEOUT)` |
| DNS ou rota indisponível | `Failure(OFFLINE)` |
| 429, 500–599 ou conexão transitória interrompida | `Failure(SERVICE_UNAVAILABLE)` |
| 400 ou outro status/contrato/JSON/TLS inesperado | `Failure(UNEXPECTED_RESPONSE)` |
| cancelamento da coroutine | relançar `CancellationException`; não produzir resultado de UI |

Timeouts do cliente:

- request: 2.500 ms;
- connect: 1.500 ms;
- socket: 2.000 ms.

Não existe retry automático. A nova chamada depende da ação `Buscar CEP`/`Tentar novamente`.

## Privacidade e observabilidade

- cliente dedicado, sem plugin Auth ou sessão Supabase;
- somente CEP no path e metadados normais da conexão HTTP;
- sem logging HTTP, corpo, resposta, URL completa ou exceção bruta;
- sem cache persistente;
- nenhum token, CNPJ, e-mail, telefone, número residencial ou endereço parcial;
- erros expostos à UI pertencem somente ao enum fechado de domínio.

## Ausências explícitas

- busca por rua/cidade/UF;
- geocodificação;
- proxy Supabase;
- API key;
- persistência ou alteração do endereço remoto;
- prefetch, consulta automática e batch de CEPs.

Fonte do contrato externo: [ViaCEP](https://viacep.com.br/).
