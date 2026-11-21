# Dados de radares fixos (Brasil) — pesquisa para o MVP

Consulta: **2026-11-21** (Agente C). Legenda: **[F]** fato verificado na fonte oficial · **[F*]** medição própria (consulta registrada abaixo) · **[I]** inferência minha · **[N]** não encontrado/não verificado. Não é aconselhamento jurídico.

## 1. OpenStreetMap — modelo de dados

Fontes: [highway=speed_camera](https://wiki.openstreetmap.org/wiki/Tag:highway%3Dspeed_camera) · [Relation:enforcement](https://wiki.openstreetmap.org/wiki/Relation:enforcement)

| Elemento | Semântica e tags |
|---|---|
| `highway=speed_camera` (nó) | "A fixed road-side or overhead speed camera" [F]. Posição: nó **na via** perto da câmera, ou na posição real **ao lado da via** quando houver relação `enforcement` [F]. Tags úteis: `maxspeed`, `direction` (graus ou pontos cardeais, para onde a câmera aponta), `ref`, `operator` [F]. A página não cita `camera:*` [N] (é do esquema de vigilância; não depender) [I]. |
| `type=enforcement` (relação, status *approved*) | `enforcement=maxspeed` \| `average_speed` \| `traffic_signals` \| `check` \| `mindistance` \| … ; `maxspeed=*` só quando `enforcement=maxspeed` [F]. |
| Papéis | `device` (nó/via/área: o equipamento); `from` (nó **na via** onde a fiscalização começa — na placa, se houver; início da média em `average_speed`); `to` (fim; opcional se `device` e `from` estão na mesma via, **obrigatório** se o equipamento está ao lado da via); `section` (via do trecho); `force` (opcional) [F]. Uma relação por equipamento/sentido [F]. |

Implicações [I]: sentido = `from`→`to`/`device` (relação) ou `direction` (nó); sem nenhum dos dois, decidir por casamento com a rota e rumo do veículo. A wiki recomenda que navegadores perguntem na instalação se incluem radares, por países que proíbem avisos [F]; no Brasil, a Res. CONTRAN 798/2020 (alterada pela 804/2020) exige que o órgão divulgue a localização dos medidores fixos — fonte secundária ([notícia](https://agenciainfra.com/blog/?p=6391)); texto oficial e artigo não conferidos [N].

## 2. Overpass API

**Consulta recomendada por tile** (sintaxe validada em 21/11 num bbox de 0,07°×0,08° no centro de SP) [F*]:

```
[out:json][timeout:25];
(
  node["highway"="speed_camera"](S,W,N,E);
  rel["type"="enforcement"]["enforcement"~"^(maxspeed|average_speed|traffic_signals)$"](S,W,N,E);
)->.base;
(
  .base;
  node(r.base);      /* nós-membro (device/from/to) com lat/lon */
);
out body qt;
```

`rel(bbox)` traz relações com algum membro no bbox; `node(r.base)` traz os nós-membro com coordenadas [I, confirmado no teste]. `device` como via não é expandido — se necessário, acrescentar `way(r.base); node(w);` [I]. Teste: 32 KB, 172 câmeras (158 com `maxspeed`, **só 6 com `direction`**), 11 relações (7 `traffic_signals`, 4 `maxspeed`; papéis 11 `from`, 10 `to`, 5 `device`), **41 s** de resposta [F*].

| Item | Resultado |
|---|---|
| Política `overpass-api.de` (wiki OSM) | ≤ 10.000 consultas/dia **e** ≤ 1 GB/dia **e** ≤ 10 min de processamento somados; uso regular: **dividir por 100** (< 100 consultas e < 10 MB/dia); a soma de **todos os usuários** do app conta; UA/Referer que identifique o app; sem scripts paralelos; uso comercial → servidor próprio/pago; após 429/406 esperar 30 s; "overloaded … do not expect high reliability" [F]. [fonte](https://wiki.openstreetmap.org/wiki/Overpass_API#Public_Overpass_API_instances) |
| Doc oficial do Overpass | ~10.000 req/dia, < 1 GB/dia; *slots* por usuário; fila de até 15 s; recusa com **429**; timeout padrão **180 s**, maxsize padrão **512 MiB** [F]. [fonte](https://dev.overpass-api.de/overpass-doc/en/preface/commons.html) |
| Operador (FOSSGIS) | Atribuição clara + link `openstreetmap.org/fixthemap`; UA válido (UA de biblioteca não serve); máx. 2 conexões (scripts: 1); download em massa proibido; e-mail de contato no site/loja do app; sem garantia, acesso revogável [F]. [fonte](https://www.fossgis.de/arbeitsgruppen/osm-server/nutzungsbedingungen/) |
| Instâncias alternativas [F] | `maps.mail.ru/osm/tools/overpass/api/interpreter` (VK, "no requests limitations"); `overpass.private.coffee/api/interpreter` (sem rate limit; avisar uso em larga escala); `ovp.dresel.systems/api-sa/interpreter` (só América do Sul, atualização diária, 4 consultas paralelas/IP); pagas: Geofabrik, overpass.ch etc. |
| Timeouts [I] | `[timeout:25]` na consulta, ~60 s no cliente HTTP; em 429/504/timeout: esperar 30 s, uma nova tentativa, depois próxima instância da lista configurável. |

## 3. Licença ODbL — app que baixa, guarda em cache e alerta

Fontes: [copyright](https://www.openstreetmap.org/copyright) · [diretrizes de atribuição](https://osmfoundation.org/wiki/Licence/Attribution_Guidelines) · [FAQ legal](https://osmfoundation.org/wiki/Licence/Licence_and_Legal_FAQ)

- **Atribuição** [F]: creditar "OpenStreetMap" e deixar claro que os dados estão sob ODbL (ex.: link para `openstreetmap.org/copyright`); "© OpenStreetMap contributors" é aceito. Em apps: visível **sem exigir interação** (pode recolher após 5 s ou ficar em splash/pop-up), e encontrável depois via "(i)"/Sobre.
- **Texto proposto** [I]: "Radares: © colaboradores do OpenStreetMap (ODbL) — openstreetmap.org/copyright · Corrigir dados: openstreetmap.org/fixthemap". Onde: tela/faixa de Radares, tela Sobre/Configurações e README da release.
- **Obrigações** [F]: só surgem com *Public Use* (disponibilizar fora da sua organização/a terceiros); sem *Public Use* "you do not have to share anything". *Produced Work* = obra acabada feita com os dados → exige atribuição. *Derivative Database* = dados adaptados/modificados/estendidos → se disponibilizado, continua ODbL (share-alike).
- **Cache local** [I]: cada aparelho baixa do Overpass para uso próprio → não é *Public Use*; os alertas são *Produced Work* → atribuição. **Não embutir** banco de radares OSM no APK (distribuir pelo GitHub seria *Public Use*; se misturado a outra fonte, tende a virar *Derivative Database* sob ODbL). Manter fontes em tabelas separadas, com campo `source`.

## 4. Qualidade no Brasil — medições (overpass-api.de, 2026-11-21)

Uma única consulta `out count` (82 s), base OSM `2026-11-21T21:55:28Z`, áreas `19:23:04Z` [F*]:

| Área | `node[highway=speed_camera]` | `rel[type=enforcement]` (qualquer tipo, ≥ 1 membro na área) |
|---|---|---|
| Brasil (`area["ISO3166-1"="BR"][admin_level=2]`) | **9.991** | **1.021** |
| Cidade de São Paulo (`area["name"="São Paulo"]["admin_level"="8"]`) | **888** | **37** |

O filtro por nome assume que só o município tem `name=São Paulo` em `admin_level=8` [I]. Comparação [F + I]: a Prefeitura de SP publica **877 locais fixos** (835 fiscalizam velocidade; CSV 2025-11) — mesma ordem de grandeza, mas não comparável 1:1 (um "local" pode ter vários equipamentos; OSM pode incluir câmeras de semáforo). Na amostra do centro de SP, 92 % têm `maxspeed` e 3,5 % têm `direction` [F*]. Atualidade e completude do OSM não são garantidas; radares removidos podem continuar mapeados [I]. Não medi `direction`/`maxspeed` no Brasil todo (evitar consulta pesada) [N].

## 5. Outras fontes brasileiras abertas

| Fonte | Cobertura | Coordenadas | Licença | Atualização | Uso |
|---|---|---|---|---|---|
| [ANTT — Radar](https://dados.antt.gov.br/dataset/radar) | Rodovias federais **concedidas** | Sim (`latitude`, `longitude`), + `sentido`, `velocidade_leve/pesado`, `tipo_de_radar`, `situacao` | "Creative Commons Atribuição" (`cc-by`, versão não indicada) [F] | Mensal; CSV/JSON out/2026: **1.254 ativos**, 14 UFs [F*] | **Candidata a 2ª fonte** (crédito "Fonte: ANTT – Dados Abertos") [I] |
| [PBH — Fiscalização Eletrônica](https://dados.pbh.gov.br/dataset/fiscalizacao-eletronica) | Belo Horizonte | Sim, `POINT` em UTM **EPSG:31983** (converter) | CC-BY [F] | Mensal; nov/2026: 473 equip. (214 de velocidade) [F*] | Possível, só BH [I] |
| [Prefeitura de SP](https://dados.prefeitura.sp.gov.br/dataset/fiscalizacao-eletronica-do-transito) | São Paulo | **Não** (só endereço) | CC-BY [F] | Irregular (2025-11, 2026-06) | Exigiria geocodificar — não recomendado [I] |
| [Fortaleza](https://dados.fortaleza.ce.gov.br/dataset/fiscalizacao-eletronica) | Fortaleza | GeoJSON | **Não informada** no portal [F] | — | Licença incerta → não usar |
| DNIT/PNCV, PRF, DERs | Rodovias federais/estaduais | Lista pública com coordenadas e licença **não encontrada** [N] | — | — | LAI é uma via possível [I] |

## 6. Política proposta [I]

- **Fonte**: OSM via Overpass (Brasil todo); ANTT opcional como camada separada para rodovias concedidas.
- **Cache por tiles** fixos de 0,25° × 0,25° (~28 km), chave `lat0_lon0`; baixar o tile atual + tiles do corredor da rota (≤ 20 por rota), **uma consulta por vez**, ≥ 1 s entre consultas.
- **Validade**: 14 dias; renovar só quando o tile for usado e estiver vencido; sem rede, usar o vencido (até 60 dias) com aviso "dados antigos". Meta: < 100 consultas/dia por aparelho, bem abaixo da política.
- **Rede**: User-Agent `HunterLab/<versão>` com contato definido pelo dono; endpoints configuráveis (overpass-api.de → private.coffee → maps.mail.ru); respeitar 30 s após 429/406.
- **Atribuição**: texto da §3 sempre que houver alerta/lista de radares, e em Sobre.
- **Aviso obrigatório** (tela de radares e primeira ativação): "Cobertura limitada: radares vêm do OpenStreetMap (mapeamento colaborativo) e podem estar ausentes, desatualizados ou errados. **A ausência de alerta não significa ausência de fiscalização.** Respeite a sinalização."

## 7. Limiares 700 m / 300 m (moto)

Hoje: busca até 700 m e alerta visível a ≤ 300 m (`PROTOCOLO.md` §5). Tempo até o radar a velocidade constante [I, cálculo]:

| Velocidade | 700 m | 300 m |
|---|---|---|
| 40 km/h | 63 s | 27 s |
| 60 km/h | 42 s | 18 s |
| 80 km/h | 32 s | 14 s |
| 110 km/h | 23 s | 10 s |

Comentário [I]: **cidade** — 700 m a 40–60 km/h cobre muitos cruzamentos e vias paralelas; sem casamento com a rota e o sentido, gera alertas falsos; 300 m (18–27 s) basta para reduzir (60→40 km/h a 3 m/s² + 1,5 s de reação ≈ 50 m). **Rodovia** — 300 m a 110 km/h são 10 s; reduzir 110→80 km/h a 3 m/s² com 1,5 s de reação consome ≈ 120 m, então ainda cabe, mas a pré-busca de 700 m (23 s) é útil. Alternativa a testar: limiar por tempo, ex. 1º aviso em `max(300 m, 25 s × v)` e 2º em `max(150 m, 10 s × v)`. Nenhum valor é universal: depende de GPS (~1 Hz, erro urbano), de onde o nó foi mapeado (na via × ao lado) e da sinalização R-19 local.
