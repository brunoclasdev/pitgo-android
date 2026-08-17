# Quickstart Validation: Layouts iniciais do PitGo

## Prerequisites

- JDK 17 configurado para executar o Gradle.
- Android SDK Platform 37 instalado.
- Emulador ou dispositivo Android API 24+ visível em `adb devices` para validação manual.
- Implementação concluída conforme [plan.md](plan.md), [data-model.md](data-model.md) e
  [initial-ui-navigation.md](contracts/initial-ui-navigation.md).

## Build and static validation

Na raiz do repositório:

```bash
./gradlew clean assembleDebug lintDebug
```

Resultado esperado:

- `BUILD SUCCESSFUL`.
- APK em `app/build/outputs/apk/debug/app-debug.apk`.
- Nenhum erro de compilação, AAR metadata, recursos, manifest, Compose ou lint bloqueante.
- Nenhum arquivo de teste novo.

## Install and launch

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.trustarx.pitgo
adb shell am start -n com.trustarx.pitgo/.MainActivity
```

## Manual scenarios

### 1. Welcome content and fixed brand treatment

1. Abra o aplicativo em modo claro.
2. Confirme que Welcome é o primeiro destino.
3. Confirme gradiente roxo, wordmark textual PitGo, título em duas linhas e subtítulo “Encontre
   assistência para bateria, óleo, pneus e muito mais, onde você estiver.”.
4. Confirme os três benefícios, textos e ordem definidos em `data-model.md`.
5. Confirme as ações Criar conta, Entrar e Sou uma empresa · Cadastrar.
6. Troque o sistema para modo escuro e reabra; o gradiente da Welcome deve permanecer igual.

### 2. Welcome responsiveness

1. Valide smartphone compacto em portrait e landscape.
2. Repita com escalas de fonte 1,0×, 1,3×, 1,5× e 2,0× quando disponíveis.
3. Role do wordmark até o último botão.
4. Confirme ausência de corte, sobreposição, altura fixa problemática e controles atrás das barras
   do sistema.

### 3. Welcome actions and navigation

1. Toque Criar conta e Cadastrar empresa; confirme feedback visual e permanência na Welcome.
2. Toque Entrar rapidamente várias vezes.
3. Confirme uma única Home no topo do histórico.
4. Pressione Back uma vez; confirme retorno à mesma Welcome.
5. Toque Entrar novamente; confirme que uma nova entrada válida continua funcionando.
6. Volte à Welcome e pressione Back; confirme o comportamento padrão de saída.

### 4. Home demonstrative content

1. Entre na Home e confirme “Olá,”, “Rafael 👋” e avatar R.
2. Confirme BMW 320i M Sport, `2020 · PGO-2A20`, ícone de veículo e endereço exato.
3. Confirme o título principal e o card “Não sei o que aconteceu”.
4. Confirme os seis serviços, ordem e valores exatos definidos em `data-model.md`.

### 5. Grid and fixed bottom navigation

1. Confirme exatamente duas colunas em smartphones portrait e landscape.
2. Role até Carro não liga; o card inteiro deve ficar acima da barra inferior.
3. Confirme Início, Serviços, Garagem, Atendimentos e Perfil, com Início selecionado.
4. Toque cada item inferior; confirme feedback, seleção ainda em Início e ausência de navegação.
5. Repita com navegação por gestos e navegação de três botões.

### 6. Home interactions

1. Toque o card de diagnóstico e cada serviço.
2. Confirme ripple/pressed state em todos.
3. Confirme que nenhum toque muda o conteúdo, a seleção inferior ou o destino.

### 7. Home Light/Dark and scalable content

1. Em modo claro, confirme fundo `#FAF9FD`, textos e contraste conforme o design system.
2. Em modo escuro, confirme fundo/superfícies próximos ao preto e roxo de destaque.
3. Verifique avatar, veículo, diagnóstico, serviços e barra inferior nos dois modos.
4. Confirme ícones claros nas barras do sistema sobre a Welcome e contraste correto das barras
   sobre a Home clara/escura.
5. Repita a grade em fonte ampliada; nomes e preços podem quebrar linha, mas não truncar, sobrepor
   ou reduzir dinamicamente.
6. Verifique especialmente Atendimentos em larguras compactas e texto ampliado.

### 8. Accessibility

1. Ative TalkBack.
2. Confirme ordem de foco: cabeçalho, contexto, diagnóstico, serviços e barra inferior.
3. Confirme um único foco por card, nome/finalidade completos e ação disponível.
4. Confirme que Início é anunciado como selecionado.
5. Confirme alvos confortáveis e acionamento por recurso de acessibilidade.
6. Confirme que ícones decorativos não repetem anúncios e que informação não depende apenas de cor.

### 9. Offline and scope

1. Desative Wi-Fi e dados móveis; limpe permissões concedidas ao app.
2. Reabra e percorra Welcome → Home.
3. Confirme que todo conteúdo e todas as interações continuam disponíveis.
4. Confirme ausência de solicitação de login, localização ou dados pessoais.
5. Confirme ausência de destinos de cadastro, prestadores, diagnóstico, serviços, garagem,
   atendimentos ou perfil.

## Scope confirmation

A validação termina na Home demonstrativa. Não são esperados autenticação, cadastro, prestadores,
diagnóstico funcional, contratação, localização, rede, persistência ou navegação inferior. Nenhum
teste unitário, de UI ou integração é criado por esta feature.
