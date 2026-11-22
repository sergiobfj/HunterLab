# Radares e limites de velocidade — Caruaru / PE

Consulta: **22/11/2026**. Implementação em `feat/mvp-radar-speed-alerts`, baseada na main `85e14b3`.
Nenhum teste físico, envio BLE, alteração de firmware ou nova identidade visual.

## Resultado da comparação real

Os recortes abaixo são caixas de pesquisa, não limites administrativos ou um inventário rodoviário oficial.
As respostas OSM são as consultas reais arquivadas em [VALIDACAO_CARUARU.md](VALIDACAO_CARUARU.md), capturadas em 22/11.
Pardal: pacote PE da release **dados-2026-11-19**, obtido em 22/11, SHA-256 do ZIP
`194deed3a269978a74701930810177791caf6c35018412ff28b8d3d1c434e6da`.

| Recorte (sul, oeste, norte, leste) | OSM: pontos / com limite | Pardal: ativos / com limite |
|---|---:|---:|
| Urbano: -8.34, -36.06, -8.20, -35.90 | 37 / 32 | 40 / 37 |
| BR-104 e entorno: -8.50, -36.10, -8.05, -35.90 | 38 / 32 | 42 / 38 |
| BR-232 e entorno: -8.40, -36.20, -8.20, -35.78 | 43 / 34 | 46 / 40 |
| União regional, sem repetir ID/registro | **44 / 34** | **48 / 41** |

Não somar as linhas: os recortes se sobrepõem. Todos os registros regionais encontrados são radares fixos;
nenhuma relação enforcement apareceu nessas três respostas. Os testes de relações são fixtures explícitas.
Os recortes das rodovias também contêm ruas urbanas e acessos; somente a pesquisa anterior de geometria
aproximou 17 nós a até 5 m da BR-232 e cinco da BR-104. Proximidade não identifica pista ou equipamento físico.

Há 42 nós OSM a até 25 m de 41 registros distintos do Pardal; 33 pares de vizinhos mais próximos têm número
nas duas bases, sem divergência numérica nesses pares. Um nó pode ter o mesmo vizinho que outro nó.
As coincidências não são 42 confirmações independentes: o Pardal inclui OSM na própria origem.
A regra conservadora de fusão usa até 5 m, mesmo tipo, sentidos conhecidos compatíveis e correspondência
única nos dois lados; vizinhos ambíguos permanecem separados. Os 57 candidatos resultantes dessa união
conservadora não representam 57 equipamentos físicos comprovados.

### Quais fontes sustentam os valores

No Pardal regional: OSM 27 registros, DER-PE+OSM 6, DNIT 4, DNIT+OSM 2, INMETRO+OSM 8, DER-PE 1.
Essas são etiquetas de proveniência do agregador, não uma nova conferência individual de cada cadastro oficial.
O CSV não conserva ID OSM, número do equipamento, data de aferição ou confiança geográfica por ponto.
As posições INMETRO isoladas podem derivar de geocodificação de endereço; é uma limitação do projeto de origem.

