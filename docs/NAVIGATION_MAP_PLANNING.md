# Mapa, busca de estabelecimentos e escolha de percurso

Branch isolada `feat/navigation-smart-search`, baseada em `290e6fd` (radares, limites, TTS e Logo 02).
A main continua preservada. Esta entrega não faz conexão com a moto, OTA ou alteração de firmware.

## Fluxo implementado

1. Abra **Navegação**: mapa MapLibre, busca e controle Recentrar. Sem posição autorizada, a câmera
   começa na região pública de Caruaru; isso não representa a posição do usuário.
2. Toque em **Buscar destino**, digite o nome e selecione uma sugestão. Nome comercial vem separado
   do endereço; a distância, quando há localização, está identificada como **em linha reta**.
3. O GraphHopper calcula até três percursos. Só aparecem os caminhos realmente retornados e válidos.
4. Toque numa linha ou no cartão. O destaque verde, resumo, geometria, instruções e associação de
   radares passam a ser os daquela rota. Vias principais vêm da resposta ou de suas instruções.
5. **Iniciar navegação** entrega a mesma instância de Route ao motor existente, sem novo pedido inicial
   de rota. **Simular rota selecionada** usa sua geometria no simulador já existente.
6. Desvio confirmado durante uma viagem continua usando o recálculo existente. O mapa passa a mostrar
   a nova geometria e os radares são associados novamente. Isso é diferente de trocar uma seleção antes de sair.

A prévia expira em três minutos ou quando a origem muda 150 m. Sem fix recente, o início real é bloqueado.
Nesse caso, o app pede **Atualizar percursos** e uma nova conferência; não inicia outra rota silenciosamente.
Não há rota inicial sem prévia válida. Perfil **car**, sem dados de trânsito: não é roteamento específico
para motocicletas, não garante circulação legal nem autoriza rótulos como “mais segura” ou “sem trânsito”.

## Provedores e custos

| Função | Escolha | Condições relevantes em 22/11/2026 |
|---|---|---|
| Renderização | MapLibre Native Android OpenGL 13.7.1, via AndroidView/Compose | BSD-2-Clause; sem serviço de hospedagem ou quota de mapas incluída no SDK |
| Cartografia | MapTiler, raster Streets v4 Dark, 256 px | Chave própria; uso interativo normal, cache ambiente do SDK, sem download offline em massa |
| Estabelecimentos/endereço | MapTiler Search & Geocoding | POIs e endereços, pt, Brasil, seis sugestões; chave e quota próprias |
| Percursos | GraphHopper Routing API existente | Chave própria, perfil car; alternativas custam um crédito adicional sobre o pedido normal segundo a tabela oficial |

O plano MapTiler Free é anunciado a US$ 0 para uso pessoal/não comercial/protótipos, com 100 mil
API requests/mês; a tabela também discrimina 5 mil map sessions e mil search sessions.
Nesta implementação MapLibre/REST não se deve presumir faturamento por sessão de outro SDK:
confira a medição aplicável à sua conta. O plano Flex é anunciado a partir de US$ 30/mês, com excedentes.
Nenhum plano foi contratado, nenhuma conta criada e nenhuma chave incluída no repositório.
Debounce de 400 ms, mínimo de três caracteres e seis sugestões limitam consultas; digitar ainda pode consumir quota.

Atribuição cartográfica e links MapTiler/OpenStreetMap ficam visíveis dentro do mapa, além do logotipo
oficial obrigatório no Free. Radares têm atribuição OpenStreetMap/Pardal/ODbL separada; não pertencem à base de tiles.
O logo MapTiler foi obtido do recurso oficial exclusivamente para atribuição, não faz parte da identidade Hunter Lab.
Licenças do SDK e componentes estão no APK em `assets/licenses/` e em **Configurações → Licenças de mapas**.

## Configuração e privacidade

Em **Configurações**, mantenha/cadastre a chave GraphHopper e adicione sua chave MapTiler.
O botão **Obter minha chave MapTiler** abre o portal do fornecedor; não configura cobrança automaticamente.
Não cole chaves em issues, relatórios, screenshots ou no Git. Recomenda-se limitar as permissões e quotas no portal.
Sem chave MapTiler, o mapa base/busca não fingem resultados: aparece uma orientação para configurar.
Coordenadas e links geo: continuam disponíveis. Sem GraphHopper, o app explica por que não pode calcular o percurso.

