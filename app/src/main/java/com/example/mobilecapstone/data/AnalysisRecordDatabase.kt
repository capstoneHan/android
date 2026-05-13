package com.example.mobilecapstone.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "analysis_records")
data class AnalysisRecordEntity(
    @PrimaryKey val recordId: String,
    val imageUri: String?,
    val imageLabel: String,
    val tagsJson: String,
    val heightCm: Double?,
    val weightKg: Double?,
    val frameType: String,
    val createdAt: Long
)

@Entity(tableName = "recommendation_items", primaryKeys = ["recordId", "productId"])
data class RecommendationItemEntity(
    val recordId: String,
    val productId: String,
    val title: String,
    val subtitle: String,
    val priceText: String,
    val description: String,
    val styleTip: String,
    val rawPrice: Int,
    val discountedPrice: Int,
    val brandName: String,
    val season: String,
    val gender: String,
    val baseColour: String,
    val usage: String,
    val rating: Int,
    val productType: String,
    val fit: String,
    val imageUrl: String,
    val matchedTagsJson: String,
    val matchScore: Double,
    val createdAt: Long
)

@Entity(tableName = "item_feedback", primaryKeys = ["recordId", "productId"])
data class ItemFeedbackEntity(
    val recordId: String,
    val productId: String,
    val userRating: Int?,
    val totalDwellTimeMs: Long,
    val viewCount: Int,
    val lastViewedAt: Long?,
    val updatedAt: Long
)

@Entity(tableName = "tag_preferences")
data class TagPreferenceEntity(
    @PrimaryKey val tag: String,
    val weight: Double,
    val updatedAt: Long
)

@Dao
interface AnalysisRecordDao {
    @Query("SELECT * FROM analysis_records ORDER BY createdAt DESC")
    fun observeRecords(): Flow<List<AnalysisRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: AnalysisRecordEntity)

    @Query("DELETE FROM analysis_records WHERE recordId = :recordId")
    suspend fun deleteById(recordId: String)
}

@Dao
interface RecommendationItemDao {
    @Query("SELECT * FROM recommendation_items WHERE recordId = :recordId ORDER BY matchScore DESC, createdAt DESC")
    fun observeByRecordId(recordId: String): Flow<List<RecommendationItemEntity>>

    @Query("SELECT * FROM recommendation_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecommendationItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RecommendationItemEntity>)

    @Query("DELETE FROM recommendation_items WHERE recordId = :recordId")
    suspend fun deleteByRecordId(recordId: String)
}

@Dao
interface ItemFeedbackDao {
    @Query("SELECT * FROM item_feedback")
    fun observeAll(): Flow<List<ItemFeedbackEntity>>

    @Query("SELECT * FROM item_feedback WHERE recordId = :recordId AND productId = :productId LIMIT 1")
    suspend fun getFeedback(recordId: String, productId: String): ItemFeedbackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(feedback: ItemFeedbackEntity)
}

@Dao
interface TagPreferenceDao {
    @Query("SELECT * FROM tag_preferences ORDER BY weight DESC")
    fun observeAll(): Flow<List<TagPreferenceEntity>>

    @Query("SELECT * FROM tag_preferences WHERE tag = :tag LIMIT 1")
    suspend fun getByTag(tag: String): TagPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: TagPreferenceEntity)
}

@Database(
    entities = [
        AnalysisRecordEntity::class,
        RecommendationItemEntity::class,
        ItemFeedbackEntity::class,
        TagPreferenceEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AnalysisRecordDatabase : RoomDatabase() {
    abstract fun analysisRecordDao(): AnalysisRecordDao
    abstract fun recommendationItemDao(): RecommendationItemDao
    abstract fun itemFeedbackDao(): ItemFeedbackDao
    abstract fun tagPreferenceDao(): TagPreferenceDao

    companion object {
        @Volatile
        private var INSTANCE: AnalysisRecordDatabase? = null

        fun getInstance(context: Context): AnalysisRecordDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnalysisRecordDatabase::class.java,
                    "analysis_records.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
