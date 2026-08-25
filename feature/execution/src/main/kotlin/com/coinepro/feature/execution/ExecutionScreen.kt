package com.coinepro.feature.execution

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSkeleton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.resolve
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.execution.ExecutionStatus
import com.coinepro.core.execution.ExecutionVenue
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.SignalDirection
import com.coinepro.core.signals.SignalController
import java.util.UUID

/**
 * Sending one signal to a venue.
 *
 * The whole screen is built around a single distinction: a request that has been *queued* is not an
 * open trade. Every status below says which of the two it is, in the venue's own words where there
 * are any, because a reader who believes they are in a position when they are not will size their
 * next one wrongly.
 *
 * Nothing about the setup is editable. Symbol, direction, entry, stop and targets belong to the
 * server; the only thing this screen contributes is quantity and an explicit confirmation.
 */
@Composable
fun ExecutionScreen(
    signalId: Long,
    signalController: SignalController,
    executionController: ExecutionController,
    onOpenConnections: () -> Unit,
) {
    LaunchedEffect(signalId) {
        signalController.loadDetail(signalId)
        executionController.refreshConnections()
    }
    DisposableEffect(signalId) {
        onDispose {
            executionController.clearExecution()
            signalController.clearDetail()
        }
    }

    val signalState by signalController.detailState.collectAsStateWithLifecycle()
    val connectionState by executionController.connections.collectAsStateWithLifecycle()
    val executionState by executionController.execution.collectAsStateWithLifecycle()
    val signal = signalState.signal
    val venue = when (signal?.market) {
        MarketType.FOREX -> ExecutionVenue.MT5
        MarketType.CRYPTO -> ExecutionVenue.LBANK
        null -> null
    }
    val connection = when (venue) {
        ExecutionVenue.MT5 -> connectionState.mt5
        ExecutionVenue.LBANK -> connectionState.lbank
        null -> null
    }
    val connectionUsable = when (venue) {
        ExecutionVenue.MT5 -> connection?.connected == true
        ExecutionVenue.LBANK -> connection?.configured == true
        null -> false
    }

    var quantityText by remember(signalId) { mutableStateOf("") }
    var confirmed by remember(signalId) { mutableStateOf(false) }
    val clientRequestId = remember(signalId) { UUID.randomUUID().toString() }
    val quantity = quantityText.toDoubleOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
        contentPadding = PaddingValues(
            horizontal = CoineProSpacing.Gutter,
            vertical = CoineProSpacing.Gutter,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = CoineProSpacing.Half),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.execution_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = CoineProColors.TextPrimary,
                )
                Text(
                    text = stringResource(R.string.execution_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
        }

        if (signalState.loading || connectionState.loading) {
            item {
                CoineProCard(modifier = Modifier.fillMaxWidth()) {
                    CoineProSkeleton(Modifier.fillMaxWidth(0.5f), height = 22.dp)
                    Spacer(Modifier.height(CoineProSpacing.OneHalf))
                    CoineProSkeleton(Modifier.fillMaxWidth(), height = 14.dp)
                }
            }
        }

        signalState.error?.let { item { Notice(it.resolve(), CoineProColors.Sell) } }
        connectionState.error?.let { item { Notice(it, CoineProColors.Sell) } }

        if (signal != null) {
            item {
                CoineProCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = BidiText.isolateLtr(signal.symbol),
                            style = MaterialTheme.typography.titleMedium,
                            color = CoineProColors.TextPrimary,
                        )
                        Text(
                            text = stringResource(signal.direction.labelRes()),
                            style = MaterialTheme.typography.labelLarge,
                            color = when (signal.direction) {
                                SignalDirection.BUY -> CoineProColors.Buy
                                SignalDirection.SELL -> CoineProColors.Sell
                                SignalDirection.NEUTRAL -> CoineProColors.TextSecondary
                            },
                        )
                    }
                    Spacer(Modifier.height(CoineProSpacing.OneHalf))
                    signal.timeframe?.let {
                        Level(stringResource(R.string.execution_timeframe), it, CoineProColors.TextSecondary)
                    }
                    Level(
                        stringResource(R.string.execution_entry),
                        signal.entry?.let(::priceText),
                        CoineProColors.TextPrimary,
                    )
                    Level(
                        stringResource(R.string.execution_stop),
                        signal.stopLoss?.let(::priceText),
                        CoineProColors.Sell,
                    )
                    signal.targets.sortedBy { it.level }.forEach { target ->
                        Level(
                            stringResource(R.string.execution_target, target.level),
                            target.price?.let(::priceText),
                            CoineProColors.Buy,
                        )
                    }
                }
            }
        }

        if (venue != null) {
            item {
                CoineProCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.execution_venue, venue.displayName()),
                        style = MaterialTheme.typography.titleSmall,
                        color = CoineProColors.TextPrimary,
                    )
                    Spacer(Modifier.height(CoineProSpacing.One))
                    Text(
                        text = when {
                            connection == null -> stringResource(R.string.execution_no_connection)
                            connection.connected -> stringResource(R.string.execution_connection_confirmed)
                            venue == ExecutionVenue.LBANK ->
                                stringResource(R.string.execution_lbank_pending)
                            // The venue's own words when it gave any, since only it knows why.
                            connection.status.isNotBlank() ->
                                stringResource(R.string.execution_connection_status, connection.status)
                            else -> stringResource(R.string.execution_connection_pending)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (connectionUsable) CoineProColors.Buy else CoineProColors.Warning,
                    )
                    if (!connectionUsable) {
                        Spacer(Modifier.height(CoineProSpacing.OneHalf))
                        CoineProSecondaryButton(
                            text = stringResource(R.string.execution_open_connections),
                            onClick = onOpenConnections,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (executionState.execution == null && signal != null && venue != null) {
            item {
                CoineProCard(modifier = Modifier.fillMaxWidth()) {
                    CoineProTextField(
                        value = quantityText,
                        onValueChange = { raw -> quantityText = raw.filter { it.isDigit() || it == '.' } },
                        label = stringResource(
                            if (venue == ExecutionVenue.MT5) {
                                R.string.execution_quantity_lot
                            } else {
                                R.string.execution_quantity_base
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.height(CoineProSpacing.One))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = confirmed,
                            onCheckedChange = { confirmed = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = CoineProColors.Gold,
                                checkmarkColor = CoineProColors.OnAccent,
                                uncheckedColor = CoineProColors.TextMuted,
                            ),
                        )
                        Text(
                            text = stringResource(R.string.execution_confirm_checkbox),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoineProColors.TextSecondary,
                        )
                    }
                    Spacer(Modifier.height(CoineProSpacing.One))

                    val ready = connectionUsable &&
                        signal.status == "active" &&
                        signal.direction in setOf(SignalDirection.BUY, SignalDirection.SELL) &&
                        quantity != null && quantity > 0 && confirmed && !executionState.loading
                    CoineProPrimaryButton(
                        text = stringResource(R.string.execution_confirm),
                        onClick = {
                            if (!ready) return@CoineProPrimaryButton
                            executionController.executeSignal(
                                signalId = signal.id,
                                venue = venue,
                                quantity = requireNotNull(quantity),
                                clientRequestId = clientRequestId,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().alpha(if (ready) 1f else 0.45f),
                    )
                }
            }
        }

        executionState.error?.let { item { Notice(it, CoineProColors.Sell) } }

        executionState.execution?.let { execution ->
            item {
                CoineProCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.execution_status_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextSecondary,
                    )
                    Text(
                        text = stringResource(execution.status.labelRes()),
                        style = MaterialTheme.typography.titleLarge,
                        color = execution.status.colour(),
                    )
                    Spacer(Modifier.height(CoineProSpacing.One))
                    Level(stringResource(R.string.execution_venue_label), venue?.displayName(), CoineProColors.TextSecondary)
                    Level(
                        stringResource(R.string.execution_product),
                        execution.product.takeIf { it.isNotBlank() },
                        CoineProColors.TextSecondary,
                    )
                    Level(
                        stringResource(R.string.execution_quantity),
                        // Server-owned text, isolated rather than reparsed: re-rounding a quantity
                        // the venue already accepted would show a number nobody submitted.
                        execution.quantity.takeIf { it.isNotBlank() }?.let(BidiText::isolateLtr),
                        CoineProColors.TextSecondary,
                    )
                    execution.providerOrderId?.let {
                        Level(
                            stringResource(R.string.execution_provider_order),
                            BidiText.isolateLtr(it),
                            CoineProColors.TextSecondary,
                        )
                    }

                    Spacer(Modifier.height(CoineProSpacing.OneHalf))
                    Notice(
                        // A failure carries the provider's own message; everything else is a
                        // description of a state the client can read off the enum.
                        message = execution.errorMessage
                            ?.takeIf { execution.status == ExecutionStatus.FAILED }
                            ?: stringResource(execution.status.explanationRes()),
                        accent = execution.status.colour(),
                    )

                    if (execution.canRequestClose) {
                        Spacer(Modifier.height(CoineProSpacing.OneHalf))
                        CoineProSecondaryButton(
                            text = stringResource(
                                if (execution.status == ExecutionStatus.QUEUED) {
                                    R.string.execution_cancel_queued
                                } else {
                                    R.string.execution_request_close
                                },
                            ),
                            onClick = executionController::requestClose,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Level(label: String, value: String?, colour: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
        Text(
            // A missing level is an em dash, never a zero: one says "not set", the other is a price.
            text = value ?: stringResource(R.string.execution_value_missing),
            style = CoineProTextStyles.RowFigure,
            color = if (value == null) CoineProColors.TextMuted else colour,
        )
    }
}

@Composable
private fun Notice(message: String, accent: Color) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.10f), MaterialTheme.shapes.medium)
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.OneHalf),
        style = MaterialTheme.typography.bodySmall,
        color = accent,
    )
}

/** Six decimals is the widest any product needs; trailing zeros are noise, so they go. */
private fun priceText(value: Double): String {
    val plain = BidiText.strip(MarketNumberFormatter.price(value, 6))
    return BidiText.isolateLtr(plain.trimEnd('0').trimEnd('.'))
}

private fun ExecutionVenue.displayName(): String =
    if (this == ExecutionVenue.MT5) "MetaTrader 5" else "LBank"

@StringRes
private fun SignalDirection.labelRes(): Int = when (this) {
    SignalDirection.BUY -> R.string.execution_direction_buy
    SignalDirection.SELL -> R.string.execution_direction_sell
    SignalDirection.NEUTRAL -> R.string.execution_direction_neutral
}

@StringRes
private fun ExecutionStatus.labelRes(): Int = when (this) {
    ExecutionStatus.QUEUED -> R.string.execution_state_queued
    ExecutionStatus.SUBMITTED -> R.string.execution_state_submitted
    ExecutionStatus.OPEN -> R.string.execution_state_open
    ExecutionStatus.CLOSE_REQUESTED -> R.string.execution_state_close_requested
    ExecutionStatus.CLOSED -> R.string.execution_state_closed
    ExecutionStatus.FAILED -> R.string.execution_state_failed
    ExecutionStatus.CANCELLED -> R.string.execution_state_cancelled
    ExecutionStatus.UNKNOWN -> R.string.execution_state_unknown
}

@StringRes
private fun ExecutionStatus.explanationRes(): Int = when (this) {
    ExecutionStatus.QUEUED -> R.string.execution_explain_queued
    ExecutionStatus.SUBMITTED -> R.string.execution_explain_submitted
    ExecutionStatus.OPEN -> R.string.execution_explain_open
    ExecutionStatus.CLOSE_REQUESTED -> R.string.execution_explain_close_requested
    ExecutionStatus.CLOSED -> R.string.execution_explain_closed
    ExecutionStatus.FAILED -> R.string.execution_explain_failed
    ExecutionStatus.CANCELLED -> R.string.execution_explain_cancelled
    ExecutionStatus.UNKNOWN -> R.string.execution_explain_unknown
}

/**
 * Only [ExecutionStatus.OPEN] and [ExecutionStatus.CLOSED] are settled states the venue has
 * confirmed. Everything between them is warning-coloured on purpose — the reader is waiting, and a
 * green pending state is exactly the misread this screen exists to prevent.
 */
@Composable
@ReadOnlyComposable
private fun ExecutionStatus.colour(): Color = when (this) {
    ExecutionStatus.OPEN -> CoineProColors.Buy
    ExecutionStatus.CLOSED -> CoineProColors.TextSecondary
    ExecutionStatus.FAILED, ExecutionStatus.UNKNOWN -> CoineProColors.Sell
    else -> CoineProColors.Warning
}
