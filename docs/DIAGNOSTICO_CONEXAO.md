# Diagnóstico — Status 147 no Laboratório BLE

> **Situação: resolvido nas sessões observadas.** Com a 0.2.1 (anúncio recente obrigatório + dispositivo do anúncio),
> o laboratório conectou no celular real em três sessões (0,4–0,5 s até conectar). A confiabilidade em uso prolongado
> e em muitas ignições ainda não foi medida. A regra continua: não inspecionar no mesmo ciclo de ignição.

## 1. Fatos relatados (0.2.0, Hunter 350 2027)

- 0.1.0: conexão e inspeção GATT funcionaram.
- 0.2.0: `RE_DISP` encontrado; o laboratório inicia a sessão; a pré-autorização funciona; o servidor GATT local fica
  pronto em ~0,1–0,2 s; o cliente GATT termina com **status 147**; nenhum pacote enviado; não houve descoberta nem handshake.
- O 147 ocorreu também com a conexão da aba Dispositivos já encerrada.

## 2. O que o status 147 significa

`147 = BluetoothGatt.GATT_CONNECTION_TIMEOUT` (constante pública a partir do Android 15 — conferido no SDK 37.2).
O **link BLE não foi estabelecido**: o controlador do celular tentou iniciar a conexão e o Tripper não a aceitou a tempo.
Não é recusa do protocolo nem erro de escrita — nenhuma escrita foi tentada.
Causa típica: o alvo **não estava anunciando de forma conectável** naquele endereço durante a tentativa.

## 3. Comparação de código: inspeção (0.1.0) × laboratório (0.2.0)

| Aspecto | `GattInspector` (funciona) | `AndroidTripperLink` (0.2.0) |
|---|---|---|
| `connectGatt` | `autoConnect=false`, `TRANSPORT_LE`, contexto da aplicação | **idêntico** |
| Objeto do dispositivo | `adapter.getRemoteDevice(endereço)` | **idêntico** |
| Busca durante a conexão | parada antes (`scanner.stop()`) | parada antes (`prepareLab()`) |
| Callbacks | geração/objeto atual; `close()` em callbacks antigos | geração atual; idem |
| Momento da conexão | logo após **ver o anúncio** na busca | **sem exigir anúncio recente** — o roteiro previa buscar numa ignição e conectar em outra |
| Servidor GATT local | não existe | aberto **imediatamente antes** do `connectGatt` |
| Conexão anterior | — | abrir o laboratório chamava `inspector.reset()`; se a inspeção ainda estivesse viva, `close()` = **desconexão pelo celular** |

Os parâmetros de conexão são iguais. As diferenças estão no **momento** e no **contexto** da conexão.

## 4. Hipóteses (da mais à menos provável)

| # | Hipótese | A favor | Contra / em aberto |
|---|---|---|---|
| H1 | O Tripper **não estava anunciando** no instante do `connectGatt` | O 147 é o sintoma esperado. As tentativas vieram depois de uma inspeção no mesmo ciclo de ignição, e a inspeção termina pelo celular (liberação automática em 20 s, *Desconectar* ou o `reset` ao abrir o laboratório). Na Meteor, isso faz o Pod parar de anunciar até ciclar a ignição. O laboratório conectava sem conferir se havia anúncio. | Não se sabe se a ignição foi ciclada entre a inspeção e o laboratório. |
| H2 | **Tipo de endereço**: `getRemoteDevice(String)` cria o dispositivo como "público"; a referência conecta com o `ScanResult.getDevice()`, que carrega o tipo real | Diferença real em relação à referência | A inspeção usa o mesmo `getRemoteDevice` e funcionou; só pesaria se o Tripper usar endereço aleatório e o Android tiver perdido o registro do scan |
| H3 | **Servidor local aberto no instante anterior** ao `connectGatt` interfere | Única mudança de recurso entre os dois caminhos | Interferência costuma aparecer como 133, não 147; a referência abre o servidor antes e conecta segundos depois |
| H4 | **Outra conexão GATT** (ex.: app oficial em segundo plano) ocupa o Tripper | O Tripper deve aceitar um só celular | O app oficial deveria estar fechado |
| H5 | Busca limitada pelo Android / estado do rádio | — | Pouco provável; agora é registrado |

## 5. Correção candidata (0.2.1) — não comprovada

**Anúncio recente obrigatório (novo padrão do laboratório):** ao tocar em *Conectar*, o app faz uma busca curta (até
12 s) e só chama `connectGatt` ao receber um anúncio **conectável** do mesmo endereço, usando o `BluetoothDevice` **do
próprio anúncio**. É exatamente a sequência da referência (servidor → busca → `onScanResult` → `result.getDevice().connectGatt`).

- Se o Tripper não anunciar em 12 s, o app **não tenta conectar** e explica as causas prováveis.
- Se o anúncio vier "não conectável", não conecta e avisa (outro celular/app pode estar conectado).
- Se `RE_DISP` aparecer com **outro endereço**, isso é registrado, mas o app não conecta nele automaticamente.
- Efeito colateral desejado: servidor e `connectGatt` ficam separados no tempo, como na referência.
- **Ordem do servidor inalterada** (antes da conexão). As variantes "depois da conexão" e "desligado" existem só para
  diagnóstico; mudar o padrão depende do reteste.
