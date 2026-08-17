# Quickstart Validation: Catálogo de Serviços na Homepage

## Prerequisites

- JDK 17 configurado para o Gradle.
- Android SDK Platform 37 instalado; o SDK local atual possui somente até 36.1.
- Emulador ou dispositivo Android API 24+ para validação manual.
- Implementação concluída conforme [plan.md](plan.md), [data-model.md](data-model.md) e
  [home-navigation.md](contracts/home-navigation.md).

Instale a plataforma pelo SDK Manager do Android Studio ou pelo `sdkmanager` disponível no SDK:

```bash
sdkmanager "platforms;android-37" "build-tools;36.0.0"
```

## Build and static validation

Na raiz do repositório:

```bash
./gradlew clean assembleDebug lintDebug
```

Resultado esperado:

- `BUILD SUCCESSFUL`.
- APK em `app/build/outputs/apk/debug/app-debug.apk`.
- Nenhum erro de AAR metadata, Compose, recursos, manifest ou lint bloqueante.

O baseline anterior ao plano falha em `checkDebugAarMetadata` porque Core 1.19.0 exige API 37 e o
projeto compilava com 36.1. A implementação deve resolver esse erro, não suprimi-lo.

## Install and launch

Com um dispositivo visível em `adb devices`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.trustarx.pitgo/.MainActivity
```

## Manual scenarios

### 1. Offline catalog

1. Desative Wi-Fi e dados móveis.
2. Abra o PitGo.
3. Confirme conteúdo útil em até 2 segundos.
4. Confirme, nesta ordem: Borracharia, Eletricista veicular, Bateria, Troca de pneu e Mecânico.

### 2. Responsive layout

1. Valide janela compacta em portrait e landscape.
2. Redimensione em multi-window e valide largura média e expandida/tablet.
3. Confirme que a quantidade de colunas se adapta sem cortes, sobreposição ou espaço inacessível.
4. Role até o último item quando necessário.

### 3. Font scaling and themes

1. Valide fonte padrão e a maior escala suportada pelo dispositivo.
2. Confirme nomes completos, inclusive “Eletricista veicular”, sem sobreposição.
3. Valide Light e Dark seguindo primeiro a configuração do sistema.
4. Confirme contraste e identificação dos cards sem depender apenas de cor.

### 4. Accessibility

1. Ative TalkBack.
2. Confirme um único foco por card.
3. Confirme que cada foco anuncia categoria, finalidade de buscar prestadores e ação disponível.
4. Confirme alvo de toque confortável e acionamento por acessibilidade.

### 5. Selection and duplicate protection

1. Toque uma vez em cada categoria, voltando à Home entre tentativas.
2. Confirme que o destino “Busca de prestadores” mostra a categoria correta.
3. Toque rapidamente várias vezes no mesmo card.
4. Confirme apenas uma entrada no destino e apenas uma ação Voltar para retornar.

### 6. Back behavior and state restoration

1. Role a grade antes de selecionar uma categoria.
2. Entre no destino da categoria.
3. Use o botão ou gesto Voltar do Android.
4. Confirme retorno à Home sem segunda instância e com posição de rolagem preservada.

### 7. Invalid internal argument

1. Durante desenvolvimento, abra o destino com um `categoryId` fora da tabela do contrato.
2. Confirme mensagem local compreensível e ação Voltar.
3. Confirme ausência de crash ou exposição de detalhe técnico.

## Scope confirmation

A validação termina quando a categoria correta chega ao destino de entrada e o retorno funciona.
Não são esperados prestadores reais, rede, localização, autenticação, solicitação ou pagamento.
Nenhum teste unitário, de UI ou integração é criado por esta feature sem pedido explícito.
