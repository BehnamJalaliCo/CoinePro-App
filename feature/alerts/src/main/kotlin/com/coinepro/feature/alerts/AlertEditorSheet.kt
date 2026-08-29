package com.coinepro.feature.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSegmentTabs
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSheetEmpty
import com.coinepro.core.designsystem.CoineProSheetSearch
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.notifications.AlertChannel
import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertMessageTemplate
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.ChannelOp
import com.coinepro.core.notifications.MoveOp
import com.coinepro.core.notifications.PriceOp
import com.coinepro.core.symbols.SymbolMeta

/**
 * The sheet that makes and changes an alert.
 *
 * ### Only the fields the chosen condition needs
 *
 * A price condition shows one number. A channel shows two. An indicator shows a picker, a period
 * stepper and a level. What the other kinds would have asked for is **not on screen at all** — not
 * greyed out, not collapsed. A disabled field is a question the reader has to answer before they
 * can ignore it: they read the label, work out why it is dim, and look for the control that would
 * turn it on. Hiding it costs them nothing, because a field they cannot fill in tells them nothing.
 *
 * The same rule governs the period stepper, which is absent for a study that has no single lookback
 * — VWAP, MACD — rather than present and stuck.
 *
 * ### The cap is stated first
 *
 * «حداکثر ۵ شرط» sits under «+ شرط» from the first condition onwards. A limit a reader discovers by
 * pressing a button that stops working looks like a fault; one they were told about is a rule.
 *
 * ### The placeholders are chips, not typing
 *
 * `{symbol}` typed by hand comes out as `{sybmol}` often enough to matter, and nothing reports it:
 * it renders as itself in the notification, in front of the reader, at the moment their level is
 * hit. Tapping a chip cannot misspell one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlertEditorSheet(
    draft: AlertDraft,
    matches: List<SymbolMeta>,
    refusal: AlertRefusal?,
    controller: AlertsController,
) {
    CoineProSheet(
        title = stringResource(
            if (draft.editing) R.string.alerts_editor_edit else R.string.alerts_editor_new,
        ),
        subtitle = if (draft.pickingSymbol) stringResource(R.string.alerts_editor_pick_symbol) else null,
        onDismiss = controller::closeEditor,
    ) {
        if (draft.pickingSymbol) {
            SymbolPicker(
                query = draft.query,
                matches = matches,
                onQuery = controller::setQuery,
                onPick = controller::setSymbol,
            )
        } else {
            EditorForm(draft = draft, refusal = refusal, controller = controller)
        }
    }
}

/**
 * The market picker.
 *
 * The list it searches has already been filtered through `SymbolArtwork.covers` by the controller,
 * so nothing here can be chosen that would later draw as a blank disc in the alert list. An empty
 * query lists the catalogue rather than nothing, because somebody who does not know the ticker still
 * has to be able to find their market.
 */
