# Quickstart: Seleção de serviços condicionada à modalidade do prestador

## Pré-requisitos

- Catálogo com pelo menos: uma categoria/serviço qualquer para testar Fixo, um serviço ativo com
  `dispatch_kind = MOBILE_SEQUENTIAL` (Guincho) e um serviço ativo com
  `dispatch_kind = PITTEC_AUTO_SEQUENTIAL` (PitTec) — já seedados pelas migrações existentes
  (`013-mobile-provider-dispatch`, `015-pittec-on-demand`).
- Build de debug instalado em um dispositivo/emulador com sessão de teste capaz de criar uma nova
  conta de prestador (fluxo "Sou uma empresa · Cadastrar").

## Cenário 1 — Fixo continua exigindo serviço

1. Iniciar o cadastro de prestador e escolher modalidade **Fixo**.
2. Preencher empresa, endereço e verificar a localização.
3. Sem escolher nenhum serviço, tentar enviar o cadastro.
   - **Esperado**: envio bloqueado, erro indicando que é preciso escolher pelo menos um serviço.
4. Escolher uma categoria, um serviço, preço e duração válidos.
5. Enviar o cadastro.
   - **Esperado**: cadastro concluído; o prestador aparece com o serviço escolhido.

## Cenário 2 — Móvel (Guincho) sem escolha de serviço

1. Iniciar o cadastro de prestador e escolher modalidade **Móvel**.
2. Observar a etapa onde antes existia a escolha de categoria/serviço.
   - **Esperado**: nenhuma escolha de categoria/serviço é exibida; apenas preço e duração
     relacionados ao atendimento de guincho.
3. Preencher empresa, endereço, verificação de localização, preço e duração do guincho.
4. Enviar o cadastro.
   - **Esperado**: cadastro concluído sem qualquer seleção manual de serviço; o prestador passa a
     oferecer "Guincho" no catálogo.

## Cenário 3 — PitTec sem serviço nem preço

1. Iniciar o cadastro de prestador e escolher modalidade **PitTec**.
2. Observar a etapa onde antes existia a escolha de categoria/serviço.
   - **Esperado**: nenhuma escolha de categoria, serviço, preço ou duração é exibida.
3. Preencher apenas empresa, endereço e verificação de localização.
4. Enviar o cadastro.
   - **Esperado**: cadastro concluído sem nenhuma etapa de serviço/preço; o prestador passa a ser
     elegível para chamados PitTec (sujeito às demais regras de disponibilidade já existentes:
     localização e canal de notificação habilitados).

## Cenário 4 — Troca de modalidade durante o preenchimento

1. Iniciar o cadastro escolhendo **Fixo** e selecionar um serviço.
2. Trocar a modalidade para **Móvel** (ou **PitTec**).
   - **Esperado**: a seleção de serviço feita para Fixo desaparece; nenhuma escolha de
     categoria/serviço permanece pendente.
3. Trocar a modalidade de volta para **Fixo**.
   - **Esperado**: a etapa de escolha de serviço volta a aparecer e a ser obrigatória para o envio.

## Cenário 5 — Catálogo sem o serviço implícito (validação defensiva)

Este cenário exige alterar temporariamente o catálogo de teste (ex.: desativar o serviço Guincho ou
o PitTec) e não deve ser executado contra um catálogo de produção.

### Variante Móvel (Guincho ausente)

1. Com o serviço `MOBILE_SEQUENTIAL` inativo (ou inexistente) no catálogo de teste, iniciar um
   cadastro com modalidade **Móvel**.
2. Tentar enviar o cadastro.
   - **Esperado**: cadastro bloqueado com uma mensagem compreensível de catálogo indisponível;
     nenhum prestador é criado sem serviço associado.

### Variante PitTec (PitTec ausente)

1. Com o serviço `PITTEC_AUTO_SEQUENTIAL` inativo (ou inexistente) no catálogo de teste, iniciar um
   cadastro com modalidade **PitTec**.
2. Tentar enviar o cadastro.
   - **Esperado**: cadastro bloqueado com uma mensagem compreensível de catálogo indisponível; nenhum
     prestador é criado sem serviço associado.

## Verificação de build

- `./gradlew :app:compileDebugKotlin`
- `./gradlew :app:assembleDebug`
- `./gradlew :app:lintDebug`

Nenhum comando de banco (`supabase db reset`/`db lint`) é necessário para esta funcionalidade, pois
não há migração nova.
