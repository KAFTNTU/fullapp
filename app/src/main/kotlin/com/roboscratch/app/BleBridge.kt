package com.roboscratch.app

import android.webkit.JavascriptInterface
import com.roboscratch.app.ble.BleManager

class BleBridge(
    private val bleManager: BleManager,
    private val activity: MainActivity
) {

    @JavascriptInterface
    fun connect() {
        activity.runOnUiThread {
            activity.requestBlePermissionsAndScan()
        }
    }

    @JavascriptInterface
    fun disconnect() {
        bleManager.disconnect()
    }

    @JavascriptInterface
    fun sendBytes(hexStr: String) {
        val bytes = hexStr.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        bleManager.sendBytes(bytes)
    }

    @JavascriptInterface
    fun isConnected(): Boolean = bleManager.isConnected()
}
