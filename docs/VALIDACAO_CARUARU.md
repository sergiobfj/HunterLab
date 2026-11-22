# Validação em casa — Caruaru, 22/11/2026

## Resultado executivo

- **GraphHopper real: INCONCLUSIVA.** A requisição com chave intencionalmente inválida retornou HTTP 401; não foi localizada chave válida nas variáveis/configurações específicas verificadas. Sem aparelho ADB para consultar a preferência do app. Nenhuma chave foi inventada, contratada, exibida ou publicada.
- **OSM consultado de verdade:** 37 nós no recorte urbano; 44 IDs únicos na união dos recortes regionais. São registros no mapa, não confirmação de existência, operação ou cobertura física completa.
- **Alertas:** trajetos sintéticos e fixtures do core, com dados públicos capturados nesta consulta. Não são passagens reais por equipamentos.
- **Android:** dez prévias Compose/Layoutlib, incluindo as quatro áreas, Playground, estados vazios e fonte a 150%. Nenhum Samsung disponível por ADB e nenhum emulador/AVD instalado; não houve instalação ou execução Android em dispositivo.
- **BLE:** guarda final dos dois caminhos Android, CRC e lista branca; bússola limitada aos 16 quadros canônicos, sem alteração dos builders ou de `03`. Integrada por merge normal `6c240c8`; CI [aprovado](https://github.com/sergiobfj/HunterLab/actions/runs/45033118648). Nenhuma conexão ou teste físico.

## Método e consultas reais

Cliente desktop, HTTPS, 22/11/2026 aproximadamente 11:25–11:32 BRT (14:25–14:32 UTC).
Endpoint: `https://overpass-api.de/api/interpreter`. Poucas consultas manuais sequenciais; sem coleta contínua, paralelismo ou teste de limites.
A [política pública do Overpass](https://dev.overpass-api.de/overpass-doc/en/preface/commons.html) exige uso moderado; esta consulta não equivale a infraestrutura sustentável para grande distribuição do app.

Filtros iguais aos do `OverpassQuery`: nós `highway=speed_camera` e relações `type=enforcement`, com
`enforcement` em `maxspeed`, `average_speed` ou `traffic_signals`, inclusive listas separadas por `;`.
Saída `out body; >; out skel qt;` para resolver membros. Nenhuma relação compatível veio nas três respostas.

| Recorte | bbox (sul, oeste, norte, leste) | Resposta | Nós de radar |
|---|---|---|---:|
| [Urbano](validation/caruaru/urban.overpassql) | -8.34, -36.06, -8.20, -35.90 | GET 200 | 37 |
| [BR-104 e entorno](validation/caruaru/br104.overpassql) | -8.50, -36.10, -8.05, -35.90 | GET 200 | 38 |
| [BR-232 e entorno](validation/caruaru/br232.overpassql) | -8.40, -36.20, -8.20, -35.78 | GET 502 duas vezes; POST da mesma consulta 200 | 43 |
| [Geometria BR-104/BR-232](validation/caruaru/roads.overpassql) | -8.50, -36.20, -8.05, -35.78 | GET 200 | 176 ways de referência |

O recorte urbano envolve as coordenadas públicas dos pontos da AMC. Os recortes alongados incluem as aproximações das rodovias ao norte/sul e leste/oeste da cidade.
São caixas de pesquisa, **não o limite administrativo municipal**: os 44 IDs regionais não são declarados todos dentro de Caruaru.
As 118 aparições nas três respostas correspondem a 44 IDs; 74 repetições entre consultas foram removidas por ID, sem apagar nós distintos.
O erro 502 não foi tratado como lista vazia. O app usa GET e sua recuperação desta falha só foi testada com fixtures; o POST foi uma consulta de pesquisa no desktop, não uma mudança no app.

Respostas, timestamps da base OSM, hashes SHA-256, métodos e licença estão nos
[snapshots de teste](../core/src/test/resources/validation/caruaru/) e em [provenance.json](../core/src/test/resources/validation/caruaru/provenance.json).
Dados de © colaboradores do OpenStreetMap, sob [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/).
Não incluem perfis de colaboradores, localização do telefone ou endereços pessoais do usuário.

## Inventário retornado

Todos os 44 são nós `highway=speed_camera` interpretados como FIXED pelo parser atual.
Somente dois têm `direction=200`. Há 42 sem sentido definido e dez sem `maxspeed`.
O sentido desconhecido permanece desconhecido: o app pode alertar nos dois sentidos, com essa limitação indicada.
Não foi identificado nenhum registro `average_speed` ou `traffic_signals` pelas relações consultadas.

A coluna de proximidade usa distância geométrica aproximada ao traçado OSM: 17 nós a até 5 m da BR-232, cinco da BR-104.
Isso não comprova pista fiscalizada, posição física do poste ou titularidade. Nós em ruas sob viadutos podem ficar perto de uma rodovia sem pertencer a ela; o inventário não os promove por uma tolerância larga.

| ID OSM (node) | Latitude | Longitude | maxspeed | direction | Traçado a até 5 m |
|---|---:|---:|---:|---:|---|
| [10124884242](https://www.openstreetmap.org/node/10124884242) | -8.3024444 | -36.0030101 | 60 | — | BR-232 |
| [10147570982](https://www.openstreetmap.org/node/10147570982) | -8.2954915 | -35.9874910 | 40 | — | BR-104 |
| [12618245077](https://www.openstreetmap.org/node/12618245077) | -8.2924830 | -35.9552894 | 50 | — | — |
| [12618245078](https://www.openstreetmap.org/node/12618245078) | -8.2923966 | -35.9552650 | 50 | — | — |
| [12791592886](https://www.openstreetmap.org/node/12791592886) | -8.2842031 | -35.9682525 | 40 | 200 | — |
| [12791592887](https://www.openstreetmap.org/node/12791592887) | -8.2852951 | -35.9683468 | 40 | 200 | — |
| [13702580919](https://www.openstreetmap.org/node/13702580919) | -8.2881871 | -35.9837913 | 40 | — | — |
| [13702766757](https://www.openstreetmap.org/node/13702766757) | -8.2719337 | -35.9748778 | 50 | — | — |
| [13702767947](https://www.openstreetmap.org/node/13702767947) | -8.2766340 | -35.9730418 | 50 | — | — |
| [13702772771](https://www.openstreetmap.org/node/13702772771) | -8.2698206 | -35.9659334 | 40 | — | — |
| [13702779449](https://www.openstreetmap.org/node/13702779449) | -8.2871252 | -35.9726669 | 40 | — | — |
| [13702785887](https://www.openstreetmap.org/node/13702785887) | -8.3106573 | -36.0320056 | 100 | — | BR-232 |
| [13702790891](https://www.openstreetmap.org/node/13702790891) | -8.2759101 | -35.9822952 | 40 | — | — |
| [13702793212](https://www.openstreetmap.org/node/13702793212) | -8.2672281 | -35.9782501 | 40 | — | — |
| [13702799277](https://www.openstreetmap.org/node/13702799277) | -8.3279966 | -36.1505769 | — | — | BR-232 |
| [13702800046](https://www.openstreetmap.org/node/13702800046) | -8.2717868 | -35.9751230 | 50 | — | — |
| [13702800429](https://www.openstreetmap.org/node/13702800429) | -8.2734476 | -35.9640160 | 40 | — | — |
| [13702810102](https://www.openstreetmap.org/node/13702810102) | -8.3305570 | -36.1244612 | — | — | BR-232 |
| [13702821954](https://www.openstreetmap.org/node/13702821954) | -8.1674369 | -35.9778464 | — | — | BR-104 |
| [13704372180](https://www.openstreetmap.org/node/13704372180) | -8.2928048 | -35.9532177 | 50 | — | — |
| [13704407046](https://www.openstreetmap.org/node/13704407046) | -8.2759781 | -35.9814003 | 40 | — | — |
| [13704409531](https://www.openstreetmap.org/node/13704409531) | -8.2552323 | -35.9645196 | 60 | — | — |
| [13704412914](https://www.openstreetmap.org/node/13704412914) | -8.2865630 | -35.9943319 | 40 | — | — |
| [13704431808](https://www.openstreetmap.org/node/13704431808) | -8.2547239 | -35.9664242 | 60 | — | — |
| [13705844549](https://www.openstreetmap.org/node/13705844549) | -8.2910621 | -36.0120914 | 50 | — | — |
| [13705935102](https://www.openstreetmap.org/node/13705935102) | -8.2639431 | -36.0047337 | 40 | — | — |
| [13705968423](https://www.openstreetmap.org/node/13705968423) | -8.2667646 | -35.9992700 | 40 | — | — |
| [13705985365](https://www.openstreetmap.org/node/13705985365) | -8.2744535 | -35.9915875 | 50 | — | — |
| [13733699033](https://www.openstreetmap.org/node/13733699033) | -8.3003907 | -35.8491727 | 60 | — | BR-232 |
| [13733699034](https://www.openstreetmap.org/node/13733699034) | -8.3002623 | -35.8490908 | 60 | — | BR-232 |
| [13969346281](https://www.openstreetmap.org/node/13969346281) | -8.3027829 | -35.9773544 | 60 | — | BR-232 |
| [13969346283](https://www.openstreetmap.org/node/13969346283) | -8.3027961 | -35.9778093 | 60 | — | BR-232 |
| [13993951485](https://www.openstreetmap.org/node/13993951485) | -8.3066661 | -35.9617661 | 60 | — | BR-232 |
| [13998758440](https://www.openstreetmap.org/node/13998758440) | -8.2338211 | -35.9770055 | 110 | — | BR-104 |
| [14013937396](https://www.openstreetmap.org/node/14013937396) | -8.3066560 | -35.9608480 | 60 | — | BR-232 |
| [14053395543](https://www.openstreetmap.org/node/14053395543) | -8.2416170 | -35.9753398 | 60 | — | BR-104 |
| [14053402357](https://www.openstreetmap.org/node/14053402357) | -8.2434130 | -35.9753803 | 60 | — | BR-104 |
| [14170005331](https://www.openstreetmap.org/node/14170005331) | -8.3026462 | -36.0030982 | — | — | BR-232 |
| [14170008699](https://www.openstreetmap.org/node/14170008699) | -8.3066141 | -35.9620697 | — | — | BR-232 |
| [14170009078](https://www.openstreetmap.org/node/14170009078) | -8.3305980 | -36.1245571 | — | — | BR-232 |
| [14170046745](https://www.openstreetmap.org/node/14170046745) | -8.3003108 | -35.8488322 | — | — | BR-232 |
| [14170047814](https://www.openstreetmap.org/node/14170047814) | -8.3067687 | -35.9603064 | — | — | BR-232 |
| [14170047815](https://www.openstreetmap.org/node/14170047815) | -8.3181161 | -36.0582776 | — | — | BR-232 |
| [14170051096](https://www.openstreetmap.org/node/14170051096) | -8.3105965 | -36.0318038 | — | — | BR-232 |

Não há IDs duplicados nem coordenadas exatamente iguais depois da união. Pares a menos de 30 m, **candidatos a revisão**, sem concluir que sejam duplicatas:

- `10124884242` / `14170005331`: 24.4 m.
- `12618245077` / `12618245078`: 10.0 m.
- `13702785887` / `14170051096`: 23.2 m.
- `13702810102` / `14170009078`: 11.5 m.
- `13733699033` / `13733699034`: 16.9 m.
- `13733699034` / `14170046745`: 29.0 m.

Podem ser pistas/sentidos distintos. Deduplicação do app é por ID; nós diferentes próximos não são fundidos automaticamente.

## Comparação com fontes oficiais

O [quadro da AMC](https://amc.caruaru.pe.gov.br/site/PDF/VELOCIDADE%20REGULAMENTADA%20DOS%20PONTOS%20DE%20FISCALIZA%C3%87%C3%83O%20ELETR%C3%94NICA%20-%20AMC.pdf)
lista 21 equipamentos em 19 coordenadas distintas, com datas de aferição em 2025. Não certifica operação em novembro de 2026.
Comparação geométrica, tolerância 75 m: 16 linhas/14 locais têm nó próximo; cinco ficam fora da tolerância.
Não há correspondência por série, nem dedução de ausência física a partir das cinco diferenças.

| AMC | Latitude | Longitude | km/h | Nó OSM mais próximo | Distância aproximada (m) |
|---|---:|---:|---:|---:|---:|
| CARUA8739 | -8.29256 | -35.9549 | 50 | 12618245077 | 43.7 |
| CARUA8740 | -8.29458 | -35.9561 | 50 | 12618245077 | 249.7 |
| CARUA8741 | -8.28669 | -35.9930 | 40 | 13704412914 | 147.2 |
| CARUA8742 | -8.28441 | -35.9682 | 40 | 12791592886 | 23.7 |
| CARUA8743 | -8.28550 | -35.9683 | 40 | 12791592887 | 23.4 |
| CARUA8744 | -8.27186 | -35.9750 | 50 | 13702766757 | 15.7 |
| CARUA8745 | -8.27186 | -35.9750 | 50 | 13702766757 | 15.7 |
| CARUA8746 | -8.28708 | -35.9725 | 40 | 13702779449 | 19.0 |
| CARUA8748 | -8.26720 | -35.9785 | 40 | 13702793212 | 27.7 |
| CARUA8749 | -8.27580 | -35.9817 | 40 | 13704407046 | 38.5 |
| CARUA8750 | -8.27590 | -35.9821 | 40 | 13702790891 | 21.5 |
| CARUA8751 | -8.28822 | -35.9840 | 40 | 13702580919 | 23.3 |
| CARUA8752 | -8.28636 | -35.9725 | 50 | 13702779449 | 87.0 |
| CARUA8753 | -8.29256 | -35.9549 | 50 | 12618245077 | 43.7 |
| CARUA8754 | -8.28244 | -35.9715 | 50 | 12791592886 | 407.6 |
| CARUA8755 | -8.27446 | -35.9913 | 50 | 13705985365 | 31.6 |
| CARUA8756 | -8.26988 | -35.9660 | 40 | 13702772771 | 9.9 |
| CARUA8757 | -8.26680 | -35.9991 | 40 | 13705968423 | 19.1 |
| CARUA8758 | -8.26410 | -36.0045 | 40 | 13705935102 | 31.1 |
| CARUA8759 | -8.27330 | -35.9638 | 40 | 13702800429 | 28.9 |
| CARU8747 | -8.29036 | -36.0123 | 50 | 13705844549 | 81.4 |

Os pontos sem correspondência na tolerância são CARUA8740, CARUA8741, CARUA8752, CARUA8754 e CARU8747.
Diferenças podem decorrer de posição imprecisa, cadastro incompleto ou associação incorreta; não foram corrigidas por adivinhação.

Para rodovias, o [DER-PE registra o instrumento R15407 na BR-232 km 126+720, sentido Recife](https://www.der.pe.gov.br/images/transito/equipamentos-de-fiscalizacao-eletronica/levantamento-tecnico/2023/DER-PE_1697.pdf).
O documento declara verificação em 02/07/2025 e validade até 01/07/2026, já passada nesta consulta. Não informa coordenada utilizável para uma correspondência inequívoca com ID OSM, e não prova situação atual.
O [inventário DNIT de junho de 2015](https://www.gov.br/dnit/pt-br/rodovias/operacoes-rodoviarias/controle-de-velocidade/PlanilhaSiteJunho.pdf)
registra BR-104 km 39,100; sua antiguidade impede usá-lo como censo operacional atual.
Não foi obtida uma lista oficial atual, completa e georreferenciada das duas rodovias nesses recortes.
A jurisdição deve ser verificada por trecho; não foi presumida só pelo prefixo BR.

## GraphHopper e destinos

Especificação [OpenAPI oficial](https://docs.graphhopper.com/openapi) consultada, arquivo público
`https://docs.graphhopper.com/_spec/openapi.yaml`, SHA-256
`fa5b5ed8635a89d6169e72718316bbb1a56295289b5a2fb0303784da2bcb3ce0`.
Confere com a implementação: `GET https://graphhopper.com/api/1/route`, dois parâmetros `point` em latitude/longitude,
`profile=car`, `locale=pt_BR`, `instructions=true`, `calc_points=true`, `points_encoded=true`; autenticação por `key`.
O rumo inicial flexível continua desligado por padrão, sem ativar consumo adicional de créditos nesta rodada.

Pontos públicos de teste, provenientes da tabela AMC: **(-8.292560, -35.954900)** → **(-8.286360, -35.972500)**,
em vias urbanas. Não foi consultada localização atual do usuário.

| Caso | Tipo de evidência | Resultado |
|---|---|---|
| Chave inválida | Requisição HTTPS real, chave de teste deliberadamente inválida | HTTP 401, resposta capturada; parser/provider reconhecem INVALID_KEY |
| Chave válida e rota GraphHopper em Caruaru | Chave legítima não localizada | INCONCLUSIVA; distância, ETA e manobras reais GraphHopper pendentes |
| Coordenadas e `geo:` simples/com `q=` | JVM, coordenadas públicas acima | Entrada reconhecida, pedido usa `car` e ordem correta |
| Endereço textual | App usa `Geocoder` do Android, não a Geocoding API GraphHopper | Pendente de aparelho; parser offline recusa endereço como esperado |
| Distância, tempo, manobras e recálculo | Fixtures GraphHopper/NavigationEngine da suíte | Aprovados offline; não são resposta real de rota Caruaru |
| Falta de internet e timeout | HttpGet falso + fixtures | Falhas distintas e seguras; rede do computador não foi desligada |
| HTTP 429 | Fixture de erro | Quota reconhecida; não provocamos rate limit no serviço |
| Resposta malformada | Fixture | INVALID_RESPONSE; não atribuída à API real |

Controle independente: requisição real ao [servidor OSRM/FOSSGIS](https://routing.openstreetmap.de/), perfil de carro,
mesmos pontos públicos; retorno **2.524,1 m, 176,1 s**, nove etapas incluindo partida/chegada.
Não há resultado válido GraphHopper para comparar quantitativamente. O resultado OSRM não foi exibido como GraphHopper nem integrado como provedor alternativo.
As rotas são de carro, **não otimizadas ou validadas para motocicleta**; estimativas não incluem comprovação de trânsito ou legalidade para motos.

Para desbloquear a validação real: seguir o [cadastro oficial](https://support.graphhopper.com/support/solutions/articles/44001976025-sign-up-for-graphhopper),
confirmar o e-mail, criar sua chave no [dashboard](https://graphhopper.com/dashboard/), manter o plano gratuito e conferir a quota.
No app: **Configurações → Rotas → Chave da API de rotas → Salvar chave**. Não enviar chave em chat, relatório, Git ou screenshot.
Repetir apenas a rota pública curta e seu recálculo simulado; não é preciso moto nem teste de Tripper.
Nenhum serviço pago ou conta foi contratado por esta validação.

## Alertas e cache

O teste `CaruaruSnapshotValidationTest` usa respostas reais como dados de entrada e **trajetos/posições sintéticos**:

- Um nó com sentido 200°: uma detecção a aproximadamente 700 m e um alerta a aproximadamente 300 m; sem repetição após passar.
- Sentido contrário, linha paralela a 100 m e saída da rota antes do radar: sem correspondência. Nó repetido pelo mesmo ID: uma correspondência.
- Nó sem direção: permanece não confirmado também na rota inversa; o teste não inventa um sentido.
- Cache serializado preserva os dados; a política do app é testada com **14 dias**, expiração e falha de rede preservando o dado antigo como desatualizado; intervalo mínimo de uma hora é respeitado.
- Nenhum radar / cobertura ausente: nenhum aviso inventado.

As regressões existentes complementam com via paralela a 40 m, GPS com baixa precisão (fixes sintéticos), oscilação,
recuo do GPS, recálculo durante o alerta e recálculo que exclui o radar. São verificações do motor, não medições em campo.
O casamento geométrico não garante separação de pistas ou ruas paralelas muito próximas, sobretudo sem `direction`.

`CaruaruRadarCacheTest` também executa o repositório de produção contra diretórios temporários e porta HTTP falsa:
cache frio salvo e relido por outra instância sem nova rede; cache em disco vencido mantido após falha; arquivo
corrompido descartado sem inventar cobertura. Isso valida I/O no computador, sem afirmar execução no armazenamento Android.
O snapshot lido pelo teste do app foi declarado como input do Gradle: alterá-lo invalida o resultado em cache do teste.

**Cache instalado no Samsung: não inspecionado**, pois não há ADB disponível. Consultas desktop não preenchem o cache do app.
Os snapshots arquivados são evidência/testes e não são embutidos como base operacional no APK.

## Android, interface e limites da validação

`adb devices -l`: lista vazia nas verificações. SDK sem pacote de emulador e sem AVD identificado.
Não houve instalação, abertura de telas, concessão de permissões, teste de notificação, bloqueio de tela ou interrupção de serviço em Android real/emulado.
Esses itens permanecem pendentes, mesmo com a suíte JVM aprovada.

Foram renderizadas e inspecionadas dez prévias Layoutlib: Início vazio/preenchido, Navegação vazia/GPS impreciso,
quatro áreas em 320dp com fonte 150% e Playground em tamanho normal/ampliado.
[Prévias normais](validation/caruaru/previews/normal.png) · [Fonte 150%](validation/caruaru/previews/fonte150.png).
Textos se reorganizam; listas longas continuam abaixo da viewport e exigem rolagem, ainda não testada por interação.
Os dados preenchidos são fixtures de apresentação, com um destino público de São Paulo, sem relação com a consulta real Caruaru.
Estados 'Tripper conectado' nas fixtures são sintéticos e não registram conexão. A ausência de conexão automática é coberta por inspeção/regressões de fontes, não observação de rádio.

O renderer emitiu timeouts de aquecimento; gerou os dez PNGs, todos inspecionados. Fontes/goldens temporários foram preservados fora do Git e retirados dos fontes do app.
Não foram adicionadas dependências experimentais de screenshot ao projeto.
A marca e o ícone ativos não foram alterados.

## Qualidade e próximos testes

Validação local completa: **471 testes**, zero falhas/erros (protocol 115, core 329, app 27); **Lint zero achados**; **build debug aprovado**.
São 462 testes após a consolidação BLE, seis testes dos snapshots e três de integração do repositório/cache em disco na JVM. Não há nova dependência de produção ou mudança de funcionalidade nesta branch.
Auditoria e resultados remotos finais são confirmados na entrega. O APK local mantém o certificado debug anterior; instalação no Samsung não foi realizada.
A proteção BLE foi consolidada em [BLE_OUTBOUND_SAFETY.md](BLE_OUTBOUND_SAFETY.md), sem importar a branch de imagem do Claude.

Sem moto, ainda executar no Samsung: instalação por atualização, quatro áreas e Playground, permissões negadas/concedidas,
chave válida, busca pelo endereço público, simulação, notificação, encerrar, tela bloqueada, parada do serviço e fonte 150%.
Com a Hunter, em rodada futura autorizada: conexão/autenticação manual, estabilidade dos comandos já permitidos,
comportamento GPS/serviço no uso real e observação dos símbolos/alertas. Nunca usar OTA ou comandos desconhecidos como teste.
