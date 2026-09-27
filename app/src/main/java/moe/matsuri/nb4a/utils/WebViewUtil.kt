package moe.matsuri.nb4a.utils

import android.os.Build
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.nekohasekai.sagernet.ktx.Logs
import java.io.ByteArrayInputStream
import java.io.InputStream

object WebViewUtil {
    fun onReceivedError(
        view: WebView?, request: WebResourceRequest?, error: WebResourceError?
    ) {
        if (Build.VERSION.SDK_INT < 23 || error == null) return
        val url = request?.url?.let { uri ->
            val path = if (uri.host == "127.0.0.1" || uri.host == "localhost") uri.encodedPath.orEmpty() else ""
            "${uri.scheme}://${uri.host}${if (uri.port >= 0) ":${uri.port}" else ""}$path"
        }
        val message = "WebView request failed: url=$url mainFrame=${request?.isForMainFrame} " +
            "code=${error.errorCode} description=${error.description}"
        if (request?.isForMainFrame == false &&
            (request?.url?.host == "127.0.0.1" || request?.url?.host == "localhost") &&
            error.errorCode == WebViewClient.ERROR_UNKNOWN &&
            error.description.toString() == "net::ERR_FAILED"
        ) {
            Logs.d(message)
        } else {
            Logs.e(message)
        }
    }

    fun interceptRequest(
        res: (String) -> InputStream?, view: WebView?, request: WebResourceRequest?
    ): WebResourceResponse {
        val path = request?.url?.path ?: "404"
        val input = res(path)
        var mime = "text/plain"
        if (path.endsWith(".js")) mime = "application/javascript"
        if (path.endsWith(".html")) mime = "text/html"
        return if (input != null) {
            WebResourceResponse(mime, "UTF-8", input)
        } else {
            WebResourceResponse(
                "text/plain", "UTF-8", ByteArrayInputStream("".toByteArray())
            )
        }
    }
}
