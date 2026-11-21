# Testes físicos — Hunter 350 2027 + Tripper Pod

> **Situação (0.2.0):** os testes somente leitura da 0.1.0 foram executados pelo usuário numa Hunter 350 2027
> (resultados abaixo, conforme relatado). **Nenhum teste de escrita foi executado.** Os roteiros T07–T11 usam o
> Laboratório BLE e seguem `docs/PRIMEIRO_HANDSHAKE.md`. Preencha apenas com observações reais.

## Antes de começar

- Moto **parada**, em local seguro, com o descanso abaixado. Nunca opere o celular pilotando.
- Celular Android 12+, Bluetooth ligado, Hunter Lab instalado, permissão *Dispositivos por perto* concedida.
- Feche o app oficial Royal Enfield (ou desative o Bluetooth dele) durante o teste. Anote se ele estava pareado.
- Tenha à mão: papel ou app de notas, e um cronômetro.
- O Tripper anuncia por **~30 s** depois de ligar a ignição (observado na Meteor 350). Planeje cada passo para caber nessa janela.

## Como registrar

Após cada inspeção, use **Copiar** ou **Compartilhar** no cartão *Relatório* do detalhe do dispositivo
e cole o texto numa pasta local `field-logs/` (já ignorada pelo git, porque contém endereços MAC).
Copie para a tabela de resultados só o que importa.

## Roteiros

### T01 — Descoberta
1. Ignição desligada. Abra **Dispositivos**.
2. Ligue a ignição e toque em **Buscar dispositivos** em até 5 s.
3. Anote: o nome anunciado, o endereço (só o prefixo `XX:XX:XX`), o RSSI, se aparece como "Tripper provável"
   e os serviços anunciados.
4. Repita a busca e anote quanto tempo o dispositivo continua aparecendo após ligar a ignição.

**Esperado (Meteor):** nome `RE_DISP`, prefixo `00:60:37`, visível por menos de ~30 s.

### T02 — Conexão e tabela GATT
1. Ignição recém-ligada. Busque, abra o dispositivo candidato e toque em **Conectar e inspecionar**.
2. Leia o aviso, marque a confirmação e conecte.
3. Anote: tempo até "Serviços inspecionados", lista de serviços, propriedades da característica `01FF0101-…`,
   o veredito de **Comparação com o perfil documentado** e qualquer serviço marcado como sensível.
4. **Não** toque em *Desconectar*. Anote quanto tempo até o Tripper encerrar a conexão e o status mostrado
   (no registro da sessão: "Desconectado (status N) após X s").

**Esperado (Meteor):** serviço e característica presentes, WRITE/WRITE_NO_RESPONSE, sem NOTIFY;
queda por status 19 ~5 s após conectar.

### T03 — Novo anúncio após queda iniciada pelo Tripper
1. Logo após o T02 (queda pelo Tripper), toque em **Buscar** de novo, sem mexer na ignição.
2. Anote se o Tripper reaparece e em quantos segundos.

**Esperado (Meteor):** volta a anunciar imediatamente após uma queda iniciada por ele.

### T04 — Leituras opcionais
1. Na inspeção, para características com **READ** (ex.: *Device Information*), toque em **Ler valor**.
2. Anote os textos (fabricante, modelo, firmware, hardware). Isso ajuda a comparar com a Meteor 350.

> Só faça se houver tempo antes da queda. Leituras não escrevem nada no dispositivo.

### T05 — Desconexão pelo celular (opcional, com cuidado)
1. Conecte e, antes da queda automática, toque em **Desconectar** e confirme.
2. Busque de novo e anote se o Tripper reaparece sem ciclar a ignição.

**Esperado (Meteor):** **não** reaparece até desligar e ligar a ignição.

### T06 — Convivência com o app oficial
1. Depois dos testes, abra o app oficial Royal Enfield e verifique se ele ainda conecta ao Tripper normalmente.
2. Anote qualquer diferença (pedido de novo pareamento, falha de conexão etc.).

**Esperado:** como nada foi escrito, o pareamento oficial deve continuar intacto. Isso precisa ser confirmado.

## Resultados

| ID | Data | Versão | Resultado | Notas (conforme relato do usuário) |
|---|---|---|---|---|
| T01 | nov/2026 | 0.1.0 | **Executado — OK** | `RE_DISP` encontrado. Prefixo do endereço, RSSI e tempo de anúncio: não informados. |
| T02 | nov/2026 | 0.1.0 | **Executado — OK** | Conexão e inspeção GATT bem-sucedidas; perfil igual ao documentado: serviço `01FF0100-…`, característica `01FF0101-…`, propriedade **WRITE_NO_RESPONSE**. Tempo até a queda e status: não informados. |
| T03 | — | — | Não informado | — |
| T04 | — | — | Não informado | — |
| T05 | — | — | Não informado | — |
| T06 | nov/2026 | 0.1.0 | **Executado — OK** | App oficial Royal Enfield continuou funcionando; relógio voltou ao normal; nenhum comportamento anormal observado. |
| T07 | nov/2026 | 0.2.0 | **Bloqueado antes do envio** | Laboratório: `RE_DISP` encontrado, pré-autorização OK, servidor local pronto em ~0,1–0,2 s, cliente GATT terminou com **status 147**; repetido com a inspeção já encerrada. **Nenhum pacote enviado**; sem descoberta nem handshake. Investigação: `docs/DIAGNOSTICO_CONEXAO.md`. |
| D1–D6 | — | 0.2.1 | Não executado | Reteste de conexão **sem transmissão** (`DIAGNOSTICO_CONEXAO.md` §7) |
| T08 | — | 0.2.0 | Não executado | PIN manual e confirmação |
| T09 | — | 0.2.0 | Não executado | Estabilidade com keepalive e Interromper |
| T10 | — | 0.2.0 | Não executado | Teste de bússola |
| T11 | — | 0.2.0 | Não executado | Retomada (`HANDSHAKE_RESUME`) |

