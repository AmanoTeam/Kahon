package eu.kanade.tachiyomi.network

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import mihon.core.metro.IsDebugBuild
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

const val PREF_PROXY_DISABLED = 0
const val PREF_PROXY_HTTP = 1
const val PREF_PROXY_SOCKS = 2

const val DEFAULT_PROXY_PORT_HTTP = 8080
const val DEFAULT_PROXY_PORT_SOCKS = 1080

@Inject
@SingleIn(AppScope::class)
class NetworkPreferences(
    preferenceStore: PreferenceStore,
    @IsDebugBuild isDebugBuild: Boolean,
) {

    val verboseLogging: Preference<Boolean> = preferenceStore.getBoolean(
        "verbose_logging",
        isDebugBuild,
    )

    val dohProvider: Preference<Int> = preferenceStore.getInt("doh_provider", -1)

    val proxyType: Preference<Int> = preferenceStore.getInt("proxy_type", PREF_PROXY_DISABLED)

    val proxyHost: Preference<String> = preferenceStore.getString("proxy_host", "")

    val proxyPort: Preference<String> = preferenceStore.getString("proxy_port", "")

    val proxyUsername: Preference<String> = preferenceStore.getString("proxy_username", "")

    val proxyPassword: Preference<String> = preferenceStore.getString(Preference.privateKey("proxy_password"), "")

    val defaultUserAgent: Preference<String> = preferenceStore.getString(
        "default_user_agent",
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36",
    )
}
