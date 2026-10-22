# Guia de validação: navegação externa

**Feature**: [spec.md](spec.md) | **Contrato**: [external-navigation.md](contracts/external-navigation.md)

Os cenários abaixo orientam a implementação e a validação; resultados efetivos são registrados ao final.

## Pré-requisitos

- Ambiente Android/Java e configurações locais de Homol já usados no projeto, sem copiar chaves para documentação ou logs.
- Aparelho de prestador, incluindo A32 na validação, e outro aparelho/sessão de cliente para observar o compartilhamento. Conferir os demais modelos disponíveis para layout e versões Android.
- Waze e Google Maps instalados, configurados e com condições de localização/conectividade adequadas. Confirmar destino em ambos sem depender de um primeiro acesso.
- Atendimentos de validação existentes para guincho e PitTec, com aceite/pagamento autorizados e destinos conhecidos. Usar as ações normais do produto para chegar a cada etapa; não forçar status no banco ou produzir fotos fictícias.

## Compilar e abrir

Na raiz do repositório:

```bash
./gradlew :app:assembleHomolDebug
adb devices -l
```

Após escolher o identificador real do aparelho:

```bash
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/homol/debug/app-homol-debug.apk
adb -s DEVICE_SERIAL shell am start -n com.trustarx.pitgo/.MainActivity
```

Confirmar ambiente Homol e permissões habituais. Não solicitar nova permissão do PitGo apenas para abrir o navegador. Este fluxo não exige publicação de backend.

## Destinos e abertura: SC-001, SC-002

| Caso | Preparação | Resultado ao escolher Waze e, separadamente, Google Maps |
|---|---|---|
| Guincho até retirada | Atendimento `ACCEPTED`, autorizado | Caminho para `guinchoTrip.pickup`. |
| Guincho até destino final | Transporte iniciado pelas regras normais, `IN_PROGRESS` | Caminho para `guinchoTrip.destination`, preservando o ponto confirmado do endereço completo. |
| PitTec até cliente | `ACCEPTED`, autorizado, chegada ainda não registrada | Caminho para o ponto vigente do atendimento. |

Em cada combinação, abrir com até dois toques desde o acompanhamento, inclusive com resumo recolhido. Conferir caminho e destino dentro do aplicativo escolhido, sem redigitar endereço. Retornar pelo Android e verificar que a tela volta ao atendimento atual.

Medir 20 aberturas distribuídas pelos seis casos: pelo menos 19 devem apresentar o aplicativo escolhido em até 5 segundos desde o toque na opção, incluindo a revalidação. Registrar modelo, Android, versões dos navegadores, condições de rede, caso e duração sem dados pessoais ou coordenadas em logs compartilhados. Configuração inicial externa não integra essa amostra.

## Exceções e elegibilidade: SC-003, SC-005

1. Com apenas um navegador disponível, conferir as duas opções e indicação de indisponibilidade. Com nenhum disponível, conferir orientação e possibilidade de fechar sem perda de dados. Usar aparelho de validação ou configurações normais dos aplicativos e restaurar a disponibilidade ao terminar.
2. Se o aplicativo deixa de estar disponível após abrir o seletor, tentar escolhê-lo: mensagem recuperável, sem crash nem troca automática. Reabrir a escolha após restaurar o aplicativo.
3. Quando um atendimento real de validação estiver sem destino válido, tentar navegar: nenhuma abertura ou ponto substituto, mensagem compreensível e possibilidade de tentar novamente quando o ponto existir.
4. Com origem indisponível somente no PitGo, manter destino válido e abrir: o navegador recebe o destino e conduz seu próprio fluxo de localização. Com cálculo interno sem resultado, confirmar que a ação externa continua disponível.
5. Abrir a escolha e alterar/cancelar o atendimento pela outra sessão, conforme o fluxo autorizado. Escolher um navegador: usar o novo destino/etapa quando permitido ou informar mudança e impedir abertura. Falha de rede na revalidação deve orientar nova tentativa, sem abrir com contexto não confirmado.
6. Conferir ausência de ação habilitada para cliente, histórico, pagamento pendente, atendimento cancelado ou concluído, outra modalidade e PitTec após chegada/início do serviço. Para guincho em vistoria antes do início do transporte, o destino continua sendo a retirada.
7. Cancelar o diálogo, tocar repetidamente na opção, voltar do navegador e retomar após mudança de configuração da tela: nenhuma abertura duplicada ou transição de chegada, início, aceite, pagamento, vistoria ou conclusão causada pela navegação.

## Localização compartilhada: SC-006

Para cada perfil, iniciar o acompanhamento autorizado e observar o cliente em outro aparelho. Abrir o navegador e realizar deslocamento seguro de validação. Confirmar que o serviço de localização permanece ativo e o cliente continua recebendo posições conforme as condições existentes: publicação em movimento a partir de 2 segundos e heartbeat parado de 10 segundos. Esses são intervalos do produtor, sujeitos à aquisição, rede e regras atuais, não um SLA novo de entrega.

Retornar ao PitGo e conferir o estado atualizado. Nenhuma parada de rastreamento ou alteração de disponibilidade deve ser provocada pela abertura. Permissões negadas e restrições de bateria seguem os tratamentos existentes; não contornar controles do sistema para declarar o cenário aprovado.

