# Consolidação Hunter Lab 0.3.1

## Origem e decisões

Em 21/11/2026, main em `a5fad26` (0.3.0); Playground `306a3cf` (0.3.1); Graphite `4b7169b` já incluída no Playground; primeira identidade/pesquisa `6c97737`. Worktrees conferidas limpas. A UI alternativa `68d5bd6` foi preservada, sem integração por duplicar a interface funcional do MVP.

A branch `feat/mvp-031-consolidation` parte do Playground. Traz a pesquisa pelo commit isolado `8d7841a`, com ajuste de link para o catálogo agora local. Nenhum merge integral da identidade. A primeira proposta H e seus recursos Android não foram aplicados; a marca nova permanece pendente de aprovação em branch própria.

## Preservação funcional

`app/`, `core/` e `protocol/` são idênticos ao commit `306a3cf`: GPS, GraphHopper, radares, fila BLE e handshake manual, propriedade de sessão, prioridade da navegação, cancelamento/gerações, testes negativos, allowlist, limites de duração e cadência, sequências apenas simuladas e relatório sanitizado. Nenhuma nova função de comunicação foi implementada.

Versão 0.3.1, versionCode 6, pacote debug `dev.hunterlab.app.debug`. Sem alteração de assinatura, permissões ou transporte. Workflows existentes preservados; a branch de consolidação já corresponde ao filtro `feat/mvp-*`.

## Documentação e limites

- [Playground](TRIPPER_PLAYGROUND.md): catálogo 81 entradas, somente bússolas individuais elegíveis no físico, sequências simuladas.
- [Pesquisa gráfica](TRIPPER_GRAPHICS_RESEARCH.md): sem evidência suficiente para GIF/vídeo/imagem arbitrária no Pod; FOTA não prova API gráfica.
- [Display independente](FUTURE_ROUND_DISPLAY.md): pesquisa separada, sem alteração do Tripper.
- [Teste físico do MVP](PHYSICAL_TEST_PLAN.md): navegação/radares continuam pendentes de validação real; roteamento de carro não é otimização validada para motocicletas; cobertura de radares não é completa.

Nenhuma conexão física, instalação em celular, transmissão experimental, abertura, gravação de firmware ou alteração elétrica ocorreu nesta entrega.

## Validação

`./gradlew test :app:lintDebug :app:assembleDebug --max-workers=2 --console=plain`: **BUILD SUCCESSFUL**.

- **448 testes**: protocol 115, core 310, app 23; zero falhas, erros ou ignorados, incluindo regressões e testes negativos do Playground e contraste Graphite.
- Lint debug: **No issues found**. Recursos Android compilados sem mudança em relação ao Playground.
- Código funcional idêntico a `306a3cf`, conferido por diff. Sete renders do Playground já existentes incluem 320 dp/fonte 150% e layout amplo; não foram declarados como hardware ou refeitos sem mudança visual.
- Auditoria de arquivos publicáveis e histórico Git, revisão de diff/staging e actionlint antes do push. A identidade nova é validada separadamente, sem alterar o APK consolidado.
- APK local consolidado: `build/distributions/HunterLab-0.3.1-consolidated-debug-local.apk`, SHA-256 `cf4cda086e4a4190c05c032e585aab40d4aea9ed40f92d7f111d4292af558113`. Certificado debug local conferido com `apksigner`, correspondente ao hash abaixo; nenhuma chave é publicada. Não presumir que APK do CI tenha a mesma assinatura local.

Aprovação de nova marca permanece independente da integração funcional. CI remoto deve ser conferido pelo SHA da branch e da main após o merge.

## Preparação do próximo teste

Antes de instalar, conferir pacote `dev.hunterlab.app.debug`, versionCode 6 e certificado da instalação existente. A chave debug local anteriormente registrada tem SHA-256 `cd7734c98417fe084fb5d9c149c98d897db6b271cc3c32f999de4b4d855033cf`. A instalação física não foi verificada. Instalar como atualização, sem desinstalar ou limpar dados; em caso de assinatura divergente, interromper e investigar. Não substituir pela compilação de identidade antiga 0.3.0.

Começar no Samsung sem sessão BLE: conferir permissões, quatro áreas Graphite, catálogo, sequências simuladas e exportação sanitizada. Só depois seguir a validação física autorizada, com moto estacionada, autenticação manual, autorização por sessão e limites existentes. GPS isolado não prova que a moto esteja parada. Verificar prioridade da navegação, Interromper e preservação de histórico; não prometer restauração imediata do relógio. Sem GIFs, firmware, bytes arbitrários ou testes desconhecidos.

