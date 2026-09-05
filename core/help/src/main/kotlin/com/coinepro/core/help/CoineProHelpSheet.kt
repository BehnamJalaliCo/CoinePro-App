package com.coinepro.core.help

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * The «؟» panel — what a tool is, how to use it, and pictures of it in use.
 *
 * Every indicator, drawing tool, chart type and timeframe in this app has one, because a
 * professional tool that cannot explain itself is a tool most people will use wrongly and blame
 * themselves for. The content is the web terminal's own, exported field by field, so somebody who
 * learned Fibonacci there reads the same words here.
 *
 * The order of the sections is the order a person actually asks the questions in: *what is this*,
 * then *show me*, then *how do I use it*, then *what should I watch out for*. The pictures come
 * second rather than last for that reason — they answer "show me" faster than three paragraphs do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoineProHelpSheet(
    entry: HelpEntry,
    onDismiss: () -> Unit,
    /** Persian is the app's default; this follows the reader's language, not the device's. */
    persian: Boolean = true,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = CoineProColors.Surface,
        dragHandle = null,
    ) {
        HelpBody(entry = entry, persian = persian)
    }
}

/** The sheet's content, without the sheet — so a screenshot can render it and a screen can embed it. */
@Composable
fun HelpBody(entry: HelpEntry, persian: Boolean = true, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().background(CoineProColors.Surface),
        contentPadding = PaddingValues(
            start = CoineProSpacing.Gutter,
            end = CoineProSpacing.Gutter,
            top = CoineProSpacing.Two,
            bottom = CoineProSpacing.Four,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        item {
            Text(
                text = entry.title.inLanguage(persian),
                style = MaterialTheme.typography.titleLarge,
                color = CoineProColors.TextPrimary,
            )
        }
        entry.useCase?.let { useCase ->
            item {
                Text(
                    text = useCase.inLanguage(persian),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.Accent,
                )
            }
        }
        // Pictures early: "show me" is answered faster by one screenshot than by three paragraphs,
        // and a reader who only wanted to recognise the tool can stop here.
        if (entry.hasImages) {
            item { HelpGallery(entry = entry, persian = persian) }
        }
        entry.what?.let { what ->
            item {
                Text(
                    text = what.inLanguage(persian),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
        }
        if (!entry.how.isEmpty) {
            item { SectionTitle(if (persian) "چطور استفاده کنم" else "How to use it") }
            itemsIndexed(entry.how.inLanguage(persian)) { index, step ->
                NumberedStep(number = index + 1, text = step, persian = persian)
            }
        }
        if (!entry.tips.isEmpty) {
            item { SectionTitle(if (persian) "نکته‌ها" else "Tips") }
            items(entry.tips.inLanguage(persian)) { tip -> BulletLine(tip) }
        }
        entry.example?.let { example ->
            item { SectionTitle(if (persian) "مثال" else "Example") }
            item {
                Text(
                    text = example.inLanguage(persian),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
        }
        // Last, and given a warning colour rather than the body colour.
        //
        // Last because a reader who stops early has still read what the tool is and how to place
        // it; coloured because this is the only section that says the tool can mislead you, and a
        // caution set in the same grey as the tips is a caution nobody reads. A hairline and a
        // tinted title, not a filled panel — the surface rules here are the same everywhere else.
        entry.pitfall?.let { pitfall ->
            item {
                Text(
                    text = if (persian) "کجا گمراهت می‌کند" else "Where it misleads you",
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.Warning,
                    modifier = Modifier.padding(top = CoineProSpacing.Two, bottom = CoineProSpacing.Half),
                )
            }
            item {
                Text(
                    text = pitfall.inLanguage(persian),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
        }
    }
}

/**
 * The screenshot gallery.
 *
 * A horizontal strip rather than a stack: several entries carry six pictures of the same tool at
 * different stages, and stacking those turns the sheet into a scroll nobody reaches the end of.
 *
 * The strip has a **fixed height** and each picture takes the width its own proportions ask for.
 * These screenshots are not a uniform set — the same entry can hold a 1024×299 chart panel and a
 * 572×1024 portrait — and any layout that fixes the width instead turns the tall one into a slab
 * three times the height of the row. Fixing the height is what makes a mixed set read as one strip.
 */
@Composable
private fun HelpGallery(entry: HelpEntry, persian: Boolean) {
    var expanded by remember(entry.id) { mutableStateOf<HelpImage?>(null) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
        items(entry.images, key = { it.file }) { image ->
            HelpPicture(
                image = image,
                persian = persian,
                onClick = { expanded = image },
            )
        }
    }
    expanded?.let { image ->
        HelpPictureDialog(image = image, persian = persian, onDismiss = { expanded = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpPictureDialog(image: HelpImage, persian: Boolean, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CoineProColors.Stage,
    ) {
        // Full width here, because this sheet exists precisely to show the picture larger than the
        // strip could.
        HelpFullPicture(
            image = image,
            persian = persian,
            modifier = Modifier.fillMaxWidth().padding(CoineProSpacing.Two),
        )
    }
}

/**
 * One screenshot, decoded from the packaged asset.
 *
 * Decoded here rather than through an image library because there is exactly one source — a webp in
 * this app's own assets — and no network, no cache invalidation and no placeholder state to manage.
 * A failed decode draws nothing rather than an error box: a missing illustration should not shout
 * over the text that was the point.
 *
 * ### Why the pictures are in the APK again
 *
 * 4.42.0 moved them to the API host to get the base download under nine megabytes, and the owner's
 * answer was immediate: «تمام عکس‌های آموزشی که در نسخه‌های قبلی بودش … برگردون». A help page whose
 * pictures depend on a host that has not been set up yet is a help page with empty boxes in it, and
 * an empty box under «؟» is the one thing that reads as broken to the reader the panel exists for.
 * The pictures ship in the base module; the size budget is back at sixteen.
 */
@Composable
private fun HelpPicture(image: HelpImage, persian: Boolean, onClick: () -> Unit) {
    val bitmap = rememberHelpBitmap(image) ?: return
    val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
    // Clamped so neither a very wide panel nor a very tall portrait dominates the strip. The
    // picture keeps its own proportions inside whatever width this yields.
    val width = (GALLERY_HEIGHT * ratio).coerceIn(GALLERY_MIN_WIDTH, GALLERY_MAX_WIDTH)

    Column(
        modifier = Modifier.width(width).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = image.alt.inLanguage(persian),
            modifier = Modifier
                .fillMaxWidth()
                .height(GALLERY_HEIGHT)
                .clip(CoineProShapes.medium)
                .background(CoineProColors.Stage),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = image.alt.inLanguage(persian),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The same picture at full width, for the tap-to-enlarge sheet. */
@Composable
private fun HelpFullPicture(image: HelpImage, persian: Boolean, modifier: Modifier = Modifier) {
    val bitmap = rememberHelpBitmap(image) ?: return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = image.alt.inLanguage(persian),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                .clip(CoineProShapes.medium),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = image.alt.inLanguage(persian),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun rememberHelpBitmap(image: HelpImage): android.graphics.Bitmap? {
    val context = LocalContext.current
    return remember(image.file) {
        runCatching {
            context.assets.open("${HelpCatalog.IMAGE_DIRECTORY}/${image.file}").use {
                BitmapFactory.decodeStream(it)
            }
        }.getOrNull()
    }
}

/** One band height for the whole strip, whatever proportions the pictures have. */
private val GALLERY_HEIGHT = 180.dp
private val GALLERY_MIN_WIDTH = 120.dp
private val GALLERY_MAX_WIDTH = 300.dp

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = CoineProColors.TextPrimary,
        modifier = Modifier.padding(top = CoineProSpacing.One),
    )
}

@Composable
private fun NumberedStep(number: Int, text: String, persian: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(CoineProColors.SurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                // A step number is a prose count, so it is written in Persian digits — unlike a
                // price, which stays Latin everywhere in this app.
                text = if (persian) number.toPersianDigits() else number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.Accent,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(CoineProColors.TextMuted),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
    }
}

