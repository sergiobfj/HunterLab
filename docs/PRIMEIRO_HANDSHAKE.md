# Primeiro handshake — Hunter 350 2027

> **Situação: procedimento revisado, NÃO executado.** Na primeira tentativa real (0.2.0) a conexão do laboratório
> falhou com **status 147** antes de qualquer envio. A 0.2.1 traz uma correção candidata; **antes do T07, faça o reteste
> sem transmissão de `docs/DIAGNOSTICO_CONEXAO.md`**. Os testes T07–T09 são manuais e dependem de você.

**Decisões aprovadas (21/11/2026):** handshake exclusivamente manual · reconhecimento do dispositivo só em memória
(não persiste entre execuções) · CCCD/notificações desativadas · sem reconexão automática · nesta etapa, só T07–T09.

## 0. Meteor (referência) × Hunter (observado)

| Item | Meteor 350 — WazeTripper | Hunter 350 2027 — Hunter Lab |
|---|---|---|
| Nome `RE_DISP` | Confirmado | **Observado** (0.1.0) |
| Serviço `01FF0100-…` / característica `01FF0101-…` | Confirmado | **Observado** (0.1.0) |
| Propriedade da característica | WRITE / WRITE_NO_RESPONSE | **WRITE_NO_RESPONSE** (0.1.0) |
| App oficial após conexão só de leitura | — | **Continuou funcionando** (0.1.0) |
| Relógio após conexão só de leitura | — | **Voltou ao normal** (0.1.0) |
| Endereço fixo entre ignições (OUI `00:60:37`) | Observado | **não verificado** |
| Janela de anúncio após ligar a ignição | < ~30 s | não medido |
| Watchdog (link cai ~5 s após o último pacote) | Observado | não medido |
| Desconexão pelo celular → Pod para de anunciar até ciclar a ignição | Observado | não testado |
| `HANDSHAKE_SHOW_PIN` faz o display mostrar um PIN | Funciona | **não testado** |
| `PIN` (`0x20` + ASCII) | Funciona (pareamento da referência) | **não testado** |
| Respostas do Pod pelo servidor GATT local | Só registradas; formato não documentado | **não testado** |
| Bússola (`10 11 41 …`) | Confirmado (capturas reais) | **não testado** (T10, fora desta etapa) |

Diferenças deliberadas do Hunter Lab em relação à referência:

- **Não grava o descritor CCCD** nem ativa notificações (a referência faz, embora a característica não tenha NOTIFY).
- **Não presume autenticação**: a referência marca "pareado" logo depois de enfileirar o PIN; o Hunter Lab só passa a
  **"Autenticado (confirmação visual)"** quando você confirma pelo display.
- **Não reconecta sozinho** e **não envia nada ao conectar**.

### 0.1 O que vai pelo ar em cada etapa

| Etapa | Gatilho | Pacotes do protocolo enviados ao Tripper |
|---|---|---|
| Conectar | Toque + diálogo | **Nenhum.** Só a conexão BLE e a descoberta de serviços (leituras padrão do GATT, como na 0.1.0). O servidor local é aberto, sem anúncio. |
| Autorizar transmissão | Toque + 5 confirmações | Nenhum. Só libera a sessão. |
| Enviar HANDSHAKE_SHOW_PIN | Toque | `21 01 …` (uma vez) |
| Keepalive | **Automático** após o handshake | `PING_FW` (`03 00 …`) quando passam ~2 s sem escrita, enquanto a sessão estiver autorizada |
| Enviar PIN | Toque | `20 …` (uma vez por toque; máx. 3) |
| Acertar relógio (opcional) | Toque | `50 hh mm …` |
| Retomar (não usado em T07–T09) | Toque | `21 00`, depois **automaticamente** `SET_TIME` e `PING_FW` ×2 |
| Servidor local | **Automático**, só se o Tripper escrever nele | Nenhum pacote do protocolo; o Android responde com a confirmação padrão do GATT (vazia) |
| Interromper / sair do app / Bluetooth desligado | Toque ou automático | **Nenhum** a partir desse instante |

Nenhum outro comando é possível: a lista branca do app só aceita `SHOW_PIN`, `RESUME`, `PIN`, `SET_TIME`, `PING_FW`
e bússola, e só na característica documentada.

