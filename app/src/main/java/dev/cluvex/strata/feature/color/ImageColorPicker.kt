package dev.cluvex.strata.feature.color

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.strata.R
import dev.cluvex.strata.core.color.PickerPrefs
import dev.cluvex.strata.core.color.PixelColor
import dev.cluvex.strata.core.data.SettingsRepository
import dev.cluvex.strata.core.i18n.tr
import dev.cluvex.strata.ui.rememberTextClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Colour picker for the image viewer. Behaves like the Color Picker of AUDIO_VIDEO_TOOLS:
 * drag on the picture to aim (a round loupe whose ring takes the sampled colour follows the finger, a ring
 * marks the exact pixel), lift to pick; the arrow pad fine-tunes one pixel at a time (hold to repeat);
 * the result lists HEX / RGB / HSL / HSV / CMYK / CIE LAB / Decimal, tap a row to copy; the history,
 * loupe zoom, pad step and auto-copy are remembered between visits.
 */

private const val MAX_PIXELS = 16_000_000L
private val LOUPE_OUTER = 168.dp
private val LOUPE_INNER = 114.dp

private class PickBitmap(val bitmap: Bitmap, val image: ImageBitmap, val sourceWidth: Int, val sourceHeight: Int) {
    val width get() = bitmap.width
    val height get() = bitmap.height
    val sampled get() = width != sourceWidth || height != sourceHeight