@Composable
private fun SymbolPicker(
    query: String,
    matches: List<SymbolMeta>,
    onQuery: (String) -> Unit,
    onPick: (String) -> Unit,
) {
    CoineProSheetSearch(
        value = query,
        onValueChange = onQuery,
        placeholder = stringResource(R.string.alerts_editor_search),
        modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
    )
    if (matches.isEmpty()) {
        CoineProSheetEmpty(text = stringResource(R.string.alerts_editor_no_market))
        return
    }
    LazyColumn(
        modifier = Modifier.heightIn(max = PICKER_HEIGHT),
        contentPadding = PaddingValues(vertical = CoineProSpacing.One),
    ) {
        items(matches, key = { it.symbol }) { meta ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(meta.symbol) }
                    .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoineProAssetLogo(symbol = meta.symbol, size = 28.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = BidiText.isolateLtr(meta.pretty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextPrimary,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = meta.listDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                        textAlign = TextAlign.Right,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** The form, in the order a reader answers it: what, then when, then how, then what it says. */
@Composable
private fun EditorForm(draft: AlertDraft, refusal: AlertRefusal?, controller: AlertsController) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = FORM_HEIGHT)
            .verticalScroll(rememberScrollState())
            .padding(bottom = CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        ChosenSymbol(symbol = draft.symbol, onChange = { controller.setPickingSymbol(true) })

        ScopeRow(draft = draft, onSelect = controller::setScopeList)

        draft.conditions.forEachIndexed { index, condition ->
            ConditionBlock(
                index = index,
                condition = condition,
                removable = draft.conditions.size > 1,
                drawings = draft.drawings,
                controller = controller,
            )
        }

        AddConditionRow(draft = draft, onAdd = controller::addCondition)

        FieldLabel(stringResource(R.string.alerts_frequency))
        CoineProChipRow(
            options = AlertFrequency.entries.map {
                CoineProChip(id = it.name, label = AlertVocabulary.frequency(it))
            },
            selectedId = draft.frequency.name,
            onSelect = { id -> AlertFrequency.entries.firstOrNull { it.name == id }?.let(controller::setFrequency) },
            compact = true,
        )

        VenueRow(
            draft = draft,
            serverOffered = controller.canUseServer(draft),
            onSelect = controller::setVenue,
        )

        FieldLabel(stringResource(R.string.alerts_channels))
        ChannelRow(selected = draft.channels, onToggle = controller::toggleChannel)

        LoudnessRow(draft = draft, onSelect = controller::setLoudness)

        MessageField(draft = draft, controller = controller)

        refusal?.let { reason ->
            Text(
                text = stringResource(
                    when (reason) {
                        AlertRefusal.LIST_FULL -> R.string.alerts_full
                        AlertRefusal.SERVER_REFUSED -> R.string.alerts_server_refused
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.Sell,
                textAlign = TextAlign.Right,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CoineProSpacing.Gutter),
            )
        }

        CoineProPrimaryButton(
            text = stringResource(R.string.alerts_save),
            onClick = controller::save,
            enabled = draft.valid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = CoineProSpacing.Gutter, end = CoineProSpacing.Gutter, top = CoineProSpacing.One),
        )
    }
}

/** The chosen market, with the way back to the picker beside it rather than hidden in the title. */
@Composable
private fun ChosenSymbol(symbol: String, onChange: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoineProAssetLogo(symbol = symbol, size = 32.dp)
        Text(
            text = BidiText.isolateLtr(symbol),
            style = MaterialTheme.typography.titleSmall,
            color = CoineProColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        TapChip(label = stringResource(R.string.alerts_editor_change_symbol), onClick = onChange)
    }
}

/**
 * One condition: its type, its operator, and only the numbers that type asks for.
 *
 * The heading row appears only once there is more than one condition. A block labelled «شرط ۱» on a
 * form with a single condition is a form telling the reader about a feature instead of asking them
 * a question.
 */
@Composable
private fun ConditionBlock(
    index: Int,
    condition: AlertConditionDraft,
    removable: Boolean,
    /** This symbol's drawings, for the one kind that picks from them. */
    drawings: List<AlertDrawingOption>,
    controller: AlertsController,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
        if (removable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = CoineProSpacing.Gutter, end = CoineProSpacing.Gutter, top = CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = (index + 1).toPersianDigits(),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(CoineProIcons.Close),
                    contentDescription = stringResource(R.string.alerts_condition_remove),
                    tint = CoineProColors.TextMuted,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { controller.removeCondition(index) },
                )
            }
        }

        CoineProSegmentTabs(
            options = AlertTriggerKind.entries.map { it to stringResource(it.labelRes()) },
            selected = condition.kind,
            onSelect = { controller.setConditionKind(index, it) },
        )

        when (condition.kind) {
            AlertTriggerKind.PRICE -> {
                PriceOpChips(condition.priceOp) { controller.setPriceOp(index, it) }
                NumberField(
                    value = condition.first,
                    label = stringResource(R.string.alerts_field_level),
                    onValueChange = { controller.setFirst(index, it) },
                )
            }

            AlertTriggerKind.CHANNEL -> {
                CoineProChipRow(
                    options = ChannelOp.entries.map {
                        CoineProChip(id = it.name, label = AlertVocabulary.channelOpChip(it))
                    },
                    selectedId = condition.channelOp.name,
                    onSelect = { id ->
                        ChannelOp.entries.firstOrNull { it.name == id }
                            ?.let { controller.setChannelOp(index, it) }
                    },
                    compact = true,
                )
                NumberField(
                    value = condition.first,
                    label = stringResource(R.string.alerts_field_low),
                    onValueChange = { controller.setFirst(index, it) },
                    isError = condition.boundsInverted,
                    supporting = if (condition.boundsInverted) {
                        stringResource(R.string.alerts_bounds_inverted)
                    } else {
                        null
                    },
                )
                NumberField(
                    value = condition.second,
                    label = stringResource(R.string.alerts_field_high),
                    onValueChange = { controller.setSecond(index, it) },
                    isError = condition.boundsInverted,
                )
            }

            AlertTriggerKind.MOVE -> {
                CoineProChipRow(
                    options = MoveOp.entries.map {
                        CoineProChip(id = it.name, label = AlertVocabulary.moveOpChip(it))
                    },
                    selectedId = condition.moveOp.name,
                    onSelect = { id ->
                        MoveOp.entries.firstOrNull { it.name == id }
                            ?.let { controller.setMoveOp(index, it) }
                    },
                    compact = true,
                )
                NumberField(
                    value = condition.first,
                    label = stringResource(
                        if (condition.moveOp.isPercent) {
                            R.string.alerts_field_percent
                        } else {
                            R.string.alerts_field_amount
                        },
                    ),
                    onValueChange = { controller.setFirst(index, it) },
                )
            }

            AlertTriggerKind.INDICATOR -> {
                CoineProChipRow(
                    options = AlertIndicators.ALL.map { CoineProChip(id = it.id, label = it.ticker) },
                    selectedId = condition.indicatorId,
                    onSelect = { id -> id?.let { controller.setIndicator(index, it) } },
                    compact = true,
                )
                // Hidden, not disabled, for a study with no single lookback. VWAP has none, and a
                // stepper stuck at a number nobody chose is worse than no stepper.
                condition.period?.let { period ->
                    PeriodStepper(period = period) { controller.setPeriod(index, it) }
                }
                PriceOpChips(condition.priceOp) { controller.setPriceOp(index, it) }
                NumberField(
                    value = condition.first,
                    label = stringResource(R.string.alerts_field_indicator_level),
                    onValueChange = { controller.setFirst(index, it) },
                )
            }

            AlertTriggerKind.DRAWING -> DrawingPicker(
                drawings = drawings,
                selectedId = condition.drawingId,
                onPick = { controller.setDrawing(index, it) },
            )
        }
    }
}

