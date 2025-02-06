package com.example.mybleapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class BLEScanner(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter?
    private val bleScanner: BluetoothLeScanner?
    private val scanResults = mutableMapOf<String, DeviceModel>()
    private val _deviceList = MutableLiveData<List<DeviceModel>>()
    val deviceList: LiveData<List<DeviceModel>> get() = _deviceList

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
    }

    private fun hasScanPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    }

    fun startScan() {
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e("BLE_SCAN", "Bluetooth is disabled")
            return
        }

        if (!hasScanPermission()) {
            Log.e("BLE_SCAN", "Missing BLUETOOTH_SCAN permission")
            return
        }

        scanResults.clear()

        val scanFilters = listOf(ScanFilter.Builder().build())
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner?.startScan(scanFilters, scanSettings, scanCallback)
            Log.d("BLE_SCAN", "Scanning started")
        } catch (e: SecurityException) {
            Log.e("BLE_SCAN", "SecurityException: Missing permissions for BLE scan", e)
        }
    }

    fun stopScan() {
        if (!hasScanPermission()) {
            Log.e("BLE_SCAN", "Missing BLUETOOTH_SCAN permission")
            return
        }
        try {
            bleScanner?.stopScan(scanCallback)
            Log.d("BLE_SCAN", "Scanning stopped")
        } catch (e: SecurityException) {
            Log.e("BLE_SCAN", "SecurityException: Missing permissions for BLE scan stop", e)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val device = it.device
                val rssi = it.rssi
                val address = device.address
                val name = device.name ?: "Unknown"

                val newDevice = DeviceModel(name, address, rssi)
                if (!scanResults.containsKey(address)) {
                    scanResults[address] = newDevice
                    _deviceList.postValue(scanResults.values.toList())
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { result -> onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE_SCAN", "Scan failed with error: $errorCode")
        }
    }
}