    companion object {
        fun load(file: File): PickBitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { BitmapFactory.decodeFile(file.path, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > MAX_PIXELS) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = runCatching { BitmapFactory.decodeFile(file.path, options) }.getOrNull() ?: return null
            val turned = runCatching { orient(file, raw) }.getOrDefault(raw)
            val srcW = if (turned.width == raw.width) bounds.outWidth else bounds.outHeight
            val srcH = if (turned.width == raw.width) bounds.outHeight else bounds.outWidth
            return PickBitmap(turned, turned.asImageBitmap(), srcW, srcH)
        }

        private fun orient(file: File, bitmap: Bitmap): Bitmap {
            val matrix = Matrix()
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
                else -> return bitmap
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}

/** How the picture sits inside the canvas (fit, centred). Maps screen points to picture pixels and back. */
private class Fit(val bw: Int, val bh: Int, w: Float, h: Float) {
    val scale = max(min(w / bw, h / bh), 0.0001f)
    val left = (w - bw * scale) / 2f
    val top = (h - bh * scale) / 2f
    val right = left + bw * scale
    val bottom = top + bh * scale

    fun pixel(p: Offset) = IntOffset(
        floor((p.x - left) / scale).toInt().coerceIn(0, bw - 1),
        floor((p.y - top) / scale).toInt().coerceIn(0, bh - 1),
    )

    fun inside(p: Offset) = p.x in left..right && p.y in top..bottom

    fun center(p: IntOffset) = Offset(left + (p.x + 0.5f) * scale, top + (p.y + 0.5f) * scale)
}

@Stable
private class PickState {
    var hover by mutableStateOf<IntOffset?>(null)
    var marker by mutableStateOf<IntOffset?>(null)
    var loupe by mutableStateOf(false)
    var touch by mutableStateOf(false)
    var current by mutableStateOf<Int?>(null)
    var currentPos by mutableStateOf<IntOffset?>(null)
    var status by mutableStateOf("")
    var hideJob: Job? = null
}

private fun channels(argb: Int) = Triple((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

private fun hexOf(argb: Int): String = channels(argb).let { (r, g, b) -> PixelColor.hex(r, g, b) }

private fun positionText(pos: IntOffset?, argb: Int): String {
    val where = pos?.let { tr(R.string.picker_position, it.x, it.y) } ?: tr(R.string.picker_from_history)
    val alpha = argb ushr 24
    return if (alpha < 255) where + " · " + tr(R.string.picker_alpha, (alpha / 255f * 100).roundToInt()) else where
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageColorPicker(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val clipboard = rememberTextClipboard()
    val settings by repo.pickSettings.collectAsStateWithLifecycle(PickerPrefs.Settings())
    val history by repo.pickHistory.collectAsStateWithLifecycle(emptyList())
    val ps = remember { PickState() }
    var failed by remember { mutableStateOf(false) }
    val loaded by produceState<PickBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { PickBitmap.load(file) }
        if (value == null) failed = true
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showResult by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)
    LaunchedEffect(ps.status) {
        if (ps.status.isNotEmpty()) {
            delay(3000)
            ps.status = ""
        }
    }

    fun fit(pic: PickBitmap): Fit? = if (canvasSize.width > 0 && canvasSize.height > 0) Fit(pic.width, pic.height, canvasSize.width.toFloat(), canvasSize.height.toFloat()) else null

    fun hideLoupe() {
        ps.hideJob?.cancel()
        ps.loupe = false
    }

    fun preview(at: IntOffset, touch: Boolean, mark: Boolean) {
        ps.hover = at
        ps.touch = touch
        ps.loupe = true
        if (mark) ps.marker = at
    }

    fun pick(pic: PickBitmap, at: IntOffset) {
        val argb = pic.bitmap.getPixel(at.x, at.y)
        ps.current = argb
        ps.currentPos = at
        ps.hover = at
        ps.marker = at
        scope.launch { repo.pushPickHistory(argb) }
        val index = if (settings.autoCopy == PickerPrefs.AutoCopy.OFF) null else settings.autoCopy.ordinal - 1
        if (index != null) {
            val (r, g, b) = channels(argb)
            val text = PixelColor.formats(r, g, b)[index].text
            clipboard.setText(text)
            ps.status = tr(R.string.picker_copied_value, text)
        }
    }

    fun aimBase(pic: PickBitmap): IntOffset = ps.hover ?: ps.currentPos ?: IntOffset(pic.width shr 1, pic.height shr 1)

    fun nudge(pic: PickBitmap, dx: Int, dy: Int) {
        val base = aimBase(pic)
        val at = IntOffset((base.x + dx * settings.step).coerceIn(0, pic.width - 1), (base.y + dy * settings.step).coerceIn(0, pic.height - 1))
        preview(at, touch = false, mark = true)
        ps.hideJob?.cancel()
        ps.hideJob = scope.launch {
            delay(2500)
            ps.loupe = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("image-color-picker")) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(file.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    val pic = loaded
                    val info = if (pic == null) stringResource(R.string.color_picker_title) else {
                        stringResource(R.string.media_image_size, pic.sourceWidth, pic.sourceHeight) +
                            if (pic.sampled) " · " + stringResource(R.string.picker_sampled, pic.width, pic.height) else ""
                    }
                    Text(info, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.picker_settings), tint = Color.White)
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { canvasSize = it }, contentAlignment = Alignment.Center) {
                val pic = loaded
                when {
                    pic != null -> PickCanvas(
                        pic = pic,
                        ps = ps,
                        zoom = settings.zoom,
                        modifier = Modifier.fillMaxSize().pointerInput(pic, settings) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                val f = fit(pic) ?: return@awaitEachGesture
                                ps.hideJob?.cancel()
                                preview(f.pixel(down.position), touch = true, mark = true)
                                var last = down.position
                                val finished = drag(down.id) { change ->
                                    last = change.position
                                    preview(f.pixel(last), touch = true, mark = true)
                                    change.consume()
                                }
                                if (finished && f.inside(last)) pick(pic, f.pixel(last))
                                hideLoupe()
                            }
                        },
                    )
                    failed -> Text(stringResource(R.string.picker_open_failed), color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(24.dp))
                    else -> CircularProgressIndicator()
                }
            }

            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val cur = ps.current
                if (ps.status.isNotEmpty()) {
                    Text(ps.status, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                } else if (cur == null) {
                    Text(stringResource(R.string.picker_hint), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                }
                if (cur != null) {
                    ResultStrip(
                        argb = cur,
                        position = positionText(ps.currentPos, cur),
                        onCopy = {
                            val text = hexOf(cur)
                            clipboard.setText(text)
                            ps.status = tr(R.string.picker_copied_value, text)
                        },
                        onMore = { showResult = true },
                    )
                }
                // The arrows are physical directions, so the pad keeps the same order in right-to-left languages.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val pic = loaded
                        PadKey("◀", stringResource(R.string.picker_move_left), Modifier.weight(1f), repeat = true) { pic?.let { nudge(it, -1, 0) } }
                        PadKey("▲", stringResource(R.string.picker_move_up), Modifier.weight(1f), repeat = true) { pic?.let { nudge(it, 0, -1) } }
                        PadKey("▼", stringResource(R.string.picker_move_down), Modifier.weight(1f), repeat = true) { pic?.let { nudge(it, 0, 1) } }
                        PadKey("▶", stringResource(R.string.picker_move_right), Modifier.weight(1f), repeat = true) { pic?.let { nudge(it, 1, 0) } }
                        PadKey(stringResource(R.string.picker_pick), stringResource(R.string.picker_pick), Modifier.weight(1.7f), accent = true) {
                            pic?.let { pick(it, aimBase(it)); hideLoupe() }
                        }
                    }
                }
            }
        }
    }

    if (showResult) {
        val cur = ps.current
        if (cur != null) {
            ResultSheet(
                argb = cur,
                position = positionText(ps.currentPos, cur),
                history = history,
                onCopy = { clipboard.setText(it) },
                onCopyAll = {
                    clipboard.setText(history.joinToString("\n") { hexOf(it) })
                    ps.status = tr(R.string.picker_copied_count, history.size)
                },
                onClear = { scope.launch { repo.clearPickHistory() } },
                onPickHistory = { ps.current = it; ps.currentPos = null },
                onDismiss = { showResult = false },
            )
        } else {
            showResult = false
        }
    }
    if (showSettings) {
        SettingsSheet(settings, onChange = { scope.launch { repo.setPickSettings(it) } }, onDismiss = { showSettings = false })
    }
}

