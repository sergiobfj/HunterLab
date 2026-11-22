# Tripper Pod original: investigação aprofundada de gráficos

Consulta e redação: **22/11/2026**. Branch de pesquisa `research/tripper-graphics-deep-dive`, criada a partir de `78f2e8a` (0.3.1).
Escopo: Pod pequeno (`RE_DISP`) da Hunter 350. Investigação **exclusivamente passiva**: nenhuma conexão com a moto, nenhum
pacote BLE, nenhum download de firmware, APK ou pacote de atualização, nenhuma consulta a backend do fabricante, nenhum
contato com terceiros e nenhuma alteração em navegação, BLE, radar, Playground, permissões ou assinatura.

Complementa [TRIPPER_GRAPHICS_RESEARCH.md](TRIPPER_GRAPHICS_RESEARCH.md) (21/11/2026) sem repeti-lo; referências daquele
documento aparecem como **[G-Rn]** e as novas como **[Rn]** (§19). Classificação de cada afirmação relevante:
**Confirmada** · **Forte indício** · **Hipótese** · **Não determinada**. "Confirmada" significa confirmada *na fonte citada*;
afirmações de terceiros não verificadas por nós dizem isso explicitamente.

## 1. Resumo executivo

**Resposta curta: não há caminho tecnicamente fundamentado para imagens próprias, GIFs ou vídeos no Tripper Pod original
mantendo o firmware de fábrica.** Isso não é uma prova de impossibilidade; é a conclusão que o conjunto de evidências
sustenta hoje. O único mecanismo de transferência em blocos identificado no Pod é o de **atualização de firmware** — e usá-lo
para outra coisa seria modificar firmware, fora do escopo e de alto risco.

O que mudou em relação a ontem:

1. **O Pod da Hunter é desta família e está homologado no Brasil.** O manual europeu do HNTR 350 declara o rádio "type
   **JDCP**"; existe um segundo Pod irmão, **NT8-J1AB** (Classic 350); a ANATEL homologou **JDCP e J1AB** (homologação
   09047-21-09215, certificado UL-BR 21.0859) [R1–R5]. Qual dos dois está na Hunter brasileira 2027 continua aberto.
2. **Hardware: uma placa redonda com um único microcontrolador sem fio NXP**, memória de 8 pinos e conector do display
   (fotos internas FCC, agora acessíveis [R6]). A leitura parcial da marcação aponta para a família **NXP KW36A** (Cortex-M0+
   48 MHz, **64 KB de SRAM**, 512 KB de flash) — **hipótese** de confiança moderada [R7, R8]. Se confirmada, a RAM não comporta
   sequer um quadro 240×240 colorido.
3. **A atualização de firmware passa pela mesma característica BLE do protocolo de telas**, com opcodes `02` e `04`–`0A`,
   blocos de flash em formato S-record e hash SHA-256 por bloco, segundo análise estática **de terceiro** do app oficial
   [R9]. O `03` que todos chamam de `PING_FW` seria uma **consulta de versão**. Consequência de segurança: o espaço de
   comandos "desconhecidos" contém comprovadamente rotinas de regravação — fuzzing seria perigoso, não apenas inútil.
4. **"Image changes via FOTA"** (relatório FCC) não indica um canal de imagens do usuário: no app oficial, qualquer mudança
   chega como regravação de blocos de flash, controlada por metadados do fabricante [R9, R10].
5. **Telas residentes documentadas pela própria Royal Enfield**: logotipo, capacetes coloridos, "Desenvolvido em parceria com
   a Google", **versão do software na inicialização**, "fonte de seta exclusiva" e seta que "se preenche" ao aproximar
   [R11, G-R6]. Um terceiro fotografou numa Hunter o preenchimento em etapas e os números de saída de rotatória [R12].
6. **Todos os projetos de terceiros encontrados** apenas selecionam símbolos residentes ou reaproveitam campos numéricos;
   um deles afirma textualmente que o Pod "não tem texto nem tamanho de fonte" [R13]. Nenhuma imagem própria demonstrada.

### Classificação final obrigatória

| Possibilidade | Classificação | Base |
|---|---|---|
| **GIF real no Tripper original, mantendo o firmware** | **Contrariado pelas evidências** | Sem canal de pixels, sem decodificador evidenciado, MCU provavelmente pequeno, app oficial e quatro projetos independentes só enviam campos fixos |
| **Imagens personalizadas estáticas** | **Contrariado pelas evidências** | Mesmas razões; o transporte BLE comportaria uma imagem pequena, mas nenhum comando de exibição de pixels foi encontrado |
| **Animações de símbolos residentes** | **Plausível com evidências** | O Pod troca de tela a cada comando e o firmware tem estados visuais (preenchimento por aproximação); taxa segura conhecida ≤ 0,5 troca/s; não testado na sua Hunter além da bússola |
| **Vídeos completos** | **Contrariado pelas evidências** | Tudo acima, mais fluxo de 72 kB/s a 1,15 MB/s contra teto teórico de ~99 kB/s do BLE 4.2 a 1 Mbps |

"Tecnicamente inviável" não foi atribuído a nenhum item: isso exigiria análise do firmware real, que não está disponível.

## 2. Estado da pesquisa anterior

| Afirmação de 21/11 | Situação hoje |
|---|---|
| Família Visteon JDCP / FCC NT8-JDCP | **Reforçada**: manual HNTR 350 (UE) declara JDCP; ANATEL cobre JDCP e J1AB [R1–R5] |
| Fotos internas inacessíveis | **Corrigida**: obtidas pelo endereço `pdf.php` do fccid.io; os anexos 5227608 e 5227630 são o mesmo arquivo [R6] |
| TFT 1,49", FOTA, HW PWB25068, SW V07.05 | **Mantida**; o J1AB tem a mesma descrição, HW PWB25073 e SW V07.05 [R2] |
| Indícios de plataforma NXP | **Reforçada**: logotipo NXP/Freescale no CI principal; candidato KW36A (hipótese) [R6, R7] |
| MCU, memória, controlador gráfico desconhecidos | MCU agora com candidato; memória dependente dele; controlador, resolução e barramento **ainda não determinados** |
| FOTA: procedimento não verificado | **Avançou** por análise estática de terceiro: mesma característica, opcodes `02`/`04–0A`, metadados no backend do fabricante [R9, R10] |
| Ausência de API de bitmap | **Mantida e reforçada** pelo construtor de navegação de campos fixos do app oficial [R10] |
| Correspondência com a Hunter brasileira 2027 | Família: **forte indício**. Variante exata (JDCP × J1AB) e versão de firmware: **não determinadas** |

## 3. Descobertas novas

