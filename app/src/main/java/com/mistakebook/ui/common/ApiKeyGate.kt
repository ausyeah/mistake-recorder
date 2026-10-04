package com.mistakebook.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mistakebook.R

/**
 * 未配置 Key 时的统一引导（验收 3：不发起请求、不 crash，只引导去设置）。
 *
 * 必须如实区分缺的是 MinerU 还是大模型接入配置——之前这里写死显示「MinerU API Key」，
 * 结果实际缺的是大模型配置时，用户反复去检查一个已经填好的 Key。
 *
 * @param onManualEntry 「直接手动录入」出口。没有它的话，一个没配 Key 的人
 *   点「拍错题」只会反复撞上这个对话框，除了去设置没有第二条路——
 *   而设置完之前他连一道题都录不进来。
 */
@Composable
fun ApiKeyRequiredDialog(
    mineruMissing: Boolean,
    llmMissing: Boolean,
    onOpenSettings: () -> Unit,
    onManualEntry: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val missing = buildList {
        if (mineruMissing) add(stringResource(R.string.settings_mineru_key))
        if (llmMissing) add(stringResource(R.string.error_no_key_missing_llm))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.error_no_key)) },
        text = {
            Column {
                Text(missing.joinToString("\n"))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.manual_entry_gate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onOpenSettings()
            }) { Text(stringResource(R.string.error_no_key_action)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onDismiss()
                    onManualEntry()
                }) { Text(stringResource(R.string.home_add_manual)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}
