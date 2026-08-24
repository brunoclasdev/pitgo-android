# Contract: RPCs de endereço do cliente

## Regras comuns

- `get_my_client_address` e `upsert_my_client_address` são `SECURITY DEFINER SET search_path = ''`,
  usam nomes totalmente qualificados e derivam o cliente de `auth.uid()` — nenhum parâmetro de
  identidade é aceito do app.
- `EXECUTE` é revogado de `public`/`anon` e concedido somente a `authenticated`; a conta precisa ter
  `account_type = 'CLIENT'` (mesma verificação `private.has_account_type('CLIENT')` já usada no
  restante do backend).
- `public.client_addresses` tem RLS habilitada e nenhum grant direto de tabela ao APK; toda leitura
  e escrita passa pelas duas RPCs abaixo.
- Inputs de texto são validados com as mesmas regras de normalização já usadas em
  `public.addresses` (trim, não vazio, limites de tamanho, CEP com 8 dígitos, UF válida).

## Projeção comum

```json
{
  "outcome": "FOUND",
  "address": {
    "street": "Rua Exemplo",
    "number": "100",
    "neighborhood": "Centro",
    "zip_code": "01001000",
    "city": "São Paulo",
    "state": "SP",
    "country": "BR",
    "complement": null,
    "latitude": -23.55,
    "longitude": -46.63
  }
}
```

Outcomes fechados: `FOUND`, `NOT_FOUND` (nenhum endereço cadastrado ainda — não é erro),
`SAVED` (após upsert bem-sucedido), `INVALID_INPUT` (validação falhou; mensagem não expõe detalhe
de SQL). A UI nunca interpreta texto livre de erro do backend.

### `get_my_client_address()`

Sem parâmetros. Retorna `NOT_FOUND` (sem `address`) quando o cliente ainda não cadastrou nenhum
endereço — este é o estado que hoje é representado apenas por um texto estático na Home
(`home_address_empty`); a partir desta feature, esse texto passa a refletir o resultado real desta
RPC.

### `upsert_my_client_address(p_street, p_number, p_neighborhood, p_zip_code, p_city, p_state, p_complement, p_latitude, p_longitude)`

Insere ou atualiza a única linha do cliente (upsert por `client_id`). `p_latitude`/`p_longitude`
são opcionais e, quando fornecidos, DEVEM ter vindo da busca de CEP/geocodificação já usada no
formulário — não são de digitação livre. Retorna o endereço salvo no mesmo formato de
`get_my_client_address`.

## Autorização

Nenhuma outra conta (`PROVIDER`, outro `CLIENT`, `anon`) pode ler ou escrever o endereço de um
cliente diferente do autenticado. O prestador **não** recebe este dado por nenhuma via — a posição
usada no mapa do prestador (US3) vem da leitura pontual de GPS do cliente durante o atendimento
(ver `data-model.md` #3), não deste cadastro.
