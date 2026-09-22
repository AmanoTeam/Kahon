package eu.kanade.tachiyomi.network

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
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

val kadImpersonateTargets = arrayOf(
    "chrome99" to "Chrome 99",
    "chrome99_android" to "Chrome 99 (Android)",
    "chrome100" to "Chrome 100",
    "chrome101" to "Chrome 101",
    "chrome104" to "Chrome 104",
    "chrome107" to "Chrome 107",
    "chrome110" to "Chrome 110",
    "chrome116" to "Chrome 116",
    "chrome119" to "Chrome 119",
    "chrome120" to "Chrome 120",
    "chrome123" to "Chrome 123",
    "chrome124" to "Chrome 124",
    "chrome131" to "Chrome 131",
    "chrome131_android" to "Chrome 131 (Android)",
    "chrome133a" to "Chrome 133a",
    "chrome136" to "Chrome 136",
    "chrome142" to "Chrome 142",
    "chrome145" to "Chrome 145",
    "chrome146" to "Chrome 146",
    "chrome150" to "Chrome 150",
    "edge99" to "Edge 99",
    "edge101" to "Edge 101",
    "firefox133" to "Firefox 133",
    "firefox135" to "Firefox 135",
    "firefox144" to "Firefox 144",
    "firefox147" to "Firefox 147",
    "okhttp4_android" to "OkHttp 4 (Android)",
    "safari153" to "Safari 15.3",
    "safari155" to "Safari 15.5",
    "safari170" to "Safari 17.0",
    "safari172_ios" to "Safari 17.2 (iOS)",
    "safari180" to "Safari 18.0",
    "safari180_ios" to "Safari 18.0 (iOS)",
    "safari184" to "Safari 18.4",
    "safari184_ios" to "Safari 18.4 (iOS)",
    "safari260" to "Safari 26.0",
    "safari260_ios" to "Safari 26.0 (iOS)",
    "safari2601" to "Safari 26.0.1",
    "tor145" to "Tor (v145)",
)

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

    val fingerprintSpoofer: Preference<String> = preferenceStore.getString(
        "fingerprint_spoofer_target",
        "",
    )

    val defaultUserAgent: Preference<String> = preferenceStore.getString(
        "default_user_agent",
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36",
    )
}