| # | Descoberta | Fonte | Classificação |
|---|---|---|---|
| N1 | Manual HNTR 350 (inglês, UE), p. 126: Visteon declara o rádio "**type JDCP**" conforme 2014/53/UE | [R3] | **Confirmada** para o HNTR europeu |
| N2 | Segundo Pod **NT8-J1AB** (concedido em 13/07/2021): "1.49" TFT … FOTA", HW PWB25073, SW V07.05; manual do Classic 350 cita esse FCC ID | [R2] | **Confirmada** |
| N3 | ANATEL: homologação **09047-21-09215**, certificado **UL-BR 21.0859** (13/07/2021–12/07/2027), fabricante Visteon, modelos JDCP (variantes J1D, J1C1, P4D, D2K/D3K, D2G2/D3G) e J1AB | [R4] dados abertos, CSV de 22/11/2026 | **Confirmada** |
| N4 | ISED Canadá: IC 3043A-JDCP e 3043A-J1AB; Japão: 技適 R 201-210459 no manual japonês da Hunter | [R2, R5] | **Confirmada** |
| N5 | Manual Hunter 350 2026: "Tripper Bluetooth version is 4.2" | [R14] (lido pela frente de hardware) | **Confirmada** na fonte |
| N6 | Fotos internas: placa única PWB25068; CI QFN principal com logotipo NXP/Freescale; CI de 8 pinos U2; conector FPC J1; antena INPAQ; cristal Y1; pads CLK/DIO/3.3V/GND (e CS) no verso | [R6] | **Confirmada** (o que é visível) |
| N7 | Marcação do CI principal: leitura parcial "…36A…" e máscara "1N41U" por dois revisores do OpenTripper; nossa frente de hardware considerou ilegível; nossa inspeção do mesmo recorte é **compatível, mas ambígua** | [R7] | Família NXP sem fio: **forte indício**. KW36A: **hipótese** |
| N8 | App oficial: atualização do Pod pela característica `01FF0101`, opcodes `02` (início), `03` (versão), `04`–`0A` (parâmetros, blocos, segmentos, hash, verificação, regravação); blocos S3; MTU 247 solicitado durante a atualização; nenhuma API de assinatura nas classes revisadas | [R9] (análise estática de terceiro; não reproduzida) | **Forte indício** |
| N9 | Metadados de firmware do Pod em coleção Firestore `RE_FOTA_TRIPPER` (URL, mapeamento, versão, versão-base, tipo, região, variante); terceiros receberam `PERMISSION_DENIED` | [R7, R10] | **Forte indício** |
| N10 | Construtor de navegação do app oficial: comando `0x10`, campos fixos, sem fonte, bitmap ou layout | [R10] | **Forte indício** |
| N11 | Sequência oficial de inicialização com **versão do software** na tela | [R11, G-R6 p. 56] | **Confirmada** |
| N12 | Fotos numa Hunter (Índia): preenchimento em etapas da seta com `[6]` = `10…50`; rotatória `0B–13` mostra números de saída 1–9 sobre um mesmo desenho | [R12] (fotos não publicadas) | **Forte indício** |
| N13 | "The Pod has **no text and no font size**…" (Tripper-Plus); velocidade GPS mostrada no campo de distância com unidade fixa | [R13] | Afirmação **confirmada** na fonte; é relato de desenvolvedor |
| N14 | Um valor "12.10" pode ser padrão interno do app oficial, não leitura do Pod | [R9] | **Forte indício**; não confiar em "12.10" isolado |
| N15 | Tripper **Dash** confirmado como família separada: NT8-REICGEN1 (2024), "4.0 inch circular TFT, connected cluster"; ANATEL "REIC GEN 1" | [R2, R4] | **Confirmada** |

## 4. Identificação de hardware

| Item | Conclusão defensável | Classificação |
|---|---|---|
| Fabricante | Visteon (silkscreen "Visteon", grantee NT8, declaração UE) | **Confirmada** |
| Família / modelo | JDCP (PWB25068) e J1AB (PWB25073), mesma descrição e mesmo SW de ensaio | **Confirmada** que existem; mesma família: **forte indício** |
| Modelo na Hunter BR 2027 | JDCP (declaração UE do HNTR) ou J1AB (ambos homologados no Brasil) | **Não determinada** |
| Arquitetura | Uma placa redonda; um CI principal QFN; U2 de 8 pinos; FPC J1 para o display; sem segundo processador visível | **Confirmada** (inspeção visual) |
| MCU / SoC | Microcontrolador sem fio NXP (logotipo, OUI `00:60:37`, Wireless UART, software de ensaio NXP) | **Forte indício** |
| Part number | KW36A (Cortex-M0+ 48 MHz, 64 KB SRAM, 512 KB flash, BLE 5.0 capaz) [R8] | **Hipótese** (moderada) |
| Chip Bluetooth | Integrado ao MCU, se KW36A | **Hipótese** |
| Versão BLE em uso | 4.2 (manual 2026); ensaio FCC a 1 Mbps | **Confirmada** na fonte; 2M PHY provavelmente não usado |
| Flash externa | U2 (8 pinos) poderia ser flash SPI ou EEPROM | **Hipótese**; capacidade **não determinada** |
| RAM / PSRAM | Sem RAM externa visível; se KW36A, 64 KB de SRAM no total | **Hipótese** condicionada ao part number |
| Controlador LCD, resolução, cor, barramento | Não legíveis; diagrama de blocos e descrição operacional sob confidencialidade de longo prazo na FCC | **Não determinada** |
| Interface de depuração | Pads CLK/DIO/3.3V/GND são compatíveis com SWD | **Hipótese**; nada indica que esteja habilitada ou sem proteção de leitura |
| Alimentação | 13,5 V nominal (relatório 15B) | **Confirmada** |
| Part numbers RE | Etiqueta de amostra "RAM00261/B"; exemplo J1AB "1100494/D"; anúncio de revendedor sugere "RAM00063" para Meteor/Classic/Hunter | Marcações **confirmadas**; equivalência **hipótese** |

Por que isso importa para gráficos: num MCU de 64 KB de SRAM com pilha BLE, o desenho típico é feito **direto na memória do
controlador do display**, em blocos, a partir de símbolos guardados em flash (interna ou na U2). Essa arquitetura é
coerente com "selecionar ícones residentes" e com a frase do manual "fonte de seta exclusiva". Ela não impede, em tese, que
um firmware desenhe uma imagem recebida — mas exigiria um firmware que faça isso.

Hipótese descartada: um CRC de aplicação dentro de quadros já protegidos pelo enlace BLE poderia sugerir uma ponte UART entre
um chip BLE e um MCU gráfico separado. As fotos mostram **um único processador**, e o mesmo CRC protege os quadros de
atualização [R9]: o CRC é melhor explicado como parte do próprio protocolo do produto.

Inacessíveis (não contornados): fcc.report e visualizador oficial da FCC (403), anexos ANATEL no SCH (login gov.br), MIC
giteki (403). Pistas não concluídas: relatório 15B do J1AB, bases de outros países, catálogo oficial de peças, teardowns.

## 5. Investigação de firmware

**Texto FOTA (verbatim, relatórios FCC MDE_VIS_2017_FCC_01 p. 9 e FCC_02 p. 8):** "A 1.49" TFT with TBT (Turn By Turn
navigation) and FOTA feature for 2 wheeler products. […] The SW updates or image changes if any, will be uploaded via FOTA."
[R15] — **Confirmada** (declaração do fabricante).