/**
 * The reader's own drawings on this symbol, as a list they can recognise.
 *
 * ### Why each row is a name and a price
 *
 * A drawing has no name. What the store holds is a numeric id, a tool id and a list of points, and
 * a picker offering «1731059442» twice is not a choice anybody can make — that objection is what
 * kept drawing alerts out of this sheet for a release, and it was right. What a reader *can* answer
 * is «خط روند at 68,500» versus «خط روند at 71,200», because those are the two lines they drew and
 * they remember where they put them.
 *
 * The level is Latin, right-aligned and isolated, like every other market figure here, and the line
 * under the list says what the number is — the drawing's price at its last anchor — so that nobody
 * reads it as the level the alert will fire at. A sloped line's level moves, which is the entire
 * reason `AlertTrigger.DrawingTouch` resolves it again at every sample rather than storing one.
 *
 * ### Empty says what to do about it
 *
 * A reader who has drawn nothing on this symbol is not looking at a broken picker; they are one
 * step earlier than this sheet. So the empty state names that step rather than saying «موردی نیست».
 */
@Composable
private fun DrawingPicker(
    drawings: List<AlertDrawingOption>,
    selectedId: String,
    onPick: (String) -> Unit,
) {
    if (drawings.isEmpty()) {
        Text(
            text = stringResource(R.string.alerts_drawing_none),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
            textAlign = TextAlign.Right,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter),
        )
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        drawings.forEach { option ->
            val chosen = option.id == selectedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(option.id) }
                    .background(
                        if (chosen) CoineProColors.SurfaceElevated else CoineProColors.Stage,
                    )
                    .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (chosen) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    // A market figure: Latin digits, and Right rather than End — the run is Latin
                    // inside a right-to-left row and End has already put one of these on the wrong
                    // edge once in this app.
                    text = AlertSentence.number(option.level),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (chosen) CoineProColors.TextPrimary else CoineProColors.TextMuted,
                    textAlign = TextAlign.Right,
                )
            }
        }
        Text(
            text = stringResource(R.string.alerts_drawing_note),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            textAlign = TextAlign.Right,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half),
        )
    }
}

