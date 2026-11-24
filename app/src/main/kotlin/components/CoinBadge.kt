package dev.spectra.components

import android.content.Context
import android.graphics.Picture
import androidx.collection.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caverock.androidsvg.SVG

/**
 * Core's crypto icon library, shipped as the SVGs in `assets/crypto/` that
 * `scripts/export-android-icons.sh` syncs. An artwork name is a file name
 * without its extension. Parsed once per artwork and kept as a [Picture].
 */
object CryptoArtwork {
    private val cache = LruCache<String, Picture>(64)
    private var names: Set<String>? = null

    /** Whether the library has `name`. */
    fun exists(context: Context, name: String): Boolean {
        if (name.isEmpty()) return false
        val known = names ?: (context.assets.list("crypto")?.map { it.removeSuffix(".svg") }?.toSet() ?: emptySet())
            .also { names = it }
        return name in known
    }

    /** The artwork drawn at its own 64×64 size, or `null` when there is none. */
    fun picture(context: Context, name: String): Picture? {
        if (!exists(context, name)) return null
        cache[name]?.let { return it }
        val picture = runCatching {
            context.assets.open("crypto/$name.svg").use { SVG.getFromInputStream(it) }.renderToPicture(64, 64)
        }.getOrNull() ?: return null
        cache.put(name, picture)
        return picture
    }
}

/**
 * Chain or token badge: the bundled artwork when there is one, otherwise a
 * circle in [color] with the first letter of [fallbackText].
 */
@Composable
fun CoinBadge(
    artworkName: String?,
    fallbackText: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val context = LocalContext.current
    val picture = remember(artworkName) { artworkName?.let { CryptoArtwork.picture(context, it) } }
    val shadowed = modifier
        .size(size)
        .shadow(elevation = 3.dp, shape = CircleShape, ambientColor = color.copy(alpha = 0.18f), spotColor = color.copy(alpha = 0.18f))
    if (picture != null) {
        Canvas(modifier = shadowed) {
            drawIntoCanvas { canvas ->
                val scale = this.size.minDimension / 64f
                canvas.nativeCanvas.save()
                canvas.nativeCanvas.scale(scale, scale)
                canvas.nativeCanvas.drawPicture(picture)
                canvas.nativeCanvas.restore()
            }
        }
    } else {
        val letter = fallbackText.firstOrNull()?.uppercase() ?: "?"
        Box(
            modifier = shadowed
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.75f)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letter,
                color = Color.White,
                style = TextStyle(fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/**
 * The app icon's five bars in a square of side [size]. The geometry and the
 * palette are those of spectra-apple's `icons/appicon/` and the launcher
 * icon, and change with them. [tint] draws every bar in one colour.
 */
@Composable
fun SpectraMark(size: Dp, modifier: Modifier = Modifier, tint: Color? = null) {
    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.width / 600f
        BARS.forEachIndexed { index, (x, width) ->
            drawRect(
                color = tint ?: PALETTE[index],
                topLeft = Offset(x * unit, 0f),
                size = Size(width * unit, this.size.height),
            )
        }
    }
}

/** The app icon as a view: its bars on the icon's black tile. */
@Composable
fun SpectraLogo(modifier: Modifier = Modifier, size: Dp = 78.dp) {
    val tile = RoundedCornerShape(size * 0.225f)
    Box(
        modifier = modifier
            .size(size)
            .shadow(16.dp, tile)
            .background(Color.Black, tile)
            .border(1.dp, Color.White.copy(alpha = 0.16f), tile),
        contentAlignment = Alignment.Center,
    ) {
        SpectraMark(size = size * 600f / 1024f)
    }
}

/** Each bar's leading edge and width, in 600ths of the side. */
private val BARS = listOf(0f to 185f, 256f to 115f, 415f to 71f, 512f to 44f, 573f to 27f)

// design-tokens: artwork
private val PALETTE = listOf(
    Color(0xFFFF8000),
    Color(0xFFFF0066),
    Color(0xFF8000FF),
    Color(0xFF0066FF),
    Color(0xFF00E0FF),
)
