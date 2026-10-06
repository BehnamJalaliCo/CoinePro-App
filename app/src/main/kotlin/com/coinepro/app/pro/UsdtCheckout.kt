package com.coinepro.app.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.coinepro.app.R
import com.coinepro.core.account.PaymentsGateway
import com.coinepro.core.account.UsdtClaim
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import kotlinx.coroutines.launch

/**
 * The site's way to pay for Pro (5.25.0): a USDT transfer on BNB Smart Chain, proved by its hash.
 *
 * Nothing is taken on the reader's word. The server reads the transaction from the chain, checks
 * that it is a USDT transfer to our wallet of at least the plan's price with enough confirmations,
 * and writes the period only then; a hash pays for one period, ever. So the sheet's job is to make
 * the transfer hard to get wrong — the amount, the network and the address, each copyable, and the
 * one warning that matters — and to hand over the hash.
 *
 * Figures in Latin digits: the amount and the address are what the reader types or pastes into a
 * wallet, and a wallet reads Latin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsdtCheckoutSheet(
    planTitle: String,
    plan: String,
    amount: String,
    wallet: String,
    network: String,
    payments: PaymentsGateway,
    onDismiss: () -> Unit,
    onActivated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var hash by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf<String?>(null) }
    val failed = stringResource(R.string.usdt_failed)
    val invalid = stringResource(R.string.usdt_hash_invalid)
    val signIn = stringResource(R.string.usdt_sign_in)

    CoineProSheet(title = stringResource(R.string.usdt_title), subtitle = planTitle, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One)
                .testTag(USDT_SHEET_TAG),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            Text(
                text = stringResource(R.string.usdt_steps),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
            CopyRow(
                label = stringResource(R.string.usdt_amount_label),
                value = "$amount USDT",
                copied = copied == "amount",
                onCopy = {
                    clipboard.setText(AnnotatedString(amount))
                    copied = "amount"
                },
            )
            CopyRow(
                label = stringResource(R.string.usdt_network_label),
                value = stringResource(R.string.usdt_network_value, network),
                copied = false,
                onCopy = null,
            )
            CopyRow(
                label = stringResource(R.string.usdt_wallet_label),
                value = wallet,
                copied = copied == "wallet",
                onCopy = {
                    clipboard.setText(AnnotatedString(wallet))
                    copied = "wallet"
                },
                tag = USDT_WALLET_TAG,
            )
            Text(
                text = stringResource(R.string.usdt_warning),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = CoineProColors.Warning,
            )
            CoineProTextField(
                value = hash,
                onValueChange = {
                    hash = it.trim()
                    message = null
                },
                label = stringResource(R.string.usdt_hash_label),
                modifier = Modifier.fillMaxWidth().testTag(USDT_HASH_TAG),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                enabled = !checking,
                isError = message != null,
                supporting = message,
            )
            CoineProPrimaryButton(
                text = stringResource(if (checking) R.string.usdt_checking else R.string.usdt_submit),
                enabled = !checking && hash.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag(USDT_SUBMIT_TAG),
                onClick = {
                    if (!isTxHash(hash)) {
                        message = invalid
                        return@CoineProPrimaryButton
                    }
                    checking = true
                    scope.launch {
                        when (val result = payments.claimUsdt(plan, hash)) {
                            is UsdtClaim.Activated -> onActivated()
                            is UsdtClaim.Refused -> message = when {
                                result.needsSignIn -> signIn
                                else -> result.message ?: failed
                            }
                        }
                        checking = false
                    }
                },
            )
        }
    }
}

@Composable
private fun CopyRow(label: String, value: String, copied: Boolean, onCopy: (() -> Unit)?, tag: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CoineProColors.SurfaceRaised)
            .border(1.dp, CoineProColors.BorderSubtle, RoundedCornerShape(12.dp))
            .then(if (onCopy != null) Modifier.clickable(onClick = onCopy) else Modifier)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = CoineProColors.TextMuted)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = CoineProColors.TextPrimary)
        }
        if (onCopy != null) {
            Text(
                text = stringResource(if (copied) R.string.usdt_copied else R.string.usdt_copy),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = CoineProColors.TextSecondary,
                modifier = Modifier.padding(start = CoineProSpacing.One),
            )
        }
    }
}

/** A BSC transaction hash: `0x` and sixty-four hex digits, or the sixty-four alone. */
internal fun isTxHash(text: String): Boolean {
    val body = text.trim().removePrefix("0x").removePrefix("0X")
    return body.length == 64 && body.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

const val USDT_SHEET_TAG = "usdt-checkout"
const val USDT_WALLET_TAG = "usdt-wallet"
const val USDT_HASH_TAG = "usdt-hash"
const val USDT_SUBMIT_TAG = "usdt-submit"