@Composable
private fun PickCanvas(pic: PickBitmap, ps: PickState, zoom: Int, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Box(modifier) {
        // the picture itself (redrawn only when the size changes)
        Canvas(Modifier.fillMaxSize()) {
            val f = Fit(pic.width, pic.height, size.width, size.height)
            clipRect(f.left, f.top, f.right, f.bottom) {
                val cell = 16.dp.toPx()
                drawRect(Color(0xFF141A23))
                var row = 0
                while (row * cell < size.height) {
                    var col = 0
                    while (col * cell < size.width) {
                        if ((row + col) % 2 == 0) drawRect(Color(0xFF1B2330), Offset(col * cell, row * cell), androidx.compose.ui.geometry.Size(cell, cell))
                        col++
                    }
                    row++
                }
            }
            withTransform({
                translate(f.left, f.top)
                scale(f.scale, f.scale, Offset.Zero)
            }) {
                drawImage(pic.image, filterQuality = if (f.scale >= 3f) FilterQuality.None else FilterQuality.Low)
            }
        }
        // marker, stem and loupe
        Canvas(Modifier.fillMaxSize()) {
            val f = Fit(pic.width, pic.height, size.width, size.height)
            val marker = ps.marker
            if (marker != null) {
                val c = f.center(marker)
                drawCircle(Color.Black, radius = 11.dp.toPx(), center = c, style = Stroke(4.dp.toPx()))
                drawCircle(Color.White, radius = 11.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
            }
            val hover = ps.hover
            if (!ps.loupe || hover == null) return@Canvas

            val argb = pic.bitmap.getPixel(hover.x, hover.y)
            val (r, g, b) = channels(argb)
            val sampled = Color(r, g, b)
            val target = f.center(hover)

            val lw = LOUPE_OUTER.toPx()
            val edge = 4.dp.toPx()
            val gap = (if (ps.touch) 40.dp else 18.dp).toPx()
            var top = target.y - gap - lw
            if (top < edge) top = target.y + gap
            top = top.coerceIn(edge, max(edge, size.height - lw - edge))
            val left = (target.x - lw / 2f).coerceIn(edge, max(edge, size.width - lw - edge))
            val cx = left + lw / 2f
            val cy = top + lw / 2f

            // a line from the loupe to the picked pixel so it is obvious where the sample is taken
            if (ps.touch) {
                val above = top + lw <= target.y
                val y0 = if (above) top + lw else target.y + 11.dp.toPx()
                val y1 = if (above) target.y - 11.dp.toPx() else top
                if (y1 - y0 > 2f) {
                    drawLine(Color.Black, Offset(target.x, y0), Offset(target.x, y1), strokeWidth = 4.dp.toPx())
                    drawLine(Color.White, Offset(target.x, y0), Offset(target.x, y1), strokeWidth = 2.dp.toPx())
                }
            }

            // the ring around the magnifier takes the sampled colour
            drawCircle(Color.Black.copy(alpha = 0.45f), radius = lw / 2f + 2.dp.toPx(), center = Offset(cx, cy + 4.dp.toPx()))
            drawCircle(sampled, radius = lw / 2f, center = Offset(cx, cy))
            drawCircle(Color.White, radius = lw / 2f - 1.5.dp.toPx(), center = Offset(cx, cy), style = Stroke(3.dp.toPx()))

            val inner = LOUPE_INNER.toPx()
            val ri = inner / 2f
            val mag = max(zoom * f.scale, 0.01f)
            val circle = Path().apply { addOval(Rect(Offset(cx, cy), ri)) }
            clipPath(circle) {
                drawRect(Color(0xFF0A0E14), Offset(cx - ri, cy - ri), androidx.compose.ui.geometry.Size(inner, inner))
                withTransform({
                    translate(cx, cy)
                    scale(mag, mag, Offset.Zero)
                    translate(-(hover.x + 0.5f), -(hover.y + 0.5f))
                }) {
                    drawImage(pic.image, filterQuality = if (mag < 2f) FilterQuality.Low else FilterQuality.None)
                }
                // the cell under the crosshair is exactly the pixel that will be picked
                val box = max(mag, 6.dp.toPx())
                drawRect(Color.Black, Offset(cx - box / 2f, cy - box / 2f), androidx.compose.ui.geometry.Size(box, box), style = Stroke(2.dp.toPx()))
                drawRect(Color.White, Offset(cx - box / 2f, cy - box / 2f), androidx.compose.ui.geometry.Size(box, box), style = Stroke(1.dp.toPx()))
            }
            drawCircle(Color.White, radius = ri - 1.dp.toPx(), center = Offset(cx, cy), style = Stroke(2.dp.toPx()))

            val ink = if (PixelColor.isLight(r, g, b)) Color.Black else Color.White
            val label = measurer.measure(
                AnnotatedString(PixelColor.hex(r, g, b)),
                TextStyle(color = ink, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, textDirection = TextDirection.Ltr),
            )
            val ring = lw / 2f - ri
            drawText(label, topLeft = Offset(cx - label.size.width / 2f, cy + ri + (ring - label.size.height) / 2f))
        }
    }
}

@Composable
private fun ResultStrip(argb: Int, position: String, onCopy: () -> Unit, onMore: () -> Unit) {
    val (r, g, b) = channels(argb)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.10f)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).background(Color(r, g, b)))
        Column(Modifier.weight(1f)) {
            Text(PixelColor.hex(r, g, b), color = Color.White, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
            Text(position, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.common_copy), tint = Color.White) }
        IconButton(onClick = onMore) { Icon(Icons.Rounded.UnfoldMore, contentDescription = stringResource(R.string.picker_details), tint = Color.White) }
    }
}

