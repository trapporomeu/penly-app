package com.trapporomeu.penlyapp.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import com.trapporomeu.penlyapp.model.ImageItem
import com.trapporomeu.penlyapp.model.Item
import com.trapporomeu.penlyapp.model.Page
import com.trapporomeu.penlyapp.model.PageTemplate
import com.trapporomeu.penlyapp.model.Pt
import com.trapporomeu.penlyapp.model.ShapeItem
import com.trapporomeu.penlyapp.model.ShapeType
import com.trapporomeu.penlyapp.model.StrokeItem
import com.trapporomeu.penlyapp.model.TextItem
import java.io.File
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Cache de imagens (fundos de PDF, fotos, stickers). */
object BitmapCache {
    private var cache = newCache(128)

    private fun newCache(mb: Int) = object : LruCache<String, Bitmap>(mb * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun configure(mb: Int) {
        val maxMb = (Runtime.getRuntime().maxMemory() / 1024 / 1024 / 3).toInt()
        cache = newCache(min(mb, maxMb).coerceAtLeast(32))
    }

    fun get(file: File, maxDim: Int = 2048): Bitmap? {
        val key = file.absolutePath
        cache.get(key)?.let { return it }
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(key, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
        val bmp = BitmapFactory.decodeFile(key, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        cache.put(key, bmp)
        return bmp
    }

    fun clear() = cache.evictAll()
}

/** Fontes disponíveis para o texto: as do sistema + as importadas pelo usuário. */
object FontRegistry {
    val builtIn = linkedMapOf(
        "Padrão" to Typeface.DEFAULT,
        "Serifa" to Typeface.SERIF,
        "Mono" to Typeface.MONOSPACE,
        "Manuscrita" to Typeface.create("casual", Typeface.NORMAL),
        "Cursiva" to Typeface.create("cursive", Typeface.NORMAL),
    )
    private val loaded = HashMap<String, Typeface>()
    private var dir: File? = null

    fun init(fontsDir: File) {
        dir = fontsDir
    }

    fun userFonts(): List<String> = dir?.listFiles()?.map { it.name }?.sorted() ?: emptyList()

    fun allNames(): List<String> = builtIn.keys.toList() + userFonts()

    fun typeface(name: String?, bold: Boolean): Typeface {
        val base = when {
            name == null -> Typeface.DEFAULT
            builtIn.containsKey(name) -> builtIn[name]!!
            else -> synchronized(loaded) {
                loaded.getOrPut(name) {
                    try {
                        Typeface.createFromFile(File(dir, name))
                    } catch (e: Exception) {
                        Typeface.DEFAULT
                    }
                }
            }
        }
        return if (bold) Typeface.create(base, Typeface.BOLD) else base
    }
}

data class RenderOptions(
    val highlighterBelow: Boolean = true,
    val hiddenIds: Set<String> = emptySet(),
)

object PageRenderer {
    private class Paints {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    }

    // Um conjunto de Paints por thread: as miniaturas são desenhadas fora da thread de UI.
    private val paints = ThreadLocal.withInitial { Paints() }
    private val fill get() = paints.get()!!.fill
    private val line get() = paints.get()!!.line
    private val strokePaint get() = paints.get()!!.strokePaint
    private val bmpPaint get() = paints.get()!!.bmpPaint
    private val textPaint get() = paints.get()!!.textPaint

    const val HIGHLIGHTER_ALPHA = 0x66

    fun drawPage(canvas: Canvas, page: Page, assets: File, opts: RenderOptions = RenderOptions()) {
        canvas.save()
        canvas.clipRect(0f, 0f, page.width, page.height)
        drawBackground(canvas, page, assets)
        val items = page.items.filter { it.id !in opts.hiddenIds }
        if (opts.highlighterBelow) {
            items.filter { it is StrokeItem && it.highlighter }.forEach { drawItem(canvas, it, assets) }
            items.filterNot { it is StrokeItem && it.highlighter }.forEach { drawItem(canvas, it, assets) }
        } else {
            items.forEach { drawItem(canvas, it, assets) }
        }
        canvas.restore()
    }

    fun renderBitmap(context: Context, page: Page, assets: File, scale: Float): Bitmap {
        val w = ceil(page.width * scale).toInt().coerceAtLeast(1)
        val h = ceil(page.height * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        drawPage(c, page, assets)
        return bmp
    }

    // ---------- Fundo / templates ----------

    fun drawBackground(c: Canvas, page: Page, assets: File) {
        val w = page.width
        val h = page.height
        fill.color = page.bgColor
        c.drawRect(0f, 0f, w, h, fill)
        page.background?.let { name ->
            BitmapCache.get(File(assets, name))?.let { bmp ->
                c.drawBitmap(bmp, null, RectF(0f, 0f, w, h), bmpPaint)
            }
        }
        when (page.template) {
            PageTemplate.BLANK -> Unit
            PageTemplate.LINED -> {
                line.color = 0xFFC9D6E3.toInt(); line.strokeWidth = 2f
                var y = 150f
                while (y < h - 50f) { c.drawLine(0f, y, w, y, line); y += 50f }
                line.color = 0xFFF2B8B8.toInt()
                c.drawLine(110f, 0f, 110f, h, line)
            }
            PageTemplate.GRID -> {
                line.color = 0xFFDDE4EA.toInt(); line.strokeWidth = 1.5f
                var x = 40f
                while (x < w) { c.drawLine(x, 0f, x, h, line); x += 40f }
                var y = 40f
                while (y < h) { c.drawLine(0f, y, w, y, line); y += 40f }
            }
            PageTemplate.DOTS -> {
                fill.color = 0xFFB0BEC5.toInt()
                var y = 40f
                while (y < h) {
                    var x = 40f
                    while (x < w) { c.drawCircle(x, y, 2.5f, fill); x += 40f }
                    y += 40f
                }
            }
            PageTemplate.TODO -> drawTodo(c, w, h)
            PageTemplate.WEEKLY -> drawWeekly(c, w, h)
            PageTemplate.COVER -> drawCover(c, page)
        }
    }

    private fun label(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = true, center: Boolean = false) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textPaint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(text, x, y, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawTodo(c: Canvas, w: Float, h: Float) {
        label(c, "LISTA DE TAREFAS", 80f, 110f, 34f, 0xFF455A64.toInt())
        line.color = 0xFFC9D6E3.toInt(); line.strokeWidth = 2f
        val box = Paint(line).apply { color = 0xFF90A4AE.toInt(); strokeWidth = 2.5f }
        var y = 180f
        while (y < h - 60f) {
            c.drawRoundRect(RectF(80f, y - 30f, 114f, y + 4f), 6f, 6f, box)
            c.drawLine(140f, y + 4f, w - 80f, y + 4f, line)
            y += 70f
        }
    }

    private fun drawWeekly(c: Canvas, w: Float, h: Float) {
        val days = listOf("SEGUNDA", "TERÇA", "QUARTA", "QUINTA", "SEXTA", "SÁBADO", "DOMINGO", "NOTAS")
        val accent = listOf(0xFFF8BBD0, 0xFFFFE0B2, 0xFFFFF9C4, 0xFFC8E6C9, 0xFFB3E5FC, 0xFFD1C4E9, 0xFFF0F4C3, 0xFFECEFF1)
        label(c, "SEMANA", 50f, 70f, 34f, 0xFF455A64.toInt())
        val top = 100f
        val m = 50f
        val gap = 16f
        val cols = 4
        val bw = (w - 2 * m - gap * (cols - 1)) / cols
        val bh = (h - top - m - gap) / 2
        for (i in days.indices) {
            val col = i % cols
            val row = i / cols
            val x = m + col * (bw + gap)
            val y = top + row * (bh + gap)
            fill.color = accent[i].toInt()
            c.drawRoundRect(RectF(x, y, x + bw, y + 48f), 10f, 10f, fill)
            label(c, days[i], x + 14f, y + 33f, 22f, 0xFF37474F.toInt())
            line.color = 0xFFCFD8DC.toInt(); line.strokeWidth = 2f
            c.drawRoundRect(RectF(x, y, x + bw, y + bh), 10f, 10f, line)
            line.color = 0xFFE3E8EC.toInt(); line.strokeWidth = 1.5f
            var ly = y + 48f + 40f
            while (ly < y + bh - 10f) { c.drawLine(x + 12f, ly, x + bw - 12f, ly, line); ly += 40f }
        }
    }

    private fun darker(color: Int, f: Float) = Color.argb(
        Color.alpha(color), (Color.red(color) * f).toInt(), (Color.green(color) * f).toInt(), (Color.blue(color) * f).toInt(),
    )

    private fun drawCover(c: Canvas, page: Page) {
        val w = page.width
        val h = page.height
        fill.color = darker(page.bgColor, 0.92f)
        c.drawRect(0f, 0f, w * 0.195f, h, fill)
        val title = page.title ?: return
        val rect = RectF(w * 0.316f, h * 0.258f, w * 0.85f, h * 0.35f)
        fill.color = 0xFFFAFDF7.toInt()
        c.drawRect(rect, fill)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = Color.BLACK
        textPaint.textAlign = Paint.Align.CENTER
        var size = w * 0.075f
        textPaint.textSize = size
        val maxW = rect.width() * 0.88f
        val tw = textPaint.measureText(title)
        if (tw > maxW) { size *= maxW / tw; textPaint.textSize = size }
        val fm = textPaint.fontMetrics
        c.drawText(title, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    // ---------- Itens ----------

    fun drawItem(c: Canvas, item: Item, assets: File) {
        when (item) {
            is StrokeItem -> drawStroke(c, item)
            is TextItem -> drawText(c, item)
            is ShapeItem -> drawShape(c, item.type, item.x1, item.y1, item.x2, item.y2, item.color, item.width)
            is ImageItem -> BitmapCache.get(File(assets, item.file))?.let {
                c.drawBitmap(it, null, RectF(item.x, item.y, item.x + item.w, item.y + item.h), bmpPaint)
            }
        }
    }

    fun buildPath(points: List<Pt>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points[0].x, points[0].y)
        if (points.size == 1) {
            path.lineTo(points[0].x + 0.1f, points[0].y)
            return path
        }
        for (i in 1 until points.size - 1) {
            val p = points[i]
            val n = points[i + 1]
            path.quadTo(p.x, p.y, (p.x + n.x) / 2, (p.y + n.y) / 2)
        }
        path.lineTo(points.last().x, points.last().y)
        return path
    }

    fun drawStroke(c: Canvas, s: StrokeItem) {
        val path = s.cachedPath ?: buildPath(s.points).also { s.cachedPath = it }
        drawLivePath(c, path, s.color, s.width, s.highlighter)
    }

    fun drawLivePath(c: Canvas, path: Path, color: Int, width: Float, highlighter: Boolean) {
        strokePaint.color = if (highlighter) (color and 0x00FFFFFF) or (HIGHLIGHTER_ALPHA shl 24) else color
        strokePaint.strokeWidth = width
        strokePaint.strokeCap = if (highlighter) Paint.Cap.SQUARE else Paint.Cap.ROUND
        c.drawPath(path, strokePaint)
    }

    fun drawShape(c: Canvas, type: ShapeType, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = width
        strokePaint.strokeCap = Paint.Cap.ROUND
        val r = RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
        when (type) {
            ShapeType.RECT -> c.drawRect(r, strokePaint)
            ShapeType.OVAL -> c.drawOval(r, strokePaint)
            ShapeType.LINE -> c.drawLine(x1, y1, x2, y2, strokePaint)
            ShapeType.ARROW -> {
                c.drawLine(x1, y1, x2, y2, strokePaint)
                val a = atan2(y2 - y1, x2 - x1)
                val len = max(24f, width * 5)
                val spread = Math.toRadians(28.0)
                c.drawLine(x2, y2, x2 - len * cos(a - spread).toFloat(), y2 - len * sin(a - spread).toFloat(), strokePaint)
                c.drawLine(x2, y2, x2 - len * cos(a + spread).toFloat(), y2 - len * sin(a + spread).toFloat(), strokePaint)
            }
        }
    }

    fun textLayout(t: TextItem): StaticLayout {
        t.cachedLayout?.let { return it }
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = t.size
            color = t.color
            typeface = FontRegistry.typeface(t.font, t.bold)
        }
        val width = t.text.split('\n').maxOf { ceil(Layout.getDesiredWidth(it, tp)).toInt() }.coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(t.text, 0, t.text.length, tp, width + 2)
            .setIncludePad(false)
            .build()
        t.cachedLayout = layout
        return layout
    }

    fun drawText(c: Canvas, t: TextItem) {
        val layout = textLayout(t)
        c.save()
        c.translate(t.x, t.y)
        layout.draw(c)
        c.restore()
    }

    // ---------- Geometria ----------

    fun bounds(item: Item): RectF = when (item) {
        is StrokeItem -> {
            var l = Float.MAX_VALUE; var tp = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            item.points.forEach { l = min(l, it.x); tp = min(tp, it.y); r = max(r, it.x); b = max(b, it.y) }
            val pad = item.width / 2
            RectF(l - pad, tp - pad, r + pad, b + pad)
        }
        is TextItem -> {
            val lay = textLayout(item)
            RectF(item.x, item.y, item.x + lay.width, item.y + lay.height)
        }
        is ShapeItem -> {
            val pad = item.width / 2
            RectF(min(item.x1, item.x2) - pad, min(item.y1, item.y2) - pad, max(item.x1, item.x2) + pad, max(item.y1, item.y2) + pad)
        }
        is ImageItem -> RectF(item.x, item.y, item.x + item.w, item.y + item.h)
    }

    private fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** O item passa perto do ponto? Usado pela borracha e pelo toque para selecionar. */
    fun hits(item: Item, x: Float, y: Float, radius: Float): Boolean = when (item) {
        is StrokeItem -> {
            val r = radius + item.width / 2
            val pts = item.points
            if (pts.size == 1) hypot(x - pts[0].x, y - pts[0].y) <= r
            else (1 until pts.size).any { distToSegment(x, y, pts[it - 1].x, pts[it - 1].y, pts[it].x, pts[it].y) <= r }
        }
        is ShapeItem -> when (item.type) {
            ShapeType.LINE, ShapeType.ARROW -> distToSegment(x, y, item.x1, item.y1, item.x2, item.y2) <= radius + item.width / 2
            else -> {
                val b = bounds(item)
                val inflated = RectF(b.left - radius, b.top - radius, b.right + radius, b.bottom + radius)
                val inner = RectF(b.left + radius + item.width, b.top + radius + item.width, b.right - radius - item.width, b.bottom - radius - item.width)
                inflated.contains(x, y) && !(inner.width() > 0 && inner.height() > 0 && inner.contains(x, y))
            }
        }
        else -> {
            val b = bounds(item)
            RectF(b.left - radius, b.top - radius, b.right + radius, b.bottom + radius).contains(x, y)
        }
    }

    fun pointInPolygon(x: Float, y: Float, poly: List<Pt>): Boolean {
        var inside = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val pi = poly[i]
            val pj = poly[j]
            if ((pi.y > y) != (pj.y > y) && x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x) inside = !inside
            j = i
        }
        return inside
    }

    fun insideLasso(item: Item, poly: List<Pt>): Boolean = when (item) {
        is StrokeItem -> {
            val inside = item.points.count { pointInPolygon(it.x, it.y, poly) }
            inside * 2 >= item.points.size
        }
        else -> {
            val b = bounds(item)
            pointInPolygon(b.centerX(), b.centerY(), poly)
        }
    }

    fun translate(item: Item, dx: Float, dy: Float): Item = when (item) {
        is StrokeItem -> item.copy(points = item.points.map { Pt(it.x + dx, it.y + dy) })
        is TextItem -> item.copy(x = item.x + dx, y = item.y + dy)
        is ShapeItem -> item.copy(x1 = item.x1 + dx, y1 = item.y1 + dy, x2 = item.x2 + dx, y2 = item.y2 + dy)
        is ImageItem -> item.copy(x = item.x + dx, y = item.y + dy)
    }

    /** Escala o item a partir de um ponto de origem (redimensionar seleção). */
    fun scale(item: Item, s: Float, ox: Float, oy: Float): Item {
        fun sx(v: Float) = ox + (v - ox) * s
        fun sy(v: Float) = oy + (v - oy) * s
        return when (item) {
            is StrokeItem -> item.copy(points = item.points.map { Pt(sx(it.x), sy(it.y)) }, width = item.width * s)
            is TextItem -> item.copy(x = sx(item.x), y = sy(item.y), size = item.size * s)
            is ShapeItem -> item.copy(x1 = sx(item.x1), y1 = sy(item.y1), x2 = sx(item.x2), y2 = sy(item.y2), width = item.width * s)
            is ImageItem -> item.copy(x = sx(item.x), y = sy(item.y), w = item.w * s, h = item.h * s)
        }
    }
}