## 1. Pré-requisitos

- APK 0.2.0 instalado manualmente por você.
- Celular com bateria, Bluetooth ligado, permissão *Dispositivos por perto* concedida.
- **Linha de base do app oficial (obrigatória):** antes do T07, abra o app oficial Royal Enfield, confirme que ele
  conecta ao Tripper normalmente e anote o que ele mostra. Depois **force a parada** dele.
- **Estabilidade do endereço:** em duas ignições seguidas, busque no Hunter Lab e confira se o endereço completo do
  `RE_DISP` (na lista de dispositivos) é o mesmo. Se mudar, use o procedimento B do §3.
- No laboratório, deixe **desligada** a *captura detalhada de bytes recebidos* (ver §9).
- **Não faça a inspeção da aba Dispositivos no mesmo ciclo de ignição do laboratório.** A inspeção termina pela
  desconexão do celular, e na Meteor isso faz o Tripper parar de anunciar até ciclar a ignição (provável causa do 147).
- No cartão *Diagnóstico de conexão*, mantenha os padrões: **anúncio recente obrigatório** e servidor **antes da conexão**.
- 20 minutos, papel ou a ficha de `TESTES_FISICOS.md`, e de preferência uma segunda pessoa para olhar o display.
- Ter lido §5 (interromper) e §7 (recuperação) antes de começar.

## 2. Preparação da moto parada

- Moto desligada, no descanso, em local seguro e plano. **Ninguém pilota durante o teste.**
- A ignição só é ligada nos passos indicados; não a deixe ligada por muito tempo sem o motor (bateria).
- Não mexer em chicote, conectores, ECU ou qualquer parte elétrica.

## 3. Procedimento de conexão

A janela de anúncio (~30 s) e o watchdog (~5 s sem pacotes, na Meteor) exigem preparar tudo antes.

**Procedimento A (endereço estável — recomendado):**

1. **Ignição ligada**, aba *Dispositivos* → **Buscar** → toque em `RE_DISP` → **Abrir laboratório**. Não conecte ainda.
2. **Desligue a ignição.** Aguarde ~10 s.
3. No laboratório, toque em **Autorizar transmissão (próxima conexão)** e marque os cinco itens.
   A pré-autorização vale **2 minutos** e **só para a próxima conexão**.
4. **Ligue a ignição**, toque em **Conectar (não envia nada)** e confirme no diálogo. O app aguarda até **12 s** por um
   anúncio do Tripper e só então conecta usando esse anúncio. Se não houver anúncio, ele não tenta conectar e explica.
5. Aguarde **Pronto para autenticar** (o app verifica serviço, característica e propriedade, e abre o servidor local).
   Se aparecer "Perfil incompatível", o app bloqueia tudo — **pare e registre**.
6. **Toque logo em "Enviar HANDSHAKE_SHOW_PIN"**. Se o link cair antes (status 19), o Pod deve voltar a anunciar na
   hora (Meteor): autorize de novo e reconecte. Não insista mais de duas vezes.

**Procedimento B (endereço muda a cada ignição):** com a ignição recém-ligada, faça os passos 1, 3, 4, 5 e 6 seguidos,
sem desligar a ignição (tudo em ~30 s). Se não der tempo, o app desiste com segurança (12 s esperando o anúncio, mais
15 s para o link); cicle a ignição e tente de novo. Registre que o endereço muda.

## 4. Procedimento de autenticação

1. Depois do `HANDSHAKE_SHOW_PIN`, observe o display. **Esperado (Meteor):** um PIN aparece.
   O app passa a enviar `PING_FW` automaticamente (keepalive) para o Pod não encerrar o link.
   - Se **nada aparecer** em ~5 s: **Interromper**, registre e encerre os testes do dia.
2. Digite o PIN em até **60 s** e toque em **Enviar PIN**. O campo aceita só letras/dígitos (até 6) e é limpo após o envio.
3. Observe o display e responda em até **45 s**:
   - Aceitou (o PIN sumiu, voltou ao relógio/tela normal) → **Sim, aceitou**. O botão só fica ativo depois que o Android
     entrega o PIN ao Bluetooth.
   - Continua pedindo PIN → **Está pedindo PIN** e confira os dígitos (máximo de **3** tentativas por sessão).
   - Sem resposta sua em 45 s → a sessão vai a **Erro** com segurança (o app não presume nada).
