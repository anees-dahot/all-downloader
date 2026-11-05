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
import com.anees.alldownloader.ui.theme.TextStyles
import com.anees.alldownloader.ui.components.PillButton
import com.anees.alldownloader.ui.components.ProgressBar
import com.anees.alldownloader.ui.components.TextAction
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
    val c = com.anees.alldownloader.ui.theme.AppTheme.c
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("New Version", style = TextStyles.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.accent)
            Gap(4.dp)
            Text("All Downloader ${rel.version}", style = TextStyles.title2, color = c.label)
            Gap(8.dp)
            Row { Tag("You have ${BuildConfig.VERSION_NAME}"); HGap(6.dp); Tag(fmtBytes(rel.sizeBytes)) }
            if (rel.notes.isNotBlank()) {
                Gap(16.dp)
                Text(
                    rel.notes.replace(Regex("(?m)^#+\\s*"), "").replace("**", ""),
                    style = TextStyles.subhead, color = c.secondary,
                    modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                )
            }
            Gap(22.dp)
            when (state) {
                is UpdateState.Downloading -> {
                    ProgressBar(state.progress)
                    Gap(8.dp)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Downloading ${(state.progress * 100).toInt()}%", style = TextStyles.numeric, color = c.secondary, modifier = Modifier.weight(1f))
                        TextAction("Cancel", onCancel)
                    }
                }
                is UpdateState.Ready -> PillButton("Install Now", { onInstall(state.file) }, Modifier.fillMaxWidth())
                else -> {
                    PillButton("Download Update", { onDownload(rel) }, Modifier.fillMaxWidth())
                    Gap(10.dp)
                    Row {
                        PillButton("Later", onDismiss, Modifier.weight(1f), container = c.fill, content = c.label, height = 46.dp)
                        HGap(10.dp)
                        PillButton("Skip Version", { onSkip(rel) }, Modifier.weight(1f), container = c.fill, content = c.label, height = 46.dp)
                    }
                }
            }
        }
    }
}