Nenhum comando de escrita foi enviado nos testes T01–T06 (a 0.1.0 não tinha código de escrita).

### Ficha da moto e do celular

| Campo | Valor |
|---|---|
| Modelo / ano | Royal Enfield Hunter 350, linha 2027 (Brasil) |
| Variante | — |
| Firmware do Tripper (se legível) | — |
| Celular / versão do Android | — |
| Versão do Hunter Lab | 0.1.0 (T01–T06); 0.2.0 (T07–T11) |

## Roteiros com escrita (0.2.0) — executar só após autorização explícita

Pré-requisitos, procedimento detalhado, interrupção e recuperação: **`docs/PRIMEIRO_HANDSHAKE.md`**.
Ao fim de cada roteiro, use *Laboratório → Histórico técnico → Copiar relatório* (sem PIN, endereço mascarado).

### T07 — HANDSHAKE_SHOW_PIN
Pré-autorizar, ligar a ignição, conectar, tocar em *Enviar HANDSHAKE_SHOW_PIN*.
Registrar: o link caiu antes do toque? O display mostrou um PIN? Quanto tempo depois? Respostas recebidas (quantidade e
resumo do histórico).

### T08 — PIN e confirmação
Digitar o PIN em até 60 s. Registrar: aceito na tentativa nº? O que o display mostrou ao aceitar/recusar?
Respostas recebidas após o PIN?

### T09 — Estabilidade e Interromper
Autenticado, manter 30 s só com keepalive. Registrar: o link caiu? Após *Interromper*: em quantos segundos o Tripper
encerrou (status no histórico)? Precisou de *Desconectar agora*? O relógio voltou sozinho ou só após ciclar a ignição?
O app oficial continuou funcionando?

### Ficha de execução T07–T09 (preencher no local, à mão)

**Antes**

| Item | Resposta |
|---|---|
| Data / hora / local | |
| Celular e versão do Android | |
| App oficial conectou normalmente antes do teste (linha de base)? | sim / não |
| Endereço do `RE_DISP` igual em duas ignições? (procedimento A ou B) | sim / não — A / B |
| Captura detalhada desligada? | sim |

**T07 — HANDSHAKE_SHOW_PIN**

| Item | Resposta |
|---|---|
| Tempo entre ligar a ignição e "Pronto para autenticar" | ___ s |
| O link caiu antes do toque? (status no histórico) | não / sim, status ___ |
| O display mostrou um PIN? Em quanto tempo? | não / sim, ~___ s |
| Quantos dígitos/caracteres? (não anote o PIN) | ___ |
| Respostas recebidas (contador do diagnóstico) | ___ |
| Resultado | OK / Falhou / Interrompido |

**T08 — PIN**

| Item | Resposta |
|---|---|
| Aceito na tentativa nº | 1 / 2 / 3 / não aceito |
| O que o display mostrou ao aceitar | |
| O que o display mostrou ao recusar (se houve) | |
| Respostas recebidas após o PIN | ___ |
| Resultado | OK / Falhou / Interrompido |

**T09 — Estabilidade e Interromper**

| Item | Resposta |
|---|---|
| Display após autenticar (relógio nativo?) | |
| SET_TIME usado? Hora correta? | não usado / sim — correta / errada |
| Link estável por 30 s? | sim / caiu após ___ s (status ___) |
| Após Interromper, o Tripper encerrou em | ___ s (status ___) / não encerrou |
| Precisou de *Desconectar agora*? | não / sim |
| O relógio voltou sozinho? | sim / só após ciclar a ignição / não |
| App oficial conectou depois? | sim / só após §7 passo ___ / não |
| Relatório copiado para `field-logs/`? | sim |
| Resultado | OK / Falhou |

### T10 — Bússola (fora desta etapa; só com os critérios do `PRIMEIRO_HANDSHAKE.md` §8 e nova autorização)
Direção N, 10 s. Registrar no app: a bússola apareceu? Após o teste: voltou ao relógio, ficou na tela ou a conexão caiu?
Em quantos segundos?

### T11 — Retomada (fora desta etapa)
Na mesma execução do app, após T08 confirmado, nova conexão com *Retomar (HANDSHAKE_RESUME)*. Registrar: o Tripper
pediu PIN ou aceitou direto?

## Depois dos testes

- Atualize `HunterValidation` dos itens comprovados (`protocol/.../Confidence.kt`, `TripperIcons`) **somente**
  com base em resultados registrados aqui.
- Se a tabela GATT divergir, registre a diferença em `docs/PROTOCOLO.md` §9 antes de qualquer escrita.

## O que nunca fazer nesta fase

- Enviar comandos fora do Laboratório BLE, sem autorização ou sem ter lido o `PRIMEIRO_HANDSHAKE.md`.
- Interagir com serviços marcados como sensíveis (provável atualização de firmware).
- Mexer em chicote, conectores, ECU ou rede elétrica da moto.
