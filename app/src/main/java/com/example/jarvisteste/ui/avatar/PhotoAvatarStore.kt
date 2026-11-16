package com.example.jarvisteste.ui.avatar

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Retrato escolhido pelo usuário, já recortado e com as coordenadas do rosto
 * detectadas. As coordenadas são normalizadas (0..1) em relação à imagem salva,
 * então o desenho funciona em qualquer tamanho de tela.
 */
data class PhotoAvatar(
    val image: File,
    val width: Int,
    val height: Int,
    val face: RectF,
    val mouth: RectF,
    val leftEye: RectF,
    val rightEye: RectF,
    val accent: Color,
    val glow: Color,
    val backdropTop: Color,
    val backdropBottom: Color
) {

    /** Preset sintético para o restante da interface (título, cores, bordas). */
    val preset: AvatarPreset
        get() = AvatarPreset(
            id = ID,
            name = "Minha foto",
            tagline = "Do seu jeito",
            style = AvatarStyle.FACE,
            hair = HairStyle.NONE,
            palette = AvatarPalette(
                backdropTop = backdropTop,
                backdropBottom = backdropBottom,
                glow = glow,
                accent = accent,
                skin = Color(0xFFE8C4A8),
                skinShade = Color(0xFFC99C7E),
                hair = Color(0xFF2A1B16),
                clothes = Color(0xFF1D2836),
                eye = Color(0xFF2B3A45),
                mouth = Color(0xFF9E4E58)
            )
        )

    /**
     * Região da imagem usada como "remendo" animado (boca ou olho), com margem
     * para a deformação chegar a zero antes da borda.
     *
     * As margens podem encolher, nunca cortar a região em si, e [minTop] /
     * [maxBottom] impedem que o remendo da boca invada os olhos (ou vice-versa)
     * em rostos com traços mais juntos.
     */
    fun patch(
        region: RectF,
        padX: Float,
        padTop: Float,
        padBottom: Float,
        minTop: Int = Int.MIN_VALUE,
        maxBottom: Int = Int.MAX_VALUE
    ): Rect {
        val regionW = (region.width() * width).coerceAtLeast(2f)
        val regionH = (region.height() * height).coerceAtLeast(2f)
        val centerX = region.centerX() * width
        val regionTop = (region.top * height).toInt().coerceIn(0, height - 2)
        val regionBottom = (region.bottom * height).toInt().coerceIn(2, height)

        val left = (centerX - regionW / 2f - regionW * padX).toInt().coerceIn(0, width - 2)
        val right = (centerX + regionW / 2f + regionW * padX).toInt().coerceIn(2, width)

        val topLow = minTop.coerceAtLeast(0).coerceAtMost(regionTop)
        val top = (regionTop - regionH * padTop).toInt().coerceIn(topLow, regionTop)

        val bottomHigh = maxBottom.coerceAtMost(height).coerceAtLeast(regionBottom)
        val bottom = (regionBottom + regionH * padBottom).toInt().coerceIn(regionBottom, bottomHigh)

        return Rect(
            left,
            top,
            right.coerceAtLeast(left + 2),
            bottom.coerceAtLeast(top + 2)
        )
    }

    /**
     * Recorte exibido na tela: enquadra o rosto ocupando boa parte da altura,
     * mantendo a proporção do espaço disponível. O desenho depois usa "cover"
     * para preencher a tela inteira sem distorcer.
     */
    fun displayCrop(viewWidth: Float, viewHeight: Float): Rect {
        if (viewWidth <= 0f || viewHeight <= 0f) return Rect(0, 0, width, height)
        val faceW = (face.width() * width).coerceAtLeast(1f)
        val faceH = (face.height() * height).coerceAtLeast(1f)
        val aspect = viewWidth / viewHeight
        var cropH = faceH / TARGET_FACE_HEIGHT
        var cropW = cropH * aspect

        if (cropW > width) {
            cropW = width.toFloat()
            cropH = cropW / aspect
        }
        if (cropH > height) {
            cropH = height.toFloat()
            cropW = cropH * aspect
        }
        cropW = cropW.coerceAtMost(width.toFloat())
        cropH = cropH.coerceAtMost(height.toFloat())

        val centerX = face.centerX() * width
        // Um pouco abaixo do centro do rosto deixa espaço para o cabelo em cima.
        val centerY = face.centerY() * height + faceH * 0.05f
        val left = (centerX - cropW / 2f).coerceIn(0f, (width - cropW).coerceAtLeast(0f))
        val top = (centerY - cropH * 0.47f).coerceIn(0f, (height - cropH).coerceAtLeast(0f))
        return Rect(
            left.toInt(),
            top.toInt(),
            (left + cropW).toInt().coerceAtMost(width),
            (top + cropH).toInt().coerceAtMost(height)
        )
    }

    companion object {
        const val ID = "foto"

        /** Fração da altura da tela que o rosto deve ocupar. */
        private const val TARGET_FACE_HEIGHT = 0.46f

        // Margens dos remendos animados, em múltiplos do tamanho da região.
        // Enxutas de propósito: um remendo grande demais arrastaria a testa e o
        // queixo junto com a boca e os olhos.
        const val MOUTH_PAD_X = 0.38f
        const val MOUTH_PAD_TOP = 0.55f
        const val MOUTH_PAD_BOTTOM = 1.25f
        const val EYE_PAD_X = 0.60f
        const val EYE_PAD_TOP = 1.10f
        const val EYE_PAD_BOTTOM = 1.10f

        /** Folga mínima entre o remendo da boca e o dos olhos, em pixels. */
        const val PATCH_GAP = 6
    }
}

