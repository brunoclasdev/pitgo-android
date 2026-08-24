# Quickstart: validar busca de endereço por CEP

## 1. Pré-requisitos

- Android SDK e JDK compatíveis com o projeto.
- Configurações Supabase e MapLibre já exigidas pelo app em `local.properties` ignorado ou
  propriedades Gradle.
- Dispositivo/emulador com internet para o cenário positivo e possibilidade de desligar a rede.
- Conta empresarial autenticada que possa abrir o cadastro de empresa.
- ViaCEP acessível por HTTPS; não é necessária chave.

Não executar migration ou `supabase db push`: esta feature não altera backend ou banco.

## 2. Confirmar o contrato externo

Na raiz do projeto, apenas para validação manual:

```bash
curl --fail-with-body --max-time 3 \
  --header 'Accept: application/json' \
  'https://viacep.com.br/ws/01001000/json/'
```

Esperado: JSON com Praça da Sé, Sé, São Paulo e SP. Para `99999999`, esperado é resposta de CEP
inexistente com `erro=true`, mesmo que o status HTTP seja 200.

## 3. Build e inspeção

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

Esperado:

- app compila sem dependência nova;
- nenhuma alteração de migration, manifest ou permissão;
- nenhum valor de resposta ou CEP real é impresso pelo build.

Opcionalmente confirme que a pilha existente continua única:

```bash
./gradlew :app:dependencyInsight \
  --dependency ktor-client-okhttp \
  --configuration debugRuntimeClasspath
```

## 4. Consulta positiva de referência

1. Entre como empresa e abra o cadastro empresarial.
2. Informe `01001000` no CEP.
3. Confirme que nenhuma consulta acontece apenas ao digitar o oitavo dígito.
4. Toque `Buscar CEP`.

Esperado:

- loading aparece com texto e impede somente outra busca/campos que poderiam ser sobrescritos;
- rua recebe `Praça da Sé`;
- complemento recebe `lado ímpar`;
- bairro recebe `Sé`;
- cidade recebe `São Paulo`;
- UF recebe `SP`;
- número permanece vazio, obrigatório e editável;
- foco segue para Número;
- todos os campos preenchidos continuam editáveis depois do resultado.

Corrija manualmente Rua e confirme que a correção é mantida enquanto o CEP não mudar.

## 5. Entrada e acionamento

Verifique separadamente:

- colar `01001-000` resulta em oito dígitos no estado e máscara visual correta;
- espaços/pontuação são descartados;
- mais de oito dígitos não são armazenados;
- com sete dígitos, botão fica desabilitado e submit mostra “Informe um CEP com 8 dígitos”;
- com oito dígitos, botão fica habilitado;
- `Search` no teclado produz a mesma ação explícita do botão somente quando válido;
- toques rápidos no botão geram apenas uma chamada enquanto `Loading`.

## 6. CEP inexistente

1. Consulte `99999999`.
2. Observe campo e mensagem.

Esperado:

- nenhum endereço antigo é aplicado ou apagado pela resposta;
- CEP fica marcado com “CEP não encontrado” e sem detalhes técnicos;
- submit não conclui enquanto o mesmo CEP conhecido como inexistente permanecer;
- editar o CEP remove esse erro e permite nova consulta.

## 7. Falhas temporárias e preenchimento manual

Teste ausência de rede, timeout e indisponibilidade/5xx com proxy ou ambiente controlado.

Esperado em todos:

- loading termina antes de deixar a tela bloqueada;
- CEP e todos os dados existentes são preservados;
- mensagem distingue offline, demora, indisponibilidade ou resposta inesperada;
- `Tentar novamente` reutiliza o CEP sem redigitação;
- preencher rua, número, bairro, cidade e UF manualmente continua permitido;
- uma falha temporária do ViaCEP não impede submit quando os campos estão válidos;
- nenhum retry automático é disparado.

## 8. Respostas tardias e troca de CEP

Com rede lenta/proxy:

1. inicie consulta do CEP A;
2. antes da resposta, altere para CEP B;
3. consulte B;
4. simule a chegada tardia de A.

Esperado: somente B pode preencher o formulário. Repita A → B → A e confirme que a tentativa antiga
de A não vence a tentativa nova, pois a geração também é comparada.

Inicie uma consulta e tente submeter: submit deve permanecer desabilitado/guardado até o lookup
terminar ou ser cancelado. Inicie submit e confirme que a busca fica desabilitada.

## 9. Proveniência e preservação do número

1. Consulte um CEP completo e informe um número.
2. Corrija manualmente um campo retornado.
3. Troque o CEP.

Esperado: campos que foram preenchidos pelo CEP anterior são limpos, inclusive a correção associada
àquele endereço; número permanece intacto.

Para um CEP genérico que omita rua ou bairro:

1. preencha manualmente o campo ausente;
2. execute a consulta;
3. confirme que a resposta vazia não apaga o valor manual;
4. edite/troque o CEP e confirme que apenas campos realmente aplicados pelo lookup anterior são
   invalidados.

## 10. Validação de todos os campos

No formulário, deixe erros espalhados, por exemplo:

- modalidade sem seleção;
- nome vazio;
- e-mail e telefone inválidos;
- CNPJ inválido;
- CEP incompleto;
- rua, número, bairro e cidade vazios;
- UF sem seleção;
- categoria/serviço ausentes;
- preço/duração inválidos.

Toque `Cadastrar empresa` uma vez.

Esperado:

- todos os campos/grupos problemáticos são marcados simultaneamente;
- cada mensagem explica obrigatório, formato, tamanho ou seleção esperada;
- primeiro inválido na ordem visual fica visível e recebe foco/é anunciado;
- corrigir parcialmente um valor ainda inválido mantém o erro;
- corrigir um campo por completo remove somente o erro dele;
- limites de texto informam a quantidade máxima, em vez de erro genérico;
- nenhuma chamada de cadastro acontece enquanto houver erro conhecido.

## 11. Rotação, teclado e acessibilidade

Validar em telefone compacto, paisagem e fonte 200%, inclusive com teclado aberto:

- CEP, botão, progresso e mensagem não se sobrepõem nem ficam cortados;
- em espaço estreito, botão fica abaixo do campo e ocupa a largura;
- em espaço confortável, o layout pode ficar horizontal sem truncar;
- todos os campos e ações continuam alcançáveis por scroll;
- rotação durante `Loading` não reinicia nem duplica a chamada;
- rotação após `Found` preserva os valores e a proveniência enquanto o destino existir;
- TalkBack anuncia campo, erro associado, botão, loading e resultado;
- loading e erros são compreensíveis sem depender de cor.

## 12. Privacidade e regressão

Inspecione tráfego por ferramenta local segura e Logcat:

- request contém somente `GET /ws/{cep}/json/` para `viacep.com.br`;
- não contém Authorization, token Supabase, cookie, CNPJ, e-mail, telefone, número ou endereço;
- URL/CEP, corpo, resposta e exceção bruta não aparecem nos logs;
- consultar CEP não cria registro no Supabase;
- fluxo existente de catálogo, geocodificação/verificação e cadastro final continua separado;
- sair ou perder/trocar sessão cancela a consulta e remove o draft conforme o comportamento atual.

## 13. Fora desta validação

Não validar como entrega 011: busca por rua/cidade, cadastro de cliente, geocodificação, mudança de
políticas Supabase, novo endereço persistido antes do cadastro, cache offline ou consulta em lote.
