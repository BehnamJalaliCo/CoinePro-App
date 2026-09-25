package com.coinepro.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A block of code, shown the way the editor shows it.
 *
 * ### Why this is a component and not four modifiers at the call site
 *
 * Code in a right-to-left app gets one of two things wrong, and both were shipped before this
 * existed. Left to the paragraph's own direction, every line comes out with its leading token at
 * the far end — `// nama 1` reads «1 nama //» and `plot(rsi, title = "RSI")` reads
 * «("RSI" = rsi, title)plot». Wrapped, a line of NamaScript becomes three lines that look like
 * three statements: «length =» / «input(14,» / «title =» is what the owner read in a 150 dp panel.
 *
 * So a code block is four decisions that always travel together, and the fourth is the one people
 * forget:
 *
 * * **Left to right**, as a *layout* direction and not only a text one — [LtrDirection] — so the
 *   block itself starts at the left edge rather than being pushed to the right of the card;
 * * **monospace**, at the editor's own size and line height;
 * * **no wrapping**, with a horizontal scroll instead, so a long line stays one line;
 * * **the Persian inside it left alone.** A comment or a title in Persian is Persian, and inside an
 *   LTR paragraph the bidi algorithm already lays each such run out right-to-left in its right
 *   place. Isolating them by hand — which is the fix the *prose* around the block needs — is the
 *   wrong answer here and produces the reversed line all over again.
 *
 * The editor uses these same four in `ScriptScreen`; the board uses them through this. That is what
 * «the same renderer as the editor» means in practice — not shared pixels, shared decisions, in one
 * place where a fifth decision can be added once.
 */
@Composable
fun CoineProCodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    /** Whether to draw the terminal plate and border. False for a block already inside a surface. */
    framed: Boolean = true,
) {
    LtrDirection {
        Text(
            text = code.trimEnd(),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = CODE_SIZE,
                lineHeight = CODE_LINE_HEIGHT,
                // Both, and they are not the same thing: the layout direction above puts the block
                // at the left of the card, and this puts the glyphs of each line in file order.
                textDirection = TextDirection.Ltr,
                textAlign = TextAlign.Left,
                color = CoineProColors.TextPrimary,
            ),
            softWrap = false,
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (framed) {
                        Modifier
                            .background(CoineProColors.Terminal, CoineProShapes.small)
                            .border(1.dp, CoineProColors.Border, CoineProShapes.small)
                            .padding(CoineProSpacing.One)
                    } else {
                        Modifier
                    },
                )
                // Outside the padding, so the plate stays put and the code slides inside it.
                .coineProHorizontalScroll(rememberScrollState()),
        )
    }
}

/** The editor's own measurements, so a script reads the same in a post as in the studio. */
private val CODE_SIZE = 13.sp
private val CODE_LINE_HEIGHT = 20.sp
