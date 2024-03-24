package com.hcl.tool

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class HttpHelper(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val afterProcess: ((HttpUrl.Builder, Request.Builder) -> Unit)?
) {

    var urlBuidler = baseUrl.newBuilder()
    var requestBuiler = Request.Builder()

    fun path(path: String): HttpHelper {
        urlBuidler.addPathSegments(path)
        return this
    }

    fun param(key: String, value: String): HttpHelper {
        urlBuidler.addQueryParameter(key, value)
        return this
    }

    fun get(): Response {
        val url = urlBuidler
        val requestB = requestBuiler
        urlBuidler = baseUrl.newBuilder()
        requestBuiler = Request.Builder()

        afterProcess?.invoke(url, requestB)
        val request = requestB.url(url.build()).build()
        return client.newCall(request).execute()
    }

}