package com.litter.android.ui.conversation

internal fun responseSubmissionErrorMessage(error: Throwable): String =
    submissionErrorMessage(error, fallback = "提交响应失败。")

internal fun turnSubmissionErrorMessage(error: Throwable): String =
    submissionErrorMessage(error, fallback = "请稍后重试。")

private fun submissionErrorMessage(error: Throwable, fallback: String): String {
    val message = error.message?.trim().orEmpty()
    val lowerMessage = message.lowercase()
    return when {
        isNeModelRequiredMessage(lowerMessage) -> neModelRequiredMessage()
        isNeLoginRequiredMessage(lowerMessage) -> neLoginRequiredMessage()
        error.isDisconnectedTransportError() -> disconnectedTransportMessage()
        else -> message.ifEmpty { fallback }
    }
}

private fun isNeLoginRequiredMessage(message: String): Boolean =
    "ne login required" in message ||
        "use /login" in message ||
        "not_configured" in message

private fun isNeModelRequiredMessage(message: String): Boolean =
    "no model selected" in message ||
        "no models available" in message ||
        "set api keys" in message

private fun neLoginRequiredMessage(): String =
    "电脑端 NeCode 登录已失效。请在电脑端运行 necode，执行 /login 完成登录；然后重启 necode mobile 或在手机端重新连接后再试。"

private fun neModelRequiredMessage(): String =
    "电脑端 NeCode 没有可用模型。请在电脑端运行 necode，完成 /login 后用 /model 选择模型；然后重启 necode mobile 或在手机端重新连接后再试。"

private fun disconnectedTransportMessage(): String =
    "连接已断开。请等待 NeCode 重新连接后再试。"

internal fun Throwable.isDisconnectedTransportError(): Boolean {
    val message = this.message?.lowercase().orEmpty()
    return "disconnected" in message ||
        ("transport error" in message && "not connected" in message)
}
