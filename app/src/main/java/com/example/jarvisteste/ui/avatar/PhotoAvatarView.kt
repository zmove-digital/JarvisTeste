package com.example.jarvisteste.ui.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.example.jarvisteste.audio.VoiceLevel
import com.example.jarvisteste.audio.VoiceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Avatar a partir de uma foto escolhida pelo usuário.
 *
 * A imagem em si não é deformada por inteiro: boca e olhos têm "remendos"
 * próprios que são deformados com [Canvas.drawBitmapMesh]. A boca abre com a
 * amplitude real do áudio (ou um envelope procedural na voz do aparelho), os
 * olhos piscam, e a cabeça balança de leve conforme o estado da conversa.
 */
@Composable
fun PhotoAvatarView(
    photo: PhotoAvatar,
    mood: AvatarMood,
    modifier: Modifier = Modifier
) {
    val bitmaps by produceState<PhotoBitmaps?>(
        initialValue = null,
        photo.image.absolutePath,
        photo.image.lastModified(),
        photo.width,
        photo.height
    ) {
        value = withContext(Dispatchers.IO) { PhotoBitmaps.load(photo) }
    }

    val transition = rememberInfiniteTransition(label = "photo-avatar")

    val sway by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "photo-sway"
    )

    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mood) {
                    AvatarMood.SPEAKING -> 900
                    AvatarMood.THINKING -> 1500
                    else -> 3000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "photo-pulse"
    )

    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5600
                1f at 0
                1f at 4850
                0.04f at 5000
                1f at 5180
                1f at 5600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "photo-blink"
    )

    // Usado quando a voz do aparelho fala (não temos acesso às amostras).
    val procedural by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 640
                0f at 0
                0.9f at 90
                0.22f at 170
                1f at 265
                0.18f at 365
                0.68f at 500
                0f at 640
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "photo-procedural-mouth"
    )

    val level by VoiceLevel.level.collectAsState()
    val source by VoiceLevel.source.collectAsState()

    val targetMouth = when {
        mood != AvatarMood.SPEAKING -> 0f
        source == VoiceSource.GEMINI -> (level * 1.15f).coerceIn(0f, 1f)
        else -> procedural
    }
    val mouthOpen by animateFloatAsState(
        targetValue = targetMouth,
        animationSpec = tween(durationMillis = 90),
        label = "photo-mouth-open"
    )

    Canvas(modifier = modifier) {
        drawRect(Brush.verticalGradient(listOf(photo.backdropTop, photo.backdropBottom)))
        val loaded = bitmaps ?: return@Canvas
        drawPhotoAvatar(photo, loaded, mood, sway, pulse, blink, mouthOpen)
    }
}

private fun DrawScope.drawPhotoAvatar(
    photo: PhotoAvatar,
    bitmaps: PhotoBitmaps,
    mood: AvatarMood,
    sway: Float,
    pulse: Float,
    blink: Float,
    mouthOpen: Float
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val crop = photo.displayCrop(w, h)
    if (crop.width() <= 1 || crop.height() <= 1) return

    val scale = max(w / crop.width(), h / crop.height())
    val dx = (w - crop.width() * scale) / 2f
    val dy = (h - crop.height() * scale) / 2f

    val angle = sway * 2f * PI.toFloat()
    val tilt = sin(angle) * 1.15f + when (mood) {
        AvatarMood.THINKING -> -1.6f
        AvatarMood.LISTENING -> 0.7f
        else -> 0f
    }

    val faceViewX = dx + (photo.face.centerX() * photo.width - crop.left) * scale
    val faceViewY = dy + (photo.face.centerY() * photo.height - crop.top) * scale
    val breath = 1f + 0.006f * sin(angle) +
        if (mood == AvatarMood.SPEAKING) 0.004f * pulse else 0f

    val native = drawContext.canvas.nativeCanvas
    native.save()
    native.translate(faceViewX, faceViewY)
    native.rotate(tilt)
    native.scale(breath, breath)
    native.translate(-faceViewX, -faceViewY)
    native.translate(dx, dy)
    native.scale(scale, scale)
    native.translate(-crop.left.toFloat(), -crop.top.toFloat())
    native.clipRect(
        crop.left.toFloat(),
        crop.top.toFloat(),
        crop.right.toFloat(),
        crop.bottom.toFloat()
    )

    native.drawBitmap(bitmaps.image, 0f, 0f, basePaint)

    val look = when (mood) {
        AvatarMood.THINKING -> -0.11f
        AvatarMood.LISTENING -> -0.03f
        else -> 0f
    }
    drawEye(native, bitmaps.leftEye, blink, look)
    drawEye(native, bitmaps.rightEye, blink, look)
    drawMouth(native, bitmaps.mouth, mouthOpen)

    native.restore()

    // Luz suave nas bordas: indica atenção/pensamento sem esconder o rosto.
    val glowAlpha = when (mood) {
        AvatarMood.LISTENING -> 0.10f + 0.08f * pulse
        AvatarMood.THINKING -> 0.12f + 0.12f * pulse
        AvatarMood.SPEAKING -> 0.05f
        else -> 0f
    }
    if (glowAlpha > 0f) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.Transparent, photo.accent.copy(alpha = glowAlpha)),
                center = Offset(faceViewX, faceViewY),
                radius = max(w, h) * 0.78f
            )
        )
    }
}

