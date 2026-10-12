package com.example.nhumonglenh.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/** Prevent ngrok's free-plan browser interstitial from replacing API JSON. */
object NgrokBrowserWarningInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host.lowercase(java.util.Locale.ROOT)
        val isNgrokDevDomain = host.endsWith(".ngrok-free.dev") || host.endsWith(".ngrok-free.app")
        return chain.proceed(
            if (isNgrokDevDomain) request.newBuilder()
                .header("ngrok-skip-browser-warning", "true")
                .build()
            else request
        )
    }
}