A [lista pública da AMC](https://amc.caruaru.pe.gov.br/site/PDF/VELOCIDADE%20REGULAMENTADA%20DOS%20PONTOS%20DE%20FISCALIZA%C3%87%C3%83O%20ELETR%C3%94NICA%20-%20AMC.pdf)
contém 21 equipamentos em 19 posições distintas. Em 16 linhas (14 posições), há nó OSM a até 75 m
com o mesmo limite informado. Cinco linhas não têm candidato nessa distância. Isso confirma concordância
numérica na vizinhança, sem comprovar identidade, instalação atual, pista ou validade da aferição.
A comparação pública por equipamento está no [JSON de evidências](validation/caruaru/radar_speed_comparison.json).
Os registros DER-PE/DNIT examinados anteriormente não permitem afirmar um censo rodoviário atual completo;
o documento DER-PE R15407 tem validade de verificação terminando em 01/07/2026. Não tratá-lo como aferição vigente.

### Pendências e divergências

- Dez pontos OSM e sete registros Pardal regionais não têm limite; não se usa velocidade típica da via.
- O nó OSM `14170005331` está sem maxspeed na captura atual, mas o vizinho Pardal na mesma posição informa 60,
  com origem OSM. É uma diferença de disponibilidade entre snapshots, sem confirmação oficial independente desse número.
- Vizinhos de `14170046745`, `14170047814` e `14170008699` informam 60 no Pardal, enquanto os nós OSM não têm limite.
  Estão a aproximadamente 9,6 m, 17,1 m e 27,5 m; não recebem automaticamente o número por proximidade.
- Dois nós OSM têm `direction=200`, mas essa tag é orientação da câmera: **nenhum dos 44 tem rumo fiscalizado confirmado**
  neste importador. Quatro registros Pardal regionais informam rumo fiscalizado; os demais são desconhecidos,
  sem presumir fiscalização nos dois sentidos como fato confirmado.
- Há diferenças de posição, cobertura e agrupamento. Não foram encontrados números discordantes nos pares
  próximos com ambos os números; conflitos futuros são tratados explicitamente e testados com fixtures.

## Matriz por nó OSM

Vizinho mais próximo do Pardal, incluindo os que ultrapassam 25 m. “—” significa ausente.
A coluna de origem é declarada pelo Pardal; a distância não comprova equivalência nem confirmação oficial.

| ID OSM | OSM km/h | Pardal km/h | Distância m | Origem Pardal |
|---|---:|---:|---:|---|
| [10124884242](https://www.openstreetmap.org/node/10124884242) | 60 | 60 | 0.1 | OSM |
| [10147570982](https://www.openstreetmap.org/node/10147570982) | 40 | 40 | 10.9 | DNIT+OSM |
| [12618245077](https://www.openstreetmap.org/node/12618245077) | 50 | 50 | 0.0 | OSM |
| [12618245078](https://www.openstreetmap.org/node/12618245078) | 50 | 50 | 0.0 | OSM |
| [12791592886](https://www.openstreetmap.org/node/12791592886) | 40 | 40 | 0.1 | INMETRO+OSM |
| [12791592887](https://www.openstreetmap.org/node/12791592887) | 40 | 40 | 0.0 | INMETRO+OSM |
| [13702580919](https://www.openstreetmap.org/node/13702580919) | 40 | 40 | 0.0 | OSM |
| [13702766757](https://www.openstreetmap.org/node/13702766757) | 50 | 50 | 0.0 | INMETRO+OSM |
| [13702767947](https://www.openstreetmap.org/node/13702767947) | 50 | 50 | 0.0 | INMETRO+OSM |
| [13702772771](https://www.openstreetmap.org/node/13702772771) | 40 | 40 | 0.1 | OSM |
| [13702779449](https://www.openstreetmap.org/node/13702779449) | 40 | 40 | 0.0 | OSM |
| [13702785887](https://www.openstreetmap.org/node/13702785887) | 100 | 100 | 0.1 | OSM |
| [13702790891](https://www.openstreetmap.org/node/13702790891) | 40 | 40 | 0.0 | INMETRO+OSM |
| [13702793212](https://www.openstreetmap.org/node/13702793212) | 40 | 40 | 0.0 | OSM |
| [13702799277](https://www.openstreetmap.org/node/13702799277) | — | — | 0.1 | OSM |
| [13702800046](https://www.openstreetmap.org/node/13702800046) | 50 | 50 | 0.0 | OSM |
| [13702800429](https://www.openstreetmap.org/node/13702800429) | 40 | 40 | 0.0 | INMETRO+OSM |
| [13702810102](https://www.openstreetmap.org/node/13702810102) | — | — | 0.0 | OSM |
| [13702821954](https://www.openstreetmap.org/node/13702821954) | — | — | 0.1 | OSM |
| [13704372180](https://www.openstreetmap.org/node/13704372180) | 50 | 50 | 0.0 | OSM |
| [13704407046](https://www.openstreetmap.org/node/13704407046) | 40 | 40 | 0.0 | OSM |
| [13704409531](https://www.openstreetmap.org/node/13704409531) | 60 | 60 | 0.1 | OSM |
| [13704412914](https://www.openstreetmap.org/node/13704412914) | 40 | 40 | 0.0 | INMETRO+OSM |
| [13704431808](https://www.openstreetmap.org/node/13704431808) | 60 | 60 | 0.0 | OSM |
| [13705844549](https://www.openstreetmap.org/node/13705844549) | 50 | 50 | 0.1 | OSM |
| [13705935102](https://www.openstreetmap.org/node/13705935102) | 40 | 40 | 0.0 | OSM |
| [13705968423](https://www.openstreetmap.org/node/13705968423) | 40 | 40 | 0.0 | OSM |
| [13705985365](https://www.openstreetmap.org/node/13705985365) | 50 | 50 | 0.1 | INMETRO+OSM |
| [13733699033](https://www.openstreetmap.org/node/13733699033) | 60 | 60 | 16.9 | OSM |
| [13733699034](https://www.openstreetmap.org/node/13733699034) | 60 | 60 | 0.0 | OSM |
| [13969346281](https://www.openstreetmap.org/node/13969346281) | 60 | 60 | 11.4 | DER-PE+OSM |
| [13969346283](https://www.openstreetmap.org/node/13969346283) | 60 | 60 | 13.5 | DER-PE+OSM |
| [13993951485](https://www.openstreetmap.org/node/13993951485) | 60 | 60 | 7.5 | DER-PE+OSM |
| [13998758440](https://www.openstreetmap.org/node/13998758440) | 110 | 110 | 0.1 | OSM |
| [14013937396](https://www.openstreetmap.org/node/14013937396) | 60 | 60 | 0.0 | OSM |
| [14053395543](https://www.openstreetmap.org/node/14053395543) | 60 | 60 | 0.0 | OSM |
| [14053402357](https://www.openstreetmap.org/node/14053402357) | 60 | 60 | 28.2 | DNIT+OSM |
| [14170005331](https://www.openstreetmap.org/node/14170005331) | — | 60 | 0.0 | OSM |
| [14170008699](https://www.openstreetmap.org/node/14170008699) | — | 60 | 27.5 | DER-PE+OSM |
| [14170009078](https://www.openstreetmap.org/node/14170009078) | — | — | 0.0 | OSM |
| [14170046745](https://www.openstreetmap.org/node/14170046745) | — | 60 | 9.6 | DER-PE+OSM |
| [14170047814](https://www.openstreetmap.org/node/14170047814) | — | 60 | 17.1 | DER-PE+OSM |
| [14170047815](https://www.openstreetmap.org/node/14170047815) | — | — | 0.1 | OSM |
| [14170051096](https://www.openstreetmap.org/node/14170051096) | — | — | 0.1 | OSM |

## Pardal: qualidade, atualização e licença

O pacote PE tem **512 linhas: 497 ativas e 15 inativas**. Somente registros ativos de tipo suportado entram no app.
O pacote foi gerado em 19/11/2026 às 16:52:09 UTC e publicado às 16:55:04 UTC. O catálogo registra aquisição
DER-PE, DNIT e SEINFRA-BA em 19/11. A data de aquisição/geração **não é inspeção física ou aferição**;
nem todas as fontes têm data individual publicada no catálogo.
O README descreve releases diárias e cópias semanais no repositório; a aplicação fixa uma edição auditada,
sem baixar automaticamente um ZIP novo ou executar código do gerador. A revisão consultou também a origem
`09a5fbf5fa71b251b9161a3230c201ae3c2cb63a`; números de anúncios antigos não substituem a contagem do ZIP.

Incluído somente `PE/radares.csv`, aviso original de licença e proveniência, com SHA-256 conferido na leitura.
Nenhum dado de acidentes, pessoas, multas ou firmware foi incorporado. As demais tabelas de limites estimados
(`limites.csv`, `limites_trechos.csv`, limites típicos ou faixas legais) **não alimentam o limite do radar**.
Tipos de fiscalização média por trecho do Pardal não viram um alerta pontual indevidamente.

Dados sob [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/): manter avisos e atribuição,
identificar a licença e oferecer bases derivadas publicamente usadas sob a mesma licença, sem restrições adicionais.
O CSV integral incluído, a comparação e as transformações estão disponíveis neste repositório, sem DRM.
Atribuição: **© colaboradores do OpenStreetMap · Pardal · DER-PE · DNIT · INMETRO · OSM · PCR · SEINFRA-BA**.
A licença dos dados é separada da licença do código; nenhum código MIT do gerador foi copiado.
Ver [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) e o aviso original em `app/src/main/assets/radar/`.
Não se afirma endosso de órgãos públicos nem auditoria jurídica completa da cadeia de fontes.

## Regras implementadas

- Evidências de nó/relação mantêm valor bruto, origem, condição e disponibilidade. `maxspeed:motorcycle`
  explícito prevalece sobre o valor geral do mesmo elemento. Relações não sobrescrevem divergências silenciosamente.
- `maxspeed:conditional`, variantes condicionais, limites direcionais ou por faixa não resolvidos deixam o número ausente.
  Valores não numéricos, múltiplos, desconhecidos, estimados ou conflitantes não aparecem como limite confirmado.
- Não soma tolerância, percentual, margem de aferição ou “velocidade considerada” ao limite.
- `direction` OSM é armazenado separadamente como orientação da câmera. Fluxo OSM só vem de geometria curta
  from→to inequívoca; sentidos discordantes permanecem desconhecidos. Não se inverte automaticamente 180°.
- Duplicatas por ID preservam evidências. Fusão entre fontes exige correspondência recíproca única em até 5 m;
  sentidos conhecidos opostos ficam separados. Números incompatíveis na vizinhança ambígua de até 25 m são ocultados.
- A fusão ocorre antes da divisão em tiles, evitando duplicação artificial na borda. Mantém o casamento com a rota existente,
  que não comprova pista em ruas paralelas muito próximas; ausência de direção permanece visível.
- Cache v2 conserva evidências/condições. Cache v1 migra sem apagar posições: oculta números cuja condição se perdeu,
  invalida o rumo antigo e solicita atualização. Cache vencido ou com atualização falha mantém pontos, sem afirmar números.
- Base Pardal embarcada vale 14 dias para apresentar números; depois conserva posições e marca dados desatualizados.
  Uma fonte atual concordante pode fornecer número mesmo com outra evidência vencida, identificada como inutilizável.
- Sem rede, a base local pode oferecer cobertura parcial; nunca declara cobertura completa só por conter alguns pontos.

## GraphHopper: complemento por trecho

A Routing API documenta `details=max_speed`: intervalos de índices da geometria com limite ou valor desconhecido.
O app solicita esse detalhe, converte intervalos em distâncias e mantém um modelo **separado** dos radares.
A navegação apresenta “Trecho: … km/h · GraphHopper / perfil car”, sem transferir esse valor a uma câmera.
Metadado ausente/inválido não invalida a rota. Se o servidor recusa explicitamente o detalhe opcional com HTTP 400,
há uma tentativa sem detalhes; 401, 429, timeout e outros erros não provocam esse segundo pedido.
Perfil **car**, não otimizado nem validado para motocicleta. Nenhuma tolerância ou inferência de velocidade legal é aplicada.

Sem chave válida localizada, `max_speed` foi validado documentalmente e por fixtures, **não por uma resposta 200 real**.
A requisição real com chave inválida e as demais limitações estão no relatório de validação doméstica.

## Android e voz

O alerta mostra tipo, distância e placa circular somente para limite cadastrado consistente. Limite ausente,
condicionado ou divergente usa traço neutro e explicação, sem placa numérica vermelha. Mostra fonte e sentido não confirmado.
O tema Graphite Grey, ícone existente e demais áreas permanecem preservados.

“Aviso por voz” é **opcional e desligado por padrão**. Um alerta ativo (300 m por padrão) pode falar uma vez por radar/passagem,
com intervalo mínimo de oito segundos entre avisos; detecção a 700 m permanece visual. Não fala ao abrir o app ou apenas ativar a opção.
A preparação TTS é assíncrona, limitada a cinco segundos de validade da fala pendente; exige navegação ativa e GPS preciso.
Encerrar, desativar, perder GPS ou parar serviço invalida callbacks e libera o motor. Recálculo não reinicia a deduplicação.
Simulação é anunciada como tal. A fala não inclui destino, coordenadas, chave, PIN ou dados pessoais.
Seleciona voz pt-BR que o motor declara instalada e sem necessidade de rede; sem ela, informa indisponibilidade,
sem bloquear navegação. Políticas internas do motor TTS instalado continuam sob controle do Android/fornecedor.
Não altera volume à força, não requer microfone e não cria serviço de áudio próprio.

## Limite numérico no Tripper original

Revisados [WazeTripper PROTOCOL.md](https://github.com/jnsdias/wazetripper/blob/main/docs/PROTOCOL.md), builders locais,
[PROTOCOLO.md](PROTOCOLO.md) e [pesquisa gráfica](TRIPPER_GRAPHICS_RESEARCH.md).
Há códigos de símbolos/manobras, distância até a ação, tema, indicador secundário e campos de rodapé com funções documentadas.
**Não foi encontrado campo documentado para placa de limite numérico arbitrário.** Símbolos numerados de saída de rotatória
não equivalem a um limite legal, e capacidade interna de desenhar números não prova comando BLE correspondente.
Não substituir distância, ETA, relógio, ícone de saída ou seta por velocidade. Mantém o indicador de radar já existente
sob a arbitragem da navegação, sem transmitir o número novo ao Tripper.
O número permanece no Android/TTS. Qualquer alternativa futura exige evidência documental e autorização separada;
nenhum opcode, builder, frequência, handshake, fila, proteção OTA ou firmware foi alterado.

## Validação e pendências

A revisão de dados também encontrou um registro PE de origem SEINFRA-BA perto da divisa em Petrolina/Juazeiro;
sem conferência de limite estadual/pista não se conclui erro, mas isso reforça que a UF do pacote não é prova de jurisdição.

Os testes incluem evidências conflitantes/condicionais, unidades, sentidos, cache v1/v2, fontes vencidas,
Pardal real, fusão ambígua, limites de trecho, fallback da API, voz assíncrona/cancelamento e integração de fontes offline.
As regressões anteriores de BLE/OTA, Playground, alertas 700/300 m, desvio, ruas paralelas e GPS continuam na suíte.
Prévias Layoutlib em `validation/caruaru/radar-previews/` usam dados sintéticos, incluindo 320 dp e fonte a 150%.
Não são captura do Samsung, execução de TTS, teste de estrada ou confirmação de fiscalização ativa.

Antes de integrar: revisar prévias e CI da branch. No Samsung: verificar voz pt-BR, volume, permissões,
notificação, bloqueio de tela, GPS perdido, encerrar e ausência de conexão BLE automática.
Depois, só com autorização e moto estacionada, testar funções já permitidas no Tripper; esta entrega não exige OTA ou comandos novos.

## Resultado automatizado desta branch

- **513 testes aprovados**: 115 protocol, 365 core, 33 app; zero falhas, erros ou testes ignorados.
- `test :app:lintDebug :app:assembleDebug`: BUILD SUCCESSFUL; Lint sem achados.
- 42 testes acrescentados à base de 471, preservando cenários anteriores. Expectativas de direção OSM,
  versão do cache e filtragem do bbox da fixture foram atualizadas para a semântica corrigida, sem eliminar testes negativos.
- Gitleaks: snapshot público e histórico de todas as refs sem segredos detectados; revisão de arquivos gerados,
  keystores, logs privados, permissões, atribuição e diff antes do push.
- APK de revisão local em `build/distributions/HunterLab-0.3.1-radar-speed-review-debug-local.apk`,
  applicationId `dev.hunterlab.app.debug`, versionName `0.3.1-debug`, versionCode `6`.
- Certificado SHA-256 `cd7734c98417fe084fb5d9c149c98d897db6b271cc3c32f999de4b4d855033cf`,
  igual ao APK local anterior. Certificado da instalação do Samsung não foi consultado nesta sessão.
  Usar o APK local para revisão; não assumir que o APK do CI tem a mesma assinatura.

A branch é publicada para revisão, sem merge na main ou release estável. Instalação e áudio no Samsung pendentes.

## Referências primárias

- [Pardal: dados, esquema, fontes e ressalvas](https://github.com/murkiriel/pardal),
  [release fixada](https://github.com/murkiriel/pardal/releases/tag/dados-2026-11-19),
  [catálogo dessa edição](https://github.com/murkiriel/pardal/releases/download/dados-2026-11-19/catalogo.json).
- [OSM speed_camera](https://wiki.openstreetmap.org/wiki/Tag:highway%3Dspeed_camera),
  [enforcement](https://wiki.openstreetmap.org/wiki/Relation:enforcement),
  [restrições condicionais](https://wiki.openstreetmap.org/wiki/Conditional_restrictions).
- [GraphHopper: path details](https://www.graphhopper.com/blog/2019/11/28/routing-api-using-path-details/),
  [API oficial](https://docs.graphhopper.com/openapi).
- [Android TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech).
- [ODbL: texto integral](https://opendatacommons.org/licenses/odbl/1-0/),
  [direitos OSM](https://www.openstreetmap.org/copyright).