## Marcadores e acessibilidade: SC-004

- Conferir guincho e PitTec, nas visões de prestador e cliente, com localização recente, última posição conhecida e ausência de posição. Manter o ícone padrão de cada participante e verificar que o relógio não aparece no acompanhamento.
- Em sessão de validação, interromper temporariamente a obtenção/publicação normal de localização e aguardar mais de 30 segundos. Conferir aviso de posição desatualizada; em offline/falha com ponto retido, conferir indicação de última posição conhecida. Restaurar as condições e confirmar nova posição e retirada do aviso correspondente.
- Sem posição válida, verificar orientação e ausência de ponto fabricado. Conferir também a indicação existente para posição antiga/sem horário do cliente PitTec.
- Usar TalkBack e fonte ampliada, com tema claro/escuro: nomes dos aplicativos, indisponibilidade, botão e avisos devem ser compreensíveis. Conferir que mapa, ações operacionais e seletor continuam acessíveis sem cortes, sobreposições ou expansão obrigatória do resumo.
- Verificar que o mapa de descoberta e seus controles continuam funcionais, pois usam parte da infraestrutura visual compartilhada.

## Evidência de conclusão

Registrar resultado da compilação, seis combinações, amostra de tempo, falhas, estados bloqueados, retorno, compartilhamento e apresentação nos aparelhos disponíveis. Distinguir cenários aprovados, falhas e cenários sem condição de execução. Não declarar validação em dispositivo somente pela compilação ou por `startActivity` bem-sucedido. Não criar testes automatizados, mocks ou fixtures para executar este guia.

## Registro da execução — 2026-10-22

- Branch confirmada: `feature/rota-externa`; início com alterações somente nos documentos da feature.
- Checklist de requisitos: 16/16 revisados, sem alteração de seus marcadores.
- Ignore files: `.gitignore` cobre os artefatos Android/Kotlin e configurações locais; nenhuma ferramenta adicional exige novo arquivo de ignore.
- A32, A33, A70 e ASUS conectados via ADB Wi-Fi. Google Maps instalado nos quatro; Waze ausente nos quatro no início da execução.
- Aceite manual e amostra de desempenho permanecem pendentes até a execução dos respectivos cenários. Não serão avançados estados operacionais ou criadas vistorias fictícias para obter evidência.

### Verificações executadas

- US1 compilou com `:app:assembleHomolDebug` em 1 min 2 s; integração US2 compilou em 22 s.
- Compilação integrada final e testes existentes: `./gradlew :app:assembleHomolDebug :app:testHomolDebugUnitTest`, concluídos em 16 s. Foram executados 9 testes, sem falha, erro ou teste ignorado; nenhum teste novo foi criado.
- APK Homol instalado com sucesso e aplicativo aberto em A32, A33, A70 e ASUS, preservando os dados locais. As telas de entrada de A32/A33 e as sessões existentes de A70/ASUS foram preservadas.
- Resolução dos intents de rota consultada no sistema nos quatro aparelhos: Google Maps resolve para sua Activity; Waze não possui Activity disponível. Isso verifica disponibilidade, sem comprovar desenho de rota ou lançamento pelo seletor do PitGo.
- No A70, foi aberto somente para leitura um atendimento concluído pelo histórico. Conferido visualmente e pela árvore de acessibilidade: “Abrir navegação” ausente; nenhum comando de chegada/início/conclusão foi executado. O mapa e a consulta da vistoria continuaram acessíveis.
- Revisão de privacidade: intents com pacotes fixos, apenas coordenadas e parâmetros constantes, sem identificadores de atendimento, origem fabricada ou dados pessoais; sem logs novos de URI/coordenadas e sem permissões novas.
- `git diff --check` passou; a implementação reutiliza as dependências e o serviço existentes. Nenhuma publicação de backend é necessária.

### Aceite ainda pendente

| Critério | Situação |
|---|---|
| SC-001 — seis combinações de rota/destino | Pendente: faltam atendimentos ativos autorizados nas etapas necessárias e Waze instalado/configurado. |
| SC-002 — dois toques e 19/20 aberturas até 5 s | Pendente: nenhuma amostra de navegação operacional foi produzida; consultas de resolução não contam como abertura. |
| SC-003 — recuperação de indisponibilidades | Disponibilidade conferida no sistema; fluxo completo do seletor/erros ainda pendente em atendimento autorizado. |
| SC-004 — mapas nas duas visões com posição atual/antiga/ausente | Produtores e textos implementados; matriz visual completa pendente nas sessões/estados necessários. |
| SC-005 — bloqueios, retorno e ausência de transições | Histórico concluído conferido no A70; retorno do navegador, concorrência e demais estados ainda pendentes. |
| SC-006 — compartilhamento durante navegação | Serviço preservado na implementação; observação ponta a ponta durante deslocamento real ainda pendente. |

Fonte ampliada/TalkBack nas novas ações e no seletor ainda dependem de uma sessão que permita alcançar essas interfaces. As tarefas T012, T016, T022, T024 e T026 permanecem abertas; T027 aguarda a consolidação final após esse aceite. Os registros acima são parciais e não substituem esses cenários.