Interpretação: a frase admite tanto "imagem de firmware" (vocabulário NXP) quanto "imagens gráficas" (a construção "SW
updates **or** image changes" sugere coisas distintas). A frente de firmware inclinou-se levemente para gráficos. **A
pergunta perde importância** diante do mecanismo encontrado no app oficial: as atualizações chegam como **blocos de flash
endereçados**, com atualização parcial por tabela de mapeamento de blocos [R9]. Mudar gráficos residentes, se e quando o
fabricante quiser, é regravar flash. **Forte indício.** Não foi identificado nenhum formato separado de "pacote de
recursos", nenhum comando de upload para o usuário, nem leitura de firmware de volta.

**OTAP padrão da NXP** (serviço `01FF5550`, arquivo com cabeçalho e sub-elementos, integridade por CRC, bootloader que
substitui a aplicação) [R16] grava o **binário executável** na flash. Pela análise do app oficial, o Pod **não usa esse
serviço** para atualizar: usa a própria característica de dados. Portanto, o bloqueio do serviço `01FF555x` no Hunter Lab
não é a proteção relevante — a proteção real é a lista branca de comandos (§17).

**Atualizações em campo:** a página oficial pede que o Pod esteja "updated regularly", sem dizer como; o FAQ do app só traz
tutorial de firmware do Dash; nenhum relato público de atualização do Pod com mudança visual foi encontrado [R17]. A
análise de terceiro também encontrou no app oficial um desvio para a concessionária quando a versão-base instalada é antiga
demais [R7]. **Não determinado** se alguma Hunter recebeu FOTA em campo.

| Pergunta técnica | Resposta | Classificação |
|---|---|---|
| Usa framebuffer? | Se KW36A, não há RAM para um quadro completo colorido; desenho por blocos no controlador do display seria o caminho natural | **Hipótese** |
| Sprites residentes? | Sim: ícones por ID, logotipos, capacetes, dígitos, fonte de seta | **Confirmada** (manuais, protocolo, fotos) |
| Aceita atualização de recursos? | Só como regravação de flash pelo fabricante | **Forte indício** |
| Camada gráfica genérica exposta? | Nenhuma | **Forte indício** (app oficial e quatro projetos) |
| O app oficial envia IDs ou conteúdo visual? | IDs e números em campos fixos | **Forte indício** [R10] |
| Decodificador de imagem? | Nenhuma evidência | **Não determinada** |
| Pode armazenar novos desenhos? | Só via firmware/flash | **Forte indício** |
| O que é renderizado no celular? | Nada que chegue ao Pod como pixel; o celular calcula manobra, distâncias e horários | **Forte indício** |
| O que é renderizado no Pod? | Tudo o que aparece: símbolos, dígitos, temas, preenchimento, relógio, PIN, logotipos | **Confirmada** |

Ferramentas gráficas: a Visteon divulga Altia/Kanzi para clusters automotivos de 64 bits [R18]; nada liga isso ao Pod.
**Não determinada.** Nenhum firmware, SDK, formato de recursos ou API do Pod foi publicado por RE, Visteon ou Google.

## 6. Protocolo BLE e comandos

Fontes: `docs/PROTOCOL.md` e `TripperBridge.java` do WazeTripper em `7c4235d` (sem commits depois de 20/09/2026,
conferido em 22/11/2026), código do Hunter Lab em `78f2e8a` e a reconstrução estática de terceiro [R9].

### 6.1 Arquitetura do transporte

| Camada | O que se sabe | Classificação |
|---|---|---|
| GATT | Serviço *Wireless UART* NXP `01FF0100-…`, característica `01FF0101-…`; na Hunter só WRITE_NO_RESPONSE, sem NOTIFY | **Confirmada** (Hunter, T02) |
| Volta (Pod → celular) | O Pod escreve num servidor GATT do celular; respostas de **20 bytes com CRC** segundo o validador do app oficial [R9] e uma resposta com CRC válido registrada por terceiro numa Hunter [R12] | Chegada **confirmada** na Hunter (T09); formato: **forte indício**; significado: **não determinado** |
| Quadro | 20 bytes = 18 de payload + CRC-16/CCITT-FALSE; na atualização há quadros maiores (segmentos de dados) | Telas: **confirmada**. Quadros maiores: **forte indício** [R9] |
| MTU | 20 bytes = MTU padrão (23 − 3). Nenhum dos dois projetos negocia MTU; no Hunter Lab, `requestMtu` é proibido por `SafetyGuardTest`. O app oficial pede MTU 247 durante a atualização | Resultado da negociação no Pod: **não determinado** |
| PHY | BLE 4.2, ensaio a 1 Mbps | **Confirmada** na fonte |
| Cadência | Reenvio de tela a cada ~2 s; watchdog ~4–5 s; escritas serializadas | **Confirmada** na Meteor; status 19 observado na Hunter |
| Controle de fluxo | Inexistente nos comandos de tela; **existe** na atualização (pare-e-espere com confirmação por bloco/segmento) [R9] | **Forte indício** |

O *Wireless UART* é um tubo de bytes genérico [G-R5]: o transporte não limita o conteúdo. O firmware já implementa
fragmentação, confirmação, hash e quadros variáveis — **mas o único consumidor conhecido desses dados é a gravação de flash.**

### 6.2 Matriz de comandos e campos

| Comando / campo | Dado transmitido | Interpretado pelo firmware como | Status | Pode representar gráfico? |
|---|---|---|---|---|
| `21 01` / `21 00` | Início de sessão | Pod mostra **PIN gerado internamente** / retoma | Confirmada (Meteor); `21 01` observado na Hunter | Não; prova glifos residentes |
| `20` + ASCII ≤ 6 | PIN digitado | Comparação | Confirmada | Não |
| `03` (`PING_FW`) | Usado como keepalive | **Consulta de versão** (resposta com 2 bytes formatados `%02x.%02x`) | Keepalive: confirmada. Versão: **forte indício** [R9] | Não |
| `50 hh mm` | Hora | Relógio com dígitos residentes | Confirmada | Não |
| `10 11` `[2]` / `[7]` | IDs de ícone grande / pequeno | Índices de símbolos residentes (com composição, ex.: número de saída sobre a rotatória) | Confirmada (42 códigos, Meteor); `0B–13` fotografado numa Hunter [R12] | Seleção, não pixels |
| `10 11` `[3-5]` | Distância uint16 + unidade (`1`, `2`; `4` na tela ociosa) | Número com fonte residente | Confirmada (1/2); `4` **não determinado** | Não |
| `10 11` `[6]` | Noite (bit 0) + nibble alto `10…50` | Tema + **preenchimento em etapas** da seta | Bit 0: confirmado; preenchimento: **forte indício** [R12] | Estado visual residente, não pixel |
| `10 11` `[8-9]` | `FF FF` ou distância de radar | Não documentado | **Não determinado** | Sem evidência |
| `10 11` `[10]` | Sempre `41` | Desconhecido | **Não determinado** | Sem evidência |
| `10 11` `[11-13]` | Rodapé: distância/tempo/chegada | Texto numérico residente | Confirmada | Não |
| `10 11 41` `[14]` | Direção `10…80` (8 setores) | Rosa dos ventos residente | Confirmada (N/E na Hunter) | Não |
| `10 11` `[15-17]` | Sempre `00` | — | **Não determinado** | Não bastam para protocolo de bloco |
| `10 11 1C` / `45` / `3C` | Recalculando / rota iniciada / ociosa | Telas residentes | Confirmada / provável / provável | Não |
| `40 05` | Sobreposição de chamada | Ícone residente posicionado pelo firmware | Confirmada (Meteor); ausente em Pods antigos segundo um projeto [R19] | Não |
| **`02`, `04`–`0A`** | **Início, parâmetros, blocos, segmentos, hash, verificação e regravação de firmware** | **Gravação de flash** | **Forte indício** [R9] | É o único canal de bloco — e é **regravação de firmware**. **Nunca enviar** |
| Serviço OTAP NXP `01FF5550-…` | — | Atualização (documentação NXP) | Não observado na Hunter; não usado pelo app oficial segundo [R9] | Fora de escopo |

### 6.3 O que um canal de imagem exigiria

Uma transferência de imagem precisaria de início com tamanho/formato, sequência ou offset por fragmento, controle de fluxo,
integridade e um comando de exibição. **Todos esses elementos existem no Pod — no procedimento de atualização de
firmware.** Nenhum existe no conjunto de comandos de tela. Os espaços ainda não mapeados (família `3x`, outros
subcomandos de `10`/`40`, unidade `4`, bytes `[8-10]`/`[15-17]`) não têm qualquer indício gráfico, e o mapa já mostra que
valores "desconhecidos" podem disparar regravação. Explorá-los exigiria transmitir valores desconhecidos ao Pod —
**proibido nesta pesquisa e desaconselhado em qualquer fase.**

### 6.4 Dados já existentes, analisáveis sem tocar na moto

- Não há bytes brutos de respostas no repositório: a captura detalhada ficou desligada em T07–T10 e `field-logs/` é
  ignorada pelo git. Por isso **nenhum parser de pesquisa foi construído** — sem dados reais, seria especulação codificada.
- **Se você guardou os relatórios** da inspeção 0.1.0 (T02) e do laboratório 0.2.1, eles respondem offline: (a) se o Pod
  expõe *Device Information* (`0x180A`) ou um serviço `01FF555x-…`; (b) se as respostas tinham 20 bytes com CRC válido.
- Como o keepalive do Hunter Lab é o próprio `03`, **as respostas contadas na T09 provavelmente incluíam a versão do
  firmware** — não foram gravadas (hipótese; ver §18).

## 7. Possibilidade de imagens personalizadas

| Nível | Definição | Requisitos | Situação |
|---|---|---|---|
| 1 — Símbolos predefinidos | Selecionar elementos existentes | Comando de seleção | **Confirmado** (Meteor); na Hunter, bússola N/E observada; demais por terceiros [R12] |
| 2 — Animação de símbolos | Alternar símbolos/estados | Nível 1 + cadência aceita | Troca de tela por comando: confirmada; cadência segura conhecida: ≤ 1 troca/2 s; mais rápido: **não determinado** |
| 3 — Imagem personalizada | Bitmap do usuário | Protocolo de transferência **+** memória **+** comando de exibição | Transferência em bloco existe só para flash; exibição de pixels externos: **sem evidência** |
| 4 — GIF / vídeo | Múltiplos quadros | Nível 3 + armazenamento de quadros + decodificador + temporização | **Sem evidência** de nenhum componente específico |

Para o Nível 3 no firmware de fábrica seria preciso um comando oculto que recebesse pixels e os desenhasse. Contra essa
hipótese pesam: o construtor de navegação do app oficial só monta campos fixos [R10]; quatro projetos independentes (dois
na Hunter) não encontraram nada além de seleção [R12, R13, R19]; o desenvolvedor do Tripper-Plus afirma que não há texto nem
tamanho de fonte [R13]; o único canal em blocos grava flash [R9]. **Contrariado pelas evidências**, sem prova de
impossibilidade.

Via **firmware de fabricante**: a Royal Enfield poderia, em tese, publicar novos desenhos por FOTA. Isso não é um caminho
para o usuário.

## 8. Possibilidade de GIFs

Um GIF real precisa do Nível 3 mais decodificação LZW, paleta, temporização e, em geral, um canvas. Nada disso é
evidenciado. Num Cortex-M0+ de 48 MHz com 64 KB de SRAM (hipótese KW36A), um decodificador GIF pequeno caberia em memória,
mas teria de existir no firmware. **Contrariado pelas evidências.**

O que **é** possível chamar de "animação" no Pod original é o Nível 2: uma sequência de telas residentes (setas, bússola,
estados de preenchimento) trocadas por comandos conhecidos. Isso já existe como **prévia** nos "Efeitos" do Playground.
Não é GIF: não há quadros próprios, só escolha de desenhos de fábrica.

## 9. Possibilidade de vídeos

Vídeo combina todos os requisitos do Nível 4 com fluxo contínuo. Mesmo com compressão MJPEG otimista, 240×240 a 10 fps
exige 72–144 kB/s, no limite ou acima do teto teórico de ~99 kB/s do BLE 4.2 a 1 Mbps, além de decodificar JPEG num MCU sem
acelerador. H.264 exigiria memória de quadros de referência incompatível com a classe de MCU candidata. **Contrariado pelas
evidências.** O streaming de vídeo do OpenDash/better-dash é do **Tripper Dash** (Wi-Fi), outro aparelho [G-R12].

## 10. Estudo quantitativo

Todos os cenários são **hipotéticos**. Nenhum afirma que o Pod aceite fragmentos, tenha essa resolução ou essa memória.

**Premissas.** 128×128 e 240×240 (pedidas) e 280×280 — extrapolação geométrica se 1,49" fosse o diâmetro de um painel
redondo com a densidade (~188 ppi) do painel comum de 1,28"/240 px; **hipótese**. Um painel redondo usa ~78,5% (π/4) dos
pixels. Fragmento hipotético no quadro de 20 bytes: 6 bytes de cabeçalho (comando, subcomando, sequência, offset) →
**12 bytes úteis**; com MTU 247: 8 bytes de cabeçalho → 236 bytes úteis.

**Taxas de transporte** (dados úteis por segundo):

| Cenário | Base | Taxa útil |
|---|---|---:|
| T0 — cadência conhecida | 1 quadro a cada 2 s | 6 B/s |
| T1 — 20 B conservador | 1 escrita por evento de 30 ms | 400 B/s |
| T2 — 20 B otimista | 4 escritas por evento de 15 ms | 3,2 kB/s |
| T3 — teto 1M PHY sem DLE | 37 B no ar + T_IFS + ACK vazio = 676 µs por 20 B de ATT | 17,8 kB/s (29,6 kB/s de ATT bruto) |
| T4 — teto 1M PHY com DLE | 261 B no ar = 2 468 µs por 244 B de ATT | 95,6 kB/s (98,9 kB/s bruto) |

T3/T4 são **tetos de rádio contínuo**, não throughput de aplicativo: Android, intervalo de conexão, pacotes por evento,
retransmissões e o processamento do Pod reduzem a taxa. O procedimento oficial de atualização ainda é **pare-e-espere**
por segmento [R9], o que derruba a taxa real bem abaixo de T3/T4.

**Uma imagem estática:**

| Resolução / formato | Bytes (quadrado) | Bytes (círculo) | Fragmentos 20 B | T0 | T1 | T2 | T3 | T4 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 128² · 1 bpp | 2 048 | 1 609 | 171 | 5,7 min | 5,1 s | 0,6 s | 0,1 s | <0,1 s |
| 128² · RGB565 | 32 768 | 25 736 | 2 731 | 1,5 h | 1,4 min | 10,2 s | 1,8 s | 0,3 s |
| 240² · 1 bpp | 7 200 | 5 655 | 600 | 20 min | 18 s | 2,2 s | 0,4 s | 0,1 s |
| 240² · 8 bpp indexado | 57 600 | 45 239 | 4 800 | 2,7 h | 2,4 min | 18 s | 3,2 s | 0,6 s |
| 240² · RGB565 | 115 200 | 90 478 | 9 600 | 5,3 h | 4,8 min | 36 s | 6,5 s | 1,2 s |
| 280² · RGB565 | 156 800 | 123 151 | 13 067 | 7,3 h | 6,5 min | 49 s | 8,8 s | 1,6 s |

**Sequências e animações** (sem compressão):

| Conteúdo | 10 quadros | 5 fps | 10 fps | Comparação |
|---|---:|---:|---:|---|
| 128² · 1 bpp | 20,5 kB | 10,2 kB/s | 20,5 kB/s | Acima de T2; dentro de T3/T4 só em teoria |
| 128² · RGB565 | 328 kB | 164 kB/s | 328 kB/s | 1,7× a 3,4× o teto T4 |
| 240² · 1 bpp | 72 kB | 36 kB/s | 72 kB/s | 2× T3; abaixo de T4 só em teoria |
| 240² · RGB565 | 1,15 MB | 576 kB/s | 1,15 MB/s | 6× a 12× o teto T4 |
| 280² · RGB565 | 1,57 MB | 784 kB/s | 1,57 MB/s | 8× a 16× o teto T4 |

**GIF simples** (240², 8 bpp, LZW 3:1 a 8:1 em arte chapada — **hipótese de conteúdo**): 7,2–19,2 kB por quadro; 10
quadros = 72–192 kB; 5 fps = 36–96 kB/s. Decodificadores embarcados comuns usam ~20–25 kB de RAM de trabalho, mais buffer de
linha e, com transparência/descartes, um canvas indexado de 57,6 kB — quase toda a SRAM de um KW36A.

**Vídeo simplificado** (MJPEG 240², 1–2 bits/pixel — **hipótese**): 7,2–14,4 kB por quadro; 10 fps = 72–144 kB/s.

**Memória.** Se o MCU for KW36A: 64 KB de SRAM e 512 KB de flash, compartilhados com a pilha BLE e com a aplicação [R8].
Dez quadros 128² RGB565 (328 kB) já ocupariam mais de metade da flash total; 240² RGB565 não cabe nem um quadro na SRAM. A
U2 poderia guardar recursos, mas sua capacidade é desconhecida.

**Barramento do display** (se SPI, **hipótese**): um quadro RGB565 240² leva 46 ms a 20 MHz ou 23 ms a 40 MHz — não seria o
primeiro gargalo.

**Conclusão numérica.** Pela cadência conhecida, uma imagem leva de minutos a horas: inviável **pelo protocolo de telas**.
O enlace BLE em si comportaria uma imagem estática pequena em segundos — o transporte **não** é o bloqueio para imagens
estáticas; o bloqueio é a ausência de um consumidor de pixels no firmware. GIF colorido a 5–10 fps e vídeo esbarram, além
disso, no teto físico do BLE 4.2 a 1 Mbps e na memória provável. Cálculos reproduzíveis pelas fórmulas acima; nenhuma
medição de throughput foi feita.

## 11. Projetos e relatos de terceiros

Categorias: **(a)** selecionar telas/símbolos residentes; **(b)** reaproveitar campos numéricos; **(c)** renderização real de
pixels. **Nenhum projeto (c) foi encontrado para o Pod original.**

| Projeto / fonte | O que faz ou afirma | Categoria | Verificação |
|---|---|---|---|
| WazeTripper (`jnsdias`), `7c4235d` | Navegação Waze, bússola, radar, chamada; sem commits, issues ou PRs após 20/09/2026 | (a) | Clone conferido por nós |
| Forks do WazeTripper: Tripper-Plus (`RaunakEduc`), wazetripper-india (`Fahrudeen`), DidoDes 0.6.0-beta | Velocidade GPS no campo de distância ("60.0 Km", unidade fixa); rotatória mão inglesa; diagnóstico | (b) / (a) | README do Tripper-Plus conferido [R13]; demais relatados pela frente de ecossistema |
| OpenTripper (`spandan-kumar`), `a2f9d12`, 31/10–03/11/2026, **sem licença** | 40 fotos numa Hunter; análise estática do app oficial (atualização, metadados); leitura das fotos FCC. Conclusão do próprio projeto: layout livre, gráficos arbitrários e troca de firmware "remain unresolved" | (a) | Documentos conferidos por nós [R9, R10, R12]; nada copiado |
| Motolog (app `in.satrujit.tripper_bridge`) | "Display Mode — Normal or Compass", tema, 12/24 h, alerta de chamada, limite de bateria fraca, aviso sem internet | (a) | Site conferido [R20] |
| Motolog — post "full tripper pod display control" | Autor alegaria ter extraído firmware do Pod e analisado no Ghidra, sem publicar método | — | Só via fonte secundária [R10]; **Reddit inacessível** para nós. **Hipótese** |
| Motolog — "restrito pelo firmware" | Frase pedida pela missão | — | **Não obtida** (Reddit bloqueou acesso automatizado; ver §18) |
| Super TripperPod (`marzochi`, BR) | Seta, próxima seta, ETA, chamada, bússola, radar; FAQ citaria problemas de bússola antes do firmware 12.10 | (a) | Site acessado; a frase sobre 12.10 **não reconferida** (FAQ dinâmico) |
| tripper-bridge (`tripper-bridge-dev`, Himalayan 411) | Pod "1ª geração": bússola distorcida e sem ícone de chamada | (a) | Relatado pela frente de ecossistema; **não reconferido**. Sugere comportamento dependente de versão |
| opentripper (`OSolerHugo`), Cruizer, TripperWipper, rumo, PodBridge, re-ride | Velocímetro no campo de distância; listas de ícones; nada aparece no Pod (PodBridge) | (a)/(b) | Relatados; **não reconferidos** |
| Quicker-pod (`praneth2580`) | Documenta resposta de versão; **inclui página de sondagem/fuzzer com `04`–`0A`**; documentos gerados por IA | — | **Não usar**: envia comandos da família de regravação |
| OpenDash, better-dash, NavDash, TripperMaps, re-connector | Streaming/mapas no **Tripper Dash** | Excluído | Outro aparelho |
| HimalayanGauge | Substitui a eletrônica por ESP32-S3 + AMOLED | Excluído | Não é o Pod original |

Observação metodológica: o OpenTripper analisou o APK oficial e consultou o backend do fabricante. **Esta pesquisa não
repetiu nada disso**; citamos achados publicados como evidência documental de terceiro, com o peso correspondente.

### Mensagens sugeridas (não enviadas; só com sua autorização)

**WazeTripper** (issue no GitHub, pt-BR):
> Olá! Mantenho o Hunter Lab, app independente para o Tripper Pod de uma Hunter 350 2027 (BR). Estamos pesquisando, sem
> fuzzing, se o Pod tem algum recurso gráfico além dos ícones residentes. Duas perguntas objetivas: (1) você chegou a
> registrar os bytes da resposta do Pod ao `03` (PING_FW)? Há relatos de que ela traz a versão do firmware — qual versão
> aparecia na sua Meteor? (2) Em algum momento viu o Pod exibir algo que não fosse um ícone/tela já catalogado? Não pedimos
> firmware nem código proprietário. Obrigado pelo protocolo documentado — ele é a base do nosso trabalho.

**Motolog** (quadro de sugestões do app, inglês):
> Hi! I maintain Hunter Lab, an independent app for the Tripper Pod (Hunter 350, Brazil). We are researching, without any
> fuzzing, whether the stock Pod has graphics capabilities beyond resident icons. You mentioned that Pod features are
> restricted by firmware. Could you share what that conclusion was based on — e.g., did you find any command that draws
> text or images, or confirm there is none? We are not asking for firmware files or proprietary code. Thanks!

**OpenTripper** (issue no GitHub, inglês):
> Hi! Your POD_OTA_PROTOCOL and Hunter photo notes were very useful for our safety review. Did any Hunter you tested
> report a firmware version through opcode 03 or the boot "SW version" screen? We'd like to correlate behaviour with
> versions. We will not use opcodes 02/04–0A. Thanks!

## 12. Evidências a favor e contra cada hipótese

| Hipótese | A favor | Contra | Conclusão |
|---|---|---|---|
| **A — comando gráfico desconhecido** | Bytes e famílias de comando não mapeados; transporte genérico; firmware já sabe receber blocos | App oficial só monta campos fixos [R10]; nenhum projeto achou desenho de pixels; "no text and no font size" [R13]; espaço desconhecido contém regravação [R9]; MCU provável pequeno | **Contrariada**; só firmware real poderia excluí-la de vez |
| **B — recursos residentes** | Manuais (logotipo, capacetes, fonte de seta, preenchimento), 42+ códigos confirmados, composição (número sobre rotatória) | Catálogo completo e animações internas além da inicialização: desconhecidos | **Confirmada** a existência; extensão **não determinada** |
| **C — recursos atualizáveis** | "image changes via FOTA"; atualização parcial por mapa de blocos [R9, R15] | Só o fabricante publica; metadados restritos no backend; formato é flash endereçada, não "pacote de imagens" | Atualizável **pelo fabricante: forte indício**; pelo usuário: **contrariada** |
| **D — limitação da interface BLE** | Tubo genérico; o próprio Pod recebe blocos com hash e quadros maiores | BLE 4.2 / 1 Mbps limita fluxo contínuo; pare-e-espere | BLE **não** é o limitador principal para imagem estática; **é** para vídeo |
| **E — limitação estrutural do firmware** | Todo o acima; arquitetura de MCU único com símbolos em flash | Sem análise do firmware; um comando oculto não pode ser excluído | **Forte indício** — explicação mais provável |

## 13. Riscos técnicos e garantia

O manual brasileiro [G-R6] trata garantia nas pp. 110–113 (exclusões por reparos/alterações não aprovados) e orienta serviço
elétrico na concessionária (p. 129). Não foi confirmado como o termo entregue com a Hunter 2027. O CDC (art. 50) distingue
garantia contratual e legal [G-R15]. Nada abaixo é parecer jurídico.

| Categoria | Exemplo | Risco técnico | Implicação de garantia (sem certeza jurídica) |
|---|---|---|---|
| **A. Pesquisa documental** | FCC, ANATEL, manuais, fóruns | Nenhum para a moto | Nenhuma conhecida |
| **B. Comandos conhecidos** | Handshake, bússola, navegação documentada | Baixo e observado (T06/T09): PIN aceito, relógio voltou, app oficial funcionou. Residuais: tela presa até ciclar ignição, conflito de pareamento, distração | Não equivale a abrir o módulo ou gravar firmware; não há evidência de perda automática. Uso de app de terceiros pode ser alegado em disputa |
| **C. Comandos desconhecidos** | Testar `3x`, subcomandos de `40`, valores fora da faixa | **Agora concreto**: a família `02`/`04–0A` inicia e conduz regravação de flash [R9]; um valor "desconhecido" pode iniciar esse fluxo. Também: travamento, perda de pareamento, estado persistente | Dano por uso fora do especificado tende a ser contestável |
| **D. Atualização/substituição de firmware** | Qualquer uso de `02`/`04–0A`, pacote alterado | **Alto**: módulo inoperante sem recuperação conhecida (não há comando de leitura/backup identificado [R9]), perda de navegação e de atualizações oficiais | Alteração de software não aprovada é o tipo de intervenção que termos de garantia costumam excluir; possível questão legal sobre contornar proteções |
| **E. Modificação física** | Abrir o Pod, usar pads de depuração, mexer no chicote | **Alto**: vedação, conectores, eletrostática; chicote/ECU/ABS | Categoria mais exposta |

**Pergunta para a Royal Enfield Brasil (por escrito), se quiser certeza:**

> "Minha Hunter 350 (linha 2027, chassi informado em separado) tem o Tripper de navegação. (1) Qual é o modelo (JDCP ou
> J1AB), o part number e a versão de software do Tripper instalado? (2) O Tripper recebe atualizações de software pelo
> aplicativo oficial ou só na concessionária? Essas atualizações podem alterar ícones ou telas? (3) Existe interface ou SDK
> oficial para aplicativos de terceiros exibirem conteúdo no Tripper? (4) O uso de um aplicativo de terceiros que se conecta
> ao Tripper por Bluetooth com os mesmos comandos de navegação, sem abrir o equipamento nem alterar seu software, afeta a
> garantia do Tripper ou da motocicleta?"

