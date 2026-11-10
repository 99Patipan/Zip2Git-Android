package com.patipan.zip2git.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patipan.zip2git.R
import com.patipan.zip2git.domain.FileChange
import com.patipan.zip2git.domain.FileChange.Kind

@Composable
private fun glassCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = Accent,
    uncheckedColor = Color.White.copy(alpha = 0.55f),
    checkmarkColor = Color.White,
    disabledCheckedColor = Accent.copy(alpha = 0.4f),
    disabledUncheckedColor = Color.White.copy(alpha = 0.25f),
)

@Composable
fun MainScreen(vm: MainViewModel) {
    val s = vm.state
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onZipPicked(uri)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onFolderPicked(uri)
    }
    val visible = remember(s.changes, s.showUnchanged) {
        if (s.showUnchanged) s.changes else s.changes.filter { it.kind != Kind.UNCHANGED }
    }
    val counts = remember(s.changes) { s.changes.groupingBy { it.kind }.eachCount() }
    val pendingCount = s.changes.size - (counts[Kind.UNCHANGED] ?: 0)
    val bars = WindowInsets.systemBars.asPaddingValues()

    // Surface (transparent) provides the light content color for all text on the dark glass background.
    Surface(modifier = Modifier.fillMaxSize().glassBackground(), color = Color.Transparent, contentColor = TextPrimary) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = bars.calculateTopPadding() + 12.dp,
                bottom = bars.calculateBottomPadding() + 28.dp,
            ),
        ) {
            // header
            item {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.app_subtitle), color = Muted, fontSize = 14.sp)
                    }
                    Box(
                        Modifier.size(56.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.CloudUpload, null, tint = Accent, modifier = Modifier.size(28.dp)) }
                }
            }
            // 01 connection
            item {
                CardBlock(stringResource(R.string.card_connection), Icons.Default.AccountCircle) {
                    OutlinedTextField(
                        value = s.tokenInput,
                        onValueChange = vm::onTokenInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.label_token)) },
                        singleLine = true,
                        shape = FieldShape,
                        colors = glassFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (s.showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = vm::toggleShowToken) {
                                Icon(if (s.showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = stringResource(R.string.btn_connect),
                            onClick = vm::connect,
                            modifier = Modifier.weight(1f),
                            enabled = s.tokenInput.isNotBlank() && !s.busy,
                            icon = Icons.Default.Link,
                        )
                        GlassButton(
                            text = stringResource(R.string.btn_disconnect),
                            onClick = vm::disconnect,
                            modifier = Modifier.weight(1f),
                            enabled = (s.token.isNotBlank() || s.tokenInput.isNotBlank()) && !s.busy,
                            icon = Icons.Default.LinkOff,
                        )
                    }
                    if (s.username.isNotBlank()) Text(stringResource(R.string.signed_in_as, s.username), color = Good)
                }
            }
            // 02 destination
            item {
                CardBlock(stringResource(R.string.card_destination), Icons.Default.Folder) {
                    PickerField(
                        label = stringResource(R.string.label_repo),
                        value = s.selectedRepo?.fullName ?: stringResource(R.string.select_repo),
                        options = s.repos.map { it.fullName },
                        onSelect = vm::selectRepo,
                        enabled = s.repos.isNotEmpty() && !s.busy,
                    )
                    PickerField(
                        label = stringResource(R.string.label_branch),
                        value = s.selectedBranch.ifBlank { stringResource(R.string.select_branch) },
                        options = s.branches,
                        onSelect = vm::selectBranch,
                        enabled = s.branches.isNotEmpty() && !s.busy,
                    )
                }
            }
            // 03 source (ZIP file or folder)
            item {
                CardBlock(stringResource(R.string.card_source), Icons.Default.FolderZip) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = stringResource(R.string.btn_pick_zip),
                            onClick = {
                                zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !s.busy,
                            icon = Icons.Default.UploadFile,
                        )
                        GlassButton(
                            text = stringResource(R.string.btn_pick_folder),
                            onClick = { folderPicker.launch(null) },
                            modifier = Modifier.weight(1f),
                            enabled = !s.busy,
                            icon = Icons.Default.Folder,
                        )
                    }
                    if (s.sourceName == null) {
                        Text(stringResource(R.string.no_source), color = Muted)
                    } else {
                        Text(
                            stringResource(
                                if (s.sourceIsFolder) R.string.source_label_folder else R.string.source_label_zip,
                                s.sourceName,
                            ),
                            color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(stringResource(R.string.source_summary, s.sourceFileCount, s.sourceTotalBytes / 1024), color = Muted)
                    }
                    if (s.commonRoot != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(s.stripRoot, vm::setStripRoot, enabled = !s.busy, colors = glassCheckboxColors())
                            Text(stringResource(R.string.strip_root, s.commonRoot), fontSize = 13.sp)
                        }
                    }
                }
            }
            // 04 compare
            item {
                CardBlock(stringResource(R.string.card_compare), Icons.Default.CompareArrows) {
                    GlassButton(
                        text = stringResource(R.string.btn_compare),
                        onClick = vm::compare,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !s.busy && s.sourceName != null && s.selectedRepo != null && s.selectedBranch.isNotBlank(),
                        icon = Icons.Default.FindReplace,
                    )
                    if (s.changes.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SummaryChip("+ ${counts[Kind.ADD] ?: 0}", Good)
                            SummaryChip("~ ${counts[Kind.MODIFY] ?: 0}", Accent)
                            SummaryChip("− ${counts[Kind.DELETE] ?: 0}", Danger)
                            SummaryChip("= ${counts[Kind.UNCHANGED] ?: 0}", Muted)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(s.showUnchanged, vm::setShowUnchanged, colors = glassCheckboxColors())
                            Text(stringResource(R.string.show_unchanged), fontSize = 13.sp)
                        }
                        s.massDeleteWarning?.let { Text(it, color = Danger, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        if ((counts[Kind.DELETE] ?: 0) > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(s.confirmDeletes, vm::setConfirmDeletes, colors = glassCheckboxColors())
                                Text(stringResource(R.string.confirm_delete), color = Danger, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            // file list (lazy glass rows)
            items(visible, key = { it.path }) { change -> ChangeRow(change) }
            if (visible.isNotEmpty()) item { Spacer(Modifier.height(10.dp)) }
            // 05 push
            item {
                CardBlock(stringResource(R.string.card_push), Icons.Default.RocketLaunch) {
                    OutlinedTextField(
                        value = s.commitMessage,
                        onValueChange = vm::setCommitMessage,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.label_commit_message)) },
                        singleLine = true,
                        shape = FieldShape,
                        colors = glassFieldColors(),
                    )
                    GradientButton(
                        text = stringResource(R.string.btn_push, pendingCount),
                        onClick = vm::push,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !s.busy && pendingCount > 0 && s.commitMessage.isNotBlank(),
                        icon = Icons.Default.CloudUpload,
                    )
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, null, tint = Muted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.push_hint), color = Muted, fontSize = 12.sp)
                    }
                }
            }
            // status
            item {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.progress?.let { p ->
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth(),
                            color = Accent,
                            trackColor = Color.White.copy(alpha = 0.12f),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (s.busy && s.progress == null) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                        } else {
                            Icon(Icons.Default.Info, null, tint = Muted, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(s.status, color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(change: FileChange) {
    val (symbol, tint) = when (change.kind) {
        Kind.ADD -> "+" to Good
        Kind.MODIFY -> "~" to Accent
        Kind.DELETE -> "−" to Danger
        Kind.UNCHANGED -> "=" to Muted
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .padding(bottom = 6.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(symbol, color = tint, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
        Text(change.path, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
