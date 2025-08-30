package com.example.poetica.data.api

import android.content.Context
import android.util.Log
import com.example.poetica.data.config.PoeticaConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiConfig {
    
    private const val TAG = "ApiConfig"
    
    // API Configuration - Timeouts
    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L
    
    // Search Configuration
    const val DEFAULT_SEARCH_LIMIT = 30
    const val DEFAULT_SECTIONS_PAGE_SIZE = 20
    const val MAX_SEARCH_RESULTS = 100
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }
    
    private val httpLoggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        val startTime = System.currentTimeMillis()
        
        Log.d(TAG, "🚀 HTTP Request: ${request.method} ${request.url}")
        Log.d(TAG, "🚀 Request headers: ${request.headers}")
        
        try {
            val response = chain.proceed(request)
            val duration = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "✅ HTTP Response: ${response.code} ${response.message} (${duration}ms)")
            Log.d(TAG, "✅ Response URL: ${response.request.url}")
            Log.d(TAG, "✅ Response headers: ${response.headers}")
            
            // Log response body for debugging (be careful with large responses)
            if (response.body != null) {
                val contentLength = response.body!!.contentLength()
                Log.d(TAG, "✅ Response body size: $contentLength bytes")
            }
            
            response
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ HTTP Request failed after ${duration}ms: ${e.message}", e)
            throw e
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(httpLoggingInterceptor)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    fun createApiService(context: Context? = null, customBaseUrl: String? = null): PoeticaApiService {
        val effectiveBaseUrl = when {
            !customBaseUrl.isNullOrBlank() -> customBaseUrl
            context != null -> PoeticaConfig.getInstance(context).apiBaseUrl
            else -> PoeticaConfig.PRODUCTION_API_BASE_URL // Fallback to production
        }
        
        Log.d(TAG, "🔧 Creating API service with base URL: $effectiveBaseUrl")
        Log.d(TAG, "🔧 Source: ${when {
            !customBaseUrl.isNullOrBlank() -> "Custom URL"
            context != null -> "PoeticaConfig (${PoeticaConfig.getInstance(context).getCurrentEnvironment()})"
            else -> "Fallback to Production"
        }}")
        Log.d(TAG, "🔧 Timeouts - Connect: ${CONNECT_TIMEOUT_SECONDS}s, Read: ${READ_TIMEOUT_SECONDS}s, Write: ${WRITE_TIMEOUT_SECONDS}s")
        
        val retrofit = Retrofit.Builder()
            .baseUrl(effectiveBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        
        val apiService = retrofit.create(PoeticaApiService::class.java)
        Log.d(TAG, "✅ API service created successfully")
        
        return apiService
    }
    
    @Deprecated("Use createApiService(context) for better configuration management", 
                ReplaceWith("createApiService(context, baseUrl)"))
    fun createApiService(baseUrl: String): PoeticaApiService {
        return createApiService(null, baseUrl)
    }
}