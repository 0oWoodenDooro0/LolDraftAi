package com.loldraft.data.sources

interface HttpTransport {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String
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
