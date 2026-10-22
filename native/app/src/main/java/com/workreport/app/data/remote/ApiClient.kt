package com.workreport.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 网络客户端（为后续在线功能预留）。
 *
 * 当前版本**不会发起任何网络请求**，App 全部功能均为纯本地计算。
 * 这里把 OkHttp 封装成单例并统一超时/重试策略，后续要做「在线模板」「云端同步」
 * 之类的功能时直接复用，不必再改网络层。
 */
class ApiClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 通用 GET，返回响应体字符串；调用方自行解析。 */
    @Throws(IOException::class)
    fun get(path: String, headers: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder().url(baseUrl.trimEnd('/') + path)
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { response: Response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    /** 当前是否允许联网：默认关闭，保证「不联网」的隐私承诺。 */
    @Volatile
    var networkEnabled: Boolean = false

    companion object {
        const val DEFAULT_BASE_URL = "https://example.com/api/"
    }
}