## 14. O que poderia ser desenvolvido apenas no Android

Somente com comandos documentados e já em lista branca, sob o modelo de segurança do Playground. **Toda transmissão exige
nova autorização.**

| Ideia | Nível | Já existe? | Requisito antes de transmitir |
|---|---|---|---|
| Sequências de símbolos residentes, uma tela por passo de 2–10 s | 2 | Sim, **só em prévia** | Validar cada símbolo na Hunter, depois sequências curtas, parado |
| Bússola girando pelas 8 direções | 2 | Prévia | Menor risco: só o comando já observado. Validar as outras 6 direções primeiro |
| "Pulso" de aproximação: mesma seta com `[6]` = `00→50` | 2 | Não | Efeito visual confirmado só por terceiro [R12]; validar na sua Hunter antes |
| Números próprios nos campos de distância/rodapé (como Tripper-Plus) | 1 | Não | Mostra outra grandeza com unidade "Km/M": **engana o piloto**. Só parado e como demonstração |
| Prévia fiel no celular do que o Pod mostraria | — | Parcial | Nenhum |
| Leitura da versão do firmware pela resposta ao `03` | — | Não (o `03` já é enviado; a resposta não é decodificada) | Ver §18 E2 |

Cadência acima de 1 tela/2 s **não** é recomendada: nenhuma fonte mediu o limite do Pod.

