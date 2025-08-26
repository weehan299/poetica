package com.example.poetica.data.api

import android.util.Log
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
    
    // API Configuration - Using actual machine IP for reliable emulator access
    const val DEFAULT_BASE_URL = "http://172.30.28.71:8000"
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
    
    fun createApiService(baseUrl: String = DEFAULT_BASE_URL): PoeticaApiService {
        val effectiveBaseUrl = if (baseUrl.isNotBlank()) baseUrl else DEFAULT_BASE_URL
        
        Log.d(TAG, "🔧 Creating API service with base URL: $effectiveBaseUrl")
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
}