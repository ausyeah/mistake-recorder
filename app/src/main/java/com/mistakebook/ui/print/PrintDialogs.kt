package com.mistakebook.ui.print

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mistakebook.R
import com.mistakebook.print.ExportPublisher

/**
 * 打印生成完成后的弹窗，包含文件路径、跳过提示、分享和打开操作。
 */
@Composable
fun PrintResultDialog(
    result: ExportPublisher.Output,
    formatName: String,
    skipped: List<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.print_done_title, formatName)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.print_path_format, result.displayPath),
                    style = MaterialTheme.typography.bodySmall
                )
                if (skipped.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.print_skipped_format,
                            skipped.joinToString("、")
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = result.format.mimeType
                    putExtra(Intent.EXTRA_STREAM, result.shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, null))
            }) { Text(stringResource(R.string.print_share)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(result.shareUri, result.format.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }
                }) { Text(stringResource(R.string.print_open)) }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    )
}

/**
 * 打印生成失败时的错误提示弹窗。
 */
@Composable
fun PrintErrorDialog(
    error: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.print_failed, "")) },
        text = { Text(error) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}