/** Abre a boca: lábio superior sobe, inferior e queixo descem, cantos apertam. */
private fun DrawScope.drawMouth(canvas: Canvas, patch: PhotoBitmaps.Patch, open: Float) {
    if (open <= 0.001f) return
    val cols = patch.cols
    val rows = patch.rows
    val pw = patch.src.width().toFloat()
    val ph = patch.src.height().toFloat()
    val mouthH = ph / MOUTH_PATCH_HEIGHTS
    val lineV = (PhotoAvatar.MOUTH_PAD_TOP + 0.5f) / MOUTH_PATCH_HEIGHTS
    val amplitude = mouthH * 0.30f * open

    var index = 0
    for (row in 0..rows) {
        val v = row.toFloat() / rows
        for (col in 0..cols) {
            val u = col.toFloat() / cols
            val du = (u - 0.5f) * 2f

            // Distância vertical até a linha dos lábios, medida em alturas da
            // boca. As gaussianas mantêm a deformação perto dos lábios: o nariz
            // e o queixo quase não se mexem.
            val distance = (v - lineV) * ph / mouthH
            val spread = if (distance >= 0f) 1.35f else 0.95f
            val vertical = exp(-(distance / spread).pow(2))
            val horizontal = exp(-(du / 1.15f).pow(2))
            val weight = vertical * horizontal

            val side = (distance / 0.9f).coerceIn(-1f, 1f)
            val x = u * pw + (-du) * pw * 0.030f * open * weight
            val y = v * ph + amplitude * weight * side

            patch.verts[index++] = x
            patch.verts[index++] = y
        }
    }

    canvas.save()
    canvas.translate(patch.src.left.toFloat(), patch.src.top.toFloat())
    canvas.drawBitmapMesh(patch.bitmap, cols, rows, patch.verts, 0, null, 0, meshPaint)

    // O vão que se abre entre os lábios é preenchido com um tom escuro, senão a
    // boca parece apenas esticada em vez de aberta.
    val interior = ((open - 0.15f) / 0.55f).coerceIn(0f, 1f)
    if (interior > 0f) {
        mouthPaint.color = MOUTH_INTERIOR
        mouthPaint.alpha = (interior * 200f).toInt().coerceIn(0, 255)
        val cx = pw / 2f
        val cy = lineV * ph
        val rx = pw * 0.24f
        val ry = (amplitude * 0.95f).coerceAtLeast(1f)
        canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), mouthPaint)
    }
    canvas.restore()
}

/** Pisca: comprime a região do olho na vertical e desenha o cílio fechado. */
private fun DrawScope.drawEye(canvas: Canvas, patch: PhotoBitmaps.Patch, blink: Float, look: Float) {
    val close = (1f - blink).coerceIn(0f, 1f)
    val cols = patch.cols
    val rows = patch.rows
    val pw = patch.src.width().toFloat()
    val ph = patch.src.height().toFloat()
    val centerY = ph / 2f
    val lookShift = look * ph

    var index = 0
    for (row in 0..rows) {
        val v = row.toFloat() / rows
        val dv = (v - 0.5f) * 2f
        for (col in 0..cols) {
            val u = col.toFloat() / cols
            val du = (u - 0.5f) * 2f

            // Influência concentrada na pálpebra: a sobrancelha acima do
            // remendo fica praticamente parada durante a piscada.
            val influence = exp(-(du / 0.95f).pow(2)) * exp(-(dv / 0.62f).pow(2))

            val x = u * pw
            val y = v * ph + (-(v * ph - centerY)) * close * 0.88f * influence +
                lookShift * influence

            patch.verts[index++] = x
            patch.verts[index++] = y
        }
    }

    canvas.save()
    canvas.translate(patch.src.left.toFloat(), patch.src.top.toFloat())
    canvas.drawBitmapMesh(patch.bitmap, cols, rows, patch.verts, 0, null, 0, meshPaint)

    if (close > 0.02f) {
        lidPaint.color = LID_COLOR.copy(alpha = close * 0.55f).toArgb()
        lidPaint.strokeWidth = max(1f, ph * 0.014f)
        canvas.drawLine(pw * 0.28f, centerY, pw * 0.72f, centerY, lidPaint)
    }
    canvas.restore()
}

