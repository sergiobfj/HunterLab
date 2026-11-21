# Arquitetura — Hunter Lab 0.2.0

## Módulos

```
┌──────────────────────────────── app (Android, Compose) ────────────────────────────────┐
│  ui/devices   ui/lab (Laboratório BLE)   ui/simulation   ui/playground   ui/tripper     │
│      │              │                                                                  │
│  ble/  BleController · BleScanner · GattInspector (somente leitura) · GattSnapshots     │
│  ble/lab/  AndroidTripperLink (cliente + servidor GATT) · HandlerLinkScheduler          │
└──────┼──────────────┼──────────────────────────────────────────────────────────────────┘
       ▼              ▼  portas: GattClientPort · LocalServerPort · LinkScheduler · LinkEvents
┌──────────────────────────────── core (Kotlin/JVM puro) ────────────────────────────────┐
│  link/       TripperLink (sessão) · LinkState/LinkTransitions · WriteQueue · LinkCommand│
│              CommandWhitelist · LinkProfile · LinkLog/InboundClassifier · LinkReport    │
│  ble/ nav/ radar/ display/ playground/   (inalterados desde a 0.1.0)                    │
└────────────────────────────────────────┬───────────────────────────────────────────────┘
                                         ▼
┌──────────────────────────────── protocol (Kotlin/JVM puro) ────────────────────────────┐
│  TripperPacket · Crc16CcittFalse · TripperCommands · NavigationFrame · PacketDecoder …  │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

Regras de dependência:

- `protocol` não conhece Android, BLE nem UI (`explicitApi()`).
- `core` depende só de `protocol` e `kotlinx-coroutines-core`. **Toda a lógica de comunicação** está aqui e é
  testada na JVM com relógio virtual e fakes do Tripper.
- `app/ble/lab/AndroidTripperLink.kt` é o **único** arquivo com escrita GATT e servidor GATT. Ele não decide nada:
  executa o que o `TripperLink` pede. Nenhuma tela fala com o BLE diretamente.

## Laboratório BLE (0.2.0)

### Máquina de estados (`LinkState`, `LinkTransitions`)

| De | Para (válidos) |
|---|---|
| DISCONNECTED | CONNECTING |
| CONNECTING | DISCOVERING, STOPPING, ERROR, DISCONNECTED |
| DISCOVERING | READY_FOR_AUTH, STOPPING, ERROR, DISCONNECTED |
| READY_FOR_AUTH | WAITING_FOR_PIN (SHOW_PIN), AUTHENTICATING (RESUME), STOPPING, ERROR, DISCONNECTED |
| WAITING_FOR_PIN | AUTHENTICATING (PIN), STOPPING, ERROR, DISCONNECTED |
| AUTHENTICATING | AUTHENTICATED (confirmação do usuário), WAITING_FOR_PIN (pede PIN), STOPPING, ERROR, DISCONNECTED |
| AUTHENTICATED | DISPLAY_ACTIVE (bússola), STOPPING, ERROR, DISCONNECTED |
| DISPLAY_ACTIVE | AUTHENTICATED (fim do teste), STOPPING, ERROR, DISCONNECTED |
| STOPPING | DISCONNECTED |
| ERROR | STOPPING, DISCONNECTED |

- Só WAITING_FOR_PIN, AUTHENTICATING, AUTHENTICATED e DISPLAY_ACTIVE permitem escrita — e só com transmissão
  autorizada na sessão.
- Comandos em estado incompatível devolvem `CommandResult.Rejected(motivo)` sem tocar no rádio.
- Qualquer transição fora da tabela é erro de programação (`check`), coberto por testes.

### Fluxo

```
connect ─► servidor local aberto ─► busca curta: anúncio conectável do endereço (≤ 12 s, 0.2.1)
        ─► connectGatt(dispositivo do anúncio, TRANSPORT_LE) ─► onConnected ─► discoverServices
        ─► LinkProfile.verify (serviço/característica/WRITE_NO_RESPONSE) ─► READY_FOR_AUTH   (nenhuma escrita)