- Prazo total de conexão: 12 s (anúncio) + 15 s (link).

Nada muda nos comandos, na autorização, no handshake manual ou na ausência de reconexão automática.

## 6. Diagnósticos adicionados (os mesmos nos dois caminhos)

Linhas `diag:` no *Registro da sessão* (inspeção) e no *Histórico técnico* (laboratório):

- Bluetooth ligado? Busca da aba Dispositivos ativa?
- Conexões GATT no sistema (de **qualquer** app), lado servidor, e estado do Tripper no sistema — inclusive
  "JÁ HÁ OUTRA CONEXÃO GATT COM ELE".
- Idade do último anúncio visto, tipo de endereço informado no anúncio e tipo do objeto usado no `connectGatt`
  (Android 15+).
- Laboratório: se a inspeção foi **encerrada pelo celular** e há quanto tempo.
- Momento do toque → anúncio recebido (RSSI, conectável, idade) → chamada e retorno do `connectGatt`.
- Cada `onConnectionStateChange`, com **status, newState** e milissegundos desde o `connectGatt`.
- Laboratório: cartão *Diagnóstico de conexão* com uma linha por tentativa (opções, tempos de cada etapa, status final)
  e a mesma tabela no relatório copiado, junto com o modelo do aparelho e a versão do Android.

Endereços aparecem sempre mascarados (`00:60:37:XX:XX:XX`).

## 7. Procedimento de reteste — sem transmissão

**Regras:** **nunca** toque em *Autorizar transmissão* nem em *Enviar HANDSHAKE*. Moto parada. App oficial com parada
forçada. Entre tentativas, **desligue a ignição por ~10 s**, salvo quando indicado. Ao fim de cada tentativa no
laboratório: *Histórico técnico → Copiar relatório* e guarde.

| Passo | Ignição | O que fazer | Objetivo |
|---|---|---|---|
| D0 | — | Instale a 0.2.1. Anote modelo do celular e versão do Android. | Linha de base |
| D1 | **Recém-ligada** | Dispositivos → Buscar → `RE_DISP` → **Conectar e inspecionar**. **Não** toque em Desconectar; espere o link cair sozinho ou a liberação em 20 s. Anote as linhas `diag:` do *Registro da sessão* (ou faça captura de tela). | Controle (caminho da 0.1.0) |
| D2a | **Sem ciclar** (logo após D1) | Abrir laboratório → *Diagnóstico de conexão*: **desligue** "Exigir anúncio recente"; servidor "antes" → Conectar. | Reproduz a condição relatada |
| D2b | Ciclar e **recém-ligada** | Buscar → `RE_DISP` → laboratório; anúncio recente **desligado**; servidor "antes" → Conectar (sem inspecionar antes). | 0.2.0 sem inspeção prévia |
| D3 | Ciclar e recém-ligada | Laboratório com os **padrões** (anúncio recente **ligado**, servidor "antes") → Conectar. | Correção candidata |
| D4 | Ciclar (só se D3 falhar) | Anúncio recente ligado; servidor **"depois da conexão"**. | Isola a ordem do servidor |
| D5 | Ciclar (só se D3 e D4 falharem) | Anúncio recente ligado; servidor **"desligado"**. | Isola a existência do servidor |
| D6 | Ciclar | Abra o app oficial e confirme que ele conecta. | Nada foi afetado |

Em D2–D5, se chegar a **"Pronto para autenticar"**, toque em **Interromper** (não autorize nada) e anote se o Tripper
encerrou sozinho (status 19).

### Como interpretar

| Resultado | Conclusão provável | Próximo passo |
|---|---|---|
| D2a = 147 e D2b conecta | **H1**: conectar depois de uma inspeção encerrada pelo celular, no mesmo ciclo | Manter a correção; regra de procedimento: nunca inspecionar no mesmo ciclo de ignição do laboratório |
| D2b = 147 e D3 conecta | H1/H2: faltava anúncio recente / dispositivo do anúncio | Correção candidata validada para esse caso |
| D3 = "Nenhum anúncio em 12 s" | O Tripper não anunciava (ver linhas `diag:`: outra conexão GATT? inspeção encerrada pelo celular?) | Ciclar a ignição e tentar em até ~10 s |
| D3 = 147 e D4 conecta | H3: ordem do servidor | Decisão sua: tornar "depois da conexão" o padrão |
| D3 e D4 = 147, D5 conecta | O servidor local em si interfere | Investigação adicional antes de qualquer handshake |
| Tudo falha no laboratório e D1 funciona | Diferença ainda não identificada | Comparar as linhas `diag:` de D1 e D3 |

**O que me enviar:** os relatórios copiados de D2a–D5, as linhas `diag:` de D1, o modelo do celular e a versão do Android.