private const val MOUTH_PATCH_HEIGHTS =
    PhotoAvatar.MOUTH_PAD_TOP + 1f + PhotoAvatar.MOUTH_PAD_BOTTOM

private const val MOUTH_INTERIOR = 0xFF3A1D20.toInt()
private val LID_COLOR = Color(0xFF1B1418)

private val basePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

private val meshPaint = Paint(
    Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
)

private val lidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
}

/** Preenche o interior da boca quando ela abre. */
private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG)

/** Bitmaps já recortados usados a cada quadro. */
class PhotoBitmaps(
    val image: Bitmap,
    val mouth: Patch,
    val leftEye: Patch,
    val rightEye: Patch
) {

    class Patch(
        val bitmap: Bitmap,
        val src: Rect,
        mouth: Boolean
    ) {
        val cols: Int = if (mouth) 18 else 10
        val rows: Int = if (mouth) 16 else 8
        val verts: FloatArray = FloatArray((cols + 1) * (rows + 1) * 2)
    }

    companion object {
        fun load(photo: PhotoAvatar): PhotoBitmaps? {
            val image = runCatching {
                BitmapFactory.decodeFile(photo.image.absolutePath)
            }.getOrNull() ?: return null
            if (image.width != photo.width || image.height != photo.height) {
                image.recycle()
                return null
            }

            // O remendo da boca é calculado primeiro, com o topo limitado pelo
            // pé dos olhos; assim ele nunca cobre os olhos, que são desenhados
            // antes e seriam sobrescritos.
            val eyeRawLeft = photo.patch(
                photo.leftEye,
                PhotoAvatar.EYE_PAD_X,
                PhotoAvatar.EYE_PAD_TOP,
                PhotoAvatar.EYE_PAD_BOTTOM
            )
            val eyeRawRight = photo.patch(
                photo.rightEye,
                PhotoAvatar.EYE_PAD_X,
                PhotoAvatar.EYE_PAD_TOP,
                PhotoAvatar.EYE_PAD_BOTTOM
            )
            val mouthRect = photo.patch(
                photo.mouth,
                PhotoAvatar.MOUTH_PAD_X,
                PhotoAvatar.MOUTH_PAD_TOP,
                PhotoAvatar.MOUTH_PAD_BOTTOM,
                minTop = maxOf(eyeRawLeft.bottom, eyeRawRight.bottom) + PhotoAvatar.PATCH_GAP
            )
            val mouthTopLimit = mouthRect.top - PhotoAvatar.PATCH_GAP
            val leftRect = photo.patch(
                photo.leftEye,
                PhotoAvatar.EYE_PAD_X,
                PhotoAvatar.EYE_PAD_TOP,
                PhotoAvatar.EYE_PAD_BOTTOM,
                maxBottom = mouthTopLimit
            )
            val rightRect = photo.patch(
                photo.rightEye,
                PhotoAvatar.EYE_PAD_X,
                PhotoAvatar.EYE_PAD_TOP,
                PhotoAvatar.EYE_PAD_BOTTOM,
                maxBottom = mouthTopLimit
            )

            return PhotoBitmaps(
                image = image,
                mouth = patch(image, mouthRect, mouth = true),
                leftEye = patch(image, leftRect, mouth = false),
                rightEye = patch(image, rightRect, mouth = false)
            )
        }

        private fun patch(source: Bitmap, rect: Rect, mouth: Boolean): Patch {
            val bitmap = Bitmap.createBitmap(
                source,
                rect.left,
                rect.top,
                rect.width().coerceAtLeast(1),
                rect.height().coerceAtLeast(1)
            )
            return Patch(bitmap, rect, mouth)
        }
    }
}
