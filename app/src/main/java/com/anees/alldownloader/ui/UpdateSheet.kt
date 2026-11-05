package com.anees.alldownloader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.anees.alldownloader.BuildConfig
import com.anees.alldownloader.ui.components.Gap
import com.anees.alldownloader.ui.components.HGap
import com.anees.alldownloader.ui.components.Tag
import com.anees.alldownloader.ui.components.fmtBytes
import com.anees.alldownloader.ui.theme.Eyebrow
import com.anees.alldownloader.update.Release
import com.anees.alldownloader.update.UpdateState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    state: UpdateState,
    onDownload: (Release) -> Unit,
    onCancel: () -> Unit,
    onInstall: (File) -> Unit,
    onSkip: (Release) -> Unit,
    onDismiss: () -> Unit,
) {
    val rel = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Ready -> state.release
        else -> return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("UPDATE AVAILABLE", style = Eyebrow, color = MaterialTheme.colorScheme.primary)
            Gap(6.dp)
            Text("Version ${rel.version}", style = MaterialTheme.typography.headlineSmall)
            Gap(6.dp)
            Row { Tag("NOW ${BuildConfig.VERSION_NAME}"); HGap(6.dp); Tag(fmtBytes(rel.sizeBytes).uppercase()) }
            if (rel.notes.isNotBlank()) {
                Gap(16.dp)
                Text(
                    rel.notes.replace(Regex("(?m)^#+\\s*"), "").replace("**", ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                )
            }
            Gap(20.dp)
            when (state) {
                is UpdateState.Downloading -> {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Butt, gapSize = 0.dp, drawStopIndicator = {})
                    Gap(8.dp)
                    Row {
                        Text("Downloading ${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onCancel) { Text("Cancel") }
                    }
                }
                is UpdateState.Ready -> Button(onClick = { onInstall(state.file) }, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) { Text("Install now") }
                else -> {
                    Button(onClick = { onDownload(rel) }, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) { Text("Download update") }
                    Gap(8.dp)
                    Row {
                        OutlinedButton(onClick = onDismiss, Modifier.weight(1f), shape = MaterialTheme.shapes.large) { Text("Later") }
                        HGap(8.dp)
                        OutlinedButton(onClick = { onSkip(rel) }, Modifier.weight(1f), shape = MaterialTheme.shapes.large) { Text("Skip version") }
                    }
                }
            }
        }
    }
}
