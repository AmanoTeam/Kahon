package eu.kanade.tachiyomi.network.interceptor

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds a `Sec-Fetch-Dest: empty` header to requests targeting the given hosts
 * that don't already carry one, mimicking a browser fetch. Requests to any
 * other host, or where the source sets its own value (`document` for
 * navigations, `image` for CDN fetches, etc.), are left untouched.
 */
class SecFetchDestInterceptor(
    private val hosts: Set<String>,
) : Interceptor {

    private fun HttpUrl.matchesHost(): Boolean = hosts.any { host == it || host.endsWith(".$it") }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        return if (originalRequest.url.matchesHost() &&
            originalRequest.header("Sec-Fetch-Dest").isNullOrEmpty()
        ) {
            val newRequest = originalRequest
                .newBuilder()
                .addHeader("Sec-Fetch-Dest", "empty")
                .build()
            chain.proceed(newRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