## 15. O que exigiria firmware alternativo

Imagens livres, GIFs e vídeo no Pod exigiriam um firmware que receba e desenhe pixels:

1. **Firmware original legalmente obtido** — inexistente em público; os metadados de atualização estão restritos [R7].
2. **Hardware conhecido** — MCU (hipótese KW36A), memória U2, controlador e resolução do display: incompletos (§4).
3. **Via de gravação**: o procedimento de atualização pela característica de dados (aceitação de imagem não oficial e
   verificação no Pod **não determinadas**) ou depuração física pelos pads (exige abrir o módulo).
4. **Reimplementar tudo** (navegação, relógio, pareamento, watchdog) mais o novo canal, em 64 KB de SRAM se KW36A.
5. **Backup/recuperação**: nenhum comando de leitura de firmware foi identificado [R9]; um erro pode não ter volta.

**Não recomendado e não executado.** Não é afirmação de impossibilidade; é custo/risco desproporcional ao objetivo,
sobretudo numa moto nova e em garantia. Para gráficos livres, uma tela independente (FUTURE_ROUND_DISPLAY.md) entrega o
resultado sem nenhum desses riscos.

## 16. O que permanece desconhecido

- Modelo exato do Pod na Hunter BR 2027 (JDCP × J1AB) e versão de firmware instalada.
- Part number do MCU; capacidade e função da U2; controlador, resolução, cor e barramento do display.
- Formato e significado das respostas do Pod (exceto, por indício, a versão no `03`).
- Se o Pod aceita MTU/DLE maiores fora do procedimento de atualização.
- Se o Pod verifica assinatura nas atualizações, e se há proteção de leitura na depuração.
- Se alguma Hunter recebeu FOTA em campo e se alguma atualização mudou desenhos.
- O que fazem os ícones não testados (`23–26`, `29`, `2A`, `3B`, `40`, `43`, `45–FE`) e os campos `[8-10]`, `[15-17]`.
- A frase exata do desenvolvedor do Motolog e a base da alegação de análise do firmware.