autorizado + toque ─► SHOW_PIN (≥ 200 ms após a descoberta) ─► WAITING_FOR_PIN + keepalive
PIN digitado ─► PIN ─► AUTHENTICATING ─► usuário confirma pelo display ─► AUTHENTICATED
retomada (só com sessão reconhecida nesta execução): RESUME → 200 ms → SET_TIME → 150 ms → PING ×2 → confirmação
teste de bússola: quadro imediato + reenvio a cada 2 s até a duração (máx. 60 s) ─► AUTHENTICATED
```

`ConnectOptions` (0.2.1): `requireFreshAdvertisement` (padrão sim; não = comportamento da 0.2.0) e `ServerMode`
(`BEFORE_CONNECT` padrão; `AFTER_CONNECT` e `DISABLED` só para diagnóstico — em `DISABLED` o handshake é recusado).
Eventos `onAdvertisementSeen`, `onConnectFailed` e `onDiagnostic` alimentam o histórico e o resumo por tentativa.
Ver `docs/DIAGNOSTICO_CONEXAO.md`.

### Portas (core ⇄ Android)

| Porta | Implementação | Observação |
|---|---|---|
| `GattClientPort` | `AndroidTripperLink` | `writePacket(geração, bytes, semResposta)` — **sem parâmetro de UUID**: o alvo é sempre `01FF0101-…`; recusa tamanho ≠ 20. |
| `LocalServerPort` | `AndroidTripperLink` | Serviço `01FF0100-…` / característica `01FF0101-…` (WRITE + WRITE_NO_RESPONSE, permissão WRITE). Sem anúncio. |
| `LinkScheduler` | `HandlerLinkScheduler` | Thread principal; nos testes, `VirtualScheduler`. |
| `LinkEvents` | `TripperLink` | Todos os eventos carregam a **geração** da sessão; os de sessões antigas são descartados (no adaptador e no core). |

### Fila de escrita (`WriteQueue`)

- Uma escrita em andamento; a próxima só após `onCharacteristicWrite` ou o timeout (1 s).
- **Conclusão ≠ confirmação do Tripper:** com WRITE_NO_RESPONSE, o callback só diz que o Android entregou o pacote
  ao controlador. O histórico usa o termo "entregue ao controlador".
- Limite de 8 itens; deduplicação por política (`UNIQUE` para handshake/PIN, `REPLACE` para tela/hora,
  `DROP_NEW` para keepalive, `NONE` para os dois PINGs da retomada); descarte de itens obsoletos (> 1,5 s; 3 s para
  handshake).
- Após um timeout, a fila entra em **quarentena** por 1 s para que um callback atrasado não seja atribuído à próxima escrita.
- Falha em comando de handshake → ERROR sem nova tentativa. Três falhas seguidas de outros comandos → ERROR.
- Todos os tempos estão em `LinkTiming` (valores da referência marcados) e são cobertos por testes.

### Keepalive e watchdog

- Verificação a cada 1 s; `PING_FW` quando há ≥ 2 s sem escrita (referência).
- Só depois do primeiro handshake de uma sessão **autorizada**; nunca antes, nunca em STOPPING/ERROR/DISCONNECTED.
- Durante a bússola o próprio reenvio de 2 s mantém o link; não há PING extra.
- Watchdog (Meteor): ~5 s sem pacotes derruba o link (status 19). Depois de uma **tela**, PING não renova o link.
- **Interromper a sessão ≠ restaurar o relógio.** Na Meteor, o relógio volta quando o Pod derruba o link e uma nova
  conexão é feita (a referência reconecta sozinha; o Hunter Lab não reconecta nesta etapa).

### Autorização e autenticação

- `TransmissionAck` (5 itens) libera a transmissão **para uma sessão**: dada em DISCONNECTED (pré-autorização para a
  próxima conexão, expira em 2 min) ou entre CONNECTING e READY_FOR_AUTH. Morre com a sessão.
- A autorização de **conexão** (aviso da 0.1.0) não libera transmissão.
- Autenticação só por `confirmAuthenticated()` (usuário) e só depois de o Android concluir a entrega do PIN/RESUME.
- `PairingRegistry` em memória registra só "este endereço foi autenticado e confirmado" — nunca PIN. Habilita RESUME
  apenas enquanto o processo do app vive.

### Servidor local e respostas

- Respostas do Pod são validadas (remetente = Tripper da sessão, característica documentada, 1–512 bytes) e
  registradas como resumo ("20 bytes · CRC válido · estrutura …"), **sem bytes** a menos que a captura detalhada seja
  ligada (em memória). Nada é interpretado e nada muda o estado.
- Leituras do servidor recebem resposta vazia (como a referência); escrita em descritor recebe "não suportado".

### Interromper e limpeza

`interrupt()`: autorização revogada, fila esvaziada, agendamentos e teste de tela cancelados **na hora** → STOPPING →
aguarda o Pod encerrar (até 8 s) → senão desconecta pelo celular → fecha cliente e servidor (2 s) → DISCONNECTED.
Também é chamado ao sair do app (`onStop`), ao desligar o Bluetooth e ao atingir 5 min de sessão autorizada.
Desconexão inesperada → limpeza imediata, sem reconexão. Testes verificam que **nenhum agendamento sobra**.

## Simulação (inalterada)

`NavigationProvider` → `ScreenComposer` (árbitro de prioridade) → `ComposedFrame` (tela tipada, pacote em memória,
prévia). O radar nunca altera `[0..6]`. As abas Navegação, Radares e Playground **não transmitem**.

## Decisões de projeto

| Decisão | Motivo |
|---|---|
| Lógica de comunicação no `core` | Testável sem Android; o adaptador fica fino e auditável. |
| Construtores de `LinkCommand` `internal` | Fora do `core` não há como criar comandos — nem bytes livres. |
| Sem gravação do CCCD (diferente da referência) | Não é comando documentado e a característica não tem NOTIFY. |
| Autenticação por confirmação humana | Respostas do Pod não são documentadas. |
| Pré-autorização antes de conectar | O watchdog (~5 s) não dá tempo de ler um diálogo depois de conectar; o envio continua manual. |
| Interromper ao sair do app | Sem tela, não há botão Interromper visível. |
| minSdk 31, sem `INTERNET`, dark mode, sem DI | Mantidos da 0.1.0. |
