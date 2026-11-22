# Tripper Pod original: investigação gráfica passiva

Data de consulta: **21/11/2026**. Escopo: Pod pequeno da Hunter/Meteor; nenhuma conexão com motocicleta, transmissão BLE, instalação, consulta a interface de atualização ou alteração de aplicativo oficial foi realizada nesta investigação.

## 1. Resumo executivo

**Classificação para GIFs no Tripper original: sem evidência suficiente.** Com o protocolo de operação conhecido, imagens arbitrárias, GIFs e vídeo **não são funcionalidades disponíveis**. Não foi encontrado um caminho demonstrado puramente pelo aplicativo Android.

Existe uma pista relevante além das setas: o relatório regulatório do **JDCP** declara TFT de 1,49 polegada, navegação TBT e FOTA, inclusive eventuais mudanças de imagens. Isso apoia investigar recursos internos atualizáveis; **não comprova um comando de bitmap, decoder GIF, vídeo ou firmware alternativo utilizável**. Tampouco comprova que a unidade brasileira de 2027 tenha a mesma revisão. Ver fontes R3/R4.

A evidência favorece telas e símbolos residentes selecionados por comandos. A capacidade de alterar esses recursos por um mecanismo oficial de manutenção é distinta de desenhar pixels durante a operação. Firmware/hardware permanecem intocados.

## 2. O que foi confirmado

| Evidência | Alcance e confiança |
|---|---|
| Hunter Lab: conexão, handshake/PIN com confirmação visual, bússola Norte/noturna e Leste/diurna; recuperação posterior do relógio e funcionamento do app oficial | Relatos físicos anteriores documentados no projeto. Nenhuma repetição nesta entrega; não comprovam navegação, outros símbolos ou GIFs |
| `TripperCommands`, `NavigationFrame`, CRC e transporte existentes | Inspeção estática do código da main `a5fad26`; não houve alteração desses módulos |
| Catálogo do Playground: 81 entradas | Branch `feat/tripper-playground-031` em `306a3cf`: 71 códigos + oito opções de bússola + duas prévias especiais. Não são 81 símbolos vistos na Hunter |
| Visteon JDCP / FCC NT8-JDCP | Registro de 20/05/2021, com manual Royal Enfield e relatórios de navegação Bluetooth [R2] |
| JDCP com TFT 1,49 polegada e FOTA declarado | Relatórios de ensaio do fabricante/laboratório [R3/R4]. A descrição é fornecida pelo fabricante; ensaio de RF não demonstra execução de imagens |

