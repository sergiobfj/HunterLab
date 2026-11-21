# Integração 0.3.0

## Origem e preservação

Base publicada: `8af0688` (0.2.1). A engenharia encerrou as escritas conforme `INTEGRACAO.md`: 28 arquivos rastreados modificados e 75 novos, sem staging. A integração parte desse conteúdo, copiado para a worktree `HunterLab-mvp-030`, branch `feat/mvp-030`. O original foi preservado integralmente com manifesto SHA-256 e cópia privada fora da árvore pública. A worktree de publicação permaneceu intacta.

Não houve alteração de `protocol/` nem de `app/.../ble/`, transporte GATT ou fluxo original de handshake. As alterações entregues em `core/link` preservam autorização, confirmação visual, whitelist e fila; acrescentam propósito de sessão, transmissão documentada de navegação e retenção dos contadores da sessão encerrada. A sessão do laboratório mantém seu limite anterior. Os testes existentes de transmissão e os novos de stream cobrem essas barreiras; não equivalem a validação física.

## Comparação das duas interfaces

| Critério | Branch UI (`f210ef9` + correção do guard) | UI entregue com a 0.3.0 |
|---|---|---|
| Contratos | Snapshot de `NavContracts` igual ao entregue; bindings opcionais | Bindings reais obrigatórios desde a Activity |
| Navegação real | Adaptável; fallback honesto para a base 0.2.1, sem provedores novos | Controlador, GPS, rotas, radares e serviço já conectados |
| Visual | Grafite/âmbar, tipografia refinada, 12 renders Layoutlib incluindo larguras e fonte ampliada | Mesma direção visual, quatro abas e seções simples; requer revisão no Samsung |
| Segurança de uso | Laboratório avançado e estados de erro/vazio explícitos | Encerrar/Interromper globais, inclusive nas ferramentas; tratamento do teclado já presente |
| Dependências de adaptação | `PublishedUiState`, ações locais sem backend e fallback necessários para a base antiga | Sem esses adaptadores; usa diretamente os estados atuais |
| Integração | Exigiria trocar a raiz e religar os provedores | Preserva o conjunto funcional entregue, com menos retrabalho |

**Decisão:** manter a UI da 0.3.0. Nenhuma tela inteira foi sobrescrita por cherry-pick. Da alternativa aproveitou-se somente a regra de apresentação que oculta manobras e estimativas antigas durante recálculo/erro, ampliada para GPS indisponível ou impreciso, com testes. A alternativa completa, seus oito testes e renders continuam na branch própria; não foram descartados nem usados como evidência visual da UI consolidada.

