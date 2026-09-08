package com.loldraft.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

interface HttpTransport {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String
}

class DefaultHttpTransport(
    private val client: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build(),
) : HttpTransport {
    override suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): String =
        withContext(Dispatchers.IO) {
            val requestBuilder =
                HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()

            for ((k, v) in headers) {
                requestBuilder.header(k, v)
            }

            val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                response.body()
            } else {
                throw RuntimeException("HTTP ${response.statusCode()}: ${response.body()}")
            }
        }
}

class MockHttpTransport(
    private val responses: Map<String, String> = emptyMap(),
    private val defaultResponse: String = "{}",
) : HttpTransport {
    private val recordedRequests = mutableListOf<String>()

    fun getRecordedRequests(): List<String> = recordedRequests.toList()

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): String {
        recordedRequests.add(url)
        return responses[url]
            ?: responses.entries.find { url.contains(it.key) }?.value
            ?: defaultResponse
    }
}
