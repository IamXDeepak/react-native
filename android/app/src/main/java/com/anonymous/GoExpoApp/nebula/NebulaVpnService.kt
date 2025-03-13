package com.anonymous.GoExpoApp.nebula

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class NebulaVpnService : VpnService() {
    companion object {
        const val TAG = "NebulaVpnService"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_CHANNEL_ID = "nebula_vpn_channel"
        
        const val ACTION_CONNECT = "com.anonymous.GoExpoApp.CONNECT"
        const val ACTION_DISCONNECT = "com.anonymous.GoExpoApp.DISCONNECT"
        
        const val EXTRA_CONFIG = "extra_config"
        const val EXTRA_PRIVATE_KEY = "extra_private_key"
        
        // Status to track if the service is running
        private val running = AtomicBoolean(false)
        
        fun isRunning(): Boolean = running.get()
    }
    
    // Store Nebula instance
    private val nebulaInstanceRef = AtomicReference<Any?>(null)
    private var tunFd: ParcelFileDescriptor? = null
    
    // Reflection fields for Nebula
    private var nebulaClass: Class<*>? = null
    private var mobilenebulaClass: Class<*>? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NebulaVpnService onCreate")
        initNotificationChannel()
        
        // Initialize Nebula classes
        try {
            // Try different package names
            val packageNames = listOf(
                "mobileNebula",
                "go.mobileNebula",
                "nebula.mobileNebula"
            )
            
            for (packageName in packageNames) {
                try {
                    mobilenebulaClass = Class.forName("$packageName.MobileNebula")
                    nebulaClass = Class.forName("$packageName.Nebula")
                    Log.d(TAG, "Found Nebula classes in package: $packageName")
                    dumpMethods(mobilenebulaClass!!, "MobileNebula")
                    dumpMethods(nebulaClass!!, "Nebula")
                    break
                } catch (e: Exception) {
                    // Continue trying other packages
                }
            }
            
            if (nebulaClass == null || mobilenebulaClass == null) {
                Log.e(TAG, "Failed to find Nebula classes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Nebula classes: ${e.message}", e)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "NebulaVpnService onStartCommand: ${intent?.action}")
        
        if (intent == null) {
            return START_NOT_STICKY
        }
        
        when (intent.action) {
            ACTION_CONNECT -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                val privateKey = intent.getStringExtra(EXTRA_PRIVATE_KEY)
                
                if (config == null || privateKey == null) {
                    Log.e(TAG, "Missing config or private key")
                    stopSelf()
                    return START_NOT_STICKY
                }
                
                // Simple approach - just start foreground service
                startForeground(NOTIFICATION_ID, createNotification("Connecting to Nebula VPN..."))
                
                connectVpn(config, privateKey)
            }
            
            ACTION_DISCONNECT -> {
                disconnectVpn()
                stopSelf()
            }
            
            else -> {
                // Unknown action
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    private fun connectVpn(config: String, privateKey: String) {
        // Launch in a background thread
        Thread {
            Log.d(TAG, "Connecting to Nebula VPN")
            
            try {
                // Save configuration for debugging
                saveConfigToFile(config, "nebula_config.yaml")
                saveConfigToFile(privateKey, "nebula_key.txt")
                
                // Create log file
                val logFile = File(filesDir, "nebula.log")
                Log.d(TAG, "Log file: ${logFile.absolutePath}")
                
                // Set up VPN interface
                val builder = Builder()
                    .setSession("Nebula VPN")
                    .addAddress("100.64.0.1", 24) // Use the IP from your config
                    .addRoute("100.64.0.0", 16)   // Route all Nebula traffic
                    .allowFamily(android.system.OsConstants.AF_INET)
                    .allowFamily(android.system.OsConstants.AF_INET6)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }
                
                tunFd = builder.establish()
                if (tunFd == null) {
                    Log.e(TAG, "Failed to establish VPN interface")
                    stopSelf()
                    return@Thread
                }
                
                Log.d(TAG, "VPN interface established with fd: ${tunFd!!.fd}")
                
                // Start Nebula using reflection
                if (mobilenebulaClass != null) {
                    try {
                        // Based on the method dump, we know the exact method name is "newNebula"
                        // with parameters (String, String, String, long)
                        val method = mobilenebulaClass!!.getMethod(
                            "newNebula", 
                            String::class.java, 
                            String::class.java, 
                            String::class.java, 
                            Long::class.javaPrimitiveType
                        )
                        
                        val nebulaInstance = method.invoke(
                            null, // static method
                            config,
                            privateKey,
                            logFile.absolutePath,
                            tunFd!!.fd.toLong()
                        )
                        
                        if (nebulaInstance != null) {
                            nebulaInstanceRef.set(nebulaInstance)
                            Log.d(TAG, "Nebula instance created")
                            
                            // Based on the method dump, the method is "start" with no parameters
                            val startMethod = nebulaClass!!.getMethod("start")
                            startMethod.invoke(nebulaInstance)
                            Log.d(TAG, "Nebula started")
                            
                            // Update notification
                            updateNotification("Connected to Nebula VPN")
                            
                            // Mark as running
                            running.set(true)
                        } else {
                            Log.e(TAG, "Failed to create Nebula instance")
                            stopSelf()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting Nebula: ${e.message}")
                        e.printStackTrace()
                        stopSelf()
                    }
                } else {
                    Log.e(TAG, "Nebula classes not found")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to VPN: ${e.message}")
                e.printStackTrace()
                stopSelf()
            }
        }.start()
    }
    
    private fun disconnectVpn() {
        Log.d(TAG, "Disconnecting from Nebula VPN")
        
        // Stop Nebula
        val nebula = nebulaInstanceRef.get()
        if (nebula != null && nebulaClass != null) {
            try {
                val stopMethod = nebulaClass!!.getMethod("stop")
                stopMethod.invoke(nebula)
                Log.d(TAG, "Nebula stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping Nebula: ${e.message}")
            }
        }
        
        // Close TUN device
        try {
            tunFd?.close()
            tunFd = null
            Log.d(TAG, "TUN device closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TUN device: ${e.message}")
        }
        
        // Reset state
        nebulaInstanceRef.set(null)
        running.set(false)
    }
    
    override fun onDestroy() {
        Log.d(TAG, "NebulaVpnService onDestroy")
        disconnectVpn()
        super.onDestroy()
    }
    
    override fun onRevoke() {
        Log.d(TAG, "NebulaVpnService onRevoke")
        disconnectVpn()
        stopForeground(true)
        stopSelf()
        super.onRevoke()
    }
    
    private fun initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Nebula VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Nebula VPN connection status"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Nebula VPN")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }
    
    // Helper method to dump all methods of a class to logcat
    private fun dumpMethods(clazz: Class<*>, className: String) {
        Log.d(TAG, "=== Methods for $className ===")
        for (method in clazz.methods) {
            val modifiers = java.lang.reflect.Modifier.toString(method.modifiers)
            val returnType = method.returnType.simpleName
            val paramTypes = method.parameterTypes.joinToString(", ") { it.simpleName }
            Log.d(TAG, "$modifiers $returnType ${method.name}($paramTypes)")
        }
        Log.d(TAG, "=== End of methods for $className ===")
    }
    
    // Helper to save config to a file
    private fun saveConfigToFile(content: String, filename: String) {
        try {
            val file = File(filesDir, filename)
            FileOutputStream(file).use { 
                it.write(content.toByteArray())
            }
            Log.d(TAG, "Saved $filename to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving $filename: ${e.message}")
        }
    }
}