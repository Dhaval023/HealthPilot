package com.example.myfitnessapp.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.myfitnessapp.models.BleDevice
import com.example.myfitnessapp.models.HealthData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private val HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val DEVICE_NAME = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
        private val RSC_MEASUREMENT = UUID.fromString("00002a53-0000-1000-8000-00805f9b34fb")
        private val CCC_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val FEE1_CHARACTERISTIC = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private var lastOpTime = 0L

    private val _foundDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val foundDevices = _foundDevices.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _healthData = MutableStateFlow(HealthData())
    val healthData = _healthData.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>("Unknown Device")
    val deviceName = _deviceName.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice = _connectedDevice.asStateFlow()

    private val _isWatch = MutableStateFlow(false)
    val isWatch = _isWatch.asStateFlow()

    private val sharedPrefs = context.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)

    private val commandQueue: Queue<Runnable> = LinkedList()
    private var isOperationPending = false

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (_connectionState.value == ConnectionState.CONNECTED) {
                refreshHealthData()
                handler.postDelayed(this, 15000)
            }
        }
    }

    private val rssiRunnable = object : Runnable {
        override fun run() {
            if (_connectionState.value == ConnectionState.CONNECTED) {
                bluetoothGatt?.readRemoteRssi()
                handler.postDelayed(this, 3000) // Faster RSSI updates (3s)
            }
        }
    }

    private fun processNextCommand() {
        handler.post {
            if (isOperationPending && System.currentTimeMillis() - lastOpTime < 2000) return@post
            if (commandQueue.isEmpty()) {
                isOperationPending = false
                return@post
            }
            
            isOperationPending = true
            lastOpTime = System.currentTimeMillis()
            val cmd = commandQueue.poll()
            handler.postDelayed({ cmd?.run() }, 150) // Small delay between commands for stability
        }
    }

    fun startScan() {
        if (isScanning) return
        
        // Auto-connect if we have a saved device and it's not connected
        if (_connectionState.value == ConnectionState.DISCONNECTED) {
            autoConnect()
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            _errorMessage.value = "Bluetooth Scanner not available. Please ensure Bluetooth is ON."
            Log.e("BleManager", "Scan failed: BluetoothLeScanner is null")
            return
        }

        _foundDevices.value = emptyList()
        _errorMessage.value = null
        isScanning = true
        
        try {
            Log.d("BleManager", "Starting BLE Scan...")
            scanner.startScan(scanCallback)
        } catch (e: Exception) {
            Log.e("BleManager", "StartScan Error: ${e.message}")
            _errorMessage.value = "Scan error: ${e.message}"
            isScanning = false
        }
        
        handler.postDelayed({ stopScan() }, 15000)
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e("BleManager", "StopScan Error: ${e.message}")
        }
    }

    fun autoConnect() {
        val address = sharedPrefs.getString("last_device_address", null)
        if (address != null && _connectionState.value == ConnectionState.DISCONNECTED) {
            Log.d("BleManager", "Attempting auto-connect to $address")
            connect(address)
        }
    }

    fun connect(address: String) {
        stopScan() // Always stop scan before connecting
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        bluetoothGatt?.close()
        bluetoothGatt = null
        commandQueue.clear()
        isOperationPending = false
        _healthData.value = HealthData()
        _connectionState.value = ConnectionState.CONNECTING
        
        handler.postDelayed({
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        }, 200)
        
        handler.removeCallbacks(refreshRunnable)
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTING
        // Clear saved address so it doesn't auto-connect next time
        sharedPrefs.edit().remove("last_device_address").apply()
        bluetoothGatt?.disconnect()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun initiateDiscovery(gatt: BluetoothGatt) {
        // Request high priority for faster discovery
        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

        // Delay MTU request slightly for OnePlus/Oppo stability
        handler.postDelayed({
            if (!gatt.requestMtu(512)) {
                Log.w("BleManager", "MTU request failed to initiate")
                gatt.discoverServices()
            }
        }, 1000)
    }

    fun refreshHealthData() {
        val gatt = bluetoothGatt ?: return
        if (_connectionState.value != ConnectionState.CONNECTED) return
        
        // Read RSSI for signal strength
        gatt.readRemoteRssi()
        
        findCommandChar(gatt)?.let { char ->
            Log.d("BleManager", "Refreshing Health Data...")
            
            // Health Requests - Primary only
            enqueueWrite(gatt, char, byteArrayOf(0x04)) // Steps (Standard)
            enqueueWrite(gatt, char, byteArrayOf(0x09, 0x01)) // Live HR (Standard)
            
            // Protocol AB Specifics (Noise/Fire-Boltt/boAt)
            enqueueWrite(gatt, char, byteArrayOf(0xAB.toByte(), 0x00, 0x04, 0xFF.toByte(), 0x51.toByte(), 0x80.toByte())) // Fitness/Steps Request
            enqueueWrite(gatt, char, byteArrayOf(0xAB.toByte(), 0x00, 0x04, 0xFF.toByte(), 0x84.toByte(), 0x80.toByte())) // Health Request
            
            // Variant for some watches that use 0x01 or 0x00 for request
            enqueueWrite(gatt, char, byteArrayOf(0xAB.toByte(), 0x00, 0x04, 0xFF.toByte(), 0x51.toByte(), 0x01.toByte()))
        }
        processNextCommand()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            // Try to get name from device or scan record
            val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                result.scanRecord?.deviceName ?: device.name
            } else {
                @Suppress("DEPRECATION")
                device.name ?: result.scanRecord?.deviceName
            }
            
            // Filter: Only show devices with names and exclude generic "Unknown Device"
            if (name.isNullOrBlank() || name.contains("Unknown", ignoreCase = true)) return
            
            Log.d("BleScan", "Found: $name [${device.address}] RSSI: ${result.rssi}")

            val bleDevice = BleDevice(name, device.address, result.rssi)
            val current = _foundDevices.value.toMutableList()
            if (current.none { it.address == bleDevice.address }) {
                current.add(bleDevice)
                _foundDevices.value = current
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleScan", "Scan Failed: Error $errorCode")
            val msg = when(errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already in progress"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal hardware error"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE Scan unsupported"
                else -> "Scan failed: $errorCode"
            }
            _errorMessage.value = msg
            isScanning = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d("BleManager", "Connection State Change: status=$status, newState=$newState")
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val errorMsg = when (status) {
                    133 -> "Device busy or already connected to another phone"
                    8 -> "Connection timeout (Out of range)"
                    19 -> "Device disconnected link"
                    else -> "Connection failed: Status $status"
                }
                _errorMessage.value = errorMsg
                
                _connectionState.value = ConnectionState.DISCONNECTED
                gatt.close()
                if (gatt == bluetoothGatt) bluetoothGatt = null
                return
            }
            
            _errorMessage.value = null

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = ConnectionState.CONNECTED
                _connectedDevice.value = gatt.device
                _deviceName.value = gatt.device.name ?: "Connected Device"
                
                // Save last connected device for auto-reconnect
                sharedPrefs.edit().putString("last_device_address", gatt.device.address).apply()
                
                initiateDiscovery(gatt)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.DISCONNECTED
                _connectedDevice.value = null
                _isWatch.value = false
                handler.removeCallbacks(refreshRunnable)
                handler.removeCallbacks(rssiRunnable)
                gatt.close()
                if (gatt == bluetoothGatt) bluetoothGatt = null
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d("BleManager", "MTU changed to $mtu, status=$status")
            // Delay discovery after MTU change
            handler.postDelayed({ gatt.discoverServices() }, 1000)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d("BleManager", "Services Discovered: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.services.forEach { service ->
                    Log.d("BleManager", "Service: ${service.uuid}")
                    service.characteristics.forEach { char ->
                        Log.d("BleManager", "  Char: ${char.uuid}, Props: ${char.properties}")
                    }
                }
                
                val sUuids = gatt.services.map { it.uuid.toString().lowercase() }
                _isWatch.value = sUuids.any { !it.startsWith("000018") && !it.startsWith("000011") } || sUuids.any { it.contains("180d") }
                
                // 1. Setup Notifications
                setupAllNotifications(gatt)
                
                // 2. Protocol AB Binding & Initial Sync (Required for some watches)
                findCommandChar(gatt)?.let { char ->
                    Log.d("BleManager", "Sending Binding & Time Sync Commands...")
                    // Binding
                    enqueueWrite(gatt, char, byteArrayOf(0xAB.toByte(), 0x00, 0x03, 0xFF.toByte(), 0xB1.toByte(), 0x80.toByte()))
                    
                    // Time Sync
                    val cal = Calendar.getInstance()
                    val timeData = byteArrayOf(
                        0xAB.toByte(), 0x00, 0x0B, 0xFF.toByte(), 0x93.toByte(),
                        (cal.get(Calendar.YEAR) shr 8).toByte(), (cal.get(Calendar.YEAR) and 0xFF).toByte(),
                        (cal.get(Calendar.MONTH) + 1).toByte(), cal.get(Calendar.DAY_OF_MONTH).toByte(),
                        cal.get(Calendar.HOUR_OF_DAY).toByte(), cal.get(Calendar.MINUTE).toByte(), cal.get(Calendar.SECOND).toByte()
                    )
                    enqueueWrite(gatt, char, timeData)

                    // Enable Real-time data
                    enqueueWrite(gatt, char, byteArrayOf(0xAB.toByte(), 0x00, 0x04, 0xFF.toByte(), 0x32.toByte(), 0x01.toByte()))
                    enqueueWrite(gatt, char, byteArrayOf(0xAB.toByte(), 0x00, 0x04, 0xFF.toByte(), 0x33.toByte(), 0x01.toByte()))
                }

                // 3. Start refresh cycle
                handler.removeCallbacks(refreshRunnable)
                handler.postDelayed(refreshRunnable, 3000) // Increased delay to allow init to finish
                
                // Start RSSI tracking
                handler.removeCallbacks(rssiRunnable)
                handler.post(rssiRunnable)

                processNextCommand()
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            Log.d("BleManager", "onCharacteristicRead: ${char.uuid}, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) handleData(char.uuid, value)
            isOperationPending = false
            processNextCommand()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
            val values = value.map { it.toInt() and 0xFF }
            Log.d("BleManager", "onCharacteristicChanged: ${char.uuid}, Value: [${values.joinToString(",")}]")
            handleData(char.uuid, value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.d("BleManager", "onCharacteristicChanged (Legacy): ${characteristic.uuid}")
            @Suppress("DEPRECATION")
            handleData(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
            Log.d("BleManager", "onCharacteristicWrite: ${char.uuid}, status=$status")
            isOperationPending = false
            processNextCommand()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, desc: BluetoothGattDescriptor, status: Int) {
            Log.d("BleManager", "onDescriptorWrite: ${desc.uuid}, status=$status")
            isOperationPending = false
            processNextCommand()
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BleManager", "RSSI Update: $rssi")
                _healthData.value = _healthData.value.copy(rssi = rssi)
            }
        }
    }

    private fun setupAllNotifications(gatt: BluetoothGatt) {
        gatt.services.forEach { service ->
            val sUuid = service.uuid.toString().lowercase()
            if (!sUuid.startsWith("00001800") && !sUuid.startsWith("00001801")) {
                service.characteristics.forEach { char ->
                    if ((char.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) {
                        enqueueNotification(gatt, char)
                    }
                }
            }
            
            // Explicitly read battery on setup if available
            if (sUuid.contains("180f")) {
                service.getCharacteristic(BATTERY_LEVEL)?.let { bChar ->
                    commandQueue.add { gatt.readCharacteristic(bChar) }
                }
            }
        }
    }

    private fun handleData(uuid: UUID, data: ByteArray) {
        if (data.isEmpty()) return
        val values = data.map { it.toInt() and 0xFF }
        Log.d("BleManager", "Data from ${uuid}: ${values.joinToString(",")}")

        when (uuid) {
            DEVICE_NAME -> _deviceName.value = String(data).trim()
            HEART_RATE_MEASUREMENT -> {
                val hr = if ((data[0].toInt() and 0x01) == 0) values[1] else (values[2] shl 8) or values[1]
                Log.d("BleManager", "Standard Heart Rate: $hr")
                if (hr in 35..220) _healthData.value = _healthData.value.copy(heartRate = hr)
            }
            BATTERY_LEVEL -> {
                val level = values[0]
                Log.d("BleManager", "Battery Level: $level%")
                if (level in 0..100) {
                    _healthData.value = _healthData.value.copy(batteryLevel = level)
                }
            }
            FEE1_CHARACTERISTIC -> {
                // Parse FEE1 data format: [steps, steps, steps, distance, distance, distance, calories, calories, calories]
                if (values.size >= 9) {
                    val steps = values[0] or (values[1] shl 8) or (values[2] shl 16)
                    val distance = values[3] or (values[4] shl 8) or (values[5] shl 16)
                    val calories = values[6] or (values[7] shl 8) or (values[8] shl 16)
                    Log.d("BleManager", "FEE1 - Steps: $steps, Distance: $distance, Calories: $calories")
                    if (steps >= 0) {
                        _healthData.value = _healthData.value.copy(steps = steps)
                    }
                    if (distance > 0) {
                        _healthData.value = _healthData.value.copy(distanceKm = distance / 1000.0)
                    }
                    if (calories > 0) {
                        _healthData.value = _healthData.value.copy(calories = calories)
                    }
                    // Some budget watches put battery level in index 1 or 2 of FEE1
                    val battery = values.getOrNull(1) ?: 0
                    if (battery in 1..100 && battery != values[0]) {
                         _healthData.value = _healthData.value.copy(batteryLevel = battery)
                    }
                }
            }
            else -> parseVendorData(data)
        }
    }

    private fun parseVendorData(data: ByteArray) {
        val values = data.map { it.toInt() and 0xFF }
        if (values.isEmpty()) return
        val header = values[0]
        val size = data.size
        
        Log.d("BleManager", "Parsing Data: Header=0x${Integer.toHexString(header).uppercase()}, Size=$size, Data=[${values.joinToString(",")}]")

        when {
            // Protocol FE (New watch data format)
            header == 0xFE && values.getOrNull(1) == 0xEA && size >= 6 -> {
                val type = values[4]
                val value = values[5]
                Log.d("BleManager", "Protocol FE: Type=0x${Integer.toHexString(type).uppercase()}, Value=$value, FullData=[${values.joinToString(",")}]")
                when (type) {
                    0x6D -> if (value in 35..220) _healthData.value = _healthData.value.copy(heartRate = value)
                    0x6B -> if (value in 80..100) _healthData.value = _healthData.value.copy(spo2 = value)
                    0x6C -> if (value > 0) _healthData.value = _healthData.value.copy(sleepDuration = "${value / 60}h ${value % 60}m")
                    0x6E -> if (value in 1..100) _healthData.value = _healthData.value.copy(batteryLevel = value)
                    // Add more type IDs here once identified from logs
                    else -> Log.d("BleManager", "Protocol FE: Unhandled type 0x${Integer.toHexString(type).uppercase()}")
                }
            }
            // Protocol A (0x41 / 'A') - Common in many generic watches
            header == 0x41 && size >= 5 -> {
                val cmdId = values[1]
                when (cmdId) {
                    0x04 -> { // Steps/Activity
                        val steps = if (size >= 5) (values[3]) or (values[4] shl 8) or (if (size >= 6) values[5] shl 16 else 0) else 0
                        val dist = if (size >= 8) (values[6]) or (values[7] shl 8) or (if (size >= 9) values[8] shl 16 else 0) else 0
                        val cal = if (size >= 11) (values[9]) or (values[10] shl 8) else 0
                        
                        _healthData.value = _healthData.value.copy(
                            steps = steps,
                            distanceKm = dist / 1000.0,
                            calories = if (cal > 0) cal else _healthData.value.calories
                        )
                    }
                    0x05 -> { // Sleep
                        if (size >= 5) {
                            val mins = (values[3]) or (values[4] shl 8)
                            if (mins in 1..1440) {
                                _healthData.value = _healthData.value.copy(sleepDuration = "${mins / 60}h ${mins % 60}m")
                            }
                        }
                    }
                    0x09 -> { // Heart Rate
                        val hr = values.getOrNull(3) ?: 0
                        if (hr in 35..220) _healthData.value = _healthData.value.copy(heartRate = hr)
                    }
                }
            }
            // Protocol AB (Noise/Fire-Boltt/boAt/FitCloudPro)
            header == 0xAB && size >= 5 -> {
                val cmdId = values[4]
                when (cmdId) {
                    0x51 -> if (size >= 8) { // Relaxed size check
                        val steps = (values[5] shl 16) or (values[6] shl 8) or values[7]
                        val dist = if (size >= 11) (values[8] shl 16) or (values[9] shl 8) or values[10] else 0
                        val cal = if (size >= 14) (values[11] shl 16) or (values[12] shl 8) or values[13] else 0
                        
                        Log.d("BleManager", "Protocol AB Activity Update: Steps=$steps")
                        _healthData.value = _healthData.value.copy(
                            steps = if (steps >= 0) steps else _healthData.value.steps,
                            distanceKm = if (dist > 0) dist / 1000.0 else _healthData.value.distanceKm,
                            calories = if (cal > 0) cal else _healthData.value.calories
                        )
                    }
                    0x32 -> if (size >= 8) { // Real-time steps update
                        val steps = (values[5] shl 16) or (values[6] shl 8) or values[7]
                        Log.d("BleManager", "Protocol AB Real-time Steps: $steps")
                        if (steps >= 0) _healthData.value = _healthData.value.copy(steps = steps)
                    }
                    0x33 -> if (size >= 8) { // Real-time health update
                        val hr = values.getOrNull(5) ?: 0
                        if (hr in 35..220) _healthData.value = _healthData.value.copy(heartRate = hr)
                    }
                    0x52 -> { // Sleep Data (AB Protocol)
                        if (size >= 9) {
                            val deep = (values[5] shl 8) or values[6]
                            val light = (values[7] shl 8) or values[8]
                            val total = deep + light
                            if (total in 1..1440) {
                                _healthData.value = _healthData.value.copy(sleepDuration = "${total / 60}h ${total % 60}m")
                            }
                        }
                    }
                    0x84 -> { // Health Data (HR, BP, SpO2)
                        val hr = values.getOrNull(5) ?: 0
                        val spo2 = values.getOrNull(8) ?: 0
                        val battery = values.getOrNull(11) ?: 0
                        if (hr in 35..220) _healthData.value = _healthData.value.copy(heartRate = hr)
                        if (spo2 in 80..100) _healthData.value = _healthData.value.copy(spo2 = spo2)
                        if (battery in 1..100) _healthData.value = _healthData.value.copy(batteryLevel = battery)
                    }
                    0x91 -> { // Heart Rate Measurement
                        val hr = if (values.getOrNull(2) == 5) values.getOrNull(6) else values.getOrNull(5)
                        if (hr != null && hr in 35..220) {
                            _healthData.value = _healthData.value.copy(heartRate = hr)
                        }
                    }
                }
            }
            header == 0x08 && size >= 2 -> {
                val hr = values[1]
                if (hr in 35..220) _healthData.value = _healthData.value.copy(heartRate = hr)
            }
            header == 0x07 && size >= 7 -> {
                val offset = if (size >= 10) 1 else 0
                val steps = (values[offset]) or (values[offset+1] shl 8) or (values[offset+2] shl 16)
                val dist = (values[offset+3]) or (values[offset+4] shl 8) or (values[offset+5] shl 16)
                val cal = if (size >= offset + 9) (values[offset+6]) or (values[offset+7] shl 8) or (values[offset+8] shl 16) else 0

                _healthData.value = _healthData.value.copy(
                    steps = steps,
                    distanceKm = dist / 1000.0,
                    calories = cal
                )
            }
            header == 0x05 && size >= 5 -> {
                val mins = if (size >= 9) (values[7]) or (values[8] shl 8) else (values[1]) or (values[2] shl 8)
                if (mins in 1..1440) {
                    _healthData.value = _healthData.value.copy(sleepDuration = "${mins / 60}h ${mins % 60}m")
                }
            }
        }
    }

    private fun findCommandChar(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        val candidates = listOf("6f01", "fee2", "fee3", "fee5", "ffd1", "ffd5", "0001", "ae01", "6e400002", "8001", "ff01", "49535343-fe7d-4ae5-8fa9-9fafd205e455")
        // Try precise candidates first
        candidates.forEach { c ->
            gatt.services.forEach { service ->
                service.characteristics.forEach { char ->
                    if (char.uuid.toString().lowercase().contains(c) && 
                        (char.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                        Log.d("BleManager", "Found Command Char (Match): ${char.uuid}")
                        return char
                    }
                }
            }
        }
        
        // Generic fallback for any writeable characteristic in a non-standard service
        val fallback = gatt.services.filter { !it.uuid.toString().startsWith("000018") }
            .flatMap { it.characteristics }
            .firstOrNull { (it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0 }
            
        Log.d("BleManager", "Command Char Fallback: ${fallback?.uuid}")
        return fallback
    }

    private fun enqueueWrite(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, data: ByteArray) {
        commandQueue.add {
            val writeType = if ((char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) 
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            
            Log.d("BleManager", "Writing to ${char.uuid}: ${data.joinToString(",")}")
            
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(char, data, writeType)
            } else {
                @Suppress("DEPRECATION")
                char.value = data
                @Suppress("DEPRECATION")
                char.writeType = writeType
                @Suppress("DEPRECATION")
                if (gatt.writeCharacteristic(char)) 0 else 1 // 0 = SUCCESS, 1 = ERROR
            }

            if (result != 0) {
                Log.e("BleManager", "Write failed immediately for ${char.uuid} with status $result")
                isOperationPending = false
                handler.postDelayed({ processNextCommand() }, 100)
            }
        }
        processNextCommand()
    }

    private fun enqueueNotification(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        commandQueue.add {
            Log.d("BleManager", "Enabling Notification/Indication for ${char.uuid}")
            gatt.setCharacteristicNotification(char, true)
            val descriptor = char.getDescriptor(CCC_DESCRIPTOR)
            if (descriptor != null) {
                // Determine if we should enable Notification or Indication
                val value = if ((char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                }
                
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, value)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = value
                    @Suppress("DEPRECATION")
                    if (gatt.writeDescriptor(descriptor)) 0 else 1 // 0 = SUCCESS
                }

                if (result != 0) {
                    Log.e("BleManager", "Descriptor write failed for ${char.uuid} with status $result")
                    isOperationPending = false
                    handler.postDelayed({ processNextCommand() }, 100)
                }
            } else { 
                Log.e("BleManager", "CCC Descriptor not found for ${char.uuid}")
                isOperationPending = false
                handler.postDelayed({ processNextCommand() }, 100)
            }
        }
        processNextCommand()
    }
}
