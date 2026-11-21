# Protocolo BLE do Tripper Pod — o que o Hunter Lab assume

Este documento resume o que se sabe do protocolo, **com o nível de confiança de cada item**.
Não é uma especificação oficial.

**Fonte:** [jnsdias/wazetripper — docs/PROTOCOL.md](https://github.com/jnsdias/wazetripper/blob/main/docs/PROTOCOL.md)
(Apache-2.0), montado por observação de um Tripper Pod de **Meteor 350** e análise de outros apps.
Nenhum código foi copiado; a implementação em `protocol/` é original.

## Níveis de confiança

| Nível | Significado (sempre relativo à **Meteor 350**) |
|---|---|
| **Confirmado** | Visto funcionando no hardware pela referência. |
| **Provável** | Consistente entre fontes, sem verificação em hardware. |
| **Não verificado** | Hipótese/palpite; exige teste físico. |

> **Hunter 350 2027:** observados o nome `RE_DISP`, o perfil GATT (WRITE_NO_RESPONSE), o handshake com PIN
> (aceito visualmente), a tela de **bússola** (2 sessões) e o fim do link por status 19 após o último quadro.
> **Telas de navegação ainda não foram testadas na Hunter** (são "Confirmado" só na Meteor).
> Os ícones seguem com `HunterValidation.NOT_TESTED`. Ver `docs/TESTES_FISICOS.md` e `docs/PRIMEIRO_HANDSHAKE.md`.

## 1. Transporte

| Item | Valor | Meteor 350 (referência) | Hunter 350 2027 |
|---|---|---|---|
| Nome no scan | `RE_DISP` | Confirmado | **Observado** |
| Serviço | `01FF0100-BA5E-F4EE-5CA1-EB1E5E4B1CE0` (Wireless UART do SDK NXP) | Confirmado | **Observado** |
| Característica | `01FF0101-BA5E-F4EE-5CA1-EB1E5E4B1CE0`, sem NOTIFY | WRITE / WRITE_NO_RESPONSE | **WRITE_NO_RESPONSE** |
| OUI do endereço | `00:60:37` (NXP Semiconductors) | Observado | não informado |
| Transporte | `connectGatt(..., TRANSPORT_LE)` | Confirmado | **Observado** (conexão funcionou) |
| Respostas do Pod | por um **servidor GATT local** no celular, mesmos UUIDs | Confirmado (só registradas) | **Observado** (chegam; não interpretadas) |
| Janela de anúncio | < ~30 s após ligar a ignição | Observado | não medido |
| Watchdog | link derrubado ~5 s após o último pacote (status 19) | Observado | **Observado** status 19 após o último quadro (tempo não medido) |
| Desconexão pelo celular | Pod deixou de anunciar até ciclar a ignição | Observado | **Compatível** (sem anúncio após sair da tela, 0.2.1) |
| Primeira escrita | esperar ~200 ms após a descoberta; serializar escritas | Observado | **Funcionou** com essa regra |

Implementação: `TripperTransport` (constantes), `TripperCandidateClassifier` (identificação),
`TripperProfileCheck` (comparação da tabela GATT descoberta), `LinkProfile` (verificação do alvo de escrita, 0.2.0).

## 2. Pacote

Sempre **20 bytes**: 18 de payload + CRC-16/CCITT-FALSE (poly `0x1021`, init `0xFFFF`, MSB primeiro,
sem XOR final) dos bytes `[0..17]`, gravado big-endian em `[18..19]`.

Implementação: `TripperPacket` (imutável, valida comprimento e CRC com erros tipados),
`Crc16CcittFalse`, `PacketDecoder`.

## 3. Comandos documentados (builders em `TripperCommands`)

| Comando | Bytes (antes do CRC) | Confiança |
|---|---|---|
| `HANDSHAKE_SHOW_PIN` | `21 01 00…` → CRC `50 A7` | Confirmado |
| `HANDSHAKE_RESUME` | `21 00 00…` → CRC `40 45` | Confirmado |
| `PING_FW` | `03 00 00…` → CRC `45 D9` | Confirmado |
| `PIN` | `20` + até 6 caracteres ASCII | Provável (o Hunter Lab rejeita PIN inválido em vez de truncar) |
| `SET_TIME` | `50 hh mm`; 12h: hora 1–12 `| 0x40` se AM | Confirmado |
| Bússola | `10 11 41 00 00 00 nn FF 00 00 00 FF FF FF dd` | Confirmado |
| Navegação | ver §4 | Confirmado |
| Rota iniciada | navegação com ícone `0x45`, 0 m, rodapé `FF FF FF` | Provável |
| Recalculando | `10 11 1C 00 00 00 nn 00…` | Confirmado (ícone visto no Pod) |
| Ícone de ligação | `40 05 00…` | Provável |
| Tela ociosa | `10 11 3C 00 00 04 40 15 00 00 41 00 04 03…` | Provável (não usada) |

Não existe builder genérico de "bytes livres": só comandos documentados podem ser gerados.

**Laboratório BLE (0.2.0)** pode transmitir somente `HANDSHAKE_SHOW_PIN`, `HANDSHAKE_RESUME`, `PIN`, `SET_TIME`,
`PING_FW` e bússola (`CommandWhitelist`). Navegação, rota iniciada, recalculando, ícone de ligação e tela ociosa
continuam **somente em memória**.

## 4. Tela de navegação (`NavigationFrame`)

```
[0-1] 10 11   [2] ícone grande       [3-4] distância (uint16 BE)   [5] unidade 1=m · 2=km×10
[6] noite/intensidade                [7] seta pequena (FF = nenhuma)
[8-9] FF FF ou distância do radar (experimental)   [10] 41   [11-13] rodapé   [14-17] 00
```

- **Distância:** até 999 m em metros; acima, `metros / 100` **truncado** com unidade 2 (2942 m → `29` = "2,9 km").
  Negativa é erro explícito. Limite `0xFFFF`.
- **Rodapé:** distância total (mesma codificação) · tempo restante `[h, m, 0]` (h ≤ 23) ·
  chegada 24h `[h, m, 0]` · chegada 12h `[h%12 | 0x40 (AM) / 0x80 (PM), m, 0]` (esquema diferente do SET_TIME) ·
  `FF FF FF` sem dado.
- **Byte [6]:** bit 0 = noite (**confirmado**). Nibble alto opcional = intensidade por distância
  (`0x50` ≤10 m, `0x40` ≤20, `0x30` ≤45, `0x20` ≤70, `0x10` ≤95, `0x00` acima) — **provável**, efeito visual não documentado.

## 5. Radar

Sem ícone dedicado conhecido. Regra (de outros apps): mostrar só a ≤ 300 m (busca até 700 m).

| Modo | `[7]` | Distância | Confiança |
|---|---|---|---|
| Compatível | `0x3C` | rodapé `[11-13]` (substitui o total) | Confirmado (captura real) |
| Experimental | `0x3C` | `[8-9]` uint16 metros, máx. `0xFFFE` | **Não verificado** |

`0x45` na seta pequena: o Pod não desenhou nada (referência).

## 6. Ícones (`TripperIcons`)

O catálogo tem 71 entradas: os 42 códigos da tabela documentada, `0x41` e `0x45`, 18 rotatórias
calculadas (`0B–13`, `32–3A`) e 9 códigos de faixas não testadas marcados como desconhecidos.

| Faixa | Situação no catálogo |
|---|---|
| Tabela §6 da referência (`00–0A`, `14–22`, `27`, `28`, `2B–31`, `3C–3F`, `42`, `44`, `FF`) | Confirmado |
| `1E`, `1F` (fontes divergem), `45` (rota iniciada) | Provável |
| `32–3A` rotatória anti-horária, saídas 1–9 (`0x32 + saída − 1`) | Provável |
| `0B–13` rotatória horária (mão inglesa) | Não verificado |
| `23–26`, `29`, `2A`, `3B`, `40`, `43` | Não verificado (desconhecido) |
| `41` | Não é ícone: seleciona a tela de bússola |

Cada ícone tem uma `IconDepiction` (significado semântico) usada pela UI para desenhar uma
**aproximação**. Os pixels reais do display não são conhecidos.

## 7. Tradução de manobras (`ManeuverMapper`, mão direita)

| Manobra neutra | `[2]` | `[7]` | Confiança |
|---|---|---|---|
| Seguir em frente | `09` | `09` | Confirmado |
| Virar esq./dir. | `14` / `15` | idem | Provável |
| Acentuada esq./dir. | `16` / `17` | idem | Provável |
| Leve esq./dir. | `18` / `19` | idem | Provável |
| Manter esq./dir. | `28` / `27` | `2B` / `2C` | Confirmado |
| Saída esq./dir. | `2E` / `2D` | `2B` / `2C` | Confirmado |
| Retorno | `3D` | `3D` | Provável |
| Rotatória, saída n | `32 + min(n,9) − 1` | idem | Provável |
| Chegada | `00` | `00` | Confirmado (captura real) |
| Destino esq./dir. | `01` / `02` | idem | Provável |
| Convergir / bifurcação | `1B`, `03`/`04` / `05`/`06` | idem | Provável |
| Mão inglesa (rotatória, retorno) | `0B+`, `1A` | idem | Não verificado |

## 8. Vetores de teste

Origem marcada em cada teste (`protocol/src/test`):

- **REAL** — pacote capturado de um Tripper real que o desenhou (ex.: virar à esquerda 167 m → CRC `19 1A`;
  bússolas SE/SW/NE/N à noite; SET_TIME 13:55 12h; radar compatível → CRC `3E E1`).
- **EXT** — referência externa (check value `0x29B1` do CRC; STOP_NAV `F7 82`; tela ociosa `10 50`).
- **LOCK** — trava de regressão de layout ainda não visto no hardware (rota iniciada `1A 5A`,
  recalculando `0B EA`, ícone de ligação `ED 5E`, radar experimental).

Todos os vetores REAL e EXT da referência são reproduzidos byte a byte pela implementação Kotlin
independente, e o CRC é conferido contra um segundo algoritmo (por tabela) em 2000 payloads aleatórios.

## 9. Perguntas em aberto para a Hunter 350 2027

1. ~~O Pod da Hunter anuncia como `RE_DISP`?~~ **Sim** (0.1.0). OUI não informado.
2. ~~A tabela GATT é idêntica?~~ **Sim** para serviço/característica; propriedade observada: só WRITE_NO_RESPONSE.
3. O watchdog de ~5 s e a janela de anúncio de ~30 s se repetem? (status 19 após o último quadro: **observado**; tempos não medidos)
4. O pareamento por PIN do app convive com o pareamento do app oficial, ou o substitui? (**app oficial continuou funcionando** após a sessão 2)
5. Os ícones "prováveis" e "não verificados" desenham o esperado?
6. Os bytes `[8-9]` mostram alguma distância (radar experimental)?
7. Existe ícone de radar dedicado nas faixas não testadas?

## 10. Sessão e handshake — referência × Hunter Lab

Fonte: `TripperBridge.java` da referência (comportamento validado na Meteor 350).

| Etapa | Referência (WazeTripper) | Hunter Lab 0.2.0 |
|---|---|---|
| Servidor local | Aberto antes do scan; serviço/característica iguais, WRITE \| WRITE_NO_RESPONSE | Igual, aberto a cada sessão e fechado ao fim; handshake só com o serviço registrado |
| Após a descoberta | `setCharacteristicNotification` + grava o CCCD (ou o 1º descritor) | **Não faz** (não é comando documentado; não há NOTIFY) |
| Início do handshake | Automático, 200 ms após a descoberta | **Manual** (toque), respeitando ≥ 200 ms após a descoberta |
| Dispositivo novo | `21 01` (SHOW_PIN) → keepalive → aguarda PIN | Igual, com timeout de 60 s para digitar |
| PIN | `20 …` enviado; **marca pareado e CONNECTED na hora** | `20 …` enviado; **AUTHENTICATING** até o usuário confirmar pelo display; até 3 tentativas manuais |
| Dispositivo conhecido | MAC salvo em preferências → `21 00`, 200 ms, `SET_TIME`, 150 ms, `PING_FW` ×2, 300 ms → CONNECTED | Só se houver autenticação **confirmada nesta execução** (registro em memória); mesma sequência; depois exige confirmação |
| Respostas recebidas | Registradas em hex | Registradas como resumo, sem bytes por padrão; nunca interpretadas |
| Keepalive | A cada 1 s; `PING_FW` se ≥ 2 s sem escrita | Igual, só em sessão autorizada após o handshake; suspenso durante a bússola |
| Telas | Reenvio a cada ~2 s | Só bússola, reenvio a cada 2 s, duração máx. 60 s |
| Fim de tela → relógio | Para de enviar telas; Pod cai em ~5 s; reconexão automática volta no relógio | Para de enviar; **sem reconexão automática** (voltar ao relógio pode exigir reconectar ou ciclar a ignição) |
| Queda do link | Reconexão automática (1 s se status 19) | Limpeza total; **sem reconexão** |

### Respostas do Pod

Não há formato documentado. O Hunter Lab classifica apenas a estrutura (tamanho, CRC, tipo de comando se tiver a forma
de um pacote conhecido). Descobrir o significado é objetivo dos testes físicos — e qualquer uso futuro como evidência
de autenticação exigirá captura real e decisão explícita.
