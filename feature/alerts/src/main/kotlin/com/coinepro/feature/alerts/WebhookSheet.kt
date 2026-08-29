package com.coinepro.feature.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSheetEmpty
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.webhook.WebhookAttempt
import com.coinepro.core.webhook.WebhookTarget

/**
 * Where an alert is posted when it fires, and whether it arrived.
 *
 * ### Why this sheet is in the alert centre
 *
 * A webhook has no meaning apart from an alert firing. It is a delivery channel, exactly as a
 * notification is, and the reader who wants one is standing in front of the alerts it will serve.
 * The competitor puts it inside the alert dialog for the same reason — and then charges for it, and
 * requires two-factor authentication on the account before the field even appears. Neither of those
 * is here. What is kept from their design is the part that is right: the URL is judged the moment it
 * is typed, and only HTTPS on ports 80 or 443, at a domain name.
 *
 * ### The refusal is under the field, not at fire time
 *
 * That is the entire reason `WebhookUrl.validate` returns a sentence rather than a boolean. A
 * webhook accepted now and refused six hours later — when a level is finally hit, to somebody who
 * has stopped watching — is the alert-that-never-arrived failure this product is defined against,
 * one layer out.
 *
 * ### The secret is written and never read back
 *
 * The field is a password field, nothing renders the stored value, and opening an existing target
 * for editing opens that field **empty**: an untouched empty field means «leave it as it was»,
 * which is why `WebhookDraft.secretTouched` exists. `WebhookAttempt` cannot carry the value and the
 * delivery log below never shows one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebhookSheet(
    targets: List<WebhookTarget>,
    draft: WebhookDraft?,
    test: WebhookAttempt?,
    controller: AlertsController,
) {
    CoineProSheet(
        title = stringResource(R.string.webhooks_title),
        subtitle = stringResource(R.string.webhooks_subtitle),
        onDismiss = controller::closeWebhooks,
    ) {
        if (draft == null) {
            WebhookList(targets = targets, controller = controller)
        } else {
            WebhookEditor(draft = draft, test = test, controller = controller)
        }
    }
}

/** The targets the reader has, each switchable and openable, with one way to make another. */
@Composable
private fun WebhookList(targets: List<WebhookTarget>, controller: AlertsController) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = SHEET_HEIGHT)
            .verticalScroll(rememberScrollState())
            .padding(bottom = CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        if (targets.isEmpty()) {
            CoineProSheetEmpty(text = stringResource(R.string.webhooks_empty))
        }
        targets.forEach { target ->
            WebhookRow(
                target = target,
                onOpen = { controller.editWebhook(target) },
                onToggle = { controller.toggleWebhook(target) },
                onDelete = { controller.deleteWebhook(target.id) },
            )
        }
        CoineProPrimaryButton(
            text = stringResource(R.string.webhooks_new),
            onClick = controller::newWebhook,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        )
    }
}

/**
 * One target.
 *
 * The URL is shown at label weight and clipped to one line, isolated as a Latin run so a
 * right-to-left row does not reorder the slashes. It is shown at all because a reader with three
 * webhooks tells them apart by where they point as often as by what they named them — and because
 * a URL nobody can see is a URL nobody can check when the bot stops answering.
 */
@Composable
private fun WebhookRow(
    target: WebhookTarget,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = target.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (target.enabled) CoineProColors.TextPrimary else CoineProColors.TextMuted,
                textAlign = TextAlign.Right,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = BidiText.isolateLtr(target.url),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                textAlign = TextAlign.Right,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Pill(
            label = stringResource(
                if (target.enabled) R.string.webhooks_on else R.string.webhooks_off,
            ),
            onClick = onToggle,
        )
        Pill(label = stringResource(R.string.webhooks_delete), onClick = onDelete, destructive = true)
    }
}

