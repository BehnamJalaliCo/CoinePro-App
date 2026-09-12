package com.coinepro.feature.news

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing
import java.net.URI
import java.net.URLEncoder

/**
 * The picture that belongs above a story.
 *
 * ### Why this stopped being written by hand (4.74.0, run K item 6)
 *
 * It was a hundred lines of fetch-and-decode, written when nothing else in the app loaded a remote
 * image and Coil was not on the classpath. Both halves of that have since stopped being true: Coil
 * is what the logos and the avatars already load through, and the owner's recording shows what the
 * hand-written loader was costing — a column of grey rectangles down the Explore feed, because this
 * loader had no disk cache (every scroll re-fetched), no retry (one failed publisher was remembered
 * as failed for the life of the process), and nothing to show when a fetch did fail.
 *
 * So the loader is Coil's, with its disk cache and its own downsampling, and what is left here is
 * the part that is this app's judgement rather than a library's: which addresses are allowed, what
 * to draw while one arrives, and what to draw when one does not.
 *
 * ### The rules it enforces, and why each one is here
 *
 * * **`https` only.** The same rule `safeHttpsUrl` applies to a story's link in the gateway, and
 *   for the same reason: a URL that arrives over the wire is not this app's URL, and a cleartext
 *   fetch would be the one request in the app that anybody on the path can rewrite. A picture is a
 *   thing the reader trusts because the app drew it, which makes a swapped one worse than none.
 * * **A length cap.** Past [MAX_URL_LENGTH] a "URL" is a payload, not an address.
 * * **Failure renders the publisher, not a broken glyph.** See `SourcePlate`.
 */
internal object NewsImagePolicy {

    /** Past this a "URL" is a payload, not an address. */
    const val MAX_URL_LENGTH: Int = 2048

    /**
     * One usable image address, or null.
     *
     * Returns the original string rather than a normalised one on purpose: it is the cache key and
     * the fetch target, and re-spelling somebody else's URL is how a working address becomes a
     * 404 that only reproduces on one phone.
     */
    fun accept(raw: String?): String? = runCatching {
        val trimmed = raw?.trim()?.takeIf(String::isNotEmpty) ?: return@runCatching null
        if (trimmed.length > MAX_URL_LENGTH) return@runCatching null
        val uri = URI(trimmed)
        if (!uri.scheme.equals("https", ignoreCase = true)) return@runCatching null
        if (uri.host.isNullOrBlank()) return@runCatching null
        trimmed
    }.getOrNull()

    /**
     * The address to actually fetch, given a backend that will re-serve pictures.
     *
     * ### Why this exists with nothing configured behind it
     *
     * Because the reason the pictures are grey is very likely not the app. A wire story's
     * illustration lives on the publisher's own CDN, and a reader on a mobile network in Iran
     * reaches this app's backend and does not reliably reach Reuters'. No retry and no image loader
     * fixes that; only re-serving the bytes from a host the reader can already reach does, which is
     * a **backend** endpoint and not an app change.
     *
     * So this is the single seam that endpoint needs, written and tested now so that turning it on
     * later is a configuration value rather than a change to this file. [base] null — which is what
     * ships today — returns the publisher's own address unchanged, exactly as before.
     */
    fun through(base: String?, url: String): String {
        val root = base?.trim()?.removeSuffix("/")?.takeIf { it.isNotEmpty() } ?: return url
        if (accept(url) == null) return url
        return root + "?url=" + URLEncoder.encode(url, Charsets.UTF_8.name())
    }

    /**
     * The backend's picture route, or null while there is not one.
     *
     * Null until the owner's backend serves it; see [through] for what it is for and why the value
     * is not guessed here. One place to set it, and every hero follows.
     */
    var proxyBase: String? = null
}