@Composable
private fun PriceOpChips(selected: PriceOp, onSelect: (PriceOp) -> Unit) {
    CoineProChipRow(
        options = PriceOp.entries.map {
            CoineProChip(id = it.name, label = AlertVocabulary.priceOpChip(it))
        },
        selectedId = selected.name,
        onSelect = { id -> PriceOp.entries.firstOrNull { it.name == id }?.let(onSelect) },
        compact = true,
    )
}

/**
 * «+ شرط», with the cap under it whether or not it has been reached.
 *
 * The button disappears at the cap rather than dimming, and the line stays, so what is on screen is
 * the rule rather than a control that has stopped responding.
 */
@Composable
private fun AddConditionRow(draft: AlertDraft, onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CoineProSpacing.Gutter, end = CoineProSpacing.Gutter, top = CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        if (draft.canAddCondition) {
            TapChip(label = stringResource(R.string.alerts_condition_add), onClick = onAdd)
        }
        Text(
            text = stringResource(
                R.string.alerts_condition_cap,
                AlertTrigger.MultiCondition.MAX_CONDITIONS.toPersianDigits(),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
    }
}

/**
 * What the alert is about: this instrument, or one of the reader's named lists.
 *
 * ### The count is on the row, in Persian digits
 *
 * «فهرست ارزها» is a name. «فهرست ارزها — ۱۲ نماد» is the thing the reader is agreeing to, and the
 * difference matters more here than anywhere else on this sheet: a watchlist alert asks its question
 * of every member independently, each with its own firing state, so twelve is twelve alerts' worth
 * of notifications from one row. TradingView keeps this behind Premium; the least this app can do is
 * say how big it is before it is chosen.
 *
 * The count is a prose count — how many things there are — so it is Persian, unlike every price on
 * this sheet. That split is the app's rule and this is the one place on the sheet it applies.
 *
 * ### Absent where there is nothing to choose between
 *
 * A reader with no named lists sees no scope control at all rather than a control with one option
 * in it. There is no question to ask them.
 */
@Composable
private fun ScopeRow(draft: AlertDraft, onSelect: (String?) -> Unit) {
    if (draft.lists.isEmpty()) return
    FieldLabel(stringResource(R.string.alerts_scope))
    CoineProChipRow(
        options = buildList {
            add(CoineProChip(id = SCOPE_SYMBOL, label = stringResource(R.string.alerts_scope_symbol)))
            draft.lists.forEach { list ->
                add(
                    CoineProChip(
                        id = list.id,
                        label = stringResource(
                            R.string.alerts_scope_list,
                            list.name,
                            list.count.toPersianDigits(),
                        ),
                    ),
                )
            }
        },
        selectedId = draft.scopeListId ?: SCOPE_SYMBOL,
        onSelect = { id -> onSelect(id?.takeIf { it != SCOPE_SYMBOL }) },
        compact = true,
    )
    if (draft.scopeListId != null) {
        Note(stringResource(R.string.alerts_scope_list_note))
    }
}

/**
 * Where the alert is decided, with what each answer costs said underneath.
 *
 * ### The note is always there, and that is the point of this control
 *
 * The two venues are not better and worse, they fail differently: a device alert needs this app
 * installed and the phone awake, and a server alert keeps watching with the app closed and needs an
 * account. Neither of those is guessable from the word «سرور», and both are discovered at the worst
 * possible moment — the one the alert was set for. So the sentence for whichever venue is selected
 * is on screen the whole time rather than behind a «؟».
 *
 * ### The server chip is absent, not disabled, and the reason is stated
 *
 * The server's route takes one price against one level on one instrument it quotes. A channel, an
 * indicator, a drawing touch, a compound condition and a watchlist scope have no server spelling at
 * all — see [ServerAlertRows.requestOf]. Offering a dim chip would set the reader looking for what
 * turns it on; the line under the row says plainly that there is nothing.
 */
@Composable
private fun VenueRow(draft: AlertDraft, serverOffered: Boolean, onSelect: (AlertVenue) -> Unit) {
    FieldLabel(stringResource(R.string.alerts_venue))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        TapChip(
            label = stringResource(R.string.alerts_venue_device),
            selected = draft.venue == AlertVenue.DEVICE,
            onClick = { onSelect(AlertVenue.DEVICE) },
        )
        if (serverOffered) {
            TapChip(
                label = stringResource(R.string.alerts_venue_server),
                selected = draft.venue == AlertVenue.SERVER,
                onClick = { onSelect(AlertVenue.SERVER) },
            )
        }
    }
    Note(
        stringResource(
            when {
                !serverOffered -> R.string.alerts_venue_server_unavailable
                draft.venue == AlertVenue.SERVER -> R.string.alerts_venue_server_note
                else -> R.string.alerts_venue_device_note
            },
        ),
    )
}

