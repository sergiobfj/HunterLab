# Créditos e revisão de terceiros

## Hunter Lab

A licença do código próprio está pendente de escolha pelo mantenedor. Não foi adicionada uma licença presumida. Opções a avaliar: Apache-2.0 ou MIT; a escolha deve considerar qualquer material derivado de terceiros identificado na revisão de origem.

## Referência de protocolo: WazeTripper

- Projeto: [jnsdias/wazetripper](https://github.com/jnsdias/wazetripper).
- Revisão consultada em 2026-11-21: `7c4235d5c4de5a1a60aa74ea6ece0c06e9ffab07`.
- [Licença Apache-2.0](https://github.com/jnsdias/wazetripper/blob/7c4235d5c4de5a1a60aa74ea6ece0c06e9ffab07/LICENSE).
- [Avisos de atribuição da referência](https://github.com/jnsdias/wazetripper/blob/7c4235d5c4de5a1a60aa74ea6ece0c06e9ffab07/NOTICE.md).
- Uso declarado no Hunter Lab: conhecimento do protocolo, constantes, catálogo de ícones e vetores de teste. Veja `docs/PROTOCOLO.md` e os testes de `protocol/`.

O código local declara implementação Kotlin independente. A revisão inicial não encontrou código Java, smali, scripts de modificação do Waze, APKs proprietários ou bibliotecas vendorizadas, exceto o Gradle Wrapper. Isso não constitui prova integral de autoria ou análise jurídica. O mantenedor deve confirmar a origem antes de atribuir licença própria ou distribuir binários.

O WazeTripper credita o [wazeology](https://github.com/agstrc/wazeology), também Apache-2.0, por sua infraestrutura de modificação do Waze. Essa infraestrutura não foi localizada no Hunter Lab. A licença do WazeTripper não licencia materiais proprietários do Waze ou da Royal Enfield.

Se for identificada adaptação de código ou texto protegido, preservar os avisos aplicáveis, incluir a licença correspondente e indicar as modificações antes de distribuir. Esta revisão não aplica Apache-2.0 ao código próprio do Hunter Lab.

## Dependências declaradas

Licenças conferidas nos POMs do cache Gradle local das versões declaradas. O inventário abaixo cobre dependências diretas e ferramentas principais; não substitui o inventário transitivo do APK final.

| Componente | Versão declarada | Licença informada |
|---|---|---|
| Kotlin e plugin Compose | 2.4.21 | Apache-2.0 |
| kotlinx.coroutines | 1.11.0 | Apache-2.0 |
| kotlinx.serialization JSON (novo na 0.3.0) | 1.9.0 | Apache-2.0 (POM local conferido) |
| AndroidX Core KTX | 1.19.1 | Apache-2.0 |
| AndroidX Activity Compose | 1.13.0 | Apache-2.0 |
| AndroidX Lifecycle | 2.11.1 | Apache-2.0 |
| Compose UI / Material 3 | BOM 2026.11.01 | Apache-2.0 (artefatos UI e Material 3 consultados) |
| JUnit Jupiter / Platform | BOM 6.1.3 | EPL-2.0 (testes) |
| JUnit | 4.13.2 | EPL-1.0 (testes) |
| Android Gradle Plugin | 9.5.2 | Apache-2.0 |
| Gradle Wrapper | 9.9.2 | Apache-2.0; avisos mantidos em `gradlew` e `gradlew.bat` |

O JAR do Gradle Wrapper inclui seu texto de licença em `META-INF/LICENSE`, conferido na revisão.

Antes de uma release pública, gerar o inventário das dependências transitivas resolvidas e incluir no aplicativo/distribuição os textos e avisos exigidos pelas licenças. Repetir a revisão quando os provedores de navegação e mapas forem integrados.

## Marcas

Royal Enfield, Hunter 350, Meteor 350, Tripper, Waze e demais marcas pertencem aos respectivos titulares. As referências descrevem compatibilidade e origem técnica; não indicam afiliação ou endosso.

## Provedores e dados da 0.3.0

O cliente GraphHopper e o parser Overpass são Kotlin do projeto, usando a porta HTTP própria. Nenhum SDK GraphHopper, banco de radares, binário proprietário ou código vendorizado foi acrescentado. O acesso à API depende da chave e das condições do serviço do usuário; não transfere ao app uma licença dos dados retornados.

As rotas e os radares mantêm a atribuição ao OpenStreetMap. Os radares baixados permanecem no cache privado do aparelho; os dados OSM são ODbL, sem aplicar essa licença ao código Kotlin. Consulte [direitos do OpenStreetMap](https://www.openstreetmap.org/copyright) e [política de radares](docs/RADAR_DATA.md). Não foram identificadas novas dependências de produção com licença incompatível nesta revisão; a escolha da licença própria continua pendente.

## Pesquisa 0.3.1

Tachyon, Waveshare, Espressif e LVGL são referências documentais para uma plataforma futura. Nenhum código, biblioteca, firmware ou asset desses projetos foi adicionado ao aplicativo. O README Tachyon declara MIT, mas a revisão consultada não contém o arquivo LICENSE; reutilização de código fica pendente de esclarecimento. Não há dependência nova de produção na 0.3.1.


### Snapshots públicos de validação — Caruaru

Os arquivos OSM em `core/src/test/resources/validation/caruaru/` são dados de © colaboradores do OpenStreetMap,
redistribuídos sob ODbL 1.0, com consultas, timestamps e hashes em `provenance.json` e `docs/VALIDACAO_CARUARU.md`.
Essa licença se refere aos dados, sem alterar a situação da licença própria do código Kotlin.
A pequena resposta de erro GraphHopper é identificada separadamente na proveniência; não contém chave válida.
As prévias de interface usam dados sintéticos. Nenhuma imagem de referência de marca ou firmware proprietário foi incluído.


### Base Pardal PE — limites e radares

`app/src/main/assets/radar/pardal-pe.csv` contém somente `PE/radares.csv` da release
[dados-2026-11-19](https://github.com/murkiriel/pardal/releases/tag/dados-2026-11-19).
Dados: **© colaboradores do OpenStreetMap · Pardal · DER-PE · DNIT · INMETRO · OSM · PCR · SEINFRA-BA**,
sob [Open Database License 1.0](https://opendatacommons.org/licenses/odbl/1-0/).
O aviso original do pacote permanece em `app/src/main/assets/radar/LICENCA-DADOS.txt`, junto da proveniência e hashes.
O CSV sem DRM, os recortes e os resultados de comparação em `docs/validation/caruaru/radar_speed_comparison.json`
são disponibilizados sob a mesma ODbL. A transformação está implementada nos parsers/fusão deste repositório;
não há licença proprietária restringindo acesso às bases derivadas. Caches ficam no armazenamento privado, sem segredos nos dados.
Atribuições também aparecem em Configurações e Navegação. O código do gerador Pardal não foi copiado;
a licença MIT do código desse projeto não substitui a ODbL dos dados nem define a licença própria do Hunter Lab.
A data do pacote não comprova funcionamento físico, calibração vigente ou cobertura completa dos equipamentos.


### Instrumentação Android — revisão no Samsung

AndroidX Test Runner 1.7.0, AndroidX Test JUnit 1.3.0 e Compose UI Test (versão do BOM existente)
são dependências **somente de testes**, sob Apache-2.0. Não entram no APK de produção.
Referências: https://developer.android.com/jetpack/androidx/releases/test e
https://developer.android.com/jetpack/androidx/releases/compose-ui . O runner e o APK de testes
exercitam telas e voz com fixtures explícitas, sem implementar transporte BLE ou provedores novos.


### Marca Hunter Lab — proposta 02

Arte própria da exploração Hunter Lab em `9cbb667`, gerada originalmente e vetorizada
a partir dessa própria arte, aplicada com aprovação do usuário. Não foram incorporadas
imagens de referência do WhatsApp, vetores de terceiros ou marcas oficiais Royal Enfield.
Fontes editáveis e proveniência: `docs/brand/horse-02/`. Não há afirmação de exclusividade
jurídica ou registro de marca. Esta aplicação não define uma licença nova para o projeto.


### MapLibre e cartografia MapTiler — planejamento visual

MapLibre Native Android OpenGL 13.7.1 é uma dependência Maven, sob BSD-2-Clause. Aviso integral
da tag `android-v13.7.1`, incluindo componentes transitivos, preservado no APK em
`app/src/main/assets/licenses/maplibre-native-android-13.7.1.txt` e acessível nas Configurações.
SHA-256 da cópia: `db3cc41e2c79f394a1dddd890c55c263426175029a898d5167820498ddebf152`.
SDK e licença: https://github.com/maplibre/maplibre-native/blob/android-v13.7.1/platform/android/LICENSE.md .

Tiles/POIs são serviços MapTiler, sujeitos à conta/quota/termos do usuário; não são hospedagem gratuita incluída
no MapLibre. Atribuição visível: © MapTiler © OpenStreetMap contributors, com links. O logo oficial foi obtido
de https://api.maptiler.com/resources/logo.svg exclusivamente para a atribuição exigida pelo plano Free;
a versão PNG é sua renderização, sem redesenho. Fonte SVG SHA-256:
`c2fef38a1e77e9ff774dc1b21a2e931c81eab12191395be726c594404e4bf332`.
Uso/atribuição: https://docs.maptiler.com/guides/map-design/attribution/add-attribution/ .
Não há cópia de tiles, bases cartográficas ou resultados comerciais reais neste commit. As prévias usam fixtures.
Esses termos não substituem ODbL da base de radares nem definem a licença própria do Hunter Lab.