As chaves ficam no arquivo privado de preferências já existente, sem criptografia adicional, sem backup/transferência.
Não constituem segredo protegido contra root, instrumentação ou ADB autorizado. A chave de mapa só segue como
configuração efêmera ao SDK; logs de recursos do SDK ficam desligados. Mensagens de erro nunca usam corpos remotos/URLs.
A busca envia o texto ao MapTiler e, com permissão, um viés regional arredondado a três casas decimais; distância
é calculada localmente. Não se solicita `proximity=ip`; o fornecedor vê o IP da conexão HTTPS. O mapa envia os tiles visualizados ao fornecedor.

Recentes (cinco) e favoritos (vinte) guardam apenas destinos escolhidos explicitamente, com nome/endereço/coordenadas,
no aparelho. Não há cache da lista inteira de resultados, exportação de buscas ou banco de POIs coletado em massa.
É possível remover um favorito tocando **Salvo**. A migração preserva os recentes antigos de três campos.
Ao cancelar/alterar a consulta, a tarefa anterior é cancelada e respostas atrasadas são invalidadas por geração.
Uma leitura HTTP já bloqueada pode levar até o timeout para encerrar; ela não pode substituir o resultado atual.

O SDK usa estado de conectividade (`ACCESS_NETWORK_STATE`, permissão normal, sem novo prompt). A declaração
transitiva `ACCESS_WIFI_STATE` foi removida do manifesto final. Não ativa localização própria do MapLibre:
o marcador recebe exclusivamente a fonte GPS existente. Permissões BLE e localização permanecem as anteriores.

## Rotas, radares e falhas

Pedido documentado: `algorithm=alternative_route`, `alternative_route.max_paths=3`,
`alternative_route.max_weight_factor=1.4`, `alternative_route.max_share_factor=0.6`.
Instruções, geometria e `details=max_speed` são preservados por caminho. Cada alternativa é validada pelo parser
existente; geometria degenerada é recusada. Caminhos praticamente iguais (amostras a até 8 m e totais a até 30 m)
são deduplicados. Não se criam atalhos ou alternativas sintéticas em produção.

Rejeição explícita de suporte a alternativas permite fallback para rota única com aviso. Falhas de quota/chave
não disparam esse fallback. O fallback anterior de `max_speed` opcional permanece. Rede, timeout, quota e
resposta inválida têm estado de erro; nenhum deles liga BLE ou cria uma rota fictícia.

Cada seleção invalida o conjunto anterior de marcadores, consulta o repositório/cache existente e executa
RouteRadarMatcher sobre a geometria selecionada. Respostas de outra seleção são descartadas. O sentido continua
classificado pelo matcher; via paralela fora da tolerância continua excluída. Grupos de marcadores se separam ao zoom.
Toque no radar: limite numérico só quando resolvido pelas evidências, status, fonte e sentido desconhecido visíveis.
Conflito, informação vencida ou limite indisponível não recebem número inventado. Ausência de marcadores não prova
que não há fiscalização. As regras e os alertas TTS do MVP não foram substituídos.

## Evidências e limites de validação

Os resultados finais de testes/Lint/build estão registrados na entrega da branch. A suíte preserva os testes
anteriores e acrescenta múltiplos caminhos, deduplicação, geometria inválida, seleção/expiração, recálculo comum,
API sem chave/quota/rede, POI com nome comercial, ausência de localização e radares distintos por geometria.

As prévias em `docs/validation/navigation-map/` são **Layoutlib com fixtures**, não capturas do Samsung ou emulador.
A área cartográfica de Layoutlib é identificada como prévia de composição; não comprova renderização nativa/tiles.
Os números de percurso e o endereço do shopping nessas imagens são sintéticos, não uma resposta real do provedor.

Nesta rodada: ADB sem aparelho; nenhum emulador instalado e virtualização de firmware não disponível ao host.
Não houve instalação do APK novo, teste de teclado/GPU no Samsung, conexão BLE ou teste na moto.
Não há chave legítima MapTiler/GraphHopper disponível para chamadas bem-sucedidas. Consultas negativas reais:
MapTiler sem chave → HTTP 403; GraphHopper com chave de teste deliberadamente inválida → HTTP 401.
Isso confirma tratamento de autenticação, **não** a existência/cobertura do POI ou alternativas reais em Caruaru.

