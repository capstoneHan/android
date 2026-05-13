package com.example.mobilecapstone.data

import com.example.mobilecapstone.RecommendationItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

internal data class ServerRecommendationRecord(
    val recordId: String,
    val recommendations: List<RecommendationItem>
)

internal class MockRecommendationServer {
    private val records = MutableStateFlow<Map<String, ServerRecommendationRecord>>(emptyMap())

    fun observeRecords(): StateFlow<Map<String, ServerRecommendationRecord>> = records

    fun saveRecommendations(recordId: String, recommendations: List<RecommendationItem>) {
        records.update { current ->
            current + (recordId to ServerRecommendationRecord(recordId, recommendations))
        }
    }

    fun deleteRecommendations(recordId: String) {
        records.update { current -> current - recordId }
    }
}
