package com.example.mobilecapstone

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

internal data class RecommendationRequest(
    val userKey: String?,
    val recordId: String?,
    val styleTags: List<String>,
    val tagPreferences: List<TagPreferencePayload>,
    val filters: RecommendationFilterPayload,
    val bodyFeatures: BodyFeaturePayload
)

internal data class TagPreferencePayload(
    val tag: String,
    val weight: Double
)

internal data class RecommendationFilterPayload(
    val minPrice: Int,
    val maxPrice: Int,
    val season: String?,
    val gender: String?,
    val usage: String?,
    val baseColour: String?,
    val brandName: String?,
    val articleType: String?,
    val styleTag: String?,
    val fit: String?,
    val discountedOnly: Boolean
)

internal data class BodyFeaturePayload(
    val frameType: String,
    val waistDefinition: String,
    val shoulderProfile: String,
    val silhouetteProfile: String,
    val upperLowerBalance: String,
    val faceShape: String,
    val skinUndertone: String,
    val skinClarity: String,
    val bmiBand: String,
    val heightBand: String
)

internal data class RecommendationListResponse(
    val recommendations: List<RecommendationResponse> = emptyList()
)

internal data class RecommendationResponse(
    val productId: String,
    val product: ProductResponse? = null,
    val matchedTags: List<String> = emptyList(),
    val matchScore: Double = 0.0,
    val topRecommendation: Boolean = false
)

internal data class ProductBatchRequest(
    val productIds: List<String>
)

internal data class ProductBatchResponse(
    val products: List<ProductResponse> = emptyList()
)

internal data class ProductResponse(
    val id: String,
    val title: String = "",
    val subtitle: String = "",
    val price: String = "",
    val description: String = "",
    val styleTip: String = "",
    val rawPrice: Int = 0,
    val discountedPrice: Int = rawPrice,
    val brandName: String = "",
    val season: String = "All",
    val gender: String = "All",
    val baseColour: String = "NA",
    val usage: String = "Fashion",
    val rating: Int = 0,
    val productType: String = "",
    val fit: String = "",
    val imageUrl: String = "",
    val tags: List<String> = emptyList()
)

internal interface RecommendationApi {
    @POST("api/recommendations")
    suspend fun createRecommendations(
        @Body request: RecommendationRequest
    ): RecommendationListResponse

    @POST("api/products/batch")
    suspend fun getProducts(
        @Body request: ProductBatchRequest
    ): ProductBatchResponse

    @GET("api/products/{productId}")
    suspend fun getProductDetail(
        @Path("productId") productId: String
    ): ProductResponse
}

internal object RecommendationClient {
    val api: RecommendationApi by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RecommendationApi::class.java)
    }
}

internal fun buildRecommendationRequest(
    userKey: String?,
    recordId: String?,
    summary: ResultSummary,
    filters: RecommendationFilterState,
    tagPreferenceWeights: Map<String, Double>
): RecommendationRequest {
    return RecommendationRequest(
        userKey = userKey,
        recordId = recordId,
        styleTags = summary.tags,
        tagPreferences = tagPreferenceWeights.map { (tag, weight) ->
            TagPreferencePayload(tag = tag, weight = weight)
        },
        filters = RecommendationFilterPayload(
            minPrice = 0,
            maxPrice = 500_000,
            season = null,
            gender = null,
            usage = null,
            baseColour = null,
            brandName = null,
            articleType = null,
            styleTag = null,
            fit = null,
            discountedOnly = false
        ),
        bodyFeatures = BodyFeaturePayload(
            frameType = summary.frameType,
            waistDefinition = summary.waistDefinition,
            shoulderProfile = summary.shoulderProfile,
            silhouetteProfile = summary.silhouetteProfile,
            upperLowerBalance = summary.upperLowerBalance,
            faceShape = summary.faceShape,
            skinUndertone = summary.skinUndertone,
            skinClarity = summary.skinClarity,
            bmiBand = summary.bmiBand,
            heightBand = summary.heightBand
        )
    )
}

internal fun RecommendationResponse.toModel(): RecommendationItem {
    val productInfo = product

    // TODO: When the backend keeps product details as the source of truth,
    // keep only recordId/productId/matchedTags/matchScore in Room and hydrate
    // productInfo from getProducts() or getProductDetail() before rendering history.
    return RecommendationItem(
        id = productInfo?.id ?: productId,
        title = productInfo?.title.orEmpty(),
        subtitle = productInfo?.subtitle.orEmpty(),
        price = productInfo?.price.orEmpty(),
        description = productInfo?.description.orEmpty(),
        styleTip = productInfo?.styleTip.orEmpty(),
        rawPrice = productInfo?.rawPrice ?: 0,
        discountedPrice = productInfo?.discountedPrice ?: (productInfo?.rawPrice ?: 0),
        brandName = productInfo?.brandName.orEmpty(),
        season = productInfo?.season ?: "All",
        gender = productInfo?.gender ?: "All",
        baseColour = productInfo?.baseColour ?: "NA",
        usage = productInfo?.usage ?: "Fashion",
        rating = productInfo?.rating ?: 0,
        productType = productInfo?.productType.orEmpty(),
        fit = productInfo?.fit.orEmpty(),
        imageUrl = productInfo?.imageUrl.orEmpty(),
        productTags = productInfo?.tags.orEmpty(),
        matchedTags = matchedTags,
        matchScore = matchScore,
        topRecommendation = topRecommendation
    )
}
