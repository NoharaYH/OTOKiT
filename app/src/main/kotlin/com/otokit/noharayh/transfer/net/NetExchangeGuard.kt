package com.otokit.noharayh.transfer.net

import com.otokit.noharayh.transfer.CaptureProtocolIssue
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean

/** Each explicitly scheduled call permits one exchange, including HTTP follow-up attempts. */
internal class NetExchangeGuard : Interceptor {
    private class Budget {
        val started = AtomicBoolean(false)
        @Volatile var status: Int? = null
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val budget = requireNotNull(chain.request().tag(Budget::class.java))
        if (!budget.started.compareAndSet(false, true)) {
            budget.status?.let { throw NetHttpException(it) }
            throw NetProtocolException(CaptureProtocolIssue.AUTOMATIC_REPLAY_BLOCKED)
        }
        return chain.proceed(chain.request()).also { budget.status = it.code }
    }

    companion object {
        fun schedule(request: Request): Request = request.newBuilder().tag(Budget::class.java, Budget()).build()
        fun configure(builder: OkHttpClient.Builder): OkHttpClient.Builder =
            builder.retryOnConnectionFailure(false).addNetworkInterceptor(NetExchangeGuard())
    }
}
