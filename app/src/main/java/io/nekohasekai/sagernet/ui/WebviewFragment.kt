package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutWebviewBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.ktx.safeSnackbar
import io.nekohasekai.sagernet.ktx.startFilesForResult
import moe.matsuri.nb4a.utils.WebViewUtil

// Fragment必须有一个无参public的构造函数，否则在数据恢复的时候，会报crash

class WebviewFragment : ToolbarFragment(R.layout.layout_webview), Toolbar.OnMenuItemClickListener {

    lateinit var mWebView: WebView

    private var panelServerSeeded = false
    private var localPanelLoaded = false
    private var pendingPanelFile: ByteArray? = null

    /**
     * 官方 dashboard 的「日志 - 保存 - 到文件」是 blob: + a[download] 触发的下载，
     * WebView 自己不会落盘，这里把内容接回来交给系统文件选择器保存。
     */
    private val savePanelFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            val bytes = pendingPanelFile
            pendingPanelFile = null
            if (uri == null || bytes == null) return@registerForActivityResult
            runOnDefaultDispatcher {
                try {
                    val resolver = (context ?: SagerNet.application).contentResolver
                    resolver.openOutputStream(uri)!!.use { it.write(bytes) }
                    runOnMainDispatcher { if (isAdded) safeSnackbar(R.string.action_export_msg) }
                } catch (e: Exception) {
                    Logs.w(e)
                    runOnMainDispatcher { if (isAdded) safeSnackbar(e.readableMessage) }
                }
            }
        }

    /** 面板 JS 通过这个接口把导出内容交回 App，只对本机面板生效。 */
    inner class PanelFileBridge {

        @JavascriptInterface
        fun saveFile(name: String, base64: String) {
            if (!localPanelLoaded) return
            val bytes = try {
                Base64.decode(base64, Base64.DEFAULT)
            } catch (e: Exception) {
                Logs.w("Failed to decode panel file: ${e.message}")
                return
            }
            val fileName = name.substringAfterLast('/').substringAfterLast('\\')
                .ifBlank { "ownbox-panel.txt" }.take(120)
            pendingPanelFile = bytes
            runOnMainDispatcher {
                if (isAdded) startFilesForResult(savePanelFile, fileName)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // layout
        toolbar.setTitle(R.string.menu_dashboard)
        toolbar.inflateMenu(R.menu.dashboard_menu)
        toolbar.setOnMenuItemClickListener(this)

        val binding = LayoutWebviewBinding.bind(view)

        // webview
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        mWebView = binding.webview
        mWebView.settings.domStorageEnabled = true
        mWebView.settings.javaScriptEnabled = true
        mWebView.addJavascriptInterface(PanelFileBridge(), PANEL_FILE_BRIDGE)
        mWebView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                WebViewUtil.onReceivedError(view, request, error)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                localPanelLoaded = url?.startsWith(LOCAL_PANEL_PREFIX) == true
                seedPanelServer(view, url)
                injectPanelDownloadHook(view, url)
            }
        }
        migrateLegacyPanelUrl()
        mWebView.loadUrl(DataStore.panelURL)

        if (!DataStore.serviceState.connected) {
            Snackbar.make(view, "提示：请先连接代理服务以获取实时仪表盘数据", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed(): Boolean {
        if (::mWebView.isInitialized && mWebView.canGoBack()) {
            mWebView.goBack()
            return true
        }
        return false
    }

    override fun onDestroyView() {
        if (::mWebView.isInitialized) {
            try {
                mWebView.stopLoading()
                mWebView.loadUrl("about:blank")
                mWebView.clearHistory()
                (mWebView.parent as? ViewGroup)?.removeView(mWebView)
                mWebView.destroy()
            } catch (e: Exception) {
                Logs.w("Failed to destroy WebView: ${e.message}")
            }
        }
        super.onDestroyView()
    }

    @SuppressLint("CheckResult")
    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_set_url -> {
                val view = EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                    setText(DataStore.panelURL)
                }
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.set_panel_url)
                    .setView(view)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        DataStore.panelURL = view.text.toString()
                        mWebView.loadUrl(DataStore.panelURL)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.close -> {
                (activity as? MainActivity)?.displayFragmentWithId(R.id.nav_configuration)
            }
        }
        return true
    }

    /**
     * 官方 dashboard 首次打开是它自己的连接页，需要手动点一次 Connect。
     * 面板是内嵌 WebView，这里替用户一次性写入本地连接记录，保持“点开就是面板”。
     * 只对内核本地面板生效：已有记录（含用户自建服务器）不覆盖，失败也只是退回连接页。
     */
    private fun seedPanelServer(webView: WebView?, url: String?) {
        if (panelServerSeeded || webView == null || url == null) return
        if (!url.startsWith(LOCAL_PANEL_PREFIX)) return
        panelServerSeeded = true
        webView.evaluateJavascript(SEED_PANEL_SERVER_JS) { result ->
            if (result?.trim('"') == "1") webView.reload()
        }
    }

    /** 面板导出是 JS 生成的 blob，WebView 不会自己下载，注入钩子把内容交给 App 保存。 */
    private fun injectPanelDownloadHook(webView: WebView?, url: String?) {
        if (webView == null || url?.startsWith(LOCAL_PANEL_PREFIX) != true) return
        webView.evaluateJavascript(PANEL_DOWNLOAD_HOOK_JS, null)
    }

    /** 上一个预览版把面板放在 9091，换到 9090 后自动迁移默认地址，避免打开是 404。 */
    private fun migrateLegacyPanelUrl() {
        if (DataStore.panelURL == LEGACY_PANEL_URL) {
            DataStore.panelURL = DEFAULT_PANEL_URL
        }
    }

    companion object {

        private const val DEFAULT_PANEL_URL = "http://127.0.0.1:9090/dashboard/"
        private const val LEGACY_PANEL_URL = "http://127.0.0.1:9091/dashboard/"
        private const val LOCAL_PANEL_PREFIX = "http://127.0.0.1:9090/dashboard"
        private const val PANEL_FILE_BRIDGE = "OwnBoxPanel"

        // 官方 dashboard 的服务器列表存在 localStorage["servers"]，url 不带协议（见其 re()/k()）
        private val SEED_PANEL_SERVER_JS = """
            (function () {
                try {
                    if (localStorage.getItem('servers')) return '0';
                    localStorage.setItem('servers', JSON.stringify({
                        servers: [{ id: 'ownbox-local', name: 'OwnBox', url: location.host, secret: '' }],
                        activeId: 'ownbox-local'
                    }));
                    return '1';
                } catch (e) {
                    return '0';
                }
            })()
        """.trimIndent()

        /**
         * 面板「保存 - 到文件」用 URL.createObjectURL + a[download].click() 触发，
         * WebView 既不下载也不会回调 DownloadListener，所以在这里接管：
         * 记住 blob、延迟 revoke（面板在 click 后立刻 revoke），把内容转 base64 交回 App。
         */
        private val PANEL_DOWNLOAD_HOOK_JS = """
            (function () {
                if (window.__ownboxPanelDownloadHook) return '0';
                window.__ownboxPanelDownloadHook = true;
                var blobs = {};
                var createObjectURL = URL.createObjectURL;
                var revokeObjectURL = URL.revokeObjectURL;
                URL.createObjectURL = function (blob) {
                    var url = createObjectURL.call(URL, blob);
                    blobs[url] = blob;
                    return url;
                };
                URL.revokeObjectURL = function (url) {
                    setTimeout(function () {
                        delete blobs[url];
                        try { revokeObjectURL.call(URL, url); } catch (e) {}
                    }, 60000);
                };
                function save(name, blob) {
                    var reader = new FileReader();
                    reader.onload = function () {
                        var result = String(reader.result || '');
                        var data = result.substring(result.indexOf(',') + 1);
                        try { OwnBoxPanel.saveFile(name || 'ownbox-panel.txt', data); } catch (e) {}
                    };
                    reader.readAsDataURL(blob);
                }
                var click = HTMLAnchorElement.prototype.click;
                HTMLAnchorElement.prototype.click = function () {
                    var name = this.getAttribute('download');
                    var href = this.getAttribute('href') || '';
                    if (name && blobs[href]) {
                        save(name, blobs[href]);
                        return;
                    }
                    if (name && href.indexOf(';base64,') > 0) {
                        try { OwnBoxPanel.saveFile(name, href.substring(href.indexOf(',') + 1)); } catch (e) {}
                        return;
                    }
                    return click.apply(this, arguments);
                };
                return '1';
            })()
        """.trimIndent()
    }
}
