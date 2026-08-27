package com.coinepro.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.model.AvatarBase
import com.coinepro.core.model.AvatarSpec
import kotlinx.coroutines.launch

/**
 * The composer in a sheet, with the system picture picker wired to it.
 *
 * The launcher lives here rather than inside [AvatarComposerBody] for one reason worth keeping:
 * an activity-result launcher cannot exist in a screenshot render, and the body is the part the
 * screenshot tests draw. Splitting them means the panel is testable and the Android glue is one
 * small file that is not.
 *
 * `PickVisualMedia` rather than `GetContent`: it is the photo picker Android added precisely so an
 * app can ask for one image without asking for permission to read every file on the phone. This app
 * declares no storage permission at all and does not need one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarComposerSheet(
    current: AvatarSpec,
    initial: String,
    onSave: (AvatarSpec) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Seeded from the spec, so reopening the composer on an avatar that is already a photograph
    // shows that photograph as the chosen tile rather than as nothing.
    var photoPath by remember(current) {
        mutableStateOf((current.base as? AvatarBase.Photo)?.path)
    }
    var pending by remember { mutableStateOf<android.net.Uri?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> pending = uri }

    // The import is I/O — a decode, a rotate and a write — so it happens off the picker's callback
    // rather than in it. A callback that blocks is a frozen picker closing animation.
    LaunchedEffect(pending) {
        val source = pending ?: return@LaunchedEffect
        pending = null
        scope.launch {
            ProfilePhoto.import(context, source)?.let { path -> photoPath = path }
        }
    }

    CoineProSheet(
        title = stringResource(R.string.avatar_title),
        subtitle = stringResource(R.string.avatar_subtitle),
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        AvatarComposerBody(
            current = current,
            initial = initial,
            photoPath = photoPath,
            onSave = onSave,
            onCancel = onDismiss,
            onPickPhoto = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
    }
}
