package eu.kanade.tachiyomi.network

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.util.system.WebViewUtil
import mihon.core.metro.IsDebugBuild
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

const val KAD_PROXY_HOST = "127.0.0.1"

object KadProxy {
    val port: Int by lazy {
        val socket = ServerSocket()

        socket.reuseAddress = true
        socket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))

        val port = socket.localPort

        socket.close()

        port
    }
}

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

    val fingerprintSpoofer: Preference<Boolean> = preferenceStore.getBoolean(
        "fingerprint_spoofer",
        WebViewUtil.supportsFingerprintSpoofer(),
    )

    val defaultUserAgent: Preference<String> = preferenceStore.getString(
        "default_user_agent",
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36",
    )
}
