# Quickstart de validação: Guincho por porte e retomada

Este guia será executável após implementar as tarefas. Nenhuma migração, RPC novo ou interface descrita aqui foi implantada pela fase de planejamento.

## Pré-requisitos

- Android SDK/JDK do projeto e dispositivo/emulador API 24+; usar ambiente homol isolado com configuração pública existente em `config/homol.properties` (não copiar credenciais ou imprimir valores).
- Supabase CLI configurado para ambiente local descartável ou homologação autorizada, migrations atuais e as novas da feature aplicadas. Não resetar dados de produção.
- Duas contas cliente para validar isolamento, dois guincheiros com localização disponível/publicada e uma conta dashboard MANAGER, além de staff sem permissão de gestão.
- Catálogo e matriz de compatibilidade aprovados pela operação, incluindo automóvel e micro-ônibus, e dois portes com valores diferentes. Pedidos legados pagos apenas em sandbox/homologação, sem cobrança real.
- Dashboard externo: antes de executar T031, identificar repositório acessível, caminhos reais da interface/cliente RPC e responsável no [contrato administrativo](contracts/dashboard-catalog.md). T031 só termina com a integração executada; T033 permanece pendente até a interface integrada estar disponível. A ausência dessa integração impede validar a entrega administrativa completa, mas não bloqueia tarefas locais independentes.

## Compilar e executar Android

Na raiz do projeto:

```sh
./gradlew :app:assembleHomolDebug
./gradlew :app:installHomolDebug
```

Abrir a variante homol instalada. Para backend local já iniciado em ambiente descartável:

```sh
supabase migration up --local
supabase functions serve guincho-quote --env-file supabase/.env.local
```

O arquivo de ambiente local deve existir fora de versionamento, com as configurações do roteador/serviço exigidas pela função existente; não criar nem exibir segredos neste guia. Para Android, o endereço configurado deve alcançar o backend a partir do emulador/dispositivo. Backend remoto de homologação usa seu procedimento existente de implantação; os comandos locais não publicam migrations nem functions remotas.

## Cenários manuais de aceitação

1. **Dashboard e permissões (US3)**: cadastrar/editar categorias, portes e compatibilidades e configurar valores pela interface externa. Repetir a gravação com request_id igual; confirmar ausência de duplicação e erro se payload diferente. Concorrer duas edições com mesma revisão: somente uma deve vencer. Tentar as RPCs com cliente/prestador/staff insuficiente e anon: todas as escritas negadas. Conferir auditoria do gestor.
2. **Cadastro e legado (US2)**: novo guincho exige porte junto dos campos existentes; classificar um legado pelo editor. Cliente escolhe categoria na garagem/onboarding. Guincho sem porte e veículo de cliente sem categoria não obtêm compatibilidade presumida; a UI orienta completar dados. PitTec/loja continuam sem exigir porte.
3. **Capacidade (US2)**: com micro-ônibus selecionado, apenas os portes autorizados na matriz aparecem no mapa e cards. Tentar cotar/reservar provider incompatível diretamente: negar no servidor. Selecionar automóvel e trocar para micro-ônibus: invalidar seleção incompatível. Com frota, mudar guincho de atendimento e confirmar que não é usada a capacidade do maior veículo automaticamente.
4. **Preço e snapshots (US3)**: selecionar cada porte e conferir preço base e composição no snapshot com [data-model.md](data-model.md), mostrando ao cliente o total conforme UX atual. Alterar tarifa após cotação: cotação ainda válida preserva total, nova cotação usa novo preço. Alterar capacidade para incompatível antes da reserva: impedir contratação. Porte sem preço não recebe fallback global.
5. **Recusa sem pagamento (US1)**: solicitar guincho no fluxo atual, recusar antes de pagar. Cliente vê aviso e volta à descoberta em até 3 segundos após receber confirmação, com veículo/retirada/destino preservados e checkout anterior limpo. Não abrir pagamento automaticamente nem enviar pedido a outro prestador.
6. **Exclusões (US1)**: o guincheiro recusante não aparece novamente; refresh, salvar os mesmos dados e reiniciar o app não limpam a exclusão. Segunda recusa acumula exclusão. Alterar veículo OU retirada OU destino confirmado inicia nova geração e remove as exclusões específicas anteriores. Se não restarem opções, mostrar vazio com nova tentativa.
7. **Recuperação (US1)**: fechar o aplicativo antes da recusa, recusar no outro aparelho e reabrir: retomar descoberta com dados e exclusões. Cortar conexão e reconectar: reconciliar. Acessar pedido antigo pelo histórico não redireciona; recusa antiga recebida durante outro atendimento não interrompe o atual. Logout/login com outra conta não expõe a viagem anterior. Repetir com pedido criado antes da implantação sem contexto persistente: recusa deve criar um único contexto e exclusão a partir do rascunho/cotação do próprio cliente, preservando a viagem também após reabertura; retry não duplica registros. Se já houver contexto de viagem mais recente ou outro atendimento ativo, não o substituir nem redirecionar por essa recusa antiga.
8. **Recusa paga legada (US1)**: usar pedido sandbox pago compatível com contrato legado; recusa retoma busca, inicia/acompanha reembolso existente uma vez e mantém histórico financeiro. Repetir evento não duplica reembolso. Falha/pêndencia financeira continua acessível sem bloquear busca.
9. **Layout (US4)**: destino no topo abaixo do cabeçalho; status é primeiro card da home; todos os cards dessas duas telas têm bordas roxas, inclusive solicitação embutida. Conferir claro/escuro, fonte ampliada, tela compacta, orientação suportada e teclado aberto; controles do mapa e ações acessíveis.
10. **Encerramento de contexto (US1)**: em pedidos separados, cancelar voluntariamente, deixar expirar conforme prazo vigente e concluir normalmente; cada encerramento deve fechar somente seu contexto. Confirmar que não há exclusão nem retorno automático por recusa nesses casos e que o comportamento financeiro/navegação existente é preservado. Repetir eventos após iniciar outro atendimento: o contexto novo não deve ser fechado ou reaberto pelo pedido antigo.
11. **Regressão operacional**: aceite → pagamento → acompanhamento → vistoria/transporte → conclusão mantém fluxo existente; checar PitTec e consulta de histórico sem retorno indevido ao mapa. Validar preços existentes de outras modalidades preservados.