@Composable
private fun PadKey(label: String, description: String, modifier: Modifier, repeat: Boolean = false, accent: Boolean = false, onPress: () -> Unit) {
    val scope = rememberCoroutineScope()
    val press by rememberUpdatedState(onPress)
    var down by remember { mutableStateOf(false) }
    val container = when {
        accent -> MaterialTheme.colorScheme.primary
        down -> Color.White.copy(alpha = 0.28f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .pointerInput(repeat) {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    first.consume()
                    down = true
                    press()
                    // hold to repeat: first after 400 ms, then every 70 ms
                    val job = if (repeat) scope.launch {
                        delay(400)
                        while (true) {
                            press()
                            delay(70)
                        }
                    } else null
                    waitForUpOrCancellation()
                    job?.cancel()
                    down = false
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                onClick { press(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (accent) MaterialTheme.colorScheme.onPrimary else Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultSheet(
    argb: Int,
    position: String,
    history: List<Int>,
    onCopy: (String) -> Unit,
    onCopyAll: () -> Unit,
    onClear: () -> Unit,
    onPickHistory: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val (r, g, b) = channels(argb)
    var copied by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(copied) {
        if (copied != null) {
            delay(1200)
            copied = null
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val ink = if (PixelColor.isLight(r, g, b)) Color.Black else Color.White
            Row(
                Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(16.dp)).background(Color(r, g, b)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(PixelColor.hex(r, g, b), color = ink, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Ltr))
                Text(position, color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            }
            PixelColor.formats(r, g, b).forEachIndexed { index, format ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onCopy(format.text); copied = index }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(format.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.34f))
                    Text(format.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr), modifier = Modifier.weight(0.66f))
                    if (copied == index) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.common_copied), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (history.isNotEmpty()) {
                Text(stringResource(R.string.picker_history), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    history.forEach { c ->
                        val selected = (c or 0xFF000000.toInt()) == (argb or 0xFF000000.toInt())
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                                .background(Color(c))
                                .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable { onPickHistory(c) }
                                .semantics { contentDescription = hexOf(c) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCopyAll) { Text(stringResource(R.string.picker_copy_all)) }
                    TextButton(onClick = onClear) { Text(stringResource(R.string.common_clear)) }
                }
            }
            Box(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(settings: PickerPrefs.Settings, onChange: (PickerPrefs.Settings) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.picker_settings), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.picker_zoom), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerPrefs.ZOOMS.forEach { z ->
                    FilterChip(
                        selected = settings.zoom == z,
                        onClick = { onChange(settings.copy(zoom = z)) },
                        label = { Text(if (z == 1) stringResource(R.string.picker_zoom_none) else stringResource(R.string.picker_zoom_x, z)) },
                    )
                }
            }
            Text(stringResource(R.string.picker_zoom_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.picker_step), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerPrefs.STEPS.forEach { s ->
                    FilterChip(selected = settings.step == s, onClick = { onChange(settings.copy(step = s)) }, label = { Text(stringResource(R.string.picker_step_px, s)) })
                }
            }
            Text(stringResource(R.string.picker_autocopy), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerPrefs.AutoCopy.entries.forEach { m ->
                    FilterChip(
                        selected = settings.autoCopy == m,
                        onClick = { onChange(settings.copy(autoCopy = m)) },
                        label = { Text(if (m == PickerPrefs.AutoCopy.OFF) stringResource(R.string.picker_off) else m.label) },
                    )
                }
            }
            Box(Modifier.height(20.dp))
        }
    }
}
