package com.anonymous.GoExpoApp.nebula

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import java.io.File
import java.io.FileOutputStream
import android.app.Activity
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.ActivityEventListener

class NebulaBridge(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val TAG = "NebulaBridge"
    
    override fun getName(): String = "NebulaBridge"
    
    @ReactMethod
fun startNebula(config: String, key: String, promise: Promise) {
    Log.d(TAG, "Attempting to start Nebula VPN")
    
    try {
        // Check if VPN permission is granted
        val intent = VpnService.prepare(reactContext)
        if (intent != null) {
            Log.d(TAG, "VPN permission required")
            
            // Instead of immediately rejecting the promise, we should store it
            // and use an Activity to request permission
            val currentActivity = reactContext.currentActivity
            if (currentActivity != null) {
                // Store the config and key for later use
                pendingConfig = config
                pendingKey = key
                pendingPromise = promise
                
                // Start the system VPN permission activity
                currentActivity.startActivityForResult(intent, VPN_REQUEST_CODE)
                return
            } else {
                promise.reject("ERROR", "Activity not available for VPN permission")
                return
            }
        }
        
        // VPN permission already granted
        startVpnService(config, key, promise)
    } catch (e: Exception) {
        Log.e(TAG, "Error starting Nebula: ${e.message}")
        e.printStackTrace()
        promise.reject("ERROR", "Failed to start Nebula: ${e.message}")
    }
}

    
    @ReactMethod
    fun stopNebula(promise: Promise) {
        Log.d(TAG, "Stopping Nebula VPN")
        try {
            // Send disconnect intent to the service
            val serviceIntent = Intent(reactContext, NebulaVpnService::class.java).apply {
                action = NebulaVpnService.ACTION_DISCONNECT
            }
            
            reactContext.startService(serviceIntent)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Nebula: ${e.message}")
            promise.reject("ERROR", "Failed to stop Nebula: ${e.message}")
        }
    }
    
    @ReactMethod
    fun checkConnectionStatus(promise: Promise) {
        try {
            // Check if our service reports running
            val serviceRunning = NebulaVpnService.isRunning()
            Log.d(TAG, "Service running: $serviceRunning")
            
            // Also check if the VPN interface is active in the system
            val connectivityManager = reactContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networks = connectivityManager.allNetworks
            
            for (network in networks) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    Log.d(TAG, "VPN interface found in system")
                    promise.resolve(true)
                    return
                }
            }
            
            // If VPN interface is not active, but our service thinks it's running, stop the service
            if (serviceRunning) {
                Log.d(TAG, "VPN interface not found but service running, stopping service")
                stopNebula(promise)
                return
            }
            
            promise.resolve(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connection status: ${e.message}")
            promise.reject("ERROR", "Failed to check connection status: ${e.message}")
        }
    }
    
    @ReactMethod
    fun testConfig(config: String, key: String, promise: Promise) {
        // Save the config temporarily
        try {
            saveConfigToFile(config, "test_config.yaml")
            saveConfigToFile(key, "test_key.txt")
            
            // For now, we'll just assume the config is valid if it can be saved
            // In a real implementation, you would call the Nebula test method
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error testing config: ${e.message}")
            promise.reject("ERROR", "Failed to test config: ${e.message}")
        }
    }
    
    @ReactMethod
    fun getHostmap(promise: Promise) {
        // In a real implementation, you would query the Nebula service for this
        try {
            val isRunning = NebulaVpnService.isRunning()
            val hostmap = if (isRunning) {
                """
                {
                  "100.64.0.1": {
                    "name": "lighthouse",
                    "remote_address": "mza.cosgrid.net:4242",
                    "connection_active": true,
                    "last_handshake": ${System.currentTimeMillis() / 1000}
                  }
                }
                """.trimIndent()
            } else {
                "{}"
            }
            promise.resolve(hostmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting hostmap: ${e.message}")
            promise.reject("ERROR", "Failed to get hostmap: ${e.message}")
        }
    }
    
    @ReactMethod
    fun rebindNebula(reason: String, promise: Promise) {
        try {
            // In a real implementation, you would send a message to the service
            // to rebind its networking
            val isRunning = NebulaVpnService.isRunning()
            promise.resolve(isRunning)
        } catch (e: Exception) {
            Log.e(TAG, "Error rebinding Nebula: ${e.message}")
            promise.reject("ERROR", "Failed to rebind Nebula: ${e.message}")
        }
    }
    
    @ReactMethod
    fun pingHost(host: String, promise: Promise) {
        try {
            if (!NebulaVpnService.isRunning()) {
                Log.d(TAG, "Can't ping when VPN is not connected")
                promise.resolve(false)
                return
            }
            
            // Execute a real ping command
            val process = Runtime.getRuntime().exec("ping -c 3 -W 2 $host")
            val exitValue = process.waitFor()
            Log.d(TAG, "Ping to $host result: $exitValue")
            promise.resolve(exitValue == 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error pinging host: ${e.message}")
            promise.reject("ERROR", "Failed to ping host: ${e.message}")
        }
    }
    
    // Helper to save config to a file in the app's files directory
    private fun saveConfigToFile(content: String, filename: String) {
        try {
            val file = File(reactContext.filesDir, filename)
            FileOutputStream(file).use { 
                it.write(content.toByteArray())
            }
            Log.d(TAG, "Saved $filename to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving $filename: ${e.message}")
        }
    }

    // Add these fields to store pending operations
private var pendingConfig: String? = null
private var pendingKey: String? = null
private var pendingPromise: Promise? = null
private val VPN_REQUEST_CODE = 24601

// Add a method to start the VPN service once permission is granted
private fun startVpnService(config: String, key: String, promise: Promise) {
    // Save config and key for the service to use
    saveConfigToFile(config, "nebula_config.yaml")
    saveConfigToFile(key, "nebula_key.txt")
    
    // Start the VPN service
    val serviceIntent = Intent(reactContext, NebulaVpnService::class.java).apply {
        action = NebulaVpnService.ACTION_CONNECT
        putExtra(NebulaVpnService.EXTRA_CONFIG, config)
        putExtra(NebulaVpnService.EXTRA_PRIVATE_KEY, key)
    }
    
    reactContext.startService(serviceIntent)
    promise.resolve(true)
}

// Add a listener for activity results
private val activityEventListener = object : BaseActivityEventListener() {
    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                // Permission granted, start the VPN
                val config = pendingConfig
                val key = pendingKey
                val promise = pendingPromise
                
                if (config != null && key != null && promise != null) {
                    startVpnService(config, key, promise)
                }
            } else {
                // Permission denied
                pendingPromise?.reject("ERROR", "VPN permission denied")
            }
            
            // Clear pending operation
            pendingConfig = null
            pendingKey = null
            pendingPromise = null
        }
    }
}

// Add this to the class constructor (init block)
init {
    reactContext.addActivityEventListener(activityEventListener)
}
}