/**
 * How loud this one alert is.
 *
 * ### Why there is a control here at all
 *
 * There was not, for a release. `LocalPriceAlert.soundLevel` existed, `AlertSound.isLoud` existed,
 * and a whole Android notification channel on the alarm output existed — and every alert this app
 * had ever written sat at the default, below the threshold, so none of it ever ran. The review that
 * shaped it is *a beep is not enough to alert someone busy at work*, and until this row existed the
 * app's answer to that person was a beep.
 *
 * ### Three words, and the loud one explains itself
 *
 * See [AlertLoudness] for why steps rather than a slider. The note changes with the selection, so
 * the escalation — a different output, at the alarm volume, which is the volume people leave up —
 * is described at the moment it is chosen rather than discovered at four in the morning.
 */
@Composable
private fun LoudnessRow(draft: AlertDraft, onSelect: (AlertLoudness) -> Unit) {
    val current = AlertLoudness.of(draft.soundLevel)
    FieldLabel(stringResource(R.string.alerts_sound))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        AlertLoudness.entries.forEach { step ->
            TapChip(
                label = stringResource(step.labelRes()),
                selected = step == current,
                onClick = { onSelect(step) },
            )
        }
    }
    Note(
        stringResource(
            if (draft.loud) R.string.alerts_sound_loud_note else R.string.alerts_sound_normal_note,
        ),
    )
}

/** A supporting line under a group of controls. Muted, small, and always the same shape. */
@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        textAlign = TextAlign.Right,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
    )
}

/** The Persian name of a loudness step. */
private fun AlertLoudness.labelRes(): Int = when (this) {
    AlertLoudness.QUIET -> R.string.alerts_sound_quiet
    AlertLoudness.NORMAL -> R.string.alerts_sound_normal
    AlertLoudness.LOUD -> R.string.alerts_sound_loud
}

/**
 * The chip id standing for «this symbol».
 *
 * A sentinel rather than null because [CoineProChipRow] treats null as "nothing selected" and would
 * draw no chip at all for the default scope — which is the scope almost every alert has.
 */
private const val SCOPE_SYMBOL = "__symbol"

/**
 * The delivery channels, as an independent set rather than a scale.
 *
 * Turning every one of them off is allowed. It is a real preference — an alert somebody wants
 * recorded and does not want to be told about — and the store keeps the difference between "none"
 * and "never chose".
 */
