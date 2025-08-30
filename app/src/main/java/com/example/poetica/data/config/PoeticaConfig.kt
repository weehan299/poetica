package com.example.poetica.data.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class PoeticaConfig private constructor(private val context: Context) {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    companion object {
        private const val TAG = "PoeticaConfig"
        private const val PREFS_NAME = "poetica_config"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_USE_REMOTE_DATA = "use_remote_data"
        private const val KEY_API_ENABLED = "api_enabled"
        
        // API URL Constants
        const val LOCAL_API_BASE_URL = "http://172.30.28.71:8000"
        const val PRODUCTION_API_BASE_URL = "https://poetica-api-544010023223.us-central1.run.app"
        
        // Default values - Will be set based on build variant
        const val DEFAULT_USE_REMOTE_DATA = true
        
        // Get default URL based on build configuration
        fun getDefaultApiBaseUrl(context: Context): String {
            return try {
                // Try to use BuildConfig API_BASE_URL if available
                val buildConfigClass = Class.forName("${context.packageName}.BuildConfig")
                val apiUrlField = buildConfigClass.getField("API_BASE_URL")
                val apiUrl = apiUrlField.get(null) as String
                Log.d(TAG, "⚙️ Using BuildConfig API URL: $apiUrl")
                apiUrl
            } catch (e: Exception) {
                try {
                    // Fallback to DEBUG field check
                    val buildConfigClass = Class.forName("${context.packageName}.BuildConfig")
                    val debugField = buildConfigClass.getField("DEBUG")
                    val isDebug = debugField.getBoolean(null)
                    val url = if (isDebug) LOCAL_API_BASE_URL else PRODUCTION_API_BASE_URL
                    Log.d(TAG, "⚙️ Using DEBUG field fallback, isDebug=$isDebug -> $url")
                    url
                } catch (e2: Exception) {
                    Log.d(TAG, "⚙️ Could not determine build type, using production URL: ${e2.message}")
                    PRODUCTION_API_BASE_URL
                }
            }
        }
        
        @Volatile
        private var INSTANCE: PoeticaConfig? = null
        
        fun getInstance(context: Context): PoeticaConfig {
            val instance = INSTANCE ?: synchronized(this) {
                INSTANCE ?: PoeticaConfig(context.applicationContext).also { INSTANCE = it }
            }
            Log.d(TAG, "⚙️ PoeticaConfig.getInstance() -> ${instance.getConfigSummary()}")
            return instance
        }
    }
    
    // API Base URL Configuration
    var apiBaseUrl: String
        get() {
            val defaultUrl = getDefaultApiBaseUrl(context)
            val url = sharedPreferences.getString(KEY_API_BASE_URL, defaultUrl) ?: defaultUrl
            Log.d(TAG, "⚙️ apiBaseUrl getter -> $url")
            return url
        }
        set(value) {
            Log.d(TAG, "⚙️ apiBaseUrl setter -> $value (was: $apiBaseUrl)")
            sharedPreferences.edit()
                .putString(KEY_API_BASE_URL, value)
                .apply()
        }
    
    // Remote Data Usage Configuration
    var useRemoteData: Boolean
        get() {
            val enabled = sharedPreferences.getBoolean(KEY_USE_REMOTE_DATA, DEFAULT_USE_REMOTE_DATA)
            Log.d(TAG, "⚙️ useRemoteData getter -> $enabled")
            return enabled
        }
        set(value) {
            Log.d(TAG, "⚙️ useRemoteData setter -> $value (was: $useRemoteData)")
            sharedPreferences.edit()
                .putBoolean(KEY_USE_REMOTE_DATA, value)
                .apply()
        }
    
    // API Enabled State (can be toggled based on health checks)
    var isApiEnabled: Boolean
        get() {
            val enabled = sharedPreferences.getBoolean(KEY_API_ENABLED, true)
            Log.d(TAG, "⚙️ isApiEnabled getter -> $enabled")
            return enabled
        }
        set(value) {
            Log.d(TAG, "⚙️ isApiEnabled setter -> $value (was: $isApiEnabled)")
            sharedPreferences.edit()
                .putBoolean(KEY_API_ENABLED, value)
                .apply()
        }
    
    // Convenience methods
    fun resetToDefaults() {
        Log.i(TAG, "⚙️ Resetting configuration to defaults")
        val defaultUrl = getDefaultApiBaseUrl(context)
        sharedPreferences.edit()
            .putString(KEY_API_BASE_URL, defaultUrl)
            .putBoolean(KEY_USE_REMOTE_DATA, DEFAULT_USE_REMOTE_DATA)
            .putBoolean(KEY_API_ENABLED, true)
            .apply()
        Log.i(TAG, "⚙️ Configuration reset completed -> ${getConfigSummary()}")
    }
    
    // API Environment Switching Methods
    fun useLocalApi() {
        Log.i(TAG, "⚙️ Switching to Local API: $LOCAL_API_BASE_URL")
        apiBaseUrl = LOCAL_API_BASE_URL
        useRemoteData = true
        isApiEnabled = true
    }
    
    fun useProductionApi() {
        Log.i(TAG, "⚙️ Switching to Production API: $PRODUCTION_API_BASE_URL")
        apiBaseUrl = PRODUCTION_API_BASE_URL
        useRemoteData = true
        isApiEnabled = true
    }
    
    fun useBundledDataOnly() {
        Log.i(TAG, "⚙️ Switching to bundled data only (no API)")
        useRemoteData = false
        isApiEnabled = false
    }
    
    fun getCurrentEnvironment(): String {
        return when {
            !useRemoteData || !isApiEnabled -> "LOCAL_BUNDLED"
            apiBaseUrl.contains(LOCAL_API_BASE_URL) -> "LOCAL_API"
            apiBaseUrl.contains(PRODUCTION_API_BASE_URL) -> "PRODUCTION_API"
            else -> "CUSTOM_API"
        }
    }
    
    fun isLocalMode(): Boolean {
        val localMode = !useRemoteData || !isApiEnabled
        Log.d(TAG, "⚙️ isLocalMode() -> $localMode (useRemoteData=$useRemoteData, isApiEnabled=$isApiEnabled)")
        return localMode
    }
    
    fun getEffectiveApiBaseUrl(): String {
        val effectiveUrl = if (useRemoteData && isApiEnabled) {
            apiBaseUrl
        } else {
            "" // Will cause API calls to fail gracefully
        }
        Log.d(TAG, "⚙️ getEffectiveApiBaseUrl() -> '$effectiveUrl' (useRemoteData=$useRemoteData, isApiEnabled=$isApiEnabled)")
        return effectiveUrl
    }
    
    // Network configuration helpers
    fun detectAndSetOptimalApiUrl(context: Context): String {
        val emulatorUrl = "http://10.0.2.2:8000"  // Android emulator localhost mapping
        val localhostUrl = "http://localhost:8000"  // Regular localhost
        
        // Detect if running on emulator
        val isEmulator = android.os.Build.FINGERPRINT.contains("generic") ||
                android.os.Build.FINGERPRINT.contains("unknown") ||
                android.os.Build.MODEL.contains("google_sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK built for") ||
                android.os.Build.MANUFACTURER.contains("Genymotion") ||
                android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic")
        
        Log.d(TAG, "📱 Device detection: isEmulator=$isEmulator")
        Log.d(TAG, "📱 Build info - FINGERPRINT=${android.os.Build.FINGERPRINT}, MODEL=${android.os.Build.MODEL}, MANUFACTURER=${android.os.Build.MANUFACTURER}")
        
        val optimalUrl = if (isEmulator) emulatorUrl else localhostUrl
        val currentUrl = apiBaseUrl
        
        Log.d(TAG, "📱 Current API URL: $currentUrl, Optimal URL: $optimalUrl")
        
        // Only update if currently using default URL or localhost
        val defaultUrl = getDefaultApiBaseUrl(context)
        if (apiBaseUrl == defaultUrl || apiBaseUrl == localhostUrl) {
            Log.i(TAG, "📱 Updating API URL from $currentUrl to $optimalUrl")
            apiBaseUrl = optimalUrl
        } else {
            Log.d(TAG, "📱 Keeping existing API URL: $currentUrl")
        }
        
        return optimalUrl
    }
    
    fun setApiUrlForEmulator() {
        Log.i(TAG, "📱 Setting API URL for emulator")
        apiBaseUrl = "http://10.0.2.2:8000"
    }
    
    fun setApiUrlForDevice(deviceIp: String = "192.168.1.100") {
        Log.i(TAG, "📱 Setting API URL for device: $deviceIp")
        apiBaseUrl = "http://$deviceIp:8000"
    }
    
    // Debug information
    fun getConfigSummary(): Map<String, Any> {
        val summary = mapOf(
            "apiBaseUrl" to apiBaseUrl,
            "useRemoteData" to useRemoteData,
            "isApiEnabled" to isApiEnabled,
            "isLocalMode" to isLocalMode(),
            "isEmulator" to isRunningOnEmulator(),
            "currentEnvironment" to getCurrentEnvironment()
        )
        Log.d(TAG, "⚙️ Config summary: $summary")
        return summary
    }
    
    fun logCurrentConfig() {
        Log.i(TAG, "⚙️ === POETICA CONFIG STATUS ===")
        Log.i(TAG, "⚙️ API Base URL: $apiBaseUrl")
        Log.i(TAG, "⚙️ Use Remote Data: $useRemoteData")
        Log.i(TAG, "⚙️ API Enabled: $isApiEnabled")
        Log.i(TAG, "⚙️ Local Mode: ${isLocalMode()}")
        Log.i(TAG, "⚙️ Is Emulator: ${isRunningOnEmulator()}")
        Log.i(TAG, "⚙️ Effective URL: ${getEffectiveApiBaseUrl()}")
        Log.i(TAG, "⚙️ ===========================")
    }
    
    private fun isRunningOnEmulator(): Boolean {
        val isEmulator = android.os.Build.FINGERPRINT.contains("generic") ||
                android.os.Build.FINGERPRINT.contains("unknown") ||
                android.os.Build.MODEL.contains("google_sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK built for") ||
                android.os.Build.MANUFACTURER.contains("Genymotion") ||
                android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic")
        
        Log.d(TAG, "📱 isRunningOnEmulator() -> $isEmulator")
        return isEmulator
    }
}