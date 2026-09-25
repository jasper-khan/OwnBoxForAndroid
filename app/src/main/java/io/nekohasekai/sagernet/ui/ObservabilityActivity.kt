package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.View
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.ActivityObservabilityBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ObservabilityActivity : ThemedActivity() {

    private lateinit var binding: ActivityObservabilityBinding
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private data class Snapshot(
        val status: JSONObject,
        val top: JSONArray,
        val active: JSONArray,
        val recent: JSONArray,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityObservabilityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.refreshButton.setOnClickListener { refresh() }
    }

    override fun onStart() {
        super.onStart()
        refresh()
    }

    private fun refresh() {
        if (!DataStore.enableObservability) {
            showMessage(R.string.connection_diagnostics_disabled)
            return
        }
        if (!DataStore.serviceState.connected || !(DataStore.enableClashAPI || DataStore.allowAccess)) {
            showMessage(R.string.connection_diagnostics_unavailable)
            return
        }

        binding.refreshButton.isEnabled = false
        lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) { runCatching { loadSnapshot() } }
            binding.refreshButton.isEnabled = true
            snapshot.onSuccess { render(it) }
                .onFailure { showMessage(R.string.connection_diagnostics_unavailable) }
        }
    }

    private fun loadSnapshot(): Snapshot {
        fun get(path: String): JSONObject {
            val request = Request.Builder().url("http://127.0.0.1:9091/observability/v1/$path").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                return JSONObject(response.body?.string() ?: throw IOException("Empty response"))
            }
        }

        return Snapshot(
            status = get("status"),
            top = get("top?dimension=outbound&limit=10").optJSONArray("data") ?: JSONArray(),
            active = get("connections/active?limit=20").optJSONArray("data") ?: JSONArray(),
            recent = get("connections/recent?limit=20").optJSONArray("data") ?: JSONArray(),
        )
    }

    private fun showMessage(message: Int) {
        binding.message.setText(message)
        binding.message.visibility = View.VISIBLE
        binding.statusText.text = ""
        binding.topText.text = ""
        binding.activeText.text = ""
        binding.recentText.text = ""
    }

    private fun render(snapshot: Snapshot) {
        binding.message.visibility = View.GONE
        val status = snapshot.status
        binding.statusText.text = getString(
            R.string.connection_diagnostics_status_format,
            DateUtils.formatElapsedTime(status.optLong("uptimeSeconds")),
            status.optInt("activeConnections"),
            status.optInt("recentConnections"),
            status.optLong("connectionsTotal"),
            formatBytes(status.optLong("uploadBytesTotal")),
            formatBytes(status.optLong("downloadBytesTotal")),
        )
        binding.topText.text = formatRows(snapshot.top, true)
        binding.activeText.text = formatRows(snapshot.active, false)
        binding.recentText.text = formatRows(snapshot.recent, false)
    }

    private fun formatRows(rows: JSONArray, top: Boolean): String {
        if (rows.length() == 0) return getString(R.string.connection_diagnostics_empty)
        return (0 until rows.length()).joinToString("\n\n") { index ->
            val row = rows.getJSONObject(index)
            val name = if (top) row.optString("value") else row.optString("outbound")
            val label = name.ifBlank { row.optString("network", "—") }
            val detail = if (top) {
                getString(R.string.connection_diagnostics_connections, row.optLong("connections"))
            } else {
                val network = row.optString("network").uppercase()
                val port = row.optInt("destinationPort")
                if (port > 0) "$network :$port" else network
            }
            "$label · $detail\n↑${formatBytes(row.optLong("upload"))} ↓${formatBytes(row.optLong("download"))}"
        }
    }

    private fun formatBytes(bytes: Long): String = Formatter.formatFileSize(this, bytes)
}
