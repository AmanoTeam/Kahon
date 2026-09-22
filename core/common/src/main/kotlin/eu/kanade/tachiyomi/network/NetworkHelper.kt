package eu.kanade.tachiyomi.network

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(AppScope::class)
class NetworkHelper(
    private val context: Context,
    private val preferences: NetworkPreferences,
) {

    val cookieJar = AndroidCookieJar()

    private val clientBuilder: OkHttpClient.Builder = run {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30.seconds)
            .readTimeout(30.seconds)
            .callTimeout(2.minutes)
            .cache(
                Cache(
                    directory = File(context.cacheDir, "network_cache"),
                    maxSize = 5L * 1024 * 1024, // 5 MiB
                ),
            )
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))

        if (preferences.verboseLogging.get()) {
            val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addNetworkInterceptor(httpLoggingInterceptor)
        }

        when (preferences.dohProvider.get()) {
            PREF_DOH_CLOUDFLARE -> builder.dohCloudflare()
            PREF_DOH_GOOGLE -> builder.dohGoogle()
            PREF_DOH_ADGUARD -> builder.dohAdGuard()
            PREF_DOH_QUAD9 -> builder.dohQuad9()
            PREF_DOH_ALIDNS -> builder.dohAliDNS()
            PREF_DOH_DNSPOD -> builder.dohDNSPod()
            PREF_DOH_360 -> builder.doh360()
            PREF_DOH_QUAD101 -> builder.dohQuad101()
            PREF_DOH_MULLVAD -> builder.dohMullvad()
            PREF_DOH_CONTROLD -> builder.dohControlD()
            PREF_DOH_NJALLA -> builder.dohNajalla()
            PREF_DOH_SHECAN -> builder.dohShecan()
            else -> builder
        }

        val proxyType = preferences.proxyType.get()
        val proxyHost = preferences.proxyHost.get().trim()
        val proxyPort = preferences.proxyPort.get().toIntOrNull()
        if (proxyType != PREF_PROXY_DISABLED && proxyHost.isNotBlank() && proxyPort != null && proxyPort in 1..65535) {
            val proxy = Proxy(
                if (proxyType == PREF_PROXY_HTTP) Proxy.Type.HTTP else Proxy.Type.SOCKS,
                InetSocketAddress(proxyHost, proxyPort),
            )
            builder.proxy(proxy)

            val proxyUsername = preferences.proxyUsername.get()
            val proxyPassword = preferences.proxyPassword.get()
            if (proxyUsername.isNotBlank()) {
                if (proxyType == PREF_PROXY_HTTP) {
                    builder.proxyAuthenticator { _, response ->
                        if (response.request.header("Proxy-Authorization") != null) {
                            null
                        } else {
                            response.request.newBuilder()
                                .header("Proxy-Authorization", Credentials.basic(proxyUsername, proxyPassword))
                                .build()
                        }
                    }
                } else {
                    Authenticator.setDefault(
                        object : Authenticator() {
                            override fun getPasswordAuthentication(): PasswordAuthentication? {
                                if (requestingPort != proxyPort) return null
                                val host = requestingHost ?: requestingSite?.hostName
                                if (host != proxyHost) return null
                                return PasswordAuthentication(proxyUsername, proxyPassword.toCharArray())
                            }
                        },
                    )
                }
            }
        }

        builder
    }

    val client = clientBuilder
        .addInterceptor(
            CloudflareInterceptor(context, cookieJar, ::defaultUserAgentProvider),
        )
        .build()

    /**
     * @deprecated Since extension-lib 1.5
     */
    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient = client

    fun defaultUserAgentProvider() = preferences.defaultUserAgent.get().trim()
}