/** Name, URL, secret, switch, test, save. In the order somebody fills them in. */
@Composable
private fun WebhookEditor(draft: WebhookDraft, test: WebhookAttempt?, controller: AlertsController) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = SHEET_HEIGHT)
            .verticalScroll(rememberScrollState())
            .padding(bottom = CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        CoineProTextField(
            value = draft.name,
            onValueChange = controller::setWebhookName,
            label = stringResource(R.string.webhooks_name),
            supporting = stringResource(R.string.webhooks_name_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter),
        )

        // The refusal is only shown once something has been typed. `WebhookUrl` calls an empty URL
        // a refusal — correctly, the store must not take one — but a form that opens already
        // complaining is a form that reads as broken before the reader has done anything.
        val refusal = draft.urlRefusal?.takeIf { draft.url.isNotEmpty() }
        CoineProTextField(
            value = draft.url,
            onValueChange = controller::setWebhookUrl,
            label = stringResource(R.string.webhooks_url),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            isError = refusal != null,
            // The module's own Persian sentence, not one invented here: the rule and the words for
            // it belong together, or they drift and the reader is told something that is not the
            // reason they were refused.
            supporting = refusal?.reason ?: stringResource(R.string.webhooks_url_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter),
        )

        CoineProTextField(
            value = draft.secret,
            onValueChange = controller::setWebhookSecret,
            label = stringResource(R.string.webhooks_secret),
            secret = true,
            supporting = stringResource(
                if (draft.editing) R.string.webhooks_secret_keep else R.string.webhooks_secret_hint,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.webhooks_enabled),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Pill(
                label = stringResource(
                    if (draft.enabled) R.string.webhooks_on else R.string.webhooks_off,
                ),
                onClick = { controller.setWebhookEnabled(!draft.enabled) },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Only offered once the URL would be accepted. A test against a URL the app has already
            // refused would report a failure the reader has been told about in the line above, and
            // teach them that the button does not work.
            if (draft.valid) {
                Pill(label = stringResource(R.string.webhooks_test), onClick = controller::testWebhook)
            }
            test?.let { attempt ->
                Text(
                    text = attempt.summary(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (attempt.delivered) CoineProColors.TextSecondary else CoineProColors.Sell,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Text(
            text = stringResource(R.string.webhooks_rules),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            textAlign = TextAlign.Right,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter),
        )

        CoineProPrimaryButton(
            text = stringResource(R.string.webhooks_save),
            onClick = controller::saveWebhook,
            enabled = draft.valid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter),
        )
        Pill(
            label = stringResource(R.string.webhooks_back),
            onClick = controller::closeWebhookEditor,
            modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
        )
    }
}

/**
 * One delivery, as a line the reader can act on.
 *
 * Outcome first, because that is the answer; then the status the receiver gave and how long it
 * took, both Latin and both isolated — they are figures, and a reader comparing a latency against
 * their own server's log needs the same digits their server printed. The error is last and only
 * where there is one.
 *
 * Never the body and never the secret. `WebhookAttempt` cannot carry either, and this does not
 * reach around it.
 */
internal fun WebhookAttempt.summary(): String = buildList {
    add(outcome.label)
    status?.let { add(BidiText.isolateLtr(it.toString())) }
    if (latencyMillis > 0) add(BidiText.isolateLtr("${latencyMillis}ms"))
    error?.takeIf { it.isNotBlank() && it != outcome.label }?.let(::add)
}.joinToString(SEPARATOR)

/**
 * How many deliveries an alert's history is summarising.
 *
 * A prose count, so Persian digits — the rule this app follows everywhere: how many things there
 * are is prose, and a status code or a latency is a market-side figure and stays Latin.
 */
internal fun deliveryCount(count: Int): String = count.toPersianDigits()

/** A small neutral pill. The same one the editor sheet uses; the sheet's one gold object is save. */
@Composable
private fun Pill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (destructive) CoineProColors.Sell else CoineProColors.TextMuted,
        modifier = modifier
            .clip(CoineProPillShape)
            .background(CoineProColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
    )
}

/** Between the parts of a delivery line. */
private const val SEPARATOR = " · "

/**
 * How tall the sheet's own column may get.
 *
 * Bounded for the reason the editor's is: a scrolling column inside a sheet has no height of its
 * own and would otherwise measure past the handle that tells the reader they are in a sheet.
 */
private val SHEET_HEIGHT = 520.dp
