package com.litter.android.state

internal object PathNormalizer {
    private val windowsDrivePattern = Regex("(?i)[a-z]:[\\\\/]")

    fun normalize(path: String?): String {
        val trimmed = path?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val deduped = dedupeConcatenatedWindowsPath(trimmed)
        return trimTrailingSeparators(deduped)
    }

    fun isWindowsAbsolute(path: String): Boolean =
        windowsDrivePattern.find(path.trim())?.range?.first == 0

    private fun dedupeConcatenatedWindowsPath(path: String): String {
        var normalized = path
        while (true) {
            val matches = windowsDrivePattern.findAll(normalized).toList()
            if (matches.size < 2 || matches.first().range.first != 0) return normalized
            normalized = normalized.substring(matches[1].range.first)
        }
    }

    private fun trimTrailingSeparators(path: String): String {
        var normalized = path
        while (normalized.length > minimumRootLength(normalized) &&
            (normalized.endsWith('/') || normalized.endsWith('\\'))
        ) {
            normalized = normalized.dropLast(1)
        }
        return normalized
    }

    private fun minimumRootLength(path: String): Int =
        if (windowsDrivePattern.find(path)?.range?.first == 0) 3 else 1
}
