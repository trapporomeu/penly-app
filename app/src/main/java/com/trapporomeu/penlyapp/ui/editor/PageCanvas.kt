package com.trapporomeu.penlyapp.ui.editor

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import com.trapporomeu.penlyapp.data.AppSettings
import com.trapporomeu.penlyapp.data.FingerMode
import com.trapporomeu.penlyapp.data.ScrollDirection
import com.trapporomeu.penlyapp.data.Size3
import com.trapporomeu.penlyapp.model.Item
import com.trapporomeu.penlyapp.model.Pt
import com.trapporomeu.penlyapp.model.ShapeItem
import com.trapporomeu.penlyapp.model.StrokeItem
import com.trapporomeu.penlyapp.model.TextItem
import com.trapporomeu.penlyapp.render.PageRenderer
import com.trapporomeu.penlyapp.render.RenderOptions
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Estado temporário de um gesto em andamento (não salvo). */
private class LiveState {
    val points = mutableStateListOf<Pt>()
    var shapeStart by mutableStateOf<Pt?>(null)
    var shapeEnd by mutableStateOf<Pt?>(null)
    val lasso = mutableStateListOf<Pt>()
    var eraser by mutableStateOf<Pt?>(null)
    // Seleção sendo movida/redimensionada
    var dragging by mutableStateOf(false)
    var dx by mutableFloatStateOf(0f)
    var dy by mutableFloatStateOf(0f)
    var scale by mutableFloatStateOf(1f)
    var ox = 0f
    var oy = 0f
}

private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    color = 0xFF5B8BD0.toInt()
}
private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFF5B8BD0.toInt() }
private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF757575.toInt() }
private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0x22000000 }

