# Contract: Captura, revisão e histórico da vistoria

**Base**: [spec.md](../spec.md), [guincho-inspection-api.md](guincho-inspection-api.md).

## Integração Android

- Adicionar rota centralizada `guinchoInspection(appointmentId)` em `PitGoRoutes.kt`/`PitGoNavHost.kt`. Uma tela resolve seu modo pela sessão e estado autoritativo: captura do guincheiro, revisão do cliente ou consulta de histórico. Parâmetro de rota não concede permissão.
- `GuinchoInspectionViewModel` expõe StateFlow imutável, usa `SavedStateHandle` e chama `GuinchoInspectionRepository`. Repository centraliza RPCs/Storage; `GuinchoInspectionCaptureFiles` cuida de arquivo, leitura/normalização e cache privado com Context de aplicação injetado. ViewModel não retém Context, Activity, View ou Bitmap.
- Composables recebem estado e eventos. Lançar câmera com Activity Result é efeito da camada de UI; regra de seleção/salvamento não fica no Composable. Preparação/processamento de arquivo ocorre fora da Main Thread.
- Acrescentar modelos/DTOs de vistoria em `features/guincho/{model,data}` e resumo opcional nos DTOs compartilhados de mobiledispatch. Não criar subframework de câmera, infraestrutura de sync, Room, WorkManager ou use case de repasse.

## Acompanhamento do atendimento

1. Guincheiro a caminho: “Cheguei” registra chegada. Após sucesso, abre a vistoria; erro mantém retry da mesma intenção. Separar `arrive()` de `startTransport()` no ViewModel compartilhado, preservando PitTec e demais serviços.
2. Chegada com DRAFT: mostrar “Fotos pendentes” e “Continuar vistoria”. Enquanto SUBMITTED, “Aguardando aprovação das fotos”. Na correção, motivo e ação “Corrigir fotos”.
3. Cliente recebe um bloco visível de revisão acima da área recolhível dos detalhes do mapa. Sua ação abre a tela da vistoria vigente.
4. APPROVED + ACCEPTED: guincheiro vê “Fotos aprovadas” e “Iniciar transporte”; cliente vê espera pelo guincheiro. Retorno do app mantém essa etapa. A aprovação não dispara startTransport por efeito de UI.
5. Só START confirmado libera “Concluir atendimento”. Aprovar fotos nunca chama confirmCompletion. A conclusão existente continua exigindo cliente.
6. Observar revisão por consulta/CHANGED existentes. Não criar outra consulta de 2 segundos dentro do mapa. Na tela dedicada, suspender observador do mapa quando oculto; observar resumo leve enquanto visível, recarregando detalhes apenas quando a revisão mudar, com uma requisição em voo por recurso.
7. Estado terminal cancela operações pendentes e transforma a vistoria em consulta, sem remover material salvo. “Voltar” respeita o destino de entrada; não navegar automaticamente para Home ao consultar serviço antigo.

## Captura

- Exibir identificação do veículo contratado, orientação de frente/traseira/laterais em relação ao próprio veículo, quatro pontos com status textual e progresso, fotos extras e prévia.
- “Tirar foto” usa TakePicture com content URI de FileProvider, em pasta restrita `cacheDir/guincho-inspections/<environment_fingerprint_hash>/<account_id>/`. Não usar file URI nem MediaStore/galeria; provider não exportado e concessão temporária ao app de câmera.
- Guardar ponto, URI, intenção de captura, operação, conta e fingerprint de ambiente em SavedStateHandle antes de abrir a câmera. No callback da câmera, rejeitar intenção de outra sessão/ambiente e limpar o arquivo temporário correspondente. STOP não apaga esses campos. Resultado cancelado/vazio/ilegível mantém seleção anterior e ponto pendente; se arquivo temporário desapareceu, orientar nova captura.
- Ao confirmar a captura na câmera, retornar ao álbum e salvar automaticamente a imagem JPEG normalizada no rascunho, passando por reserva remota, upload privado e confirmação. Exibir miniatura/progresso no ponto correspondente; falha conserva a prévia e oferece nova tentativa. Refazer continua disponível antes de enviar o conjunto para aprovação. Descrição opcional de avaria é preenchida antes de abrir a câmera. Não representar cache local como salvamento concluído.
- Se o Android recriar o processo durante a câmera, manter a entrada restaurada da vistoria e seu SavedStateHandle enquanto a sessão inicializa/verifica. Nesse intervalo, exibir somente loading autenticado pendente; registrar/receber o resultado da câmera após a sessão válida sem perder a rota para o acompanhamento. Sessão inválida, logout e troca de conta/ambiente continuam encerrando o acesso.
- Uma operação de captura/upload por vez no aparelho evita ambiguidade; simultaneidade entre aparelhos continua protegida por revisão do servidor. Retry mantém chave da intenção. O usuário pode descartar reserva não confirmada; isso não apaga fotos READY.
- “Enviar fotos para aprovação” só fica habilitado com quatro pontos selecionados READY, sem envio pendente. Refazer gera novo objeto; versões enviadas ficam somente leitura.
- Correção abre sucessora que reutiliza fotos anteriores e mostra o motivo. Permitir substituir pontos, acrescentar detalhes ou desmarcar fotos do rascunho, conservando tudo que já foi confirmado no histórico.
- Indisponibilidade/bloqueio de câmera oferece orientação e nova tentativa. Como o app delega a captura, não solicitar CAMERA desnecessariamente; falha do app de câmera não dispensa vistoria.

## Revisão pelo cliente

- Identificar veículo, número da versão, quatro pontos e imagens adicionais. Miniaturas com loading/erro/retry e ampliação acessível; não depender só de cor para informar pendências.
- Carregar fotos de forma paginada/lazy sem perder contagem total. Manter confirmação de apresentação por `(version_id,photo_id)`; todos os itens do manifesto devem poder ser apresentados e ter carregamento bem-sucedido antes de habilitar aprovação. A navegação por páginas pode apresentar as miniaturas; não exigir abrir cada imagem individualmente em tela cheia.
- “Aprovar fotos” envia apenas a versão vigente e revisão esperada. Após confirmação da aprovação pelo servidor, retornar o cliente ao acompanhamento da mesma transação, aguardando o guincheiro iniciar o transporte. Emitir esse retorno somente como resultado da ação do cliente; consultar fotos já aprovadas permanece possível. Limpar confirmações de carregamento quando a versão mudar; falha em foto bloqueia aprovação até recuperação. Não aprovar automaticamente depois de retry ou troca de versão.
- “Solicitar correção” exige motivo aparado de 1 a 1000 caracteres; decisão bem-sucedida mostra espera pelo guincheiro. Não permitir aprovação simultânea da versão já corrigida.
- Cliente offline ou que sai da tela mantém pendência. Mostrar progresso durante decisão e erro recuperável, sem sucesso otimista irreversível.

## Histórico e cache

- `GuinchoHistoryScreen` já abre `appointmentTracking(id,historical=true)`. Acrescentar “Ver vistoria” nesse detalhe, abrindo a mesma rota em consulta. Sem registro, “Vistoria não registrada”.
- Mostrar versões, pontos, autor, captura e salvamento, decisão/data/hora, motivo de correção e destaque da aprovada. Fotos READY fora de conjunto enviado ficam identificadas como “Não enviada”. Nenhuma vira aprovada por pertencer ao mesmo pedido.
- Download no Repository via Storage autenticado. Antes de servir cache, validar sessão e vínculo atual do snapshot. Arquivos e chaves de imagem separados por EnvironmentConfig.fingerprint + account_id + photo_id; cancelar trabalho no logout, limpar temporários/cache dessa conta e remover entradas de imagem correspondentes. Repetir a limpeza no callback clearIncompatible de EnvironmentStateRepository.validate em PitGoAppContainer, pois troca de flavor/backend não passa necessariamente por beforeSignOut.
- Coil existente dimensiona miniaturas/ampliação; manter cache em memória da sessão e arquivos privados com limite local de 128 MiB e descarte LRU dos downloads confirmados. Evicção de download não apaga evidência remota; imagens da captura pendente não são removidas pelo LRU durante a intenção ativa. Não criar cache global compartilhável nem logar caminhos/tokens.
- Dados do histórico não exigem novo acesso à câmera. Consultar, corrigir, aprovar e iniciar respeitam a conta autenticada; outra conta não herda foto, decisão ou URI pendente.

## Acessibilidade e design

Material3 e tokens existentes, Light/Dark/System Default, strings centralizadas em pt-BR, rótulos para ponto/foto e ampliação, fonte ampliada, botões alcançáveis e área de toque mínima. Tela pequena deve rolar sem esconder ações nem deslocar navbar global. Informar ação principal e estado com texto, não apenas ícone/cor.

## Suporte

No repositório do dashboard, integrar uma galeria somente leitura ao detalhe de contestação existente. Consultar o manifesto auditado com sessão MANAGER+ e usar rota de imagem autenticada do servidor. Não adicionar permissão para aprovar/editar vistoria, nova fila de suporte ou nova decisão financeira. Ler o AGENTS.md e os guias Next.js locais aplicáveis antes de implementar.