## Evidência e conclusão

Registrar ambiente, versão do app/backend, IDs não sensíveis dos cenários e resultados. SC-001–006 exigem 100% dos cenários correspondentes; SC-007 requer pelo menos cinco participantes por perfil, 80% de conclusão sem ajuda em até 10 segundos. Não afirmar validação de usabilidade sem executá-la.

Executar `git diff --check` antes da revisão. Não gerar testes automaticamente. Aprovação depende de compilação, aplicação limpa das migrações em ambiente isolado, permissões/snapshots verificados e resultados dos cenários; o planejamento por si só não comprova comportamento implementado.

## Execução de implementação — 2026-11-03

- Configuração pública homol existente e Supabase CLI disponível. Docker/psql não encontrados no PATH; validação local de migrations depende de disponibilizar runtime PostgreSQL/Docker. Nenhum segredo foi lido ou publicado.
- Frontend do dashboard e contas de validação não disponíveis neste workspace; T031/T033 e usabilidade com participantes continuam dependências externas.

### Evidências locais

| Verificação | Resultado |
|---|---|
| `./gradlew :app:assembleHomolDebug` | PASS; APK em `app/build/outputs/apk/homol/debug/app-homol-debug.apk` |
| Sintaxe das 11 migrations novas | PASS com parser PostgreSQL/PLpgSQL; 28 funções PL/pgSQL analisadas |
| Extensões dinâmicas de funções existentes | Pontos de substituição conferidos nos corpos anteriores aos wrappers de avaliação, preservando suas projeções |
| `git diff --check` | PASS |
| Backend local | Indisponível: Supabase CLI detectou ausência de Docker/Podman; nenhuma migration foi aplicada remotamente |
| Dispositivo | `adb devices` sem dispositivos conectados; walkthrough/validação visual pendentes |
| Dashboard e usabilidade | Integração externa e participantes não disponíveis; pendentes |

O parser confirma sintaxe, não execução, permissões efetivas, planos de consulta nem integridade de dados reais. Não foram criados testes automatizados, dados comerciais fictícios ou migrações de backfill presumindo capacidade. SC-001–007 continuam sem aprovação fim a fim.

Para regularizar guincho legado sem veículo, abrir Perfil e cadastrar placa, marca, modelo, cor e porte pelo novo editor. O cadastro/seleção usa RPC de proprietário. O cadastro de veículo no onboarding já usa o mesmo formulário da garagem; a seleção de categoria foi implementada nesse ponto compartilhado.


### Ampliação solicitada: atualização de cadastro

- Adicionada rota `registration-edit`, acessível por **Perfil → Atualizar cadastro** para cliente, guincho, PitTec e loja. Contato/endereço usam leitura e atualização owner-scoped; e-mail de acesso e documento ficam somente para consulta.
- O guincheiro cadastra o primeiro veículo ou edita placa, marca, modelo, cor e porte na seção **Veículo e porte do guincho**. O editor substitui o formulário compacto antes embutido na home. Cliente acessa suas categorias de veículos pela garagem.
- Cadastro do prestador valida endereço modificado pelo geocodificador existente antes de persistir coordenadas. Atualização usa controle de concorrência e preserva input em erro; a sessão recebe nome/telefone atualizados sem logout.
- `assembleHomolDebug` aprovado; APK instalado com sucesso no A70 (`192.168.0.11:5555`). A32/A33 indisponíveis na verificação final. Aparelho A70 bloqueado impediu walkthrough visual; salvamento não foi executado.
- Nova migration `20261103110000_self_registration_updates.sql` validada sintaticamente. Consulta read-only confirmou 12 migrations locais pendentes e três versões históricas remotas ausentes localmente. Homol e produção apontam ao mesmo projeto: nenhuma migration foi publicada nesta etapa. Antes da publicação, recuperar os históricos remotos para alinhar o histórico local sem reparar/regravar versões aplicadas.