## 17. Decisão técnica recomendada

1. **Encerrar a frente "imagens/GIF/vídeo no Pod original"** como linha de desenvolvimento. Reabrir só se surgir um gatilho:
   firmware oficial legalmente disponível com rotina de desenho, documentação/SDK oficial, ou demonstração reproduzível de
   terceiro com imagem própria no Pod original.
2. **Manter o Pod original** para navegação e para "efeitos" de Nível 2 com símbolos residentes, sob o modelo atual do
   Playground, após validação física autorizada símbolo a símbolo.
3. **Priorizar o display independente** para GIFs, imagens e vídeo, conforme [FUTURE_ROUND_DISPLAY.md](FUTURE_ROUND_DISPLAY.md).
4. **Endurecimento recomendado ao integrador** (não implementado nesta branch, que só contém documentação):
   - teste de regressão garantindo que nenhuma lista branca (`CommandWhitelist`, Playground, navegação) aceite primeiro byte
     `0x02` ou `0x04`–`0x0A`;
   - nota em `PROTOCOLO.md`: `PING_FW` é, por indício forte, consulta de versão; a família de atualização usa a característica
     de dados, então o bloqueio do serviço `01FF555x` não basta;
   - registrar como **referência externa** (não validação Hunter Lab) as fotos de terceiro para `0B–13` e para `[6]`.

