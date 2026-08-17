# Quickstart Validation: Solicitar Atendimento

## Prerequisites

- JDK 17 configurado para executar o Gradle.
- Android SDK Platform 37 instalado.
- Emulador ou dispositivo Android API 24+ visível em `adb devices`.
- Implementação concluída conforme [plan.md](plan.md), [data-model.md](data-model.md) e
  [request-service-navigation.md](contracts/request-service-navigation.md).

## Build and static validation

Na raiz do repositório:

```bash
./gradlew clean assembleDebug lintDebug
```

Resultado esperado:

- `BUILD SUCCESSFUL`.
- APK em `app/build/outputs/apk/debug/app-debug.apk`.
- Nenhum erro de compilação, recursos, Navigation Compose, Compose ou lint bloqueante.
- Nenhuma dependência, arquivo XML de layout ou arquivo de teste novo.
- O catálogo compartilhado contém seis IDs únicos e os preços definidos em `data-model.md`.

## Install and launch

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.trustarx.pitgo
adb shell am start -n com.trustarx.pitgo/.MainActivity
```

Entre pela Welcome para alcançar a Home.

## Manual scenarios

### 1. Home → RequestService para os seis serviços

Para cada linha, volte à Home, toque o card e confira título, nome, ícone e estimativa:

| Home card | Expected estimate |
|---|---:|
| Bateria | R$ 320 |
| Troca de óleo | R$ 220 |
| Pneus | R$ 180 |
| Luz no painel | R$ 150 |
| Pequenos reparos | R$ 260 |
| Carro não liga | R$ 280 |

Confirme que nenhum nome/preço de uma seleção anterior permanece na tela e que toques rápidos no
mesmo card criam somente um destino no topo.

### 2. Conteúdo inicial

1. Abra Bateria.
2. Confirme o título “Solicitar atendimento” e a ação Voltar.
3. Confirme Serviço/Bateria, veículo `BMW 320i M Sport · 2020`, endereço
   `Av. Paulista, 1500 · São Paulo`, chegada `20-40 min` e estimativa `R$ 320`.
4. Confirme o campo opcional, PIX selecionado, CTA “SOLICITAR PITTECH” e o aviso completo.
5. Confirme fundo, superfícies, bordas, elevação, roxo, tipografia e espaçamento coerentes com a
   Home.

### 3. Descrição e teclado

1. Foque o campo e confirme label “Descreva o problema (opcional)” e placeholder solicitado.
2. Digite quatro ou mais linhas, use Enter e edite o início/fim do texto.
3. Confirme que o conteúdo permanece visível/editável e que o campo cresce ou rola sem corte.
4. Abra e feche o teclado; role até pagamentos, CTA e aviso.
5. Apague todo o texto e confirme que o CTA continua habilitado enquanto o status está `READY`.

### 4. Pagamento exclusivo

1. Confirme PIX como única opção inicial.
2. Selecione Crédito, Débito e PIX sucessivamente, inclusive com toques rápidos.
3. Após cada toque, confirme exatamente uma opção selecionada.
4. Confirme borda/fundo/texto roxos e check na selecionada; as demais permanecem brancas/com borda
   neutra.
5. Com TalkBack, confirme grupo de seleção, nome de cada opção e anúncio do estado selecionado.

### 5. Ação simulada e proteção contra repetição

1. Com qualquer descrição e pagamento, toque “SOLICITAR PITTECH” várias vezes rapidamente.
2. Confirme ripple no primeiro toque, uma única execução do callback mock e CTA desabilitado.
3. Confirme ausência de tela de confirmação, atendimento real, busca de PitTech, cobrança,
   solicitação de localização, rede ou notificação.
4. Volte à Home, reabra o serviço e confirme uma nova instância em estado `READY` com PIX inicial.

### 6. Back stack e serviço inválido

1. Abra um serviço e pressione a seta Voltar; confirme retorno à mesma Home.
2. Abra novamente e use Back do sistema; confirme o mesmo resultado.
3. Revise o destino `request-service?serviceId={serviceId}` em modo de inspeção/desenvolvimento com
   argumento ausente e com valor `UNKNOWN`.
4. Confirme `InvalidService`: mensagem compreensível, ação Voltar, nenhum serviço/preço/formulário
   e retorno seguro à Home.
5. Confirme que Bateria nunca é usada como fallback para entrada inválida.

### 7. Barra inferior fixa

1. Role do topo até o aviso final; a barra permanece fixa e nenhum conteúdo fica atrás dela.
2. Confirme Início, Serviços, Garagem, Atendimentos e Perfil, com Início selecionado.
3. Toque cada item: confirme feedback Material, permanência em RequestService e Início ainda
   selecionado, igual ao comportamento da Home atual.
4. Repita com navegação por gestos e navegação Android de três botões.

### 8. Responsividade, tema e barras do sistema

1. Valide smartphones compactos em portrait e landscape.
2. Repita com escalas de fonte 1,0×, 1,3×, 1,5× e 2,0× quando disponíveis.
3. Confirme textos completos, cards sem sobreposição e estimativas lado a lado quando couberem ou
   empilhadas quando necessário.
4. Confirme as três opções de pagamento alcançáveis e o CTA/aviso acima da bottom navigation.
5. Valide modo claro e escuro; confirme papéis semânticos do tema e contraste apropriado nas barras
   do sistema, como já ocorre na Home.

### 9. Acessibilidade

1. Ative TalkBack e percorra TopAppBar, cards, descrição, estimativas, pagamento, CTA, aviso e barra
   inferior na ordem visual.
2. Confirme headings, rótulos completos, ação Voltar, serviço/preço e pagamento selecionado.
3. Confirme um único foco por controle/card quando o ícone for decorativo.
4. Confirme alvos de pelo menos 48 × 48 dp e que nenhuma informação depende somente de cor.

### 10. Offline e escopo

1. Desative Wi-Fi e dados móveis e remova permissões concedidas ao app.
2. Percorra Welcome → Home → RequestService, edite o formulário e acione o CTA.
3. Confirme que conteúdo e interações continuam disponíveis e nenhuma permissão é solicitada.
4. Confirme ausência de backend, Supabase, pagamento real, localização, distância, chegada real,
   persistência, cobrança, push ou atualização em tempo real.

## Scope confirmation

A validação termina no callback local mock. Não são esperados atendimento persistido, confirmação,
prestador, pagamento, geolocalização, rede, destinos da barra inferior ou testes automatizados.
