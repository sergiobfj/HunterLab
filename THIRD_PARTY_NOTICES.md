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

## Ferramenta opcional de prévias de marca

`tools/render_brand_concepts.py` e `tools/render_horse_refinement.py` são rotinas desktop opcionais que usam PyMuPDF para rasterizar SVGs originais. PyMuPDF/MuPDF são oferecidos sob AGPL e licença comercial Artifex; consultar os [termos oficiais](https://pymupdf.readthedocs.io/en/latest/about.html#license-and-copyright) antes de reutilizar a ferramenta em outro contexto. A biblioteca não é vendorizada, não faz parte das dependências Android e não é incluída no APK. Nenhuma licença própria do Hunter Lab foi escolhida ou alterada nesta entrega. Os arquivos SVG/Android contêm geometria original, sem vetores, fotografias ou emblemas de terceiros copiados.
