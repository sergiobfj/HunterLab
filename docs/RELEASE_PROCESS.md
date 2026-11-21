# Processo de release

Os workflows **ativos** são os já publicados no repositório: `.github/workflows/android-ci.yml` e
`android-release.yml`, descritos em `docs/DISTRIBUICAO.md`. Este documento cobre a parte que depende de você:
a chave de assinatura e a troca do APK de debug pelo de release. Não há release definitiva publicada por este procedimento; nenhuma chave estável foi criada.

## Situação da assinatura

| Item | Hoje |
|---|---|
| APK no celular | `dev.hunterlab.app.debug`, assinado com a chave de **debug desta máquina** |
| Certificado de debug (SHA-256) | `CD:77:34:C9:84:17:FE:08:4F:B5:D9:C1:49:C9:8D:89:7D:B6:B2:71:CC:3C:32:F9:99:DE:4B:4D:85:50:33:CF` |
| APK de CI | Debug assinado com uma chave **aleatória do runner** → **não atualiza** o app instalado |
| Release | `dev.hunterlab.app` (sem sufixo), assinado no workflow com a chave estável dos *Secrets* |

**Atualização local 0.2.1 → 0.3.0:** use o APK compilado nesta máquina somente após comparar o certificado, `applicationId` e `versionCode`. Instale com `adb install -r <apk>`; não desinstale e não use `-d`. Se houver `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, pare e confira o certificado da instalação existente. O APK da CI tem assinatura própria do runner e não deve ser usado como atualização da instalação local sem essa conferência.

**Debug × release:** os identificadores atuais são diferentes, portanto uma futura release pode instalar ao lado do debug, preservando-o. Dados privados e a chave não são transferidos entre os dois apps. A escolha de assinatura estável ainda depende do mantenedor; não é necessário desinstalar o debug para testar o MVP local. Não deixe os dois apps disputarem uma sessão do Tripper.

## Criar a chave estável (uma vez, na sua máquina)

```bash
keytool -J-Duser.language=en -genkeypair -v -keystore hunterlab-release.jks -alias hunterlab \
  -keyalg RSA -keysize 4096 -validity 36500
```
- Guarde **fora** do projeto. Backup em dois lugares offline (ex.: pendrive + cofre de senhas com o arquivo anexado)
  e as senhas no gerenciador de senhas. **Perder a chave = nunca mais atualizar o app** (só desinstalando).
- Nunca commite o `.jks` nem as senhas (o `.gitignore` já bloqueia `*.jks`/`*.keystore`).

## Cadastrar no GitHub (Settings → Secrets and variables → Actions)

| Secret | Conteúdo |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 hunterlab-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_PASSWORD` | senhas da keystore / da chave |
| `ANDROID_KEY_ALIAS` | `hunterlab` |

## Publicar uma versão (só com sua aprovação)
1. `versionName` no `app/build.gradle.kts` = versão desejada; notas em `docs/releases/vX.Y.Z.md`.
2. Tag `vX.Y.Z` no commit auditado de `main`.
3. *Actions → android-release → Run workflow* com a tag. O resultado é uma release **rascunho** com o APK assinado e
   `SHA256SUMS.txt`; você revisa e publica.

## Conferir no celular
- SHA-256 do APK baixado igual ao do `SHA256SUMS.txt`.
- Android 12 (API 31) ou superior. Instalar pelo app *Arquivos* permitindo a fonte.

## Alternativa preparada e não integrada
Um segundo conjunto de workflows (`ci.yml`/`release.yml`, secrets `HUNTERLAB_*`, assinatura opcional pelo Gradle e
nome `HunterLab-vX.Y.Z.apk`) foi validado localmente pelo Agente D, mas **não foi colocado no projeto** para não
duplicar os workflows ativos nem mudar o nome do APK que o `android-release.yml` espera. Está guardado fora do
projeto (scratchpad da sessão) e pode ser descartado.
