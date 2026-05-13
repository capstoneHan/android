package com.example.mobilecapstone.data

import com.example.mobilecapstone.RecommendationItem
import com.example.mobilecapstone.jsonArrayToList
import com.example.mobilecapstone.tagsToJson

internal fun RecommendationItem.toEntity(recordId: String, createdAt: Long): RecommendationItemEntity {
    return RecommendationItemEntity(
        recordId = recordId,
        productId = id,
        title = title,
        subtitle = subtitle,
        priceText = price,
        description = description,
        styleTip = styleTip,
        rawPrice = rawPrice,
        discountedPrice = discountedPrice,
        brandName = brandName,
        season = season,
        gender = gender,
        baseColour = baseColour,
        usage = usage,
        rating = rating,
        productType = productType,
        fit = fit,
        imageUrl = imageUrl,
        matchedTagsJson = tagsToJson(matchedTags),
        matchScore = matchScore,
        createdAt = createdAt
    )
}

internal fun RecommendationItemEntity.toModel(feedback: ItemFeedbackEntity? = null): RecommendationItem {
    return RecommendationItem(
        id = productId,
        title = title,
        subtitle = subtitle,
        price = priceText,
        description = description,
        styleTip = styleTip,
        rawPrice = rawPrice,
        discountedPrice = discountedPrice,
        brandName = brandName,
        season = season,
        gender = gender,
        baseColour = baseColour,
        usage = usage,
        rating = rating,
        productType = productType,
        fit = fit,
        imageUrl = imageUrl,
        matchedTags = jsonArrayToList(org.json.JSONArray(matchedTagsJson)),
        matchScore = matchScore,
        userRating = feedback?.userRating,
        totalDwellTimeMs = feedback?.totalDwellTimeMs ?: 0L
    )
}