@Composable
private fun ChannelRow(selected: Set<AlertChannel>, onToggle: (AlertChannel) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        AlertChannel.entries.forEach { channel ->
            TapChip(
                label = AlertVocabulary.channel(channel),
                selected = channel in selected,
                onClick = { onToggle(channel) },
            )
        }
    }
}

/** The reader's own wording, with the four placeholders offered rather than spelled out. */
@Composable
private fun MessageField(draft: AlertDraft, controller: AlertsController) {
    FieldLabel(stringResource(R.string.alerts_message))
    CoineProTextField(
        value = draft.message,
        onValueChange = controller::setMessage,
        label = stringResource(R.string.alerts_message),
        isError = draft.messageTooLong,
        supporting = if (draft.messageTooLong) {
            stringResource(R.string.alerts_message_too_long)
        } else {
            stringResource(R.string.alerts_message_hint)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        AlertMessageTemplate.PLACEHOLDERS.forEach { placeholder ->
            TapChip(
                // A placeholder is Latin and braced; isolated, so the braces stay round the word
                // they belong to inside a right-to-left row.
                label = BidiText.isolateLtr(placeholder),
                onClick = { controller.appendPlaceholder(placeholder) },
            )
        }
    }
}

/**
 * The lookback, as a stepper rather than a text field.
 *
 * A period is a small whole number chosen from a handful of conventional ones, and a keyboard for
 * it invites `14.5`. The bounds are clamped rather than refused, so holding the button does
 * nothing surprising at either end.
 */
@Composable
private fun PeriodStepper(period: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.alerts_period),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        TapChip(label = stringResource(R.string.alerts_period_less), onClick = { onChange(period - 1) })
        Text(
            // A lookback is a market figure — it is what the chart's own settings show — so the
            // digits are Latin like every other number on this sheet.
            text = BidiText.isolateLtr(period.toString()),
            style = MaterialTheme.typography.titleSmall,
            color = CoineProColors.TextPrimary,
        )
        TapChip(label = stringResource(R.string.alerts_period_more), onClick = { onChange(period + 1) })
    }
}

/** A numeric field. Decimal keyboard, and Persian digits are folded on the way out by the draft. */
@Composable
private fun NumberField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    supporting: String? = null,
) {
    CoineProTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = isError,
        supporting = supporting,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
    )
}

/** The name of a group of controls, at label weight so it does not compete with them. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextSecondary,
        modifier = Modifier.padding(start = CoineProSpacing.Gutter, end = CoineProSpacing.Gutter, top = CoineProSpacing.One),
    )
}

/**
 * A small pill that does something when pressed.
 *
 * Neutral rather than gold whether or not it is selected: the sheet's one gold object is its save
 * button, and a second one here would read as the thing to press.
 */
@Composable
private fun TapChip(label: String, onClick: () -> Unit, selected: Boolean = false) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) CoineProColors.TextPrimary else CoineProColors.TextMuted,
        modifier = Modifier
            .clip(CoineProPillShape)
            .background(if (selected) CoineProColors.SurfaceElevated else CoineProColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
    )
}

/** The Persian name of a condition type, as the segmented control prints it. */
private fun AlertTriggerKind.labelRes(): Int = when (this) {
    AlertTriggerKind.PRICE -> R.string.alerts_kind_price
    AlertTriggerKind.CHANNEL -> R.string.alerts_kind_channel
    AlertTriggerKind.MOVE -> R.string.alerts_kind_move
    AlertTriggerKind.INDICATOR -> R.string.alerts_kind_indicator
    AlertTriggerKind.DRAWING -> R.string.alerts_kind_drawing
}

/**
 * How tall the picker's list may get.
 *
 * Bounded because a `LazyColumn` inside a sheet's own column has no height of its own and would
 * otherwise measure to infinity. Chosen to leave the search field and the sheet's handle visible,
 * which is what tells the reader they are in a picker rather than on a screen.
 */
private val PICKER_HEIGHT = 380.dp

/** The same reason, for the form: it scrolls inside the sheet rather than growing past it. */
private val FORM_HEIGHT = 520.dp
