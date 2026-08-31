package com.example.myfitnessapp.data.repository

import com.example.myfitnessapp.ble.BleManager
import com.example.myfitnessapp.models.BleDevice
import com.example.myfitnessapp.models.HealthData
import kotlinx.coroutines.flow.StateFlow

class HealthRepository(private val bleManager: BleManager) {

    val foundDevices: StateFlow<List<BleDevice>> = bleManager.foundDevices
    val connectionState: StateFlow<BleManager.ConnectionState> = bleManager.connectionState
    val healthData: StateFlow<HealthData> = bleManager.healthData
    val connectedDeviceName: StateFlow<String?> = bleManager.deviceName
    val isWatch: StateFlow<Boolean> = bleManager.isWatch
    
    val connectedDeviceAddress: String?
        get() = bleManager.connectedDevice.value?.address

    fun startScan() = bleManager.startScan()
    fun stopScan() = bleManager.stopScan()
    fun autoConnect() = bleManager.autoConnect()
    fun connect(address: String) = bleManager.connect(address)
    fun disconnect() = bleManager.disconnect()
    fun refreshData() {
        bleManager.refreshHealthData()
    }
}