O `SafetyGuardTest` da alternativa passou a reconhecer exatamente os três arquivos auditados que usam `NavActions`. Mantidas todas as barreiras e adicionados contraexemplos no `UiBoundaryTest`; 268/268 testes, build debug e Lint debug aprovados. Commit `68d5bd6`; CI remota [44961243812](https://github.com/sergiobfj/HunterLab/actions/runs/44961243812) aprovada.

## Ajustes da revisão de integração

- Falha de criação/promoção ou encerramento do serviço foreground encerra navegação, busca/conexão do Tripper e GPS, em vez de apenas exibir mensagem.
- Encerrar a navegação, desativar radar ou trocar de rota invalida downloads pendentes de radares e callbacks antigos. Um HTTP já em curso pode terminar dentro dos timeouts e alimentar o cache; não inicia tiles seguintes da rota cancelada. Dados frescos continuam reutilizados. Política de validade e limites da engenharia preservada.
- Backup em nuvem e transferência entre aparelhos excluem explicitamente preferências, arquivos e demais domínios privados, além de `allowBackup=false`. Isso protege chave, destinos e cache nas modalidades declaradas; não torna o armazenamento criptografado nem protege contra root/depuração autorizada.
- A tela Início informa perfil de carro e ausência de validação para motos. Configurações informa o armazenamento privado sem criptografia adicional.
- Reutilizada apenas a correção de CI `5c77d6d`: runtime Node 24 da action Android e permissão executável do wrapper. O workflow existente passa a executar também em `feat/mvp-*`; assinatura e release mantidas.

## Segurança, privacidade e licenças

Gitleaks sobre conteúdo e histórico, inspeção de arquivos candidatos, endereços, credenciais, logs e licenças: nenhum segredo real identificado. `local.properties`, keystores, `.env*`, relatórios físicos e logs continuam ignorados. Endereços completos encontrados são fixtures sintéticas de testes ou exemplos do mascarador; o fingerprint público do certificado não é endereço Bluetooth. Não foram incluídos dumps, relatórios físicos ou dados pessoais de localização.

A chave GraphHopper vem das preferências privadas, não é embutida em código ou build. Não é registrada nas URLs/erros; o cliente aceita somente HTTPS, hosts explícitos e não segue redirecionamentos. O campo é mascarado e não salvo no estado persistente da UI. Em APK debug, alguém com depuração autorizada pode ler o sandbox; em aparelho com root a proteção também é insuficiente. Remover a chave em Configurações limpa a preferência, sem promessa de apagamento forense. Não foi adicionada telemetria.

Origem e destino são enviados à GraphHopper, caixas de tiles ao Overpass e a busca de endereços ao Geocoder do sistema. Destinos recentes (até cinco) e tiles persistem localmente. A navegação não grava um histórico contínuo de posições. Não compartilhar screenshots/logs com destinos privados. O perfil é de **carro**, sem garantia de adequação ou acesso legal para motos. OSM não garante cobertura completa de fiscalização.

A dependência nova `kotlinx-serialization-json:1.9.0` declara Apache-2.0 no POM local; nenhuma biblioteca vendorizada ou SDK proprietário novo. Avisos WazeTripper preservados; atribuição OSM/ODbL documentada. Não foi atribuída licença própria sem decisão do mantenedor.

## Revisão Android e limites da evidência

Permissões BLE Android 12+, localização precisa/aproximada, notificações API 33+, tipos foreground `location|connectedDevice` e wake lock declarados; serviço não exportado. Não há permissão de localização em segundo plano. O serviço nasce de ação com app visível, usa os tipos permitidos, notificação com Encerrar, `START_NOT_STICKY`, libera wake lock e recursos ao parar/remover tarefa. O wake lock é não cumulativo e limitado a seis horas. A sessão de laboratório continua encerrando ao sair da Activity; a de navegação pertence ao serviço.

Sem dispositivo ADB ou emulador acessível nesta revisão. Ainda exigem teste no Samsung: tela bloqueada/Doze, precisão concedida/aproximada, revogação de permissões, notificação negada, troca GPS/Bluetooth, remoção dos recentes, encerramento, limites de bateria e retomada do app oficial. Não foi feito teste físico no Tripper 0.3.0. O polimento fica para depois desse primeiro teste.

As funções `@Preview` de `ui/preview/SampleStates.kt` permitem inspecionar as quatro telas, PIN, radar, estado vazio e fonte ampliada no Android Studio. São dados fictícios e não comprovação de navegação ou hardware.

## Validação consolidada

Validação local sobre o conteúdo consolidado: **421/421 testes**, protocol 115, core 287 e app 19, zero falhas/erros/ignorados. Os 416 testes entregues pela engenharia foram preservados e há cinco regressões adicionais (três de cache/cancelamento e duas de apresentação). As três tarefas de teste foram executadas novamente com `--rerun`, sem restaurar os resultados do cache. `:app:lintDebug`: zero achados. `:app:assembleDebug`: sucesso. Actionlint: sem erros. Compilador Kotlin mantém três avisos não bloqueantes entregues pela engenharia: duas APIs de máscara de senha obsoletas e um `!!` desnecessário; não são achados de Lint. Nenhuma mudança de UI para polimento foi feita.

APK local: `dev.hunterlab.app.debug`, `0.3.0-debug`, versionCode **5**, assinatura v2 verificada. Certificado SHA-256 `cd7734c98417fe084fb5d9c149c98d897db6b271cc3c32f999de4b4d855033cf`, igual ao APK local de referência 0.2.1. SHA-256 do APK consolidado: `eacf091810b395fe0a7ce481a376785cfc8dcfaef6c65f28d3c75ed9dd6a3aef`. Isso confirma pacote/assinatura contra o arquivo anterior, não uma instalação real no Samsung (ADB sem aparelhos). Instrução: `adb install -r build/distributions/HunterLab-0.3.0-debug-local.apk`, sem desinstalar. Parar em caso de incompatibilidade. O arquivo e checksum estão disponíveis localmente nessa pasta ignorada pelo Git; o artefato remoto de CI tem outra assinatura.

A CI da branch e da main e os SHAs de publicação devem ser conferidos no GitHub após o push; esta documentação não antecipa sucesso remoto. O APK local mantém a assinatura debug existente; o artefato de CI não deve ser tratado como atualização compatível do Samsung sem comparar certificados. Nenhuma release definitiva ou chave estável foi criada.

Ajuste de higiene: removido espaço final de uma fixture JSON propositalmente truncada; o conteúdo continua malformado e os testes de rejeição permanecem. O core continua congelando o último progresso quando perde GPS (comportamento já coberto nos testes da engenharia); o app oculta a instrução antiga, mas o tratamento visual dessa perda no Pod requer acompanhamento no teste físico e eventual ajuste coordenado com a engenharia.
