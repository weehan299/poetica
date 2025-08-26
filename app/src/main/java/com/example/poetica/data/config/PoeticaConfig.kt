package com.example.poetica.data.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class PoeticaConfig private constructor(context: Context) {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    companion object {
        private const val TAG = "PoeticaConfig"
        private const val PREFS_NAME = "poetica_config"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_USE_REMOTE_DATA = "use_remote_data"
        private const val KEY_API_ENABLED = "api_enabled"
        
        // Default values - Using actual machine IP for reliable emulator access
        const val DEFAULT_API_BASE_URL = "http://172.30.28.71:8000"
        const val DEFAULT_USE_REMOTE_DATA = true
        
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
            val url = sharedPreferences.getString(KEY_API_BASE_URL, DEFAULT_API_BASE_URL) ?: DEFAULT_API_BASE_URL
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
        sharedPreferences.edit()
            .putString(KEY_API_BASE_URL, DEFAULT_API_BASE_URL)
            .putBoolean(KEY_USE_REMOTE_DATA, DEFAULT_USE_REMOTE_DATA)
            .putBoolean(KEY_API_ENABLED, true)
            .apply()
        Log.i(TAG, "⚙️ Configuration reset completed -> ${getConfigSummary()}")
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
        
        // Only update if currently using default localhost
        if (apiBaseUrl == DEFAULT_API_BASE_URL || apiBaseUrl == localhostUrl) {
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
            "isEmulator" to isRunningOnEmulator()
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