# Contract: Home e alerta de Guincho

## Entrada e elegibilidade

O prestador autenticado MOBILE que oferece Guincho recebe a Home com mapa. Clientes, fixos e PitTec preservam suas telas. Modo do cadastro não muda. Home inicial não substitui atendimento ativo.

## Home

Entrada: posição precisa e estado de obtenção/permissão, disponibilidade e estado do mapa. Saída: eventos de permissão, recuperação, gesto e Recentralizar. UI não busca dados diretamente. Ao obter posição atual, centrar marcador próprio na área livre do mapa; ao atualizar, mover câmera somente com followEnabled. Gesto real desativa followEnabled; movimento programático não o altera. Na Home com mapa e card, reservar espaço suficiente para marcador e Recentralizar, inclusive com fonte ampliada; card extenso tem rolagem.

## Card global

Entrada: snapshot validado do pedido vigente, prazo calculado pelo deadline e estado de envio. Elegibilidade: GUINCHO_DIRECT/PENDING pago e dentro do prazo, com providerId da conta. Aparecer uma vez acima das rotas do guincheiro, incluindo Perfil e Histórico. No Perfil e Histórico, preservar a tela atual sem exigir mapa ou Recentralizar. Não mudar rota na chegada, não reutilizar tela ProviderRequest paralela para o mesmo pedido.

Conteúdo: novo atendimento, cliente, veículo, retirada, destino, remuneração sem os 10% e taxas e contagem regressiva. Aceitar e Recusar usam ações/revisão existentes. Durante envio, ações desabilitadas. Resposta confirmada decide navegação; falha recuperável exibe feedback e restaura estado autoritativo. Expiração/cancelamento remove card sem resposta tardia. Aceite abre tracking; recusa confirmada retorna à Home. Mensagem de erro não expõe códigos internos.

## Notificação

Reutilizar canal do PitTec, importância e preferências. Conteúdo genérico sem dados pessoais, também na versão pública da tela bloqueada. Uma notificação por appointmentId; revisão atualiza silenciosamente; timeout não ultrapassa deadline. Uma postagem sonora inicial por conta/pedido/aparelho sob configurações permitidas, inclusive depois de recriação de serviço. Notificação em primeiro plano não deve criar outro card nem repetir som.

Tocar abre MainActivity e recupera o pedido vigente: PENDING mostra o card sobre a rota atual ou Home se aplicação acabou de abrir; aceito abre tracking; encerrado mostra informação compreensível e não oferece ações. Contexto opcional no Intent limita-se a id/conta para identificar toque antigo, sempre comparado com snapshot autorizado. Troca de conta não pode abrir dados anteriores. Saída/troca remove notificações e dados visíveis, preservando somente o marcador mínimo até encerramento/expiração para impedir novo som ao retornar à mesma conta.

## Backend existente

Usar MobileDispatchRepository.restoreAnyDispatchKind(), observeProvider(), respond() e snapshot/revisão/deadline existentes. Eventos privados despertam consulta, não substituem autoridade. Conservar polling existente com requisição de refresh em voo única por observador e lifecycle. Não criar RPC nem chamar Mercado Pago diretamente para alertar.

## Limites e recuperação

Sem permissão de notificação: card continua disponível e há acesso às configurações. Sem localização: funcionalidades independentes continuam acessíveis; disponibilidade mantém suas regras existentes. Offline: indicar falha e revalidar ao reconectar. Segundo plano exige serviço existente ativo; force-stop não tem garantia de entrega, e abertura recupera o estado. Guardar apenas marcador de deduplicação sem dados pessoais, nunca coordenadas em logs.

Elegibilidade confirmada: ProviderSnapshot.locationMode == MOBILE, modalidade que recebe Guincho implicitamente no cadastro (feature 016 FR-003); sem comparação por nome traduzido.