## Resultado local final

- **556 testes JVM aprovados**: 115 protocol, 400 core, 41 app; zero falhas, erros ou ignorados.
- **Android Lint: zero achados**. Build debug e compilação do APK de instrumentação aprovados.
- **Dez renders Layoutlib** revisados, incluindo busca, uma/três rotas, seleção da rota 2, navegação simulada,
  loading, erro, ausência de localização e 320 dp/fonte 150%. Foram corrigidos o reflow de “Chaves” e a
  apresentação do cartão selecionado; a ação Iniciar permanece fixa fora do painel rolável.
- Sem execução no Samsung/emulador nesta rodada. Layoutlib não valida GPU, tiles, teclado real ou ciclo de vida Android.
- APK: `build/distributions/HunterLab-0.3.1-navigation-smart-search-debug-local.apk`.
  SHA-256 `9ca35fdd66a9e9cd16a560d2b1e1754f0513504a0f115cf8895ad48a1ba002f2`.
- Pacote `dev.hunterlab.app.debug`, versão `0.3.1-debug`, código 6. Certificado debug SHA-256
  `cd7734c98417fe084fb5d9c149c98d897db6b271cc3c32f999de4b4d855033cf`: igual ao APK extraído do Samsung na rodada anterior.
  Isso não substitui nova consulta ao aparelho antes de uma próxima instalação. **APK novo não instalado.**
- `zipalign -P 16` aprovado para empacotamento; não é um teste do renderizador nativo no aparelho.
- Auditoria de publicação: nenhum segredo encontrado; sem arquivos temporários de teste, chaves ou logs privados.

[Prancha do fluxo](validation/navigation-map/fluxo.png) · [Estados e fonte ampliada](validation/navigation-map/estados.png) ·
[Resultados e hashes](validation/navigation-map/validation.json).
Avisos de compilação existentes do Material 3 e avisos da ferramenta experimental de render não foram ocultados;
“zero achados” refere-se ao relatório do Lint. Não houve alteração do workflow de produção para gerar estas prévias.

## Validação pendente antes do merge

- Configurar as duas chaves no aparelho sem expô-las; buscar “Caruaru Shopping” e conferir o endereço retornado.
- Conferir tiles, zoom, movimento, atribuição, marcador autorizado e câmera no Samsung.
- Comparar distância/ETA das rotas reais; se vier só uma, não mostrar alternativas inventadas.
- Selecionar outra rota, conferir radares e iniciar; confirmar que a geometria não muda no início.
- Exercitar teclado e fonte 150% em 320 dp; testar fundo/retorno, perda de rede e quota.
- Comparar certificado instalado com o APK local antes de atualizar por cima; não desinstalar/limpar dados.
- Navegação/radares/Tripper físicos e confirmação auditiva humana do TTS permanecem pendentes independentes.

## Fontes primárias

- [MapLibre Android API 13.7.1](https://maplibre.org/maplibre-native/android/api/)
- [Variantes OpenGL](https://maplibre.org/maplibre-native/docs/book/platforms/android/android-rendering-backends.html)
- [Licença e avisos do SDK fixado](https://github.com/maplibre/maplibre-native/blob/android-v13.7.1/platform/android/LICENSE.md)
- [MapTiler Maps API](https://docs.maptiler.com/cloud/api/maps/)
- [MapTiler Search API](https://docs.maptiler.com/cloud/api/geocoding/)
- [Preços e quotas MapTiler](https://www.maptiler.com/cloud/pricing/)
- [Termos Cloud](https://www.maptiler.com/terms/cloud/) e [termos gerais](https://www.maptiler.com/terms/)
- [Atribuição OSM, incluindo forma curta com link](https://osmfoundation.org/wiki/Attribution_Guidelines)
- [Atribuição e logo oficial](https://docs.maptiler.com/guides/map-design/attribution/add-attribution/)
- [OpenAPI GraphHopper](https://docs.graphhopper.com/openapi)
- [Créditos e alternativas](https://support.graphhopper.com/support/solutions/articles/44000718211-what-is-one-credit-)

A publicação da branch não equivale a aprovação para uso rodoviário ou merge na main.
