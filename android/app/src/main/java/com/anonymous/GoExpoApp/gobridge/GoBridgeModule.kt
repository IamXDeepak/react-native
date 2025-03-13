package com.anonymous.GoExpoApp

import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import java.lang.Integer
import java.lang.Long
import java.lang.Double
import java.lang.Float

class GoBridgeModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    
    companion object {
        private const val TAG = "GoBridgeModule"
    }
    
    init {
        try {
            Log.d(TAG, "Initializing GoBridgeModule")
            // Try to load the class and log its methods
            try {
                val mygoClass = Class.forName("mygomodule.Mygomodule")
                Log.d(TAG, "Found Mygomodule class")
                val methods = mygoClass.methods
                
                // More detailed logging of methods
                methods.forEach { method ->
                    val paramTypes = method.parameterTypes.joinToString(", ") { it.name }
                    val returnType = method.returnType.name
                    Log.d(TAG, "Method: ${method.name}($paramTypes) -> $returnType")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading class details: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during initialization", e)
        }
    }
    
    // Find the Go class directly using the path we found in the extracted AAR
    private val goClass by lazy {
        try {
            val className = "mygomodule.Mygomodule"
            Log.d(TAG, "Trying to load class: $className")
            val foundClass = Class.forName(className)
            
            if (foundClass != null) {
                Log.d(TAG, "Successfully found Go class: ${foundClass.name}")
                // Log all available methods
                val methods = foundClass.methods
                Log.d(TAG, "Class methods: ${methods.joinToString { it.name }}")
            } else {
                Log.e(TAG, "Could not find Go class")
            }
            
            foundClass
        } catch (e: Exception) {
            Log.e(TAG, "Error finding Go class", e)
            null
        }
    }
    
    override fun getName(): String {
        return "GoBridge"
    }

    @ReactMethod
fun add(a: Int, b: Int, promise: Promise) {
    try {
        Log.d(TAG, "Attempting to call add($a, $b)")
        val cls = goClass
        if (cls == null) {
            Log.e(TAG, "Go module class not found")
            promise.reject("GO_ERROR", "Go module class not found")
            return
        }
        
        try {
            // Use the correct case and parameter types found in the logs
            val method = cls.getMethod("add", java.lang.Long.TYPE, java.lang.Long.TYPE)
            Log.d(TAG, "Found method: ${method.name} with params: ${method.parameterTypes.joinToString { it.name }}")
            
            // Convert integer parameters to long as required by the method
            val longA = a.toLong()
            val longB = b.toLong()
            
            val result = method.invoke(null, longA, longB)
            Log.d(TAG, "add result: $result")
            
            // Convert result to int if needed
            val intResult = when (result) {
                is Long -> result.toInt()
                else -> result.toString().toInt()
            }
            
            promise.resolve(intResult)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Go add function", e)
            promise.reject("GO_ERROR", "Error calling Go add function: ${e.message}", e)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Unexpected error in add method", e)
        promise.reject("GO_ERROR", "Unexpected error: ${e.message}", e)
    }
}

    @ReactMethod
    fun helloWorld(promise: Promise) {
        try {
            Log.d(TAG, "Attempting to call GoExpoApp()")
            val cls = goClass
            if (cls == null) {
                Log.e(TAG, "Go module class not found")
                promise.reject("GO_ERROR", "Go module class not found")
                return
            }
            
            try {
                // Log all methods to find the correct GoExpoApp method
                val methods = cls.methods
                Log.d(TAG, "Looking for GoExpoApp method among: ${methods.joinToString { it.name }}")
                
                // Try to find the GoExpoApp method dynamically with different case variations
                val helloWorldMethod = methods.find { it.name == "GoExpoApp" || it.name == "helloWorld" }
                
                if (helloWorldMethod != null) {
                    Log.d(TAG, "Found GoExpoApp method: ${helloWorldMethod.name}")
                    val result = helloWorldMethod.invoke(null) as String
                    Log.d(TAG, "GoExpoApp result: $result")
                    promise.resolve(result)
                } else {
                    Log.e(TAG, "GoExpoApp method not found")
                    promise.reject("GO_ERROR", "GoExpoApp method not found in available methods")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calling Go GoExpoApp function", e)
                promise.reject("GO_ERROR", "Error calling Go GoExpoApp function: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in helloWorld method", e)
            promise.reject("GO_ERROR", "Unexpected error: ${e.message}", e)
        }
    }
    
    // Helper function to convert a value to the required type
    private fun convertToType(value: Int, targetType: Class<*>): Any? {
        return when {
            targetType == Int::class.java || targetType == java.lang.Integer.TYPE -> value
            targetType == Long::class.java || targetType == java.lang.Long.TYPE -> value.toLong()
            targetType == Double::class.java || targetType == java.lang.Double.TYPE -> value.toDouble()
            targetType == Float::class.java || targetType == java.lang.Float.TYPE -> value.toFloat()
            targetType == String::class.java -> value.toString()
            else -> null
        }
    }
}