/**
 * A story's picture, at the top of the story, at a fixed aspect so the text below it never moves.
 *
 * The owner asked for this twice and in the same words: where a headline has a picture, the picture
 * goes **above** it. So this is drawn before the eyebrow, before the headline, before anything —
 * and it is the reason the card's own padding is applied to the text rather than to the card, since
 * a hero inset by sixteen points on every side is a thumbnail with delusions.
 *
 * ### The three states, and why the third one changed (run K item 6)
 *
 * * **Nothing**, for a story with no picture or an address that is not `https`. The layout below is
 *   written to read properly without one — it has to be, because a wire row can genuinely arrive
 *   without an illustration — and a placeholder for a picture that was never promised is furniture.
 * * **A plate the height the picture will be**, while it loads, so the headline does not jump down
 *   the screen mid-read.
 * * **The publisher, and a way to ask again**, when the fetch fails. This used to render *nothing*,
 *   on the argument that a broken-image glyph is worse than no picture — which is true of a glyph
 *   and was not true of what shipped: the picture had already reserved its space by then, so what
 *   the reader actually got was the empty grey rectangle the owner's recording is full of. A
 *   monogram of the source with «تلاش دوباره» under it says what the space is for and offers the
 *   one thing that sometimes works, which is asking again on a better connection.
 *
 * There is no scrim and no text over the picture, which is a design decision that also happens to
 * be the policy: `scripts/quality/check-motion-policy.sh` allow-lists gradients by file, a scrim is
 * a gradient, and this file is not on that list. Text under the picture rather than over it is what
 * a newspaper does anyway — a headline reversed out of a photograph is legible only when the
 * photograph was chosen for it, and these are chosen by somebody else's wire feed.
 */
@Composable
internal fun NewsHero(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    aspectRatio: Float = HERO_ASPECT,
    /**
     * The picture's own corners.
     *
     * Passed in rather than fixed because the two places a hero appears want different answers: in
     * a card it has to carry that card's top corners exactly, or the card's fill shows through as a
     * notch behind a picture with a smaller radius; on the reading page it is edge to edge and has
     * no corners at all.
     */
    shape: Shape? = null,
    /**
     * Who published the story, for the plate a failed fetch leaves behind.
     *
     * Null draws the plate without a monogram, which is the right answer for a fixture and for a
     * feed that does not name its source — an initial taken from an empty string is a blank disc.
     */
    source: String? = null,
) {
    val accepted = remember(url) { NewsImagePolicy.accept(url) } ?: return
    val corners = shape ?: MaterialTheme.shapes.medium
    // Bumped by the reader's tap on a failed plate. It is part of the request's key, so a bump is
    // what makes Coil fetch again rather than answer from its own memory of the failure.
    var attempt by remember(accepted) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val request = remember(accepted, attempt) {
        ImageRequest.Builder(context)
            .data(NewsImagePolicy.through(NewsImagePolicy.proxyBase, accepted))
            // A distinct key per attempt, so a retry is a retry. Without it Coil is entitled to
            // return the failure it already has and the button does nothing, which is worse than
            // no button.
            .memoryCacheKey(accepted + "#" + attempt)
            // Off. The app's motion policy has no room for decoration that reports nothing, and a
            // picture fading in under a headline the reader is already reading is exactly that.
            .crossfade(false)
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier.fillMaxWidth().aspectRatio(aspectRatio).clip(corners),
        // Cropped rather than letterboxed. A wire photograph arrives at whatever shape its
        // publisher uses and the card has one shape; bars down the sides of every third picture is
        // what makes a feed look assembled rather than designed.
        contentScale = ContentScale.Crop,
        loading = {
            Box(modifier = Modifier.fillMaxSize().background(CoineProColors.SurfaceElevated))
        },
        error = {
            SourcePlate(source = source, onRetry = { attempt += 1 })
        },
        success = { SubcomposeAsyncImageContent() },
    )
}

/**
 * What stands in for a picture that did not arrive: the publisher's initial, and a way to ask again.
 *
 * Not a broken-image glyph and not an empty grey box. A reader who can see *which* wire the story
 * came from has been told something true, and the retry is offered because the common failure here
 * is the connection rather than the picture — the same address usually works a minute later, and on
 * a feed the alternative is leaving and coming back to the whole screen.
 */
@Composable
private fun SourcePlate(source: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.SurfaceElevated)
            .clickable(onClick = onRetry)
            .padding(CoineProSpacing.One)
            .semantics { contentDescription = "news-image-retry" },
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        source?.trim()?.firstOrNull()?.let { initial ->
            Box(
                modifier = Modifier
                    .size(MONOGRAM)
                    .clip(CircleShape)
                    .background(CoineProColors.SurfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = CoineProColors.TextMuted,
                )
            }
        }
        Text(
            text = stringResource(R.string.news_image_retry),
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** The monogram disc on a failed plate: large enough to read, small enough not to be a logo. */
private val MONOGRAM = 40.dp

/** Sixteen by nine: what a wire service crops to, so a crop of a crop stays a photograph. */
internal const val HERO_ASPECT: Float = 16f / 9f