/** Resultado da análise de uma foto, pronto para ser salvo. */
class ImportedPhoto(
    val jpeg: ByteArray,
    val width: Int,
    val height: Int,
    val face: RectF,
    val mouth: RectF,
    val leftEye: RectF,
    val rightEye: RectF,
    val accent: Int,
    val glow: Int,
    val backdropTop: Int,
    val backdropBottom: Int
)

/**
 * Guarda o retrato e as coordenadas do rosto em armazenamento privado do app.
 * Nada é enviado para a nuvem: a imagem só sai daqui para a tela.
 */
class PhotoAvatarStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "avatar")
    private val imageFile = File(dir, "retrato.jpg")
    private val metaFile = File(dir, "retrato.json")

    val hasPhoto: Boolean
        get() = imageFile.isFile && metaFile.isFile

    fun load(): PhotoAvatar? {
        if (!hasPhoto) return null
        return runCatching {
            val meta = JSONObject(metaFile.readText(Charsets.UTF_8))
            val width = meta.getInt("width")
            val height = meta.getInt("height")
            if (width <= 0 || height <= 0) return null
            PhotoAvatar(
                image = imageFile,
                width = width,
                height = height,
                face = meta.rect("face") ?: return null,
                mouth = meta.rect("mouth") ?: return null,
                leftEye = meta.rect("leftEye") ?: return null,
                rightEye = meta.rect("rightEye") ?: return null,
                accent = Color(meta.getInt("accent")),
                glow = Color(meta.getInt("glow")),
                backdropTop = Color(meta.getInt("backdropTop")),
                backdropBottom = Color(meta.getInt("backdropBottom"))
            )
        }.getOrNull()
    }

    fun save(imported: ImportedPhoto): Boolean = runCatching {
        dir.mkdirs()
        val tempImage = File(dir, "retrato.jpg.tmp")
        tempImage.outputStream().use { it.write(imported.jpeg) }
        if (!tempImage.renameTo(imageFile)) {
            imageFile.outputStream().use { it.write(imported.jpeg) }
            tempImage.delete()
        }

        val meta = JSONObject()
            .put("version", 1)
            .put("width", imported.width)
            .put("height", imported.height)
            .put("accent", imported.accent)
            .put("glow", imported.glow)
            .put("backdropTop", imported.backdropTop)
            .put("backdropBottom", imported.backdropBottom)
            .putRect("face", imported.face)
            .putRect("mouth", imported.mouth)
            .putRect("leftEye", imported.leftEye)
            .putRect("rightEye", imported.rightEye)

        val tempMeta = File(dir, "retrato.json.tmp")
        tempMeta.writeText(meta.toString(), Charsets.UTF_8)
        if (!tempMeta.renameTo(metaFile)) {
            metaFile.writeText(meta.toString(), Charsets.UTF_8)
            tempMeta.delete()
        }
        true
    }.getOrDefault(false)

    fun clear() {
        imageFile.delete()
        metaFile.delete()
    }

    private fun JSONObject.putRect(name: String, rect: RectF): JSONObject = put(
        name,
        JSONArray()
            .put(rect.left.toDouble())
            .put(rect.top.toDouble())
            .put(rect.right.toDouble())
            .put(rect.bottom.toDouble())
    )

    private fun JSONObject.rect(name: String): RectF? {
        val array = optJSONArray(name) ?: return null
        if (array.length() < 4) return null
        return RectF(
            array.getDouble(0).toFloat(),
            array.getDouble(1).toFloat(),
            array.getDouble(2).toFloat(),
            array.getDouble(3).toFloat()
        )
    }
}
