# Quickstart: Validar ícone e splash do PitGo

## Pré-requisitos

1. Colocar `pitgo-icon.png` e `pitgo-image1200.png` no diretório de entrada definido pelo produto.
2. Confirmar que os dois arquivos foram copiados para os recursos do módulo Android sem alteração
   de conteúdo que comprometa a marca.
3. Ter um dispositivo ou emulador com Android API 24+ conectado e um segundo perfil com densidade
   ou tamanho diferente quando possível.

## Validação de compilação

```bash
./gradlew assembleDebug
```

Resultado esperado: build concluído sem erro e recursos de launcher/splash empacotados.

## Validação do launcher

1. Instalar o APK em uma instalação limpa.
2. Verificar o ícone na tela inicial, na lista de aplicativos recentes e nas informações do app.
3. Atualizar a mesma instalação com o novo APK e repetir a verificação.

Resultado esperado: a arte completa de `pitgo-icon.png` aparece legível dentro da área segura, sem
ícone genérico ou corte relevante.

## Validação da splash

1. Encerrar o app completamente.
2. Abrir pelo launcher e observar a inicialização.
3. Repetir em pelo menos três tamanhos/densidades quando disponíveis.
4. Medir visualmente que a splash não pisca antes de 500 ms e desaparece assim que a primeira tela
   funcional fica pronta.
5. Confirmar que uma inicialização rápida não deixa a splash presa e que uma falha de recurso não
   impede a entrada no app.

Resultado esperado: `pitgo-image1200.png` aparece inteiro, centralizado e sem deformação; a
transição para a primeira tela ocorre sem tela vazia, travamento ou interação extra.

## Referências

- Requisitos: [spec.md](./spec.md)
- Decisões: [research.md](./research.md)
- Recursos e estados: [data-model.md](./data-model.md)
