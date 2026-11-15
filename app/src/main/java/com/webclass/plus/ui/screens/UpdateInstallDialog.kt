package com.webclass.plus.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webclass.plus.util.UpdateChecker
import com.webclass.plus.util.UpdateInstaller
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private enum class UpdateInstallStage { Confirm, Downloading, Permission, Error }

@Composable
fun UpdateInstallDialog(update: UpdateChecker.UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(UpdateInstallStage.Confirm) }
    var progress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf<Long?>(update.assetSize) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf("") }

    fun launchInstaller(file: File) {
        try {
            context.startActivity(UpdateInstaller.installIntent(context, file))
            onDismiss()
        } catch (error: Exception) {
            errorMessage = error.message ?: "インストーラを開けませんでした。"
            stage = UpdateInstallStage.Error
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val file = downloadedFile
        if (file != null && UpdateInstaller.canInstallPackages(context)) launchInstaller(file)
        else {
            errorMessage = "このアプリからのインストールを許可してください。"
            stage = UpdateInstallStage.Permission
        }
    }

    fun beginDownload() {
        val url = update.downloadUrl
        if (url == null) {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(update.releasePageUrl)))
            onDismiss()
            return
        }
        stage = UpdateInstallStage.Downloading
        scope.launch {
            runCatching {
                UpdateInstaller.downloadApk(context, update) { value ->
                    downloadedBytes = value.downloadedBytes
                    totalBytes = value.totalBytes
                    progress = value.fraction ?: 0f
                }
            }.onSuccess { file ->
                downloadedFile = file
                if (UpdateInstaller.canInstallPackages(context)) launchInstaller(file)
                else {
                    stage = UpdateInstallStage.Permission
                    permissionLauncher.launch(UpdateInstaller.unknownSourcesIntent(context))
                }
            }.onFailure { error ->
                errorMessage = error.message ?: "APKを取得できませんでした。"
                stage = UpdateInstallStage.Error
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (stage != UpdateInstallStage.Downloading) onDismiss() },
        icon = {
            Icon(
                when (stage) {
                    UpdateInstallStage.Confirm -> Icons.Outlined.InstallMobile
                    UpdateInstallStage.Downloading -> Icons.Outlined.Download
                    UpdateInstallStage.Permission -> Icons.Outlined.InstallMobile
                    UpdateInstallStage.Error -> Icons.Outlined.ErrorOutline
                },
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                when (stage) {
                    UpdateInstallStage.Confirm -> "Kosen+をアップデート"
                    UpdateInstallStage.Downloading -> "アップデートを取得中"
                    UpdateInstallStage.Permission -> "インストールの許可が必要です"
                    UpdateInstallStage.Error -> "アップデートできませんでした"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (stage) {
                    UpdateInstallStage.Confirm -> {
                        Text("バージョン ${update.latestVersion} をGitHub Releasesから取得してインストールします。")
                        update.assetName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        update.assetSize?.let { Text(formatBytes(it), style = MaterialTheme.typography.labelMedium) }
                    }
                    UpdateInstallStage.Downloading -> {
                        if (totalBytes != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatBytes(downloadedBytes), style = MaterialTheme.typography.bodySmall)
                            totalBytes?.let { Text("${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall) }
                        }
                        Text("取得後にAPKの整合性を確認します。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    UpdateInstallStage.Permission -> Text("設定画面で「この提供元のアプリを許可」を有効にして戻ってください。")
                    UpdateInstallStage.Error -> Text(errorMessage)
                }
                Spacer(Modifier.height(1.dp))
            }
        },
        confirmButton = {
            when (stage) {
                UpdateInstallStage.Confirm -> Button(onClick = ::beginDownload) { Text(if (update.downloadUrl == null) "Releaseを開く" else "ダウンロード") }
                UpdateInstallStage.Downloading -> Unit
                UpdateInstallStage.Permission -> Button(onClick = { permissionLauncher.launch(UpdateInstaller.unknownSourcesIntent(context)) }) { Text("設定を開く") }
                UpdateInstallStage.Error -> Button(onClick = ::beginDownload) { Text("再試行") }
            }
        },
        dismissButton = {
            if (stage != UpdateInstallStage.Downloading) TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

private fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L -> String.format("%.1f MB", value / (1024.0 * 1024.0))
    value >= 1024L -> String.format("%.1f KB", value / 1024.0)
    else -> "$value B"
}
