package com.example.mobilecapstone

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST


private const val BASE_URL = "http://192.100.10.100:8080/" // 본인 ip로 실행
internal data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String,
    val height: Int,
    val weight: Int
)

internal data class LoginRequest(
    val email: String,
    val password: String
)

internal data class AuthResponse(
    val success: Boolean,
    val message: String,
    val username: String? = null,
    val height: Int? = null,
    val weight: Int? = null
)

internal interface AuthApi {
    @POST("api/user/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): AuthResponse

    @POST("api/user/login")
    suspend fun login(
        @Body request: LoginRequest
    ): AuthResponse
}

internal object AuthClient {
    val api: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }
}