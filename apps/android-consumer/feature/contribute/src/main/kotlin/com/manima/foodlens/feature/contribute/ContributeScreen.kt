package com.manima.foodlens.feature.contribute

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.manima.foodlens.core.data.PhotoKind
import com.manima.foodlens.core.ui.AaharTopBar
import com.manima.foodlens.core.ui.MessageState
import java.io.File
import java.util.UUID

@Composable
fun ContributeRoute(
    onBack: () -> Unit,
    onMyContributions: () -> Unit,
    viewModel: ContributeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        AaharTopBar(
            stringResource(if (state.correction) R.string.contribute_title_correction else R.string.contribute_title),
            onBack = onBack
        )
        when {
            !state.enabled -> MessageState(
                title = stringResource(R.string.contribute_disabled_title),
                body = stringResource(R.string.contribute_disabled_body),
                primaryAction = stringResource(R.string.contribute_back) to onBack
            )
            state.submitted -> MessageState(
                title = stringResource(R.string.contribute_done_title),
                body = stringResource(R.string.contribute_done_body),
                icon = Icons.Filled.CheckCircle,
                primaryAction = stringResource(R.string.contribute_view_mine) to onMyContributions,
                secondaryAction = stringResource(R.string.contribute_back) to onBack,
                modifier = Modifier.testTag("contribution_queued")
            )
            else -> ContributeForm(state, viewModel)
        }
    }
}

@Composable
private fun ContributeForm(state: ContributeUiState, viewModel: ContributeViewModel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp)
    ) {
        Text(
            stringResource(
                if (state.correction) R.string.contribute_intro_correction else R.string.contribute_intro,
                state.gtin14
            ),
            style = MaterialTheme.typography.bodyLarge
        )
        SLOTS.forEach { (kind, label, hint) ->
            PhotoSlotCard(
                title = stringResource(label),
                hint = stringResource(hint),
                slot = state.photos[kind],
                onPicked = { viewModel.onPhoto(kind, it) },
                onRotate = { viewModel.rotate(kind) },
                onRemove = { viewModel.remove(kind) }
            )
        }
        OutlinedTextField(
            value = state.productName,
            onValueChange = viewModel::onName,
            label = { Text(stringResource(R.string.contribute_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.brandName,
            onValueChange = viewModel::onBrand,
            label = { Text(stringResource(R.string.contribute_brand)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::onNotes,
            label = {
                Text(
                    stringResource(
                        if (state.correction) R.string.contribute_notes_correction else R.string.contribute_notes
                    )
                )
            },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.contribute_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (state.error) {
            SubmitError.NeedPhoto -> ErrorText(stringResource(R.string.contribute_need_photo))
            SubmitError.ImageUnreadable -> ErrorText(stringResource(R.string.contribute_bad_image))
            SubmitError.None -> Unit
        }
        Button(
            onClick = viewModel::submit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("contribute_submit")
        ) {
            Text(stringResource(if (state.submitting) R.string.contribute_preparing else R.string.contribute_submit))
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

private data class Slot(val kind: PhotoKind, val label: Int, val hint: Int)

private val SLOTS = listOf(
    Slot(PhotoKind.FRONT, R.string.slot_front, R.string.slot_front_hint),
    Slot(PhotoKind.INGREDIENTS, R.string.slot_ingredients, R.string.slot_ingredients_hint),
    Slot(PhotoKind.NUTRITION, R.string.slot_nutrition, R.string.slot_nutrition_hint),
    Slot(PhotoKind.BACK, R.string.slot_back, R.string.slot_back_hint)
)

/** Camera via TakePicture into app cache (FileProvider), or Photo Picker. No storage permission (SPEC §10.1). */
@Composable
private fun PhotoSlotCard(
    title: String,
    hint: String,
    slot: PhotoSlot?,
    onPicked: (Uri) -> Unit,
    onRotate: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pending?.let(Uri::parse)
        if (saved && uri != null) onPicked(uri)
        pending = null
    }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPicked) }
    val cameraAvailable = remember {
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(72.dp)
                ) {
                    if (slot != null) {
                        AsyncImage(
                            model = slot.uri,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.rotate(slot.rotation.toFloat())
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (slot != null) {
                    IconButton(onClick = onRotate) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.contribute_rotate, title)
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.contribute_remove, title)
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cameraAvailable) {
                    OutlinedButton(
                        onClick = {
                            val uri = context.captureUri()
                            pending = uri.toString()
                            takePicture.launch(uri)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.contribute_take_photo)) }
                }
                OutlinedButton(
                    onClick = {
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.contribute_choose_photo)) }
            }
        }
    }
}

/** Authority declared in :app's manifest as `${applicationId}.fileprovider`. */
private fun Context.captureUri(): Uri {
    val file = File(cacheDir, "capture/${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
    return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
}