4. O estado passa a **"Autenticado (confirmação visual)"**. Isso registra **o que você viu**, não uma confirmação do
   protocolo: as respostas do Tripper não têm formato documentado e não são interpretadas (ficam só contadas no
   histórico). O relatório diz isso explicitamente.
5. Sem tela enviada, o Tripper deve mostrar o **relógio nativo**. Opcional: **Acertar relógio (SET_TIME)** e conferir a hora.
6. Deixe a sessão estável por **30 s** e anote se o link cai. (A sessão autorizada tem limite de 5 min.)
7. Toque em **Interromper** e acompanhe o encerramento (§5).
8. **Histórico técnico → Copiar relatório** e guarde o texto (§9).

Se o link cair **depois** do PIN aparecer e **antes** de você enviar, a próxima sessão precisa de um novo
`HANDSHAKE_SHOW_PIN`; use o PIN que aparecer **nessa** vez.

## 5. Como interromper

| Meio | O que acontece |
|---|---|
| **Interromper** (rodapé do laboratório ou faixa no topo de qualquer aba) | Fila, agendamentos e autorização cancelados **na hora**; nenhuma escrita depois. O app aguarda até **8 s** o Tripper encerrar o link sozinho; se não encerrar, desconecta pelo celular e libera os recursos. |
| Sair do app (Home, botão de energia) | Interrupção automática. Com sessão ativa, a tela do laboratório não apaga sozinha. |
| Desligar o Bluetooth do celular | Interrupção automática. |
| **Desconectar agora** (só em "Interrompendo"/"Erro") | Desconexão pelo celular imediata. |
| Desligar a ignição | Último recurso, sempre disponível. |

### A interrupção pode atrasar a volta ao relógio

Interromper **para a transmissão na hora**, mas **não garante** que o display volte imediatamente ao relógio:

| Situação | O que esperar (Meteor) / o que observar (Hunter) |
|---|---|
| Interromper **depois** de autenticado, sem tela enviada (T09) | O relógio já deveria estar na tela. Sem PING, o Pod encerra o link em ~5 s. É o caso mais simples. |
| Interromper **com o PIN na tela** (T07/T08) | Não documentado. O PIN pode continuar na tela até o link cair ou até ciclar a ignição. **Observe e registre.** |
| O Tripper **não** encerra em 8 s, e o app desconecta pelo celular | Na Meteor, o Pod parou de anunciar até ciclar a ignição; o display pode ficar no último estado. **Cicle a ignição.** |
| Depois de uma **tela** (bússola, T10 — fora desta etapa) | Na Meteor, PING não segura o link; o relógio volta após a queda e uma nova conexão. Sem reconexão automática, pode exigir ciclar a ignição. |

Em qualquer caso de display preso: aguarde ~10 s e, se preciso, **desligue a ignição por ~10 s**.

## 6. Possíveis falhas

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `RE_DISP` não aparece | Janela de anúncio encerrada | Ciclar a ignição e buscar logo |
| "Sem resposta do dispositivo" após Conectar | Endereço mudou ou anúncio parou | Procedimento B do §3 |
| "Nenhum anúncio … em 12 s" | O Tripper não estava anunciando (janela encerrada, desconexão anterior pelo celular, outro app conectado) | Ciclar a ignição; não inspecionar antes; ver `DIAGNOSTICO_CONEXAO.md` |
| Status 147 | Link não estabelecido (`GATT_CONNECTION_TIMEOUT`) | `DIAGNOSTICO_CONEXAO.md` |
| Link cai em ~5 s antes do handshake (status 19) | Watchdog sem pacotes | Reautorizar, reconectar e tocar mais rápido |
| Status 133 / 62 | Falha genérica do Android / anúncio parou | Tentar uma vez; confirmar app oficial fechado |
| "Perfil incompatível — escrita bloqueada" | Firmware diferente do documentado | **Parar**; registrar o relatório GATT |
| "Servidor GATT local ainda não está pronto" | Registro do serviço local atrasado/falhou | Aguardar 1 s; se persistir, desligar/ligar o Bluetooth |
| Nenhum PIN no display após SHOW_PIN | Comportamento diferente na Hunter | **Interromper**; registrar; não repetir no mesmo dia |
| "Autenticação não confirmada" | Passaram 45 s sem sua resposta | Interromper; nova sessão se quiser repetir |
| PIN recusado três vezes | Dígitos errados ou protocolo diferente | Sessão vai a Erro; interromper; revisar antes de outra sessão |
| "Handshake não enviado" / falhas de escrita | Android recusou a escrita | Interromper; registrar código mostrado |
| Display mostra algo inesperado | Desconhecido | Interromper; fotografar; registrar |
| App oficial não conecta depois | Link ainda aberto ou pareamento substituído | §7 |

