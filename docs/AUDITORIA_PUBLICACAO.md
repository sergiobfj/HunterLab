# Auditoria antes da publicação

Data da revisão inicial: 2026-11-21. Este documento registra as verificações antes do primeiro push da base 0.2.1. A confirmação do conteúdo remoto é relatada separadamente na entrega.

## Estado inicial do Git

- Pasta do projeto confirmada pelo conteúdo dos módulos `protocol`, `core` e `app`.
- Não havia repositório Git local, arquivos rastreados, branch ou histórico de commits.
- Consulta ao repositório autorizado não retornou referências remotas nem commits iniciais para integrar.
- Repositório local inicializado em `main`; `origin` configurado como `https://github.com/sergiobfj/HunterLab.git`.
- Identidade Git existente preservada. Não foram alterados nome/email nem autoria existente; não foram adicionados trailers de ferramentas.

## Inspeção de segurança

- Inventário dos arquivos candidatos, incluindo arquivos ocultos, fontes, testes, documentação e configuração Gradle.
- Busca de conteúdo por chaves privadas, tokens conhecidos, atribuições de senha/API key, emails, caminhos pessoais e endereços Bluetooth completos.
- Revisão dos PINs de exemplo e dos MACs nos testes: dados sintéticos usados para validar codificação e anonimização; nenhum endereço completo foi encontrado no código de produção ou na documentação técnica pública inspecionada.
- `local.properties` contém um caminho local de SDK e permanece ignorado. Não foi encontrado keystore no conjunto candidato.
- Gitleaks **8.30.1**, binário conferido contra o checksum publicado: **nenhum segredo detectado** nas varreduras iniciais e na cópia exata dos 114 arquivos do índice da base isolada (antes de adicionar os dois workflows). Varrer também o histórico final antes do push.
- Gradle Wrapper JAR conferido contra o checksum oficial da versão 9.9.2: `621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8`.
- Inventário de binários: apenas o Gradle Wrapper no conjunto de fontes; APKs, caches e saídas de build ficam fora do Git.
- `.gitignore` ampliado para `.env*`, chaves, configuração de assinatura, credenciais de serviços, logs, dumps e pastas de relatórios privados. Regras verificadas com `git check-ignore`.

Não havia histórico local nem remoto a varrer no início. Depois do primeiro commit, executar também a varredura do histórico antes do primeiro push. Inspecionar o índice e a árvore exata do commit, não apenas o diretório de trabalho.

Relatórios brutos da auditoria e cópias de conferência permanecem em `.git/audit/`, fora do conteúdo publicável. Nenhuma evidência técnica privada foi excluída do projeto local. A varredura automatizada complementa a revisão manual e não prova ausência absoluta de dados sensíveis.

## Origem e licenças

Foram consultados código de protocolo, LICENSE e NOTICE do WazeTripper. O projeto local declara uma implementação Kotlin independente; não foram localizados os scripts de modificação do Waze ou código proprietário. A revisão não comprova autoria integral. Referências, dependências diretas e pendências estão em [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

A licença própria ainda depende de escolha do mantenedor. O inventário transitivo e os avisos do APK final devem ser concluídos antes de uma release pública.

## Validação da base isolada 0.2.1

- `gradlew.bat test :app:assembleDebug :app:lintDebug :app:assembleRelease :app:lintRelease`: **sucesso**, 260 testes, zero falhas/erros/ignorados; APKs debug e release sem assinatura compilados; Lint debug e release sem apontamentos.
- Workflows de CI e release: verificados com **actionlint 1.7.12**, sem erros.
- Documentação/configuração do integrador: `git diff --cached --check` sem erros.
- Nenhum aparelho disponível em `adb devices`; não houve validação visual em hardware ou reteste BLE nesta sessão.
- As 89 fontes/recursos do índice foram comparadas com a base original, permitindo apenas a normalização de finais de linha para LF definida em `.gitattributes`; nenhuma alteração funcional.
- Nenhuma chave definitiva criada, nenhum secret configurado e nenhuma release publicada.

## Isolamento da base para publicação

O Claude Code estava em execução durante a preparação e foram observadas alterações para a 0.2.2, dependência de serialização e novos arquivos de geometria. Para preservar o desenvolvimento local, foi criado um worktree separado na branch `codex/publication`.

As **89 fontes e recursos originais** foram recuperadas das cópias locais da auditoria e conferidas individualmente contra os hashes SHA-256 registrados antes das mudanças. A configuração Gradle foi restaurada no worktree ao conteúdo 0.2.1 inspecionado no início: versionCode 3, sem a dependência de serialização adicionada posteriormente. Não se alterou o código BLE nem se substituíram arquivos de desenvolvimento na pasta original.

As mudanças do MVP 0.2.2 ficam fora da primeira publicação. Antes de integrar esse MVP, combinar uma janela com a engenharia, revisar o diff completo, auditar os novos provedores/dependências e repetir os testes da árvore final.

Antes do primeiro push: validar testes/build/Lint na base isolada; revisar índice, árvore e histórico; criar commits Conventional Commits com a identidade existente; conferir se o remoto continua compatível; fazer push normal e comparar os SHAs local/remoto. Não usar force push.