**Respostas diretas:**

1. **Conseguimos fazer isso agora?** Imagens próprias, GIFs e vídeo no Pod original: **não**. Sequências de símbolos
   residentes: tecnicamente sim com comandos conhecidos, pendente de validação física autorizada.
2. **O que impede?** O firmware de fábrica só expõe seleção de símbolos e campos numéricos; o único canal em blocos é a
   regravação de firmware.
3. **Que evidência mudaria a conclusão?** Firmware oficial com rotina de desenho de dados externos; SDK/documentação da RE ou
   Visteon; demonstração reproduzível de imagem própria no Pod original.
4. **Há pesquisa de alto valor pendente?** Para gráficos, só a análise de firmware — bloqueada. Para o projeto: ler a versão
   do firmware (§18), checar relatórios já salvos e consultar a RE.
5. **Dá para avançar sem tocar na moto?** Na documentação e na preparação, sim. Para resolver a questão gráfica, não.
6. **Qual o bloqueio?** Firmware proprietário não publicado + única via de escrita em flash = intervenção de categoria D.
7. **Continuar no Pod ou display independente?** Pod original para navegação e efeitos residentes; **display independente
   para qualquer conteúdo gráfico próprio.**

## 18. Próximo experimento seguro

| ID | Experimento | Toca na moto? | Autorização | Valor |
|---|---|---|---|---|
| E0 | Conferir relatórios T02 (0.1.0) e 0.2.1 já guardados: serviços listados (`0x180A`? `01FF555x`?), tamanho/CRC das respostas | Não | Não precisa | Alto, risco zero |
| E1 | Abrir no navegador os posts do Motolog (`1uxy7jy`, `1v9wmtu`) e registrar a frase sobre o firmware | Não | Não precisa | Médio |
| E2 | No uso normal, filmar a inicialização do Pod e ler **"Versão do software"** (tela oficial do manual) | Só observar a ignição | Recomendada | **Alto**: identifica a versão sem Bluetooth; comparar com V07.05 |
| E3 | Abrir com seu login gov.br os anexos da homologação ANATEL 09047-21-09215 (podem ter fotos melhores) | Não | Sua decisão | Médio: talvez legibilidade do MCU |
| E4 | Sessão já autorizada no modelo atual, com captura de respostas **só após o PIN aceito**, registrando a resposta ao `03` | Sim | **Nova autorização explícita** | Médio: confirma a hipótese "versão no `03`"; nenhum comando novo |

Não proposto: qualquer comando fora da lista branca, MTU maior, leitura de serviços sensíveis, pads de depuração, OTA.
Nenhum desses experimentos foi executado nesta pesquisa.

## 19. Referências

Todas consultadas em **22/11/2026**, salvo indicação. Documentos regulatórios e manuais foram lidos em cópias públicas e
**não** foram copiados para o repositório. Achados de terceiros não reproduzidos por nós estão marcados no texto.

