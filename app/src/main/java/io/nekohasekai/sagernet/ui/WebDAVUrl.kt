package io.nekohasekai.sagernet.ui

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun String.toSecureWebDAVUrlOrNull(): HttpUrl? =
    trim().toHttpUrlOrNull()?.takeIf { it.scheme == "https" }