// `historical` (pontos intermediários da caneta) ainda é marcado como experimental.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PageCanvas(
    st: EditorState,
    settings: AppSettings,
    onTextTap: (x: Float, y: Float, existing: TextItem?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val page = st.page ?: return
    val live = remember { LiveState() }
    val textTap by rememberUpdatedState(onTextTap)
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var swipe by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val padPx = with(density) { 20f * this.density }
    val handlePx = with(density) {
        when (settings.selectionScale) {
            Size3.SMALL -> 9f; Size3.MEDIUM -> 13f; Size3.LARGE -> 18f
        } * this.density
    }
    val dark = MaterialTheme.colorScheme.background != Color.White
    val bg = if (dark) Color(0xFF101114) else Color(0xFFF5F5F5)

    LaunchedEffect(st.pageIndex) {
        zoom = 1f
        pan = Offset.Zero
        swipe = Offset.Zero
    }

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        // Geometria: página centralizada, ajustada à tela, com zoom e deslocamento.
        fun fit(): Float {
            val p = st.page ?: page
            return min((w - 2 * padPx) / p.width, (h - 2 * padPx) / p.height)
        }
        fun viewScale() = fit() * zoom
        fun base(z: Float): Offset {
            val p = st.page ?: page
            val s = fit() * z
            return Offset((w - p.width * s) / 2, (h - p.height * s) / 2)
        }
        fun topLeft() = base(zoom) + pan + swipe
        fun toPage(o: Offset): Pt {
            val tl = topLeft()
            val s = viewScale()
            return Pt((o.x - tl.x) / s, (o.y - tl.y) / s)
        }
        fun clampPan(p: Offset, z: Float): Offset {
            val pg = st.page ?: page
            val s = fit() * z
            val cw = pg.width * s
            val ch = pg.height * s
            val lx = if (cw > w - 2 * padPx) (cw - w) / 2 + padPx else 0f
            val ly = if (ch > h - 2 * padPx) (ch - h) / 2 + padPx else 0f
            return Offset(p.x.coerceIn(-lx, lx), p.y.coerceIn(-ly, ly))
        }

        fun fingerDraws(): Boolean = when (settings.fingerMode) {
            FingerMode.ALWAYS -> true
            FingerMode.NEVER -> false
            FingerMode.AUTO -> !st.stylusSeen
        }

        /** Dois dedos: zoom e mover. Continua até todos os dedos saírem. */
        suspend fun AwaitPointerEventScope.transformLoop() {
            while (true) {
                val ev = awaitPointerEvent()
                if (ev.changes.none { it.pressed }) break
                if (!st.zoomLocked) {
                    val zf = ev.calculateZoom()
                    val pn = ev.calculatePan()
                    val c = ev.calculateCentroid(useCurrent = true)
                    val newZoom = (zoom * zf).coerceIn(1f, 6f)
                    val f = newZoom / zoom
                    val tl = topLeft() - swipe
                    val newTl = if (c.isValid()) c - (c - tl) * f + pn else tl + pn
                    zoom = newZoom
                    pan = clampPan(newTl - base(newZoom), newZoom)
                }
                ev.changes.forEach { it.consume() }
            }
        }

        /** Um dedo que não desenha: rola, troca de página ou toca. */
        suspend fun AwaitPointerEventScope.navigate(down: PointerInputChange, onTap: (Pt) -> Unit) {
            val slop = viewConfiguration.touchSlop
            var moved = false
            var total = Offset.Zero
            val vertical = settings.scrollDirection == ScrollDirection.VERTICAL
            while (true) {
                val ev = awaitPointerEvent()
                if (ev.changes.count { it.pressed } >= 2) {
                    swipe = Offset.Zero
                    transformLoop()
                    return
                }
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                val d = ch.positionChange()
                total += d
                if (!moved && total.getDistance() > slop) moved = true
                if (moved) {
                    if (zoom > 1.01f && !st.zoomLocked) {
                        pan = clampPan(pan + d, zoom)
                    } else if (!st.zoomLocked || zoom <= 1.01f) {
                        swipe = if (vertical) Offset(0f, swipe.y + d.y) else Offset(swipe.x + d.x, 0f)
                    }
                    ch.consume()
                }
            }
            if (!moved) {
                onTap(toPage(down.position))
            } else if (swipe != Offset.Zero) {
                val amount = if (vertical) swipe.y else swipe.x
                val limit = (if (vertical) h else w) * 0.12f
                if (amount < -limit) st.goTo(st.pageIndex + 1)
                else if (amount > limit) st.goTo(st.pageIndex - 1)
                swipe = Offset.Zero
            }
        }

        fun addPoint(list: MutableList<Pt>, p: Pt) {
            val last = list.lastOrNull()
            if (last == null || hypot(last.x - p.x, last.y - p.y) > 0.6f) list.add(p)
        }

        fun eraseAt(p: Pt) {
            val pg = st.page ?: return
            val r = st.eraserSize
            if (pg.items.any { (it is StrokeItem || it is ShapeItem) && PageRenderer.hits(it, p.x, p.y, r) }) {
                st.liveUpdatePage { pp ->
                    pp.copy(items = pp.items.filterNot { (it is StrokeItem || it is ShapeItem) && PageRenderer.hits(it, p.x, p.y, r) })
                }
            }
        }

        fun itemAt(p: Pt): Item? {
            val pg = st.page ?: return null
            val r = 10f / viewScale() * 2
            return pg.items.lastOrNull { PageRenderer.hits(it, p.x, p.y, r) }
        }

        /** Caneta, marca-texto, borracha e formas. */
        suspend fun AwaitPointerEventScope.drawGesture(down: PointerInputChange, tool: Tool, isPen: Boolean) {
            val start = toPage(down.position)
            when (tool) {
                Tool.PEN, Tool.HIGHLIGHTER -> { live.points.clear(); live.points.add(start) }
                Tool.ERASER -> { st.beginLive(); live.eraser = start; eraseAt(start) }
                Tool.SHAPE -> { live.shapeStart = start; live.shapeEnd = start }
                else -> Unit
            }
            down.consume()
            var cancelled = false
            var last = start
            while (true) {
                val ev = awaitPointerEvent()
                if (!isPen && ev.changes.count { it.pressed } >= 2 && (tool != Tool.PEN && tool != Tool.HIGHLIGHTER || live.points.size < 12)) {
                    cancelled = true
                    break
                }
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                val pts = ch.historical.map { toPage(it.position) } + toPage(ch.position)
                when (tool) {
                    Tool.PEN, Tool.HIGHLIGHTER -> pts.forEach { addPoint(live.points, it) }
                    Tool.ERASER -> pts.forEach { p ->
                        // interpola para não "pular" traços quando o movimento é rápido
                        val dist = hypot(p.x - last.x, p.y - last.y)
                        val steps = (dist / (st.eraserSize / 2)).toInt().coerceAtMost(30)
                        for (i in 1..steps) eraseAt(Pt(last.x + (p.x - last.x) * i / (steps + 1), last.y + (p.y - last.y) * i / (steps + 1)))
                        eraseAt(p)
                        last = p
                        live.eraser = p
                    }
                    Tool.SHAPE -> live.shapeEnd = pts.last()
                    else -> Unit
                }
                ch.consume()
            }
            when (tool) {
                Tool.PEN, Tool.HIGHLIGHTER -> {
                    if (!cancelled && live.points.isNotEmpty()) {
                        val hl = tool == Tool.HIGHLIGHTER
                        st.addItem(
                            StrokeItem(
                                points = live.points.toList(),
                                color = if (hl) st.highlighterColor else st.penColor,
                                width = if (hl) st.highlighterWidth else st.penWidth,
                                highlighter = hl,
                            ),
                        )
                    }
                    live.points.clear()
                }
                Tool.ERASER -> { st.endLive(); live.eraser = null }
                Tool.SHAPE -> {
                    val a = live.shapeStart
                    val b = live.shapeEnd
                    if (!cancelled && a != null && b != null && hypot(a.x - b.x, a.y - b.y) > 4f) {
                        st.addItem(ShapeItem(type = st.shapeType, x1 = a.x, y1 = a.y, x2 = b.x, y2 = b.y, color = st.shapeColor, width = st.shapeWidth))
                    }
                    live.shapeStart = null
                    live.shapeEnd = null
                }
                else -> Unit
            }
            if (cancelled) transformLoop()
        }

        /** Laço: selecionar, mover e redimensionar. */
        suspend fun AwaitPointerEventScope.lassoGesture(down: PointerInputChange, canDraw: Boolean) {
            val p0 = toPage(down.position)
            val sel = st.selectionBounds()
            val s = viewScale()
            val handleR = handlePx * 1.8f / s
            val onHandle = sel != null && hypot(p0.x - sel.right, p0.y - sel.bottom) <= handleR
            val inside = sel != null && RectF(sel).apply { inset(-handleR / 2, -handleR / 2) }.contains(p0.x, p0.y)

            if (onHandle || inside) {
                live.dragging = true
                live.dx = 0f; live.dy = 0f; live.scale = 1f
                live.ox = sel!!.left; live.oy = sel.top
                down.consume()
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    val p = toPage(ch.position)
                    if (onHandle) {
                        val sx = (p.x - sel.left) / max(1f, sel.width())
                        val sy = (p.y - sel.top) / max(1f, sel.height())
                        live.scale = max(0.1f, max(sx, sy))
                    } else {
                        live.dx = p.x - p0.x
                        live.dy = p.y - p0.y
                    }
                    ch.consume()
                }
                if (live.dx != 0f || live.dy != 0f || live.scale != 1f) {
                    st.transformSelection(live.dx, live.dy, live.scale, live.ox, live.oy)
                }
                live.dragging = false
                return
            }

            if (!canDraw) {
                navigate(down) { p -> st.selection = itemAt(p)?.let { setOf(it.id) } ?: emptySet() }
                return
            }

            live.lasso.clear()
            live.lasso.add(p0)
            down.consume()
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                ch.historical.forEach { addPoint(live.lasso, toPage(it.position)) }
                addPoint(live.lasso, toPage(ch.position))
                ch.consume()
            }
            val poly = live.lasso.toList()
            live.lasso.clear()
            var length = 0f
            for (i in 1 until poly.size) length += hypot(poly[i].x - poly[i - 1].x, poly[i].y - poly[i - 1].y)
            val pg = st.page ?: return
            st.selection = if (length * s < 20f || poly.size < 3) {
                itemAt(p0)?.let { setOf(it.id) } ?: emptySet()
            } else {
                pg.items.filter { PageRenderer.insideLasso(it, poly) }.map { it.id }.toSet()
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(page.id, w, h, settings.fingerMode, settings.scrollDirection) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val isPen = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                        if (isPen && !st.stylusSeen) st.stylusSeen = true
                        val canDraw = isPen || fingerDraws()
                        val tool = if (down.type == PointerType.Eraser) Tool.ERASER else st.tool
                        when {
                            tool == Tool.LASSO -> lassoGesture(down, canDraw)
                            tool == Tool.TEXT -> navigate(down) { p ->
                                textTap(p.x, p.y, (itemAt(p) as? TextItem))
                            }
                            canDraw -> drawGesture(down, tool, isPen)
                            else -> navigate(down) { }
                        }
                    }
                },
        ) {
            drawRect(bg)
            val pg = st.page ?: return@Canvas
            val tl = topLeft()
            val s = viewScale()
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.drawRect(tl.x + 3f, tl.y + 4f, tl.x + pg.width * s + 3f, tl.y + pg.height * s + 4f, shadowPaint)
                nc.save()
                nc.translate(tl.x, tl.y)
                nc.scale(s, s)
                val hidden = if (live.dragging) st.selection else emptySet()
                PageRenderer.drawPage(nc, pg, st.assets, RenderOptions(settings.highlighterBelow, hidden))

                if (live.dragging) {
                    nc.save()
                    nc.clipRect(0f, 0f, pg.width, pg.height)
                    nc.translate(live.dx, live.dy)
                    nc.scale(live.scale, live.scale, live.ox, live.oy)
                    pg.items.filter { it.id in st.selection }.forEach { PageRenderer.drawItem(nc, it, st.assets) }
                    nc.restore()
                }
                if (live.points.isNotEmpty()) {
                    val hl = st.tool == Tool.HIGHLIGHTER
                    PageRenderer.drawLivePath(
                        nc, PageRenderer.buildPath(live.points),
                        if (hl) st.highlighterColor else st.penColor,
                        if (hl) st.highlighterWidth else st.penWidth,
                        hl,
                    )
                }
                val a = live.shapeStart
                val b = live.shapeEnd
                if (a != null && b != null) PageRenderer.drawShape(nc, st.shapeType, a.x, a.y, b.x, b.y, st.shapeColor, st.shapeWidth)

                dashPaint.strokeWidth = 2f / s
                dashPaint.pathEffect = DashPathEffect(floatArrayOf(10f / s, 8f / s), 0f)
                if (live.lasso.size > 1) {
                    val path = Path()
                    path.moveTo(live.lasso[0].x, live.lasso[0].y)
                    live.lasso.drop(1).forEach { path.lineTo(it.x, it.y) }
                    nc.drawPath(path, dashPaint)
                }
                if (st.tool == Tool.LASSO) {
                    st.selectionBounds()?.let { r0 ->
                        val r = RectF(r0)
                        if (live.dragging) {
                            r.set(
                                live.ox + (r.left - live.ox) * live.scale + live.dx,
                                live.oy + (r.top - live.oy) * live.scale + live.dy,
                                live.ox + (r.right - live.ox) * live.scale + live.dx,
                                live.oy + (r.bottom - live.oy) * live.scale + live.dy,
                            )
                        }
                        val pad = 6f / s
                        r.inset(-pad, -pad)
                        nc.drawRect(r, dashPaint)
                        nc.drawCircle(r.right, r.bottom, handlePx / s, handlePaint)
                    }
                }
                live.eraser?.let {
                    eraserPaint.strokeWidth = 1.5f / s
                    nc.drawCircle(it.x, it.y, st.eraserSize, eraserPaint)
                }
                nc.restore()
            }
        }
    }
}

private fun Offset.isValid() = x.isFinite() && y.isFinite()