## 7. Como recuperar o funcionamento original

**No Tripper:**

1. **Interromper** e aguardar o link cair (ou *Desconectar agora*).
2. **Desligar a ignição** por ~10 s e ligar de novo. O Tripper deve voltar ao estado normal.

**Se o app oficial Royal Enfield deixar de conectar** (siga na ordem e pare no passo que resolver):

1. Confirme que o Hunter Lab **não** está com sessão (sem a faixa "Laboratório BLE ativo"). Se estiver, Interromper.
2. *Configurações → Apps → Hunter Lab → Forçar parada.* Isso fecha qualquer conexão BLE do Hunter Lab.
3. Desligue e ligue o **Bluetooth do celular**; cicle a **ignição** da moto.
4. Abra o app oficial e tente conectar como de costume (compare com a linha de base do §1).
5. Reinicie o celular e tente de novo.
6. Se o app oficial pedir para **parear de novo**, siga **somente o fluxo do próprio app oficial**. O Hunter Lab não
   cria pareamento do sistema Android: não há nada dele a remover em *Configurações → Bluetooth*. Não remova pareamentos
   do sistema por conta própria, a menos que o app oficial instrua.
7. Para eliminar qualquer interferência, **desinstale o Hunter Lab** (nenhum dado dele precisa ser guardado; o relatório
   já foi copiado).
8. Se ainda assim não funcionar, **pare todos os experimentos**, registre o que aconteceu e procure o suporte/concessionária
   Royal Enfield informando que o Tripper foi usado com um app de terceiros.

**Não faça:** não use o Hunter Lab para tentar "consertar" (novo handshake, novo PIN); não insista em várias sessões
seguidas; não mexa na parte elétrica.

## 8. Critérios para avançar ao teste de bússola (etapa posterior)

Avance **somente** se todos forem verdadeiros e com nova autorização sua:

- [ ] O display mostrou um PIN após `HANDSHAKE_SHOW_PIN`.
- [ ] O PIN foi aceito (confirmado por você) em até duas tentativas.
- [ ] Autenticado, o link ficou estável por ≥ 30 s só com keepalive.
- [ ] **Interromper** funcionou: nenhuma escrita depois, link encerrado, relógio normal (no máximo após ciclar a ignição).
- [ ] O app oficial continuou funcionando (ou a recuperação do §7 foi simples).
- [ ] O relatório da sessão foi guardado.

## 9. PIN, histórico e relatório

- O PIN **nunca** é gravado em arquivo, preferências ou estado salvo da tela; não aparece no histórico nem no relatório
  (inclusive o CRC do pacote, que o revelaria). Testes automatizados verificam isso.
- O texto do PIN existe na memória só até o pacote ser enviado; o campo é limpo após o envio.
- **Exceção:** a *captura detalhada de bytes recebidos* mostra o que o Tripper enviar — se ele devolver o PIN, ele
  aparecerá no histórico e no relatório. Por isso ela fica **desligada** em T07–T09. Se for ligada em algum momento,
  o relatório traz um aviso no topo.
- O relatório mascara o endereço (só `00:60:37:XX:XX:XX`) e vai para a área de transferência só quando você toca em
  *Copiar relatório*. Cole-o em `field-logs/` (ignorado pelo git).

## 10. Registro

Use a ficha T07–T09 de `docs/TESTES_FISICOS.md`. Atualize `PROTOCOLO.md` §9 com o que mudou na Hunter.
