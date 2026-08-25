package com.coinepro.feature.kyc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.account.AccountController
import com.coinepro.core.account.KycState
import com.coinepro.core.account.KycSubmission
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField

/**
 * Level-one verification.
 *
 * The four fields the server asks for and nothing else. There is no upload here and no second
 * level: this is the step that unlocks the rest, and adding fields the server does not read would
 * ask the reader for their documents twice.
 *
 * The screen never judges the answers. Length and format are the server's to decide — a national id
 * checksum belongs where the registry is — so the button is enabled once the fields are non-empty
 * and any refusal is shown in the server's own words.
 */
@Composable
fun KycScreen(controller: AccountController) {
    val status by controller.kyc.collectAsStateWithLifecycle()
    val submission by controller.kycSubmission.collectAsStateWithLifecycle()

    LaunchedEffect(controller) {
        controller.refreshKyc()
        // Any outcome on screen belongs to the visit that produced it. Reopening the screen with
        // last week's refusal still showing would read as a fresh one.
        controller.clearKycSubmission()
    }

    var fullName by rememberSaveable { mutableStateOf("") }
    var nationalId by rememberSaveable { mutableStateOf("") }
    var birthDate by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }

    val sending = submission is KycSubmission.Sending
    val state = status?.state
    // Nothing to fill in while the server is already holding a submission or has accepted one.
    val settled = state == KycState.PENDING || state == KycState.APPROVED ||
        submission is KycSubmission.Accepted
    val complete = remember(fullName, nationalId, birthDate, phone) {
        listOf(fullName, nationalId, birthDate, phone).all { it.isNotBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .verticalScroll(rememberScrollState())
            .padding(CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
    ) {
        CoineProCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(state.headingRes()),
                style = MaterialTheme.typography.titleMedium,
                color = CoineProColors.TextPrimary,
            )
            Spacer(Modifier.height(CoineProSpacing.One))
            Text(
                text = stringResource(state.explanationRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
            // A rejection's reason is the reviewer's own words and is the only part of this screen
            // that tells the reader what to change.
            status?.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                Spacer(Modifier.height(CoineProSpacing.One))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.Sell,
                )
            }
        }

        if (!settled) {
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                CoineProTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = stringResource(R.string.kyc_full_name),
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(CoineProSpacing.One))
                // Digits are folded as they are typed, so what is stored is what will be sent. A
                // Persian keyboard produces Persian numerals, and Char.isDigit accepts them — the
                // field would look right and travel wrong.
                CoineProTextField(
                    value = nationalId,
                    onValueChange = { nationalId = it.foldDigitsToLatin().filter(Char::isDigit) },
                    label = stringResource(R.string.kyc_national_id),
                    enabled = !sending,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(CoineProSpacing.One))
                // The calendar is left alone. Both servers read a Jalali date, and converting one
                // here would put a second implementation of a famously fiddly calendar in front of
                // a field whose refusal message says nothing about dates.
                CoineProTextField(
                    value = birthDate,
                    onValueChange = { birthDate = it.foldDigitsToLatin() },
                    label = stringResource(R.string.kyc_birth_date),
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(CoineProSpacing.One))
                CoineProTextField(
                    value = phone,
                    onValueChange = {
                        phone = it.foldDigitsToLatin().filter { character ->
                            character.isDigit() || character == '+'
                        }
                    },
                    label = stringResource(R.string.kyc_phone),
                    enabled = !sending,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )

                (submission as? KycSubmission.Refused)?.let { refused ->
                    Spacer(Modifier.height(CoineProSpacing.OneHalf))
                    Text(
                        // The server's wording, as written. The app has no better account of why a
                        // particular id was refused, and a local guess would be one in its voice.
                        text = refused.message?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.kyc_refused_unexplained),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.Sell,
                    )
                }

                Spacer(Modifier.height(CoineProSpacing.OneHalf))
                CoineProPrimaryButton(
                    text = stringResource(if (sending) R.string.kyc_sending else R.string.kyc_submit),
                    onClick = {
                        if (!sending && complete) {
                            controller.submitKycLevel1(fullName, nationalId, birthDate, phone)
                        }
                    },
                    enabled = !sending && complete,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        status?.requiredFields?.takeIf { it.isNotEmpty() && !settled }?.let { fields ->
            Text(
                // Server field names, left in the server's spelling and isolated so a Latin name
                // does not reverse inside a Persian sentence.
                text = stringResource(R.string.kyc_required_fields, BidiText.isolateLtr(fields.joinToString("، "))),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

private fun KycState?.headingRes(): Int = when (this) {
    KycState.APPROVED -> R.string.kyc_state_approved
    KycState.PENDING -> R.string.kyc_state_pending
    KycState.REJECTED -> R.string.kyc_state_rejected
    KycState.NOT_STARTED, null -> R.string.kyc_state_not_started
}

private fun KycState?.explanationRes(): Int = when (this) {
    KycState.APPROVED -> R.string.kyc_body_approved
    KycState.PENDING -> R.string.kyc_body_pending
    KycState.REJECTED -> R.string.kyc_body_rejected
    KycState.NOT_STARTED, null -> R.string.kyc_body_not_started
}
