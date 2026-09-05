package com.coinepro.feature.kyc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.account.AccountController
import com.coinepro.core.account.KycDocumentType
import com.coinepro.core.account.KycIdentity
import com.coinepro.core.account.KycState
import com.coinepro.core.account.KycSubmission
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import java.util.Locale

/**
 * Level-one verification.
 *
 * ### Region-aware, and the same four answers for an Iranian reader
 *
 * The form was «کد ملی» hard-wired as its second field, which is the right form for an Iranian
 * reader and a wrong one for everybody else. It is a country and a document now: the country opens
 * as Iran, the document as the national card, and a reader in Tehran types the same ten digits into
 * a field with the same label and nothing about their visit has changed. A reader anywhere else
 * picks their country from the list and names a passport or a licence, and the server receives the
 * generic fields — see `KycIdentity` and `KycLevel1Request.of`.
 *
 * The screen never judges the answers. Length and format are the server's to decide — a national
 * id checksum belongs where the registry is — so the button is enabled once the fields are
 * non-empty and any refusal is shown in the server's own words.
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
    var country by rememberSaveable { mutableStateOf(KycIdentity.IRAN) }
    var documentType by rememberSaveable { mutableStateOf(KycDocumentType.NATIONAL_ID) }
    var documentNumber by rememberSaveable { mutableStateOf("") }
    var birthDate by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var choosingCountry by rememberSaveable { mutableStateOf(false) }

    val sending = submission is KycSubmission.Sending
    val state = status?.state
    // Nothing to fill in while the server is already holding a submission or has accepted one.
    // An unknown status settles the form too. Offering somebody a verification form when the app
    // does not know whether they have already been verified is how a duplicate submission happens.
    val settled = state == null || state == KycState.PENDING || state == KycState.APPROVED ||
        submission is KycSubmission.Accepted
    val complete = remember(fullName, country, documentNumber, birthDate, phone) {
        listOf(fullName, country, documentNumber, birthDate, phone).all { it.isNotBlank() }
    }
    val iranianCard = country == KycIdentity.IRAN && documentType == KycDocumentType.NATIONAL_ID

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
                CountryField(
                    code = country,
                    enabled = !sending,
                    onClick = { choosingCountry = true },
                )
                Spacer(Modifier.height(CoineProSpacing.One))
                Text(
                    text = stringResource(R.string.kyc_document_type),
                    style = MaterialTheme.typography.labelMedium,
                    color = CoineProColors.TextSecondary,
                )
                Spacer(Modifier.height(CoineProSpacing.Half))
                CoineProSegmentedControl(
                    options = KycDocumentType.entries.map { it to stringResource(it.labelRes()) },
                    selected = documentType,
                    onSelect = { documentType = it },
                )
                Spacer(Modifier.height(CoineProSpacing.One))
                // Digits are folded as they are typed, so what is stored is what will be sent. A
                // Persian keyboard produces Persian numerals, and Char.isDigit accepts them — the
                // field would look right and travel wrong. A national card is digits only; a
                // passport or licence number carries letters, so those keep them.
                CoineProTextField(
                    value = documentNumber,
                    onValueChange = { typed ->
                        documentNumber = if (iranianCard) {
                            typed.foldDigitsToLatin().filter(Char::isDigit)
                        } else {
                            typed.foldDigitsToLatin().filter { it.isLetterOrDigit() }.uppercase()
                        }
                    },
                    label = stringResource(documentType.numberLabelRes(iranianCard)),
                    enabled = !sending,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (iranianCard) KeyboardType.Number else KeyboardType.Ascii,
                    ),
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
                            controller.submitKycLevel1(
                                KycIdentity(
                                    fullName = fullName,
                                    country = country,
                                    documentType = documentType,
                                    documentNumber = documentNumber,
                                    birthDate = birthDate,
                                    phone = phone,
                                ),
                            )
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

    if (choosingCountry) {
        CountrySheet(
            selected = country,
            onSelect = { code ->
                country = code
                // The national card is an Iranian document. Leaving it selected for another
                // country would send `national_id` for a reader who has none.
                if (code != KycIdentity.IRAN && documentType == KycDocumentType.NATIONAL_ID) {
                    documentType = KycDocumentType.PASSPORT
                }
                choosingCountry = false
            },
            onDismiss = { choosingCountry = false },
        )
    }
}

/** The country, drawn as a field so the form reads as one column of answers. */
@Composable
private fun CountryField(code: String, enabled: Boolean, onClick: () -> Unit) {
    val name = remember(code) { countryName(code) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CoineProShapes.small)
            .background(CoineProColors.Surface)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = FIELD_HEIGHT)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.kyc_country),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = CoineProColors.TextPrimary,
        )
    }
}

/**
 * Every country the platform knows, searchable, Iran first.
 *
 * The list is the platform's own — `Locale.getISOCountries()` in the reader's language — rather
 * than one typed here, so a Persian reader sees «آلمان» and an English one "Germany" without a
 * table to keep in step. Iran is pinned to the top because it is where most readers are, and the
 * rest are alphabetical in whatever script the names come back in.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CountrySheet(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val countries = remember { countries() }
    val shown = remember(query, countries) {
        val needle = query.trim()
        if (needle.isEmpty()) countries else countries.filter { it.second.contains(needle, ignoreCase = true) || it.first.equals(needle, ignoreCase = true) }
    }
    CoineProSheet(title = stringResource(R.string.kyc_country), onDismiss = onDismiss) {
        CoineProTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.kyc_country_search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter),
        )
        Spacer(Modifier.height(CoineProSpacing.One))
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = SHEET_LIST_HEIGHT)) {
            items(shown, key = { it.first }) { (code, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(code) }
                        .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.OneHalf),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (code == selected) FontWeight.Bold else FontWeight.Normal,
                        color = CoineProColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = BidiText.isolateLtr(code),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                    )
                }
                HorizontalDivider(color = CoineProColors.BorderSubtle)
            }
        }
    }
}

private fun countryName(code: String): String =
    Locale("", code).getDisplayCountry(Locale.getDefault()).ifBlank { code }

private fun countries(): List<Pair<String, String>> {
    val default = Locale.getDefault()
    val all = Locale.getISOCountries()
        .map { code -> code to Locale("", code).getDisplayCountry(default).ifBlank { code } }
        .sortedBy { it.second }
    val iran = all.filter { it.first == KycIdentity.IRAN }
    return iran + all.filterNot { it.first == KycIdentity.IRAN }
}

private fun KycDocumentType.labelRes(): Int = when (this) {
    KycDocumentType.NATIONAL_ID -> R.string.kyc_document_national_id
    KycDocumentType.PASSPORT -> R.string.kyc_document_passport
    KycDocumentType.DRIVER_LICENCE -> R.string.kyc_document_driver_licence
}

/** «کد ملی» for the Iranian card, which is the name the reader knows it by; a generic label otherwise. */
private fun KycDocumentType.numberLabelRes(iranianCard: Boolean): Int = when {
    iranianCard -> R.string.kyc_national_id
    this == KycDocumentType.PASSPORT -> R.string.kyc_passport_number
    this == KycDocumentType.DRIVER_LICENCE -> R.string.kyc_licence_number
    else -> R.string.kyc_id_number
}

/**
 * Null is **not** "not started".
 *
 * They were mapped to the same heading and the same body, so a reader whose status read failed —
 * no network, a 500, or simply the first frame before the request returns — was told they had not
 * begun. Somebody with a submission already pending was then invited to submit it again. A status
 * this app does not know is a status it must not assert.
 */
private fun KycState?.headingRes(): Int = when (this) {
    KycState.APPROVED -> R.string.kyc_state_approved
    KycState.PENDING -> R.string.kyc_state_pending
    KycState.REJECTED -> R.string.kyc_state_rejected
    KycState.NOT_STARTED -> R.string.kyc_state_not_started
    null -> R.string.kyc_state_unknown
}

private fun KycState?.explanationRes(): Int = when (this) {
    KycState.APPROVED -> R.string.kyc_body_approved
    KycState.PENDING -> R.string.kyc_body_pending
    KycState.REJECTED -> R.string.kyc_body_rejected
    KycState.NOT_STARTED -> R.string.kyc_body_not_started
    null -> R.string.kyc_body_unknown
}

/** The same height as a text field, so the country reads as one of them. */
private val FIELD_HEIGHT = 56.dp

private val SHEET_LIST_HEIGHT = 420.dp
