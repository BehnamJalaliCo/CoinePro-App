@file:Suppress("unused")

package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinepro.web.WebDrawable
import com.coinepro.web.WebResources
import com.coinepro.web.drawablePainter
import com.coinepro.web.formatAndroid
import com.coinepro.web.plural
import com.coinepro.web.rememberDrawable
import com.coinepro.web.str

/*
 * `androidx.compose.ui.res`, for the browser: the same calls the phone's screens make, resolved
 * through the generated `R` ids (`WebResources`) to the exported string tables and the bundled
 * drawables. A drawable still arriving is drawn as nothing for the one frame it takes, never as a
 * box of the wrong colour.
 */

@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun stringResource(id: Int): String = str(WebResources.nameOf(id))

@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun stringResource(id: Int, vararg formatArgs: Any): String = str(WebResources.nameOf(id), *formatArgs)

@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun pluralStringResource(id: Int, count: Int): String = plural(WebResources.nameOf(id), count)

@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun pluralStringResource(id: Int, count: Int, vararg formatArgs: Any): String = plural(WebResources.nameOf(id), count, *formatArgs)

@Composable
fun painterResource(id: Int): Painter = drawablePainter(WebResources.nameOf(id)) ?: TRANSPARENT

private val TRANSPARENT: Painter = ColorPainter(Color.Transparent)

@Composable
fun booleanResource(id: Int): Boolean = WebResources.valueOf(id)?.toBooleanStrictOrNull() ?: false

@Composable
fun integerResource(id: Int): Int = WebResources.valueOf(id)?.toIntOrNull() ?: 0

@Composable
fun colorResource(id: Int): Color = com.coinepro.web.parseAndroidColor(WebResources.valueOf(id)) ?: Color.Unspecified

@Composable
fun dimensionResource(id: Int): Dp = WebResources.valueOf(id)?.removeSuffix("dp")?.removeSuffix("dip")?.toFloatOrNull()?.dp ?: 0.dp

@Composable
fun stringArrayResource(id: Int): Array<String> = emptyArray()

@Composable
fun vectorResource(id: Int): ImageVector? = (rememberDrawable(WebResources.nameOf(id)) as? WebDrawable.Vector)?.image

object ImageVector {
    @Composable
    fun vectorResource(id: Int): androidx.compose.ui.graphics.vector.ImageVector =
        (rememberDrawable(WebResources.nameOf(id)) as? WebDrawable.Vector)?.image ?: EMPTY_VECTOR

    private val EMPTY_VECTOR = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).build()
}

@Composable
fun imageResource(id: Int): ImageBitmap = (rememberDrawable(WebResources.nameOf(id)) as? WebDrawable.Bitmap)?.image ?: ImageBitmap(1, 1)