Referências locais: [PROTOCOLO.md](PROTOCOLO.md), [TESTES_FISICOS.md](TESTES_FISICOS.md), [PRIMEIRO_HANDSHAKE.md](PRIMEIRO_HANDSHAKE.md). O catálogo detalhado está em [TRIPPER_PLAYGROUND.md na branch 0.3.1](https://github.com/sergiobfj/HunterLab/blob/306a3cfbeb3cd504cec3291433de5e4c4143910c/docs/TRIPPER_PLAYGROUND.md); esse arquivo ainda não pertence à main/base desta branch.

## 3. Evidências coletadas e revisão do protocolo

A referência WazeTripper cobre explicitamente o **Pod da Meteor**, não o Dash [R1]. Seu protocolo documenta quadros de controle e seleção de telas, não um transporte de mídia. O Hunter Lab mantém a distinção entre confiança externa e validação na Hunter.

| Pergunta | Resultado da inspeção estática |
|---|---|
| Quais comandos gráficos? | Família `10 11`: bússola, quadro de navegação com identificadores de ícones, distâncias, unidades, rodapé e dia/noite; builders adicionais de rota iniciada, recálculo e ocioso. Overlay de ligação na família `40 05`. Confiança varia; existência de builder não equivale a autorização nem validação física |
| Comando para bitmap? | Nenhum documentado na referência ou nos builders inspecionados |
| Transferência fragmentada? | Não foi identificado protocolo de upload gráfico com início/tamanho/offset/conclusão. CRC dos quadros não constitui upload fragmentado |
| Outros canais BLE? | O perfil conhecido usa Wireless UART e respostas por servidor GATT no telefone. O projeto reconhece UUIDs padrão e uma família provável OTAP sensível; reconhecimento não comprova que todos estejam presentes nesta Hunter nem que transportem imagens |
| Coordenadas de pixel? | Nenhum campo documentado com essa função. Distâncias e IDs de ícones não são coordenadas |
| Alterar símbolos armazenados? | Os comandos existentes **selecionam** símbolos/telas; não substituem seus desenhos. FOTA é uma pista separada, sem formato público verificado de recursos |
| Fontes/desenhos/imagens livres? | Não há API documentada. O manual JDCP descreve glifos de seta, sem API de fontes editáveis [R8] |
| Atualização de firmware? | FOTA declarada no JDCP [R3/R4]; guia público NXP identifica a família de serviço OTAP [R5]. Não foi verificado o procedimento efetivo do Pod do usuário, seus pacotes ou controles |
| O que é especulação? | MCU exato, memória, framebuffer, formato de sprites, decoder e acesso a firmware alternativo; também a ideia de que GIF funcionaria apenas aumentando MTU/cadência |

Os quadros conhecidos têm 20 bytes, com payload e CRC. **Isso não é um limite universal do Bluetooth para imagens**: seria necessário um protocolo receptor que remonte blocos, valide conteúdo e desenhe. Não foi encontrada essa camada de operação. A cadência próxima de dois segundos usada no projeto atende ao comportamento observado do watchdog; não demonstra teto de processamento ou banda do hardware e não foi alterada.

A NXP documenta Wireless UART com os mesmos UUIDs [R5]. Isso e o software NXP citado no ensaio são indícios de plataforma, **não uma identificação de chip Kinetis/KW específico**. Nenhuma operação OTAP foi executada.

## 4. Identificação do hardware e limites da FCC

O registro NT8-JDCP é coerente com a família do pequeno Tripper: descreve um módulo de navegação, inclui manual Royal Enfield, e o manual oficial da Himalayan **411** identifica o Tripper com esse FCC ID [R2/R9]. Isso estabelece uma relação com a família; **não vincula automaticamente a placa da Hunter 2027**.

O relatório FCC `MDE_VIS_2017_FCC_01`, páginas 9–10, informa TFT 1,49 polegada, BLE ensaiado a 1 Mbps, HW **PWB25068**, SW **V07.05** e software de ensaio NXP [R3]. O relatório japonês de 2021 identifica a mesma revisão e descreve FOTA [R4]. São amostras regulatórias, não leituras do veículo do usuário.

O documento de rotulagem tem título interno herdado de outro projeto, mas o corpo identifica JDCP e variantes J1D/J1C1/P4D/D2K/D3K/D2G2/D3G. Apresenta dimensões aproximadas de 7 × 5 cm e 70 g e explica a rotulagem reduzida [R7]. Esses números descrevem o documento de 2021, não uma medição da Hunter.

| Item solicitado | Conclusão defensável |
|---|---|
| Fabricante e geração | Visteon, família JDCP documentada em 2021; revisão 2027 desconhecida |
| MCU/controlador principal | **Não identificado** |
| RAM/Flash ou memória gráfica | **Desconhecidas**; nenhum tamanho foi inferido do nome de serviço |
| Chip Bluetooth | Fabricante/plataforma NXP é indício; part number não identificado |
| Controlador TFT, resolução e barramento | TFT 1,49 polegada declarado; controlador, resolução, SPI/paralelo e profundidade de cor não confirmados |
| Interfaces internas | Relatório lista conversor USB–TTL como equipamento auxiliar de ensaio; isso não comprova console, pinout de debug ou acesso do consumidor |
| Fotos internas | IDs 5227608 e 5227630 constam no acervo, porém os PDFs/visualizações não ficaram acessíveis por estas ferramentas; não foi possível ler marcações. **Não houve identificação visual de componentes** |
| Esquemático/bloco/operação | Metadados de confidencialidade no acervo; conteúdo não disponível para análise |

As tentativas públicas retornaram HTTP 403/erro de carregamento para os anexos de fotos. O texto de relatórios foi consultado em cópias públicas indexadas. Não foram contornados bloqueios. Nenhum PDF regulatório, fotografia proprietária ou dado pessoal dos requerentes foi copiado para o repositório.

Para confirmar a revisão, o próximo passo documental é obter do fabricante/concessionária o part number e a revisão aplicáveis à Hunter brasileira 2027, por escrito ou em documentação já fornecida, **sem desmontar nem consultar o dispositivo**. A hipótese JDCP continua pertinente, mas a correspondência exata permanece aberta.

## 5. Investigação do firmware

A declaração de FOTA inclui a expressão “SW updates or image changes if any” [R3]. Ela pode se referir a atualização de recursos incorporados ou à própria imagem de software; sem pacote/formato não se pode escolher uma interpretação. Não é evidência de um framebuffer remoto acessível durante o uso.

Não foi localizado, nas fontes e repositórios inspecionados, firmware do Pod com autorização clara para download/análise, código de renderização, tabela de sprites extraída, SDK de desenho ou bootloader alternativo demonstrado. Portanto **não houve análise binária de firmware**. Não se afirma que tais materiais não existam em qualquer lugar.

Também não foram acessados endpoints privados do fabricante, pacotes de atualização de aplicativo, interfaces de manutenção ou mecanismos de extração. A ausência de firmware impede verificar assinatura de atualizações, compressão, armazenamento de glifos, framebuffer e decodificadores. Não se propõe contornar essas proteções.

O autor de WazeTripper relata que o aplicativo seleciona imagens residentes e que não obteve firmware para estudar um minimapa [R10]. É um relato de quem implementou interoperabilidade, útil como indício, sem provar todos os caminhos internos do firmware.

## 6. Capacidade gráfica conhecida e diferença para o Dash

| Produto | Evidência disponível | Consequência |
|---|---|---|
| Tripper Pod pequeno, Hunter/Meteor | Navegação TBT, relógio, símbolos e modos; protocolo BLE de quadros sem upload gráfico conhecido | Explorar símbolos existentes não equivale a enviar imagem livre |
| Tripper Dash, Himalayan 450/Guerrilla 450 | Fabricante apresenta TFT circular de 4 polegadas e mapa completo [R11] | Outra arquitetura; não comprova capacidade do Pod |
| OpenDash, implementação para Dash | Código público de encoder e packetizer H.264/RTP, streaming por Wi-Fi; recursos de wallpaper no projeto [R12] | Fragmentação de vídeo do Dash não é parte do protocolo BLE do Pod |

Um TFT pode desenhar pixels controlados pelo firmware, mesmo quando o protocolo externo só escolhe telas. Isso é diferente de demonstrar RAM suficiente para framebuffer duplo, decoder GIF ou vídeo. Como referência matemática **hipotética**, uma imagem de 240 × 240 em RGB565 ocupa 115.200 bytes; não se conhece essa resolução nem essa memória no Pod. GIF precisa de decodificação e estado entre quadros; vídeo adiciona requisitos diferentes. Nenhuma taxa de quadros útil foi medida.

## 7. Hipóteses restantes e experiências de terceiros

| Fonte / data | O que foi inspecionado | Confiabilidade e limite |
|---|---|---|
| WazeTripper, revisão `7c4235d`, consulta 21/11/2026; última atualização informada 21/09/2026 | Protocolo e fonte Java de construção de quadros; sem upload bitmap identificado [R1] | Alta para o código observado; confirmação física declarada na Meteor, não todos os ícones da Hunter |
| Autor WazeTripper, postagem e discussão de 2026, consulta 21/11/2026 [R10] | Relato de símbolos residentes e hipótese de firmware para minimapa | Relato primário; hipótese não demonstrada. Data relativa exibida pela plataforma, sem fixar dia não verificado |
| Motolog, publicação de 2026, consulta 21/11/2026 [R13] | Autor relata navegação funcionando em sua Hunter por Bluetooth | Evidência de interoperabilidade declarada; nenhum binário, firmware ou demonstração de imagens foi analisado |
| `Ronin86-antony/tripper-pod`, revisão `62003c3`, consulta 21/11/2026 | Árvore HTML/JS e `ble.js`; interface aceita UUIDs configuráveis | Código examinável, sem evidência encontrada de upload gráfico no Pod. Sem licença declarada; nenhum código aproveitado |
| OpenDash, revisão `a33d830`, consulta 21/11/2026 [R12] | Fonte `RtpPacketizer.kt`, protocolo e README | Evidência de implementação para **Dash**, excluída como prova sobre Pod |
| Vídeo de apresentação do TFT Dash [R14], consulta 21/11/2026 | Descrição pública do tutorial de conexão/mapas | Equipamento diferente; vídeo não foi reproduzido nem usado como prova de GIF no Pod |

Nenhuma demonstração verificável de imagem arbitrária, GIF/vídeo ou firmware alternativo no **Pod pequeno** foi encontrada neste recorte. Fórum, aparência de uma tela em vídeo e título de repositório não bastam: é necessário identificar equipamento, firmware e mecanismo reproduzível. Nenhum projeto de terceiro foi executado ou incorporado.

## 8. Matriz de cenários e riscos

| Cenário | Evidências / obstáculos | Viabilidade atual | Intervenção e riscos | Próximo passo seguro |
|---|---|---|---|---|
| A. Imagens por comando ainda desconhecido | Não há API de bitmap documentada; FOTA não demonstra comando de operação | Não demonstrada; sem caminho implementável conhecido | Talvez software, mas descobrir por tentativas desconhecidas arriscaria travamento/estado persistente; garantia incerta | Documentação pública adicional ou esclarecimento oficial; **sem fuzzing/envio** |
| B. Símbolos e telas predefinidos | Campos de ID e builders, relatos anteriores e fonte externa | Melhor explicação para o comportamento conhecido | Interoperabilidade autorizada exige controles existentes; callbacks de escrita não provam exibição | Reanalisar os registros já disponíveis, sem novo teste físico nesta etapa |
| C. Hardware gráfico, firmware diferente necessário | TFT e FOTA declarados; desconhecidos CPU/memória/formato/assinatura | Plausível como arquitetura, não comprovado como solução | Poderia exigir firmware e até acesso físico; risco de brick, perda de recursos e disputa de garantia. **Bloqueado nesta etapa** | Especificação/autorização por escrito e firmware legalmente disponibilizado antes de considerar análise futura |
| D. Recursos insuficientes para animação útil | Não há especificações de memória/renderer nem throughput útil medido | Indeterminada; não concluir inviabilidade pelo pacote de 20 bytes | Aumentar cadência não é justificado e pode prejudicar estabilidade | Identificar componentes em material público legível e obter datasheets, sem abrir o Pod |

Falhas de comandos desconhecidos podem incluir estado visual incorreto, perda de conexão, corrupção persistente caso atinjam manutenção e módulo inutilizável caso alterem firmware. Não há evidência aqui de que os quadros documentados tenham causado dano físico; isso também não autoriza declarar ausência de risco.

## 9. Segurança e garantia no Brasil

Foi consultado o manual brasileiro disponibilizado no domínio oficial, edição com copyright 2023 [R6]. As páginas impressas **110–113** tratam de garantia, incluindo danos por reparos/alterações não aprovados, peças e acessórios. A página **129** orienta serviço elétrico na concessionária. Esse documento é regionalmente pertinente, mas **não foi confirmado como o termo contratual entregue com a Hunter 2027**.

Tecnicamente, um aplicativo que usa o fluxo BLE existente não equivale a abrir o módulo, alterar chicote ou gravar firmware. Juridicamente, essa distinção não permite concluir automaticamente que a garantia esteja preservada ou perdida. O CDC, art. 50, distingue garantia contratual e legal [R15]; a aplicação ao caso depende dos fatos, do termo entregue e de eventual avaliação jurídica.

Antes de qualquer futura modificação de firmware/hardware, consultar a Royal Enfield **por escrito**, identificando modelo/revisão e procedimento pretendido. Esta entrega não enviou mensagens a terceiros. Não solicitar desmontagem ao usuário, não tocar ECU/ABS/chicote, não instalar firmware e não executar comandos desconhecidos. Avisos FCC sobre autorização de rádio também não substituem o contrato brasileiro de garantia.

## 10. Referências e rastreabilidade

Todas consultadas em 21/11/2026. Fontes regulatórias são documentos do requerente/laboratório servidos por espelhos públicos; índices não substituem conteúdo. Os links de fotos ficam registrados como **não acessíveis para inspeção visual nesta sessão**.

- **R1** [WazeTripper / protocolo](https://github.com/jnsdias/wazetripper/blob/7c4235d5c4de5a1a60aa74ea6ece0c06e9ffab07/docs/PROTOCOL.md), [builder Java](https://github.com/jnsdias/wazetripper/blob/7c4235d5c4de5a1a60aa74ea6ece0c06e9ffab07/src/com/waze/wazetripper/TripperProtocol.java). Código Apache-2.0; referência, sem cópia nesta entrega.
- **R2** [FCC NT8-JDCP](https://fccid.io/NT8-JDCP), [manual Royal Enfield anexado](https://fccid.io/NT8-JDCP/User-Manual/Royal-Enfield-JDCP-User-Manual-5227611), [fotos 5227608](https://fccid.io/NT8-JDCP/Internal-Photos/Internal-Photos-5227608), [fotos 5227630](https://fccid.io/NT8-JDCP/Internal-Photos/Internal-Photos-5227630).
- **R3** [Relatório FCC 5227624, pp. 9–10](https://fcc.report/FCC-ID/nt8jdcp/5227624.pdf), [cópia textual pública do mesmo relatório](https://device.report/m/4de3ddfa24fb9167d59b8aee6a28d35e914d682f3ab114be993a6cc1fdf81906).
- **R4** [Relatório japonês MDE_VIS_2017_JRL_01, pp. 8–10](https://device.report/m/ee9a0a626f770c8cd89b0f63ac635e6a4820cdb0d51531bb947e4b050b2a34df), produzido por 7layers para Visteon, data do relatório 09/07/2021; não é dump de firmware.
- **R5** NXP: [Wireless UART](https://mcuxpresso.nxp.com/mcuxsdk/latest/html/middleware/wireless/bluetooth/doc/Bluetooth%20Low%20Energy%20Demo%20Applications%20Users%20Guide/topics/wireless_uart.html), [identificação pública do serviço OTAP](https://mcuxpresso.nxp.com/mcuxsdk/25.09.00/html/middleware/wireless/bluetooth/doc/Bluetooth%20Low%20Energy%20Application%20Developers%20Guide/topics/otap_service_and_characteristics.html). Consulta documental, sem exploração de interface.
- **R6** [Página oficial de manuais Brasil](https://www.royalenfield.com/br/pt/support/owners-manual/), [Hunter 350, PDF brasileiro](https://www.royalenfield.com/content/dam/royal-enfield/hunter-350/owners-manual/new/royal-enfield-hunter-350-owners-manual.pdf). PDF obtido e lido localmente; não redistribuído.
- **R7** [Documento Visteon de rotulagem JDCP, 20/04/2021](https://device.report/m/bdf922ff7b21cf61cdb972d90162f3502322146b27c7d27d5d542d1b625b98e0).
- **R8** [Manual Visteon/RE reproduzido publicamente](https://device.report/manual/2112519). Índice agrega J1AB e JDCP; usado apenas para a descrição do produto, sem atribuir identidade de placa.
- **R9** [Manual Royal Enfield Himalayan 411 USA, p. 141](https://www.aspowersports.com/wp-content/uploads/2025/04/royal-enfield-himalayan-411-usa-owners-manual-1.pdf). Manual do fabricante em hospedagem de concessionária; relação de família, não identificação da Hunter.
- **R10** [Discussão primária do autor WazeTripper](https://www.reddit.com/r/motoca/comments/1wm0i1d/waze_integrado_ao_tripper_pod_da_royal_enfield/), [publicação em inglês](https://www.reddit.com/r/royalenfield/comments/1wlzjp3/waze_integration_with_tripper_pod/).
- **R11** [Tripper Dash: fabricante](https://www.royalenfield.com/in/en/tripper-dash/).
- **R12** [OpenDash](https://github.com/cotton1234/tripper/tree/a33d83085d3585d20473823ed5d5c88f74cd0d83), [packetizer implementado](https://github.com/cotton1234/tripper/blob/a33d83085d3585d20473823ed5d5c88f74cd0d83/app/src/main/java/com/example/opendash/dash/video/RtpPacketizer.kt). Apache-2.0; não copiado.
- **R13** [Relato do autor Motolog](https://www.reddit.com/r/RoyalEnfieldHunter350/comments/1udwy15/build_a_app_to_connect_to_tripper_pod_and_share/), [outro repositório Pod inspecionado](https://github.com/Ronin86-antony/tripper-pod/tree/62003c3cd2e6840473b88db76c94f60da54e7109).
- **R14** [Tutorial publicado do Tripper TFT Dash](https://www.youtube.com/watch?v=EL6pYSM8jro). Descrição pública consultada; não prova sobre Pod.
- **R15** [CDC, texto oficial compilado, art. 50](https://planalto.gov.br/ccivil_03/leis/l8078compilado.htm).

## 11. Conclusão técnica

**Não existe, nas evidências encontradas, solução demonstrada para colocar GIFs, imagens próprias ou vídeos no Tripper Pod original pelo protocolo de operação conhecido.** A afirmação absoluta de impossibilidade de hardware também não seria sustentada. A pista FOTA torna atualização de recursos internos uma questão documental concreta, sem demonstrar capacidade de mídia livre e sem autorizar intervenção.

O próximo avanço depende de documentação pública mais completa, fotos legíveis e identificação exata da revisão, ou de material de firmware disponibilizado legalmente. Se exigir gravação de firmware, extração física ou abertura, existe um **bloqueio de escopo e segurança**: não executar.

## 12. Próxima experiência segura proposta

Somente trabalho offline: correlacionar os comandos já documentados com as capturas **já existentes**, sem publicar PIN/MAC/logs privados; conferir se qualquer campo foi indevidamente interpretado como coordenada. Em paralelo documental, buscar uma cópia pública legível dos anexos FCC e a confirmação oficial do part number/revisão 2027. Analisar estaticamente firmware apenas se houver autorização e procedência legal verificáveis, sem instruções de instalação.

Não há ensaio novo na moto proposto ou autorizado por este relatório. Uma eventual futura experiência ativa exige autorização posterior específica e revisão de risco; GIFs permanecem fora das funcionalidades do Playground.
