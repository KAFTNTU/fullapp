package com.roboscratch.app.ble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.webkit.WebView
import java.util.UUID

class BleManager(private val context: Context, private val webView: WebView) {

    companion object {
        // HM-10 / HM-16 style modules
        val UUID_SERVICE_FFE0: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val UUID_CHAR_FFE1: UUID  = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

        // Nordic UART Service
        val UUID_NUS_SERVICE: UUID  = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val UUID_NUS_TX: UUID       = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val UUID_NUS_RX: UUID       = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

        val UUID_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val SCAN_PERIOD = 10_000L
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter get() = bluetoothManager.adapter
    private val scanner get() = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())

    private var _isConnected = false
    fun isConnected() = _isConnected

    // ─── Scanning ────────────────────────────────────────────────────────────

    fun startScan() {
        if (scanning) return
        scanning = true

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID_SERVICE_FFE0)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID_NUS_SERVICE)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        handler.postDelayed({ stopScan() }, SCAN_PERIOD)

        try {
            scanner?.startScan(filters, settings, scanCallback)
        } catch (e: SecurityException) {
            scanning = false
        }
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {}
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            stopScan()
            connectDevice(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
        }
    }

    // ─── Connection ──────────────────────────────────────────────────────────

    private fun connectDevice(device: BluetoothDevice) {
        try {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (_: SecurityException) {}
    }

    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: SecurityException) {}
        bluetoothGatt = null
        writeCharacteristic = null
        _isConnected = false
        notifyJs("window.onBleDisconnected && window.onBleDisconnected()")
    }

    // ─── GATT Callback ───────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    try { gatt.discoverServices() } catch (_: SecurityException) {}
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _isConnected = false
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    writeCharacteristic = null
                    notifyJs("window.onBleDisconnected && window.onBleDisconnected()")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            // Try FFE0/FFE1 first, then NUS
            var service = gatt.getService(UUID_SERVICE_FFE0)
            var writeChar = service?.getCharacteristic(UUID_CHAR_FFE1)

            if (writeChar == null) {
                service = gatt.getService(UUID_NUS_SERVICE)
                writeChar = service?.getCharacteristic(UUID_NUS_TX)
            }

            writeCharacteristic = writeChar

            // Enable notifications on FFE1 or NUS RX
            val notifyChar = gatt.getService(UUID_SERVICE_FFE0)?.getCharacteristic(UUID_CHAR_FFE1)
                ?: gatt.getService(UUID_NUS_SERVICE)?.getCharacteristic(UUID_NUS_RX)

            if (notifyChar != null) {
                try {
                    gatt.setCharacteristicNotification(notifyChar, true)
                    val desc = notifyChar.getDescriptor(UUID_CCCD)
                    desc?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    desc?.let { gatt.writeDescriptor(it) }
                } catch (_: SecurityException) {}
            }

            _isConnected = true
            notifyJs("window.onBleConnected && window.onBleConnected()")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val bytes = characteristic.value ?: return
            val hex = bytes.joinToString("") { "%02x".format(it) }
            notifyJs("window.onBleData && window.onBleData('$hex')")
        }

        // Android 13+ override
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val hex = value.joinToString("") { "%02x".format(it) }
            notifyJs("window.onBleData && window.onBleData('$hex')")
        }
    }

    // ─── Send ─────────────────────────────────────────────────────────────────

    fun sendBytes(bytes: ByteArray) {
        val gatt = bluetoothGatt ?: return
        val char = writeCharacteristic ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                @Suppress("DEPRECATION")
                char.value = bytes
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        } catch (_: SecurityException) {}
    }

    // ─── JS Bridge ───────────────────────────────────────────────────────────

    private fun notifyJs(script: String) {
        handler.post {
            webView.evaluateJavascript(script, null)
        }
    }
}
