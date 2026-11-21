# Compilação contínua e distribuição

## Situação

A base física informada é a 0.2.1, assinada com debug local. Nenhuma chave de release foi criada ou enviada ao Git. Nenhum APK deste trabalho foi publicado em GitHub Releases.

## CI

O workflow `android-ci.yml` verifica testes, Lint e APK de debug, com Java 25, SDK 37.2 e Build-Tools 36.0.0. O cache Gradle evita resolver e baixar novamente tudo em cada execução. O APK de CI é um artefato de desenvolvimento, com checksum SHA-256; não é uma release com certificado estável.

Os workflows usam ações fixadas por SHA e `GITHUB_TOKEN` somente de leitura no CI. Pull requests não recebem segredos de assinatura. Não usar `pull_request_target` para compilar código de contribuidores.

## Preparação da assinatura estável

1. O mantenedor escolhe o identificador de pacote de distribuição e aprova a criação ou reutilização de uma chave estável. Não gerar uma chave definitiva automaticamente.
2. Guardar keystore, alias e senhas em local privado, com backup recuperável e acesso restrito. A perda da chave impede atualizações normais do mesmo pacote.
3. Configurar um environment `release` no GitHub e, quando disponível no plano, exigir aprovação de um mantenedor para o uso dos segredos.
4. Cadastrar os secrets do environment: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD`. Base64 é transporte, não criptografia.
5. Cadastrar a variável `ANDROID_RELEASE_CERT_SHA256` com a impressão SHA-256 do certificado aprovado (64 caracteres hexadecimais, sem separadores).
6. Só após validar a configuração, definir a variável do repositório `RELEASE_SIGNING_READY=true`.

Nenhuma dessas configurações é criada automaticamente por este trabalho. Não colar segredos em arquivos versionados, issues ou logs.

## Workflow de release

`android-release.yml` é manual e cria apenas um **rascunho de release** após as verificações. Não dispara em push de tags. Exige a variável de habilitação, todos os secrets, certificado esperado e uma tag `vX.Y.Z` já existente em um commit de `main`.

O workflow verifica versão do APK contra a tag, compila a variante release sem chave no Gradle, alinha o APK, assina com `apksigner`, verifica a assinatura e compara o certificado aprovado. O keystore fica no diretório temporário do runner e é removido ao final. Apenas o job de release recebe `contents: write`.

Cada rascunho inclui APK assinado, `SHA256SUMS.txt`, versão, changelog da versão em `docs/releases/vX.Y.Z.md` e instruções de instalação. O mantenedor revisa os arquivos e publica o rascunho manualmente após testar instalação e atualização em aparelho.

## Migração do debug

O debug usa `dev.hunterlab.app.debug`; a variante release atual declara `dev.hunterlab.app`. Se esses IDs forem mantidos, os aplicativos são instalações separadas, e a release não atualiza o debug.

Se a distribuição adotar o mesmo ID de um APK já instalado, mas outro certificado, a atualização normal será recusada. Nesse caso poderá ser necessária uma desinstalação única, com perda dos dados locais desse aplicativo. Não anunciar compatibilidade de atualização sem testar pacote, certificado e versionCode reais.

## Critérios antes da publicação de uma release

- Testes, build, Lint e auditoria do commit/tag concluídos.
- Janela de integração encerrada, sem arquivos parcialmente escritos.
- Licença própria escolhida e inventário/avisos de dependências transitivas revisados.
- Instalação e atualização verificadas no Samsung SM-A155M; comunicação com o Tripper retestada quando pertinente.
- Changelog contém somente comportamentos implementados e validados, com limitações explícitas.

Documentação oficial: [assinatura Android](https://developer.android.com/studio/publish/app-signing), [apksigner](https://developer.android.com/tools/apksigner) e [secrets do GitHub Actions](https://docs.github.com/en/actions/security-for-github-actions/security-guides/using-secrets-in-github-actions).
