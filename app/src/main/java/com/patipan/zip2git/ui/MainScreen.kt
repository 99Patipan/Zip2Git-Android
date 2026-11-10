@file:OptIn(ExperimentalMaterial3Api::class)

package com.patipan.zip2git.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.patipan.zip2git.R
import com.patipan.zip2git.domain.FileChange
import com.patipan.zip2git.domain.FileChange.Kind

@Composable
fun MainScreen(vm: MainViewModel) {
    val s = vm.state
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onZipPicked(uri)
    }
    val visible = remember(s.changes, s.showUnchanged) {
        if (s.showUnchanged) s.changes else s.changes.filter { it.kind != Kind.UNCHANGED }
    }
    val counts = remember(s.changes) { s.changes.groupingBy { it.kind }.eachCount() }
    val pendingCount = s.changes.size - (counts[Kind.UNCHANGED] ?: 0)

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.app_subtitle), color = Muted, fontSize = 11.sp)
                    }
                },
                actions = { Icon(Icons.Default.CloudUpload, null, tint = Accent, modifier = Modifier.padding(end = 16.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            // 01 connection
            item {
                CardBlock(stringResource(R.string.card_connection), Icons.Default.AccountCircle) {
                    OutlinedTextField(
                        value = s.tokenInput,
                        onValueChange = vm::onTokenInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.label_token)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (s.showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = vm::toggleShowToken) {
                                Icon(if (s.showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::connect, enabled = s.tokenInput.isNotBlank() && !s.busy) {
                            Text(stringResource(R.string.btn_connect))
                        }
                        OutlinedButton(
                            onClick = vm::disconnect,
                            enabled = (s.token.isNotBlank() || s.tokenInput.isNotBlank()) && !s.busy,
                        ) { Text(stringResource(R.string.btn_disconnect)) }
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
            // 03 zip
            item {
                CardBlock(stringResource(R.string.card_zip), Icons.Default.FolderZip) {
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !s.busy,
                    ) {
                        Icon(Icons.Default.UploadFile, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (s.zipName == null) R.string.btn_choose_zip else R.string.btn_choose_another))
                    }
                    if (s.zipName == null) {
                        Text(stringResource(R.string.no_zip), color = Muted)
                    } else {
                        Text(s.zipName, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.zip_summary, s.zipFileCount, s.zipTotalBytes / 1024), color = Muted)
                    }
                    if (s.commonRoot != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(s.stripRoot, vm::setStripRoot, enabled = !s.busy)
                            Text(stringResource(R.string.strip_root, s.commonRoot), fontSize = 13.sp)
                        }
                    }
                }
            }
            // 04 compare
            item {
                CardBlock(stringResource(R.string.card_compare), Icons.Default.CompareArrows) {
                    Button(
                        onClick = vm::compare,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !s.busy && s.zipName != null && s.selectedRepo != null && s.selectedBranch.isNotBlank(),
                    ) {
                        Icon(Icons.Default.FindReplace, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_compare))
                    }
                    if (s.changes.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SummaryChip("+ ${counts[Kind.ADD] ?: 0}", Good)
                            SummaryChip("~ ${counts[Kind.MODIFY] ?: 0}", Accent)
                            SummaryChip("− ${counts[Kind.DELETE] ?: 0}", Danger)
                            SummaryChip("= ${counts[Kind.UNCHANGED] ?: 0}", Muted)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(s.showUnchanged, vm::setShowUnchanged)
                            Text(stringResource(R.string.show_unchanged), fontSize = 13.sp)
                        }
                        s.massDeleteWarning?.let { Text(it, color = Danger, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        if ((counts[Kind.DELETE] ?: 0) > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(s.confirmDeletes, vm::setConfirmDeletes)
                                Text(stringResource(R.string.confirm_delete), color = Danger, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            // file list (lazy rows)
            items(visible, key = { it.path }) { change -> ChangeRow(change) }
            // 05 push
            item {
                CardBlock(stringResource(R.string.card_push), Icons.Default.RocketLaunch) {
                    OutlinedTextField(
                        value = s.commitMessage,
                        onValueChange = vm::setCommitMessage,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.label_commit_message)) },
                        singleLine = true,
                    )
                    Button(
                        onClick = vm::push,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !s.busy && pendingCount > 0 && s.commitMessage.isNotBlank(),
                    ) {
                        Icon(Icons.Default.CloudUpload, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_push, pendingCount))
                    }
                    Text(stringResource(R.string.push_hint), color = Muted, fontSize = 11.sp)
                }
            }
            // status
            item {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.progress?.let { p -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (s.busy && s.progress == null) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        if (s.busy && s.progress == null) Spacer(Modifier.width(8.dp))
                        Text(s.status, color = Muted, fontSize = 12.sp)
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
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(symbol, color = tint, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
        Text(change.path, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
