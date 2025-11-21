package com.example.gps_android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.annotation.NonNull
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.tasks.Task
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener

class GpsAndroidPlugin : FlutterPlugin, MethodCallHandler, ActivityAware, ActivityResultListener {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var _result: Result? = null
    private var activity: Activity? = null
    private val REQUEST_CHECK_SETTINGS = 1001

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "gps_android")
        context = flutterPluginBinding.applicationContext
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "enableGPS" -> enableGPS(result)
            else -> result.notImplemented()
        }
    }

    private fun enableGPS(result: Result) {
        if (activity == null) {
            result.error("ERROR", "Activity is null", null)
            return
        }
        // Защита от двойного вызова
        if (_result != null) {
            result.error("ERROR", "Another request is already running", null)
            return
        }
        _result = result
        val locationRequest = LocationRequest.Builder(LocationRequest.PRIORITY_HIGH_ACCURACY, 1000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(context)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            _result?.success(true)
            _result = null
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    activity?.let {
                        exception.startResolutionForResult(it, REQUEST_CHECK_SETTINGS)
                    } ?: run {
                        _result?.error("ERROR", "Activity is null", null)
                    }
                } catch (sendEx: IntentSender.SendIntentException) {
                    _result?.error("ERROR", "Cannot start resolution for GPS", null)
                }
            } else {
                _result?.error("ERROR", "GPS unavailable", null)
            }
            _result = null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            val isGpsEnabled = (resultCode == Activity.RESULT_OK)
            _result?.success(isGpsEnabled)
            _result = null
            return true
        }
        return false
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }
}
