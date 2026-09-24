package io.nekohasekai.sagernet.ui

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.github.shadowsocks.plugin.Empty
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.widget.AppListPreference
import io.nekohasekai.sagernet.widget.ListListener
import io.nekohasekai.sagernet.widget.OutboundPreference
import kotlinx.parcelize.Parcelize
import moe.matsuri.nb4a.ui.EditConfigPreference
import moe.matsuri.nb4a.ui.SimpleMenuPreference
import org.json.JSONArray
import org.json.JSONObject

@Suppress("UNCHECKED_CAST")
class RouteSettingsActivity(
    @LayoutRes resId: Int = R.layout.layout_settings_activity,
) : ThemedActivity(resId),
    OnPreferenceDataStoreChangeListener {

    fun init(packageName: String?) {
        RuleEntity().apply {
            if (!packageName.isNullOrBlank()) {
                packages = setOf(packageName)
                name = app.getString(R.string.route_for, PackageCache.loadLabel(packageName))
            }
        }.init()
    }

    fun RuleEntity.init() {
        DataStore.routeName = name
        DataStore.serverConfig = config
        syncActionPreferences()
        DataStore.routeDomain = domains
        DataStore.routeIP = ip
        DataStore.routePort = port
        DataStore.routeSourcePort = sourcePort
        DataStore.routeNetwork = network
        DataStore.routeSource = source
        DataStore.routeProtocol = protocol
        DataStore.routeRuleset = ruleset
        DataStore.routeOutboundRule = outbound
        DataStore.routeOutbound = when (outbound) {
            0L -> 0
            -1L -> 1
            -2L -> 2
            else -> OutboundPreference.VALUE_SELECT_PROFILE.toInt()
        }
        DataStore.routePackages = packages.joinToString("\n")
    }

    fun RuleEntity.serialize() {
        name = DataStore.routeName
        config = DataStore.serverConfig
        domains = DataStore.routeDomain
        ip = DataStore.routeIP
        port = DataStore.routePort
        sourcePort = DataStore.routeSourcePort
        network = DataStore.routeNetwork
        source = DataStore.routeSource
        protocol = DataStore.routeProtocol
        ruleset = DataStore.routeRuleset
        outbound = when (DataStore.routeOutbound) {
            0 -> 0L
            1 -> -1L
            2 -> -2L
            else -> DataStore.routeOutboundRule
        }
        packages = DataStore.routePackages.split("\n").filter { it.isNotBlank() }.toSet()

        if (DataStore.editingId == 0L) {
            enabled = true
        }
    }

    private lateinit var editConfigPreference: EditConfigPreference
    private lateinit var actionPreference: SimpleMenuPreference
    private lateinit var resolveMatchOnly: SwitchPreference
    private lateinit var resolveStrategy: SimpleMenuPreference
    private lateinit var resolveServer: EditTextPreference
    private lateinit var resolveTimeout: EditTextPreference
    private lateinit var resolveDisableCache: SwitchPreference
    private lateinit var resolveDisableOptimisticCache: SwitchPreference
    private lateinit var resolveRewriteTtl: EditTextPreference
    private lateinit var resolveClientSubnet: EditTextPreference
    private lateinit var sniffers: MultiSelectListPreference
    private lateinit var sniffTimeout: EditTextPreference
    private fun syncActionPreferences() {
        val json = runCatching { JSONObject(DataStore.serverConfig) }.getOrDefault(JSONObject())
        val action = json.optString("action").ifBlank { "route" }
        val choice = if (action in setOf("route", "resolve", "sniff", "sniff-override-destination")) action else "custom"
        val selectedSniffers = when (val value = json.opt("sniffer")) {
            is JSONArray -> (0 until value.length()).mapNotNull { value.optString(it).takeIf(String::isNotBlank) }.toSet()
            is String -> setOf(value)
            else -> emptySet()
        }

        if (DataStore.routeAction != choice) DataStore.routeAction = choice
        if (DataStore.routeResolveMatchOnly != json.optBoolean("match_only")) DataStore.routeResolveMatchOnly = json.optBoolean("match_only")
        if (DataStore.routeResolveStrategy != json.optString("strategy")) DataStore.routeResolveStrategy = json.optString("strategy")
        val resolveServerValue = json.optString("server").takeIf { action == "resolve" }.orEmpty()
        if (DataStore.routeResolveServer != resolveServerValue) DataStore.routeResolveServer = resolveServerValue
        val resolveTimeoutValue = json.optString("timeout").takeIf { action == "resolve" }.orEmpty()
        if (DataStore.routeResolveTimeout != resolveTimeoutValue) DataStore.routeResolveTimeout = resolveTimeoutValue
        val disableCache = action == "resolve" && json.optBoolean("disable_cache")
        if (DataStore.routeResolveDisableCache != disableCache) DataStore.routeResolveDisableCache = disableCache
        val disableOptimisticCache = action == "resolve" && json.optBoolean("disable_optimistic_cache")
        if (DataStore.routeResolveDisableOptimisticCache != disableOptimisticCache) DataStore.routeResolveDisableOptimisticCache = disableOptimisticCache
        val rewriteTtl = json.opt("rewrite_ttl")?.takeIf { action == "resolve" && it != JSONObject.NULL }?.toString().orEmpty()
        if (DataStore.routeResolveRewriteTTL != rewriteTtl) DataStore.routeResolveRewriteTTL = rewriteTtl
        val clientSubnet = json.optString("client_subnet").takeIf { action == "resolve" }.orEmpty()
        if (DataStore.routeResolveClientSubnet != clientSubnet) DataStore.routeResolveClientSubnet = clientSubnet
        if (DataStore.routeSniffers != selectedSniffers) DataStore.routeSniffers = selectedSniffers
        val sniffTimeoutValue = json.optString("timeout").takeIf { action == "sniff" }.orEmpty()
        if (DataStore.routeSniffTimeout != sniffTimeoutValue) DataStore.routeSniffTimeout = sniffTimeoutValue
        if (::actionPreference.isInitialized) {
            if (actionPreference.value != choice) actionPreference.value = choice
            if (resolveMatchOnly.isChecked != DataStore.routeResolveMatchOnly) resolveMatchOnly.isChecked = DataStore.routeResolveMatchOnly
            if (resolveStrategy.value.orEmpty() != DataStore.routeResolveStrategy) resolveStrategy.value = DataStore.routeResolveStrategy
            if (resolveServer.text.orEmpty() != DataStore.routeResolveServer) resolveServer.text = DataStore.routeResolveServer
            if (resolveTimeout.text.orEmpty() != DataStore.routeResolveTimeout) resolveTimeout.text = DataStore.routeResolveTimeout
            if (resolveDisableCache.isChecked != DataStore.routeResolveDisableCache) resolveDisableCache.isChecked = DataStore.routeResolveDisableCache
            if (resolveDisableOptimisticCache.isChecked != DataStore.routeResolveDisableOptimisticCache) resolveDisableOptimisticCache.isChecked = DataStore.routeResolveDisableOptimisticCache
            if (resolveRewriteTtl.text.orEmpty() != DataStore.routeResolveRewriteTTL) resolveRewriteTtl.text = DataStore.routeResolveRewriteTTL
            if (resolveClientSubnet.text.orEmpty() != DataStore.routeResolveClientSubnet) resolveClientSubnet.text = DataStore.routeResolveClientSubnet
            if (sniffers.values != selectedSniffers) sniffers.values = selectedSniffers
            if (sniffTimeout.text.orEmpty() != DataStore.routeSniffTimeout) sniffTimeout.text = DataStore.routeSniffTimeout
            updateActionVisibility(choice)
        }
    }

    private fun updateActionVisibility(action: String) {
        outbound.isVisible = action == "route"
        resolveMatchOnly.isVisible = action == "resolve"
        resolveStrategy.isVisible = action == "resolve"
        resolveServer.isVisible = action == "resolve"
        resolveTimeout.isVisible = action == "resolve"
        resolveDisableCache.isVisible = action == "resolve"
        resolveDisableOptimisticCache.isVisible = action == "resolve"
        resolveRewriteTtl.isVisible = action == "resolve"
        resolveClientSubnet.isVisible = action == "resolve"
        sniffers.isVisible = action == "sniff"
        sniffTimeout.isVisible = action == "sniff"
    }

    private fun changeActionConfig(update: (JSONObject) -> Unit): Boolean {
        val json = try {
            JSONObject(DataStore.serverConfig.ifBlank { "{}" })
        } catch (e: Exception) {
            Toast.makeText(this, e.localizedMessage ?: getString(R.string.error_title), Toast.LENGTH_LONG).show()
            return false
        }
        update(json)
        DataStore.serverConfig = if (json.length() == 0) "" else json.toString(2)
        editConfigPreference.notifyChanged()
        return true
    }

    fun needSave(): Boolean {
        return DataStore.dirty
    }

    fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.route_preferences)

        editConfigPreference = findPreference(Key.SERVER_CONFIG)!!
    }

    override fun onResume() {
        super.onResume()

        if (::editConfigPreference.isInitialized) {
            syncActionPreferences()
            editConfigPreference.notifyChanged()
        }
    }

    val selectProfileForAdd = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { (resultCode, data) ->
        if (resultCode == Activity.RESULT_OK) runOnDefaultDispatcher {
            val profile = ProfileManager.getProfile(
                data!!.getLongExtra(
                    ProfileSelectActivity.EXTRA_PROFILE_ID, 0
                )
            ) ?: return@runOnDefaultDispatcher
            DataStore.routeOutboundRule = profile.id
            onMainDispatcher {
                outbound.value = OutboundPreference.VALUE_SELECT_PROFILE
            }
        }
    }

    val selectAppList = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { (_, _) ->
        apps.postUpdate()
    }

    lateinit var outbound: OutboundPreference
    lateinit var apps: AppListPreference

    fun PreferenceFragmentCompat.viewCreated(view: View, savedInstanceState: Bundle?) {
        outbound = findPreference(Key.ROUTE_OUTBOUND)!!
        apps = findPreference(Key.ROUTE_PACKAGES)!!
        actionPreference = findPreference(Key.ROUTE_ACTION)!!
        resolveMatchOnly = findPreference(Key.ROUTE_RESOLVE_MATCH_ONLY)!!
        resolveStrategy = findPreference(Key.ROUTE_RESOLVE_STRATEGY)!!
        resolveServer = findPreference(Key.ROUTE_RESOLVE_SERVER)!!
        resolveTimeout = findPreference(Key.ROUTE_RESOLVE_TIMEOUT)!!
        resolveDisableCache = findPreference(Key.ROUTE_RESOLVE_DISABLE_CACHE)!!
        resolveDisableOptimisticCache = findPreference(Key.ROUTE_RESOLVE_DISABLE_OPTIMISTIC_CACHE)!!
        resolveRewriteTtl = findPreference(Key.ROUTE_RESOLVE_REWRITE_TTL)!!
        resolveClientSubnet = findPreference(Key.ROUTE_RESOLVE_CLIENT_SUBNET)!!
        sniffers = findPreference(Key.ROUTE_SNIFFERS)!!
        sniffTimeout = findPreference(Key.ROUTE_SNIFF_TIMEOUT)!!
        syncActionPreferences()

        actionPreference.setOnPreferenceChangeListener { _, newValue ->
            val action = newValue.toString()
            if (action == "custom") {
                startActivity(editConfigPreference.intent)
                false
            } else {
                val changed = changeActionConfig { json ->
                    listOf(
                        "match_only", "strategy", "server", "timeout", "disable_cache",
                        "disable_optimistic_cache", "rewrite_ttl", "client_subnet", "sniffer"
                    ).forEach { key -> json.remove(key) }
                    if (action == "route") json.remove("action") else json.put("action", action)
                    if (action != "route") json.remove("outbound")
                }
                if (changed) syncActionPreferences()
                changed
            }
        }
        resolveMatchOnly.setOnPreferenceChangeListener { _, value ->
            changeActionConfig { json ->
                if (value == true) json.put("match_only", true) else json.remove("match_only")
            }
        }
        resolveStrategy.setOnPreferenceChangeListener { _, value ->
            changeActionConfig { json ->
                val strategy = value.toString()
                if (strategy.isBlank()) json.remove("strategy") else json.put("strategy", strategy)
            }
        }
        resolveServer.setOnPreferenceChangeListener { _, value ->
            changeActionConfig { json ->
                val server = value.toString().trim()
                if (server.isBlank()) json.remove("server") else json.put("server", server)
            }
        }
        resolveTimeout.setOnPreferenceChangeListener { _, value ->
            changeActionConfig { json ->
                val timeout = value.toString().trim()
                if (timeout.isBlank()) json.remove("timeout") else json.put("timeout", timeout)
            }
        }
        resolveDisableCache.setOnPreferenceChangeListener { _, value ->
            changeActionConfig { json ->
                if (value == true) json.put("disable_cache", true) else json.remove("disable_cache")
            }
        }
        resolveDisableOptimisticCache.setOnPreferenceChangeListener { _, value ->
            changeActionConfig { json ->
                if (value == true) json.put("disable_optimistic_cache", true) else json.remove("disable_optimistic_cache")
            }
        }
        resolveRewriteTtl.setOnPreferenceChangeListener { _, value ->
            val text = value.toString().trim()
            if (text.isBlank()) {
                changeActionConfig { it.remove("rewrite_ttl") }
            } else {
                val ttl = text.toLongOrNull()
                if (ttl == null || ttl !in 0..4294967295L) {
                    Toast.makeText(this@RouteSettingsActivity, R.string.route_resolve_rewrite_ttl_invalid, Toast.LENGTH_SHORT).show()
                    false
                } else {
                    changeActionConfig { it.put("rewrite_ttl", ttl) }
                }
            }
        }
        resolveClientSubnet.setOnPreferenceChangeListener { _, value ->
            changeActionConfig { json ->
                val subnet = value.toString().trim()
                if (subnet.isBlank()) json.remove("client_subnet") else json.put("client_subnet", subnet)
            }
        }
        sniffers.setOnPreferenceChangeListener { _, value ->
            changeActionConfig { json ->
                val selected = value as Set<String>
                val ordered = resources.getStringArray(R.array.route_sniffer_entry).filter { it in selected }
                if (ordered.isEmpty()) json.remove("sniffer")
                else json.put("sniffer", JSONArray(ordered))
            }
        }
        sniffTimeout.setOnPreferenceChangeListener { _, value ->
            changeActionConfig { json ->
                val timeout = value.toString().trim()
                if (timeout.isBlank()) json.remove("timeout") else json.put("timeout", timeout)
            }
        }
        outbound.setOnPreferenceChangeListener { _, newValue ->
            if (newValue.toString() == OutboundPreference.VALUE_SELECT_PROFILE) {
                selectProfileForAdd.launch(
                    Intent(
                        this@RouteSettingsActivity, ProfileSelectActivity::class.java
                    ).apply {
                        ProfileManager.getProfile(DataStore.routeOutboundRule)?.let {
                            putExtra(ProfileSelectActivity.EXTRA_SELECTED, it)
                        }
                    }
                )
                false
            } else {
                true
            }
        }

        apps.setOnPreferenceClickListener {
            selectAppList.launch(
                Intent(
                    this@RouteSettingsActivity, AppListActivity::class.java
                )
            )
            true
        }
    }

    fun displayPreferenceDialog(preference: Preference): Boolean {
        return false
    }

    class UnsavedChangesDialogFragment : AlertDialogFragment<Empty, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.unsaved_changes_prompt)
            setPositiveButton(R.string.yes) { _, _ ->
                runOnDefaultDispatcher {
                    (requireActivity() as RouteSettingsActivity).saveAndExit()
                }
            }
            setNegativeButton(R.string.no) { _, _ ->
                requireActivity().finish()
            }
            setNeutralButton(android.R.string.cancel, null)
        }
    }

    @Parcelize
    data class ProfileIdArg(val ruleId: Long) : Parcelable
    class DeleteConfirmationDialogFragment : AlertDialogFragment<ProfileIdArg, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.delete_route_prompt)
            setPositiveButton(R.string.yes) { _, _ ->
                runOnDefaultDispatcher {
                    ProfileManager.deleteRule(arg.ruleId)
                }
                requireActivity().finish()
            }
            setNegativeButton(R.string.no, null)
        }
    }

    companion object {
        const val EXTRA_ROUTE_ID = "id"
        const val EXTRA_PACKAGE_NAME = "pkg"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.cag_route)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        if (savedInstanceState == null) {
            val editingId = intent.getLongExtra(EXTRA_ROUTE_ID, 0L)
            DataStore.editingId = editingId
            runOnDefaultDispatcher {
                if (editingId == 0L) {
                    init(intent.getStringExtra(EXTRA_PACKAGE_NAME))
                } else {
                    val ruleEntity = SagerDatabase.rulesDao.getById(editingId)
                    if (ruleEntity == null) {
                        onMainDispatcher {
                            finish()
                        }
                        return@runOnDefaultDispatcher
                    }
                    ruleEntity.init()
                }

                onMainDispatcher {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.settings, MyPreferenceFragmentCompat())
                        .commit()

                    DataStore.dirty = false
                    DataStore.profileCacheStore.registerChangeListener(this@RouteSettingsActivity)
                }
            }


        }

    }

    suspend fun saveAndExit() {

        if (!needSave()) {
            onMainDispatcher {
                MaterialAlertDialogBuilder(this@RouteSettingsActivity).setTitle(R.string.empty_route)
                    .setMessage(R.string.empty_route_notice)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            return
        }

        val editingId = DataStore.editingId
        if (editingId == 0L) {
            if (intent.hasExtra(EXTRA_PACKAGE_NAME)) {
                setResult(RESULT_OK, Intent())
            }

            ProfileManager.createRule(RuleEntity().apply { serialize() })
        } else {
            val entity = SagerDatabase.rulesDao.getById(DataStore.editingId)
            if (entity == null) {
                finish()
                return
            }
            ProfileManager.updateRule(entity.apply { serialize() })
        }
        finish()

    }

    val child by lazy { supportFragmentManager.findFragmentById(R.id.settings) as MyPreferenceFragmentCompat }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.profile_config_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = child.onOptionsItemSelected(item)

    override fun onBackPressed() {
        if (needSave()) {
            UnsavedChangesDialogFragment().apply { key() }.show(supportFragmentManager, null)
        } else super.onBackPressed()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    override fun onDestroy() {
        DataStore.profileCacheStore.unregisterChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        if (key != Key.PROFILE_DIRTY) {
            DataStore.dirty = true
        }
    }

    class MyPreferenceFragmentCompat : PreferenceFragmentCompat() {

        var activity: RouteSettingsActivity? = null

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStore.profileCacheStore
            try {
                activity = (requireActivity() as RouteSettingsActivity).apply {
                    createPreferences(savedInstanceState, rootKey)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    SagerNet.application,
                    "Error on createPreferences, please try again.",
                    Toast.LENGTH_SHORT
                ).show()
                Logs.e(e)
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            ViewCompat.setOnApplyWindowInsetsListener(listView, ListListener)
            setDivider(null)
            setDividerHeight(0)

            activity?.apply {
                viewCreated(view, savedInstanceState)
            }
        }

        override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
            R.id.action_delete -> {
                if (DataStore.editingId == 0L) {
                    requireActivity().finish()
                } else {
                    DeleteConfirmationDialogFragment().apply {
                        arg(ProfileIdArg(DataStore.editingId))
                        key()
                    }.show(parentFragmentManager, null)
                }
                true
            }

            R.id.action_apply -> {
                runOnDefaultDispatcher {
                    activity?.saveAndExit()
                }
                true
            }

            else -> false
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            activity?.apply {
                if (displayPreferenceDialog(preference)) return
            }
            super.onDisplayPreferenceDialog(preference)
        }

    }

    object PasswordSummaryProvider : Preference.SummaryProvider<EditTextPreference> {

        override fun provideSummary(preference: EditTextPreference): CharSequence {
            val text = preference.text
            return if (text.isNullOrBlank()) {
                preference.context.getString(androidx.preference.R.string.not_set)
            } else {
                "\u2022".repeat(text.length)
            }
        }

    }

}