- **R1** [FCC NT8-JDCP — índice de anexos](https://fccid.io/NT8-JDCP) (block diagram, schematics e operational description marcados como confidenciais).
- **R2** [FCC NT8-J1AB](https://fccid.io/NT8-J1AB): relatório MDE_VIS_2017_FCC_03_Rev01 pp. 9–10; manual do Classic 350 (anexo 5335904, p. 139); [fotos internas](https://fccid.io/pdf.php?id=5335948); [etiqueta](https://fccid.io/pdf.php?id=5335949). Excluído por ser Dash: [NT8-REICGEN1](https://fccid.io/NT8-REICGEN1).
- **R3** [Manual HNTR 350, inglês (UE), p. 126](https://www.royalenfield.com/content/dam/open-pdf/royal-enfield-hntr-350-owners-manual-english.pdf).
- **R4** [ANATEL — dados abertos de produtos certificados](https://www.anatel.gov.br/dadosabertos/paineis_de_dados/certificacao_de_produtos/produtos_certificados.zip) (CSV de 22/11/2026; homologação 09047-21-09215; UL-BR 21.0859). Anexos no SCH exigem login gov.br e não foram acessados.
- **R5** [Manual japonês da Hunter 350, p. 126](https://www.royalenfield.com/node/assets/OwnersManual/Hunter350-OwnersManual_Japanese.pdf) (技適 R 201-210459).
- **R6** [Fotos internas JDCP](https://fccid.io/pdf.php?id=5227630) (mesmo arquivo do anexo 5227608; 2 páginas, JPEG 1240×930); [relatório 15B / JBP 5227635](https://fccid.io/NT8-JDCP/Test-Report/Test-Report-JBP-5227635) (13,5 V; USB–TTL; software NXP); foto externa 5227629 (etiqueta "RAM00261/B").
- **R7** [OpenTripper — POD_FIRMWARE_INVESTIGATION.md @ a2f9d12](https://github.com/spandan-kumar/OpenTripper/blob/a2f9d128fc9d6c6654c4c3d72605fc49ca435107/docs/POD_FIRMWARE_INVESTIGATION.md) (leitura parcial da marcação; candidato KW36A); [errata NXP máscara 1N41U](https://www.nxp.com/docs/en/errata/KINETIS_W_1N41U.pdf).
- **R8** [NXP MKW36A512 — datasheet](https://www.nxp.com/docs/en/data-sheet/MKW36A512.pdf) ([cópia Mouser](https://www.mouser.lt/pdfdocs/NXP_Kinetis_KW36A_KW35A_DS.pdf)): Cortex-M0+ 48 MHz, 64 KB SRAM, 512 KB flash, BLE 5.0. Comparativos: [K32W061](https://nxp.com/docs/en/data-sheet/K32W061.pdf) (640 KB/152 KB), [KW41Z](https://www.nxp.com/part/MKW41Z512VHT4) (até 512 KB/128 KB).
- **R9** [OpenTripper — POD_OTA_PROTOCOL.md @ a2f9d12](https://github.com/spandan-kumar/OpenTripper/blob/a2f9d128fc9d6c6654c4c3d72605fc49ca435107/docs/POD_OTA_PROTOCOL.md) e [POD_FIRMWARE_ACQUISITION_2026-11-02.md](https://github.com/spandan-kumar/OpenTripper/blob/a2f9d128fc9d6c6654c4c3d72605fc49ca435107/docs/POD_FIRMWARE_ACQUISITION_2026-11-02.md). Análise estática de terceiro do app oficial 10.1.23(45); **não reproduzida**; projeto sem licença, nada copiado.
- **R10** [OpenTripper — POD_UI_RESEARCH.md @ a2f9d12](https://github.com/spandan-kumar/OpenTripper/blob/a2f9d128fc9d6c6654c4c3d72605fc49ca435107/docs/POD_UI_RESEARCH.md) (construtor de navegação de campos fixos; alegação atribuída ao Motolog).
- **R11** [Manual Meteor 350 EUA, pp. 47–50](https://www.royalenfield.com/content/dam/open-pdf/royal-enfield-meteor-350-owners-manual-usa.pdf) e manual Hunter Brasil pp. 53–56 [G-R6]: sequência de inicialização e "fonte de seta exclusiva".
- **R12** [OpenTripper — HUNTER_RESULT_2026-11-02.md @ a2f9d12](https://github.com/spandan-kumar/OpenTripper/blob/a2f9d128fc9d6c6654c4c3d72605fc49ca435107/docs/HUNTER_RESULT_2026-11-02.md) (33 fotos numa Hunter; fotos não publicadas).
- **R13** [Tripper-Plus — README](https://github.com/RaunakEduc/Tripper-Plus) ("The Pod has no text and no font size…").
- **R14** [Manual Hunter 350 BS VI, inglês, 2026](https://www.royalenfield.com/content/dam/open-pdf/royal-enfield-owners-manual-hunter-350_bsvi-english-2026.pdf) ("Tripper Bluetooth version is 4.2", p. 47 impressa, lida pela frente de hardware).
- **R15** [Relatório FCC DTS 5227624, p. 9](https://fccid.io/NT8-JDCP/Test-Report/Test-Report-DTS-5227624) e JBP 5227635, p. 8 (texto FOTA verbatim).
- **R16** NXP OTAP: [serviço e características](https://mcuxpresso.nxp.com/mcuxsdk/25.09.00/html/middleware/wireless/bluetooth/doc/Bluetooth%20Low%20Energy%20Application%20Developers%20Guide/topics/otap_service_and_characteristics.html), [AN13860](https://www.nxp.com.cn/docs/en/application-note/AN13860.pdf), [BLE Application Developer's Guide Rev. 7, §11.4](https://community.nxp.com/pwmxy87654/attachments/pwmxy87654/wireless-connectivity/7638/1/Bluetooth%20Low%20Energy%20Application%20Developer's%20Guide.pdf).
- **R17** [Página oficial do Tripper Pod](https://www.royalenfield.com/in/en/motorcycles/tripper/), [FAQ do app RE](https://royalenfield.com/in/en/re-app), [App Store — notas de versão](https://apps.apple.com/in/app/royal-enfield-app/id1472963660).
- **R18** [Visteon — instrument clusters](https://www.visteon.com/products/instrument-clusters/).
- **R19** Relatados pela frente de ecossistema, **não reconferidos**: [tripper-bridge](https://github.com/tripper-bridge-dev/tripper-bridge), [opentripper (OSolerHugo)](https://github.com/OSolerHugo/opentripper), [Quicker-pod](https://github.com/praneth2580/Quicker-pod) (**não usar**), [Super TripperPod](https://supertripper.marzochi.com.br/).
- **R20** [Motolog — site](https://motolog.satrujit.in/), [Google Play](https://play.google.com/store/apps/details?id=in.satrujit.tripper_bridge); posts [1uxy7jy](https://www.reddit.com/r/RoyalEnfieldHunter350/comments/1uxy7jy/its_live_the_royal_enfield_tripper_companion_app/) e [1v9wmtu](https://www.reddit.com/r/RoyalEnfieldHunter350/comments/1v9wmtu/motolog_update_full_tripper_pod_display_control/) **inacessíveis** (Reddit bloqueou acesso automatizado; não contornado).
- **R21** [WazeTripper @ 7c4235d](https://github.com/jnsdias/wazetripper/tree/7c4235d5c4de5a1a60aa74ea6ece0c06e9ffab07) (Apache-2.0), clonado somente para leitura.

Inacessíveis nesta sessão (registrados, não contornados): fcc.report e apps.fcc.gov (403), device.report (403), páginas HTML
de anexos do fccid.io (403; os PDFs via `pdf.php` funcionaram), ANATEL SCH (login), MIC giteki (403), Reddit (bloqueio),
Team-BHP (403), Play Store (conteúdo dinâmico), busca de código do GitHub (exige autenticação).

---

*Nenhum experimento físico, transmissão BLE, download de firmware/APK, consulta a backend ou contato com terceiros foi
realizado. Conclusões sobre o Pod da sua Hunter dependem de evidência documental e de terceiros até que E2/E4 sejam feitos.*
