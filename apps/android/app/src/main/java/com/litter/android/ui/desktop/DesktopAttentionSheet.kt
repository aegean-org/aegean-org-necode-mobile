package com.litter.android.ui.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.litter.android.ui.LitterTheme
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopAttentionSheet(state: JSONObject, scope: String, busy: Boolean,
    onDismiss: () -> Unit, onReply: (String, String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LitterTheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("需要你处理", color = LitterTheme.textPrimary, style = MaterialTheme.typography.titleLarge)
            val permissions = state.getJSONArray("permissions")
            (0 until permissions.length()).forEach { index ->
                val request = permissions.getJSONObject(index)
                Text(request.optString("permission").ifBlank { "操作授权" }, color = LitterTheme.textPrimary)
                val patterns = request.optJSONArray("patterns") ?: JSONArray()
                Text((0 until patterns.length()).joinToString("\n") { patterns.getString(it) }, color = LitterTheme.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("once" to "允许一次", "always" to "始终允许", "reject" to "拒绝").forEach { (reply, label) ->
                        TextButton(enabled = !busy, onClick = {
                            onReply("/permission/${request.getString("id")}/reply", JSONObject().put("reply", reply)
                                .put("requestSource", request.getString("requestSource")).toString())
                        }) { Text(label) }
                    }
                }
                HorizontalDivider()
            }
            val requests = state.getJSONArray("questions")
            (0 until requests.length()).forEach { requestIndex ->
                val request = requests.getJSONObject(requestIndex)
                key(scope, request.getString("requestSource"), request.getString("id")) {
                    val selected = remember { mutableStateMapOf<Int, List<String>>() }
                    val custom = remember { mutableStateMapOf<Int, String>() }
                    val questions = request.getJSONArray("questions")
                    (0 until questions.length()).forEach { index ->
                        val question = questions.getJSONObject(index)
                        Text(question.getString("question"), color = LitterTheme.textPrimary)
                        val options = question.getJSONArray("options")
                        val multiple = question.optBoolean("multiple")
                        (0 until options.length()).forEach { optionIndex ->
                            val option = options.getJSONObject(optionIndex)
                            val label = option.getString("label")
                            val checked = label in (selected[index] ?: emptyList())
                            TextButton(enabled = !busy, onClick = {
                                custom[index] = ""
                                selected[index] = if (multiple) {
                                    if (checked) (selected[index] ?: emptyList()) - label else (selected[index] ?: emptyList()) + label
                                } else listOf(label)
                            }) { Text((if (checked) "✓ " else "") + label + " · " + option.optString("description")) }
                        }
                        if (question.optBoolean("custom", true)) OutlinedTextField(
                            value = custom[index] ?: "", onValueChange = { custom[index] = it; if (!multiple) selected[index] = emptyList() },
                            enabled = !busy, label = { Text("其他回答") }, modifier = Modifier.fillMaxWidth())
                    }
                    val answers = (0 until questions.length()).map { index ->
                        (selected[index] ?: emptyList()) + listOfNotNull(custom[index]?.trim()?.takeIf { it.isNotEmpty() })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy && answers.all { it.isNotEmpty() }, onClick = {
                            onReply("/question/${request.getString("id")}/reply", JSONObject().put("answers", JSONArray(answers.map { JSONArray(it) }))
                                .put("requestSource", request.getString("requestSource")).toString())
                        }) { Text("提交回答") }
                        TextButton(enabled = !busy, onClick = {
                            onReply("/question/${request.getString("id")}/reject", JSONObject().put("requestSource", request.getString("requestSource")).toString())
                        }) { Text("暂不回答") }
                    }
                    HorizontalDivider()
                }
            }
            if (permissions.length() == 0 && requests.length() == 0) Text("当前没有待处理的请求", color = LitterTheme.textSecondary)
            Spacer(Modifier.height(16.dp))
        }
    }
}
