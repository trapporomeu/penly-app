package com.trapporomeu.penlyapp.model

import android.graphics.Path
import android.text.StaticLayout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Tamanho padrão de uma página (unidades de página; A4 em retrato). */
const val PAGE_W = 1000f
const val PAGE_H = 1414f

@Serializable
enum class PageTemplate { BLANK, LINED, GRID, DOTS, TODO, WEEKLY, COVER }

@Serializable
enum class ShapeType { RECT, OVAL, LINE, ARROW }

@Serializable
enum class PenType { PENCIL, FOUNTAIN, BRUSH }

/** Ponto de um traço. [p] é a pressão da caneta (0..1); dedo usa um valor fixo. */
@Serializable
data class Pt(val x: Float, val y: Float, val p: Float = 1f)

@Serializable
sealed class Item {
    abstract val id: String
}

@Serializable
@SerialName("stroke")
data class StrokeItem(
    override val id: String = newId(),
    val points: List<Pt>,
    val color: Int,
    val width: Float,
    val highlighter: Boolean = false,
    val pen: PenType = PenType.PENCIL,
) : Item() {
    // Cache do Path desenhado. Como os itens são imutáveis, cada cópia recria o seu.
    @Transient
    var cachedPath: Path? = null
}

@Serializable
@SerialName("text")
data class TextItem(
    override val id: String = newId(),
    val x: Float,
    val y: Float,
    val text: String,
    val size: Float,
    val color: Int,
    val font: String? = null,
    val bold: Boolean = false,
) : Item() {
    @Transient
    var cachedLayout: StaticLayout? = null
}

@Serializable
@SerialName("shape")
data class ShapeItem(
    override val id: String = newId(),
    val type: ShapeType,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val color: Int,
    val width: Float,
) : Item()

@Serializable
@SerialName("image")
data class ImageItem(
    override val id: String = newId(),
    /** Nome do arquivo dentro da pasta assets do caderno. */
    val file: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
) : Item()

@Serializable
data class Page(
    val id: String = newId(),
    val width: Float = PAGE_W,
    val height: Float = PAGE_H,
    val template: PageTemplate = PageTemplate.BLANK,
    /** Imagem de fundo (ex.: página de PDF importado), dentro da pasta assets do caderno. */
    val background: String? = null,
    val bgColor: Int = 0xFFFFFFFF.toInt(),
    /** Título mostrado na etiqueta da capa (template COVER). */
    val title: String? = null,
    val items: List<Item> = emptyList(),
)

@Serializable
data class Notebook(
    val id: String = newId(),
    val name: String,
    val pages: List<Page>,
    val lastPage: Int = 0,
)

@Serializable
data class NotebookMeta(
    val id: String,
    val name: String,
    val folderId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val pageCount: Int = 0,
)

@Serializable
data class Folder(
    val id: String = newId(),
    val name: String,
    val parentId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class Library(
    val folders: List<Folder> = emptyList(),
    val notebooks: List<NotebookMeta> = emptyList(),
)

@Serializable
data class BackupInfo(val notebookId: String, val file: String, val time: Long)
