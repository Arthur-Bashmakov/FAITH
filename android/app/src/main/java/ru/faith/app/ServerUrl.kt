package ru.faith.app

internal fun String.normalizeServerUrl(): String {
    val trimmed = trim()
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    return withScheme.trimEnd('/') + "/"
}
