# Notas de versão

Antes de executar o workflow manual de release, adicionar `vX.Y.Z.md` ao mesmo commit da versão a ser marcada. O nome deve corresponder à tag e ao `versionName` do APK. Não registrar como validado algo que não foi testado.

Modelo de conteúdo:

```markdown
# Hunter Lab vX.Y.Z

## Alterações

- Descrever o comportamento entregue e suas limitações.

## Validação

- Testes automatizados, build e Lint: resultado da versão.
- Hardware: informar aparelho, moto e verificações realmente realizadas, ou indicar pendência.
- Atualização de versão anterior: informar resultado real, ou indicar que não foi testada.

## Limitações conhecidas

- Registrar pendências relevantes para quem instala.
```

O workflow acrescenta instruções de instalação, identifica o commit e anexa APK e SHA-256. A saída é um rascunho que depende de revisão e teste do mantenedor antes da publicação.
