# Penly App

App Android de cadernos e planners digitais para escrever à mão no tablet, inspirado no Penly.
Feito em Kotlin + Jetpack Compose.

## Instalar no tablet

1. No tablet, abra **https://github.com/trapporomeu/penly-app/releases/tag/apk-latest**
2. Toque em **penly-app.apk** para baixar.
3. Abra o arquivo baixado. Se o Android pedir, permita "instalar apps desta fonte".

Cada vez que o código é atualizado, o GitHub Actions gera um APK novo nesse mesmo link.
Ele instala por cima do anterior sem apagar seus cadernos.

O app se chama **Penly App** e fica separado do Penly original (pode ter os dois instalados).

## O que já funciona

**Tela inicial (Minhas Notas)**
- Grade de cadernos com miniatura, nome e data; pastas (com subpastas)
- Menu de cada caderno: abrir, renomear, duplicar, mover para pasta, exportar PDF, exportar `.penlyapp`, excluir
- Botão **+**: novo caderno (modelo de página, retrato/paisagem, nº de páginas, capa colorida com nome), nova pasta, **importar PDF** (ex.: planners), importar `.penlyapp`
- Ordenar (modificado, nome, criação) e buscar
- Menu lateral: Minhas Notas, Backups, Lixeira

**Editor**
- Barra igual à do Penly: voltar, páginas, adicionar página | caneta, marca-texto, borracha, texto, formas, imagem, stickers, laço | página anterior, `1 / 12`, próxima | travar zoom, desfazer, refazer, opções
- Caneta e marca-texto com cores e espessuras; borracha de traço
- Texto com fontes (inclusive fontes `.ttf`/`.otf` importadas), tamanho, negrito e cor
- Formas: retângulo, oval, linha, seta
- Imagens da galeria e stickers (emojis + suas próprias imagens)
- Laço: selecionar, mover, redimensionar, duplicar, mudar cor, excluir
- Zoom e movimento com dois dedos; deslizar para trocar de página
- **Smart Pen automático**: o dedo desenha até você usar a caneta; depois disso o dedo só rola/dá zoom
- Visão geral das páginas: ir, inserir, duplicar, mover, excluir
- Modelos de página: em branco, pautado, quadriculado, pontilhado, tarefas, semanal, capa
- Salvamento automático, desfazer/refazer, backups automáticos

**Configurações**: tamanho da interface, claro/escuro, smart pen, direção de rolagem, barras do sistema,
posição do texto, fontes, qualidade de exportação, backups automáticos, cache, refazer planners iniciais.

## Ainda não tem (próximos passos)

- Escrita à mão → texto
- Links clicáveis dentro de PDFs (índices de planners)
- Sincronização na nuvem
- Rotação de itens, caneta com pressão

## Compilar

```bash
./gradlew assembleDebug      # APK de teste
./gradlew assembleRelease    # APK otimizado (assinado com a chave do projeto)
```

A chave `app/penly-app.keystore` está no repositório de propósito, para que todo APK gerado
(no seu computador ou no GitHub Actions) tenha a mesma assinatura e possa ser atualizado por cima.
Ela serve só para instalar o app no seu próprio aparelho, não para publicar na Play Store.
