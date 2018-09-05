package com.sss.blecommunicator

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import java.util.UUID

import android.bluetooth.BluetoothProfile.GATT_SERVER
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED


class BluetoothLEService : Service() {
    internal var mBinder: IBinder = LocalBinder()
    private var mConnectionState = STATE_DISCONNECT
    private var mBluetoothAdapter: BluetoothAdapter? = null

    private var mBluetoothGatt: BluetoothGatt? = null
    private var bluetoothAddress: String? = null
    private var isDiscovered = false

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Util.showSaveLog(TAG, "onConnectionStateChange $newState")

            val intentAction: String
            if (newState == BluetoothProfile.STATE_CONNECTING) {
                Util.showSaveLog(TAG, "STATE_CONNECTING to GATT server")
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED
                mConnectionState = STATE_CONNECTED
                broadcastUpdate(intentAction)
                Util.showSaveLog(TAG, "Connected to GATT server.")

                Util.showSaveLog(TAG, "Attempting to start service discovery:" + mBluetoothGatt!!.discoverServices())


            } else if (newState == STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED
                mConnectionState = STATE_DISCONNECTED
                Util.showSaveLog(TAG, "Disconnected from GATT server.")
                broadcastUpdate(intentAction)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Util.showSaveLog(TAG, "onServicesDiscovered $status")
            isDiscovered = true
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
            } else {
                Util.showSaveLog(TAG, "onServicesDiscovered received: $status")
            }

        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            Util.showSaveLog(TAG, "onCharacteristicRead $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Util.showSaveLog(TAG, "onCharacteristicWrite $status")
            broadcastUpdate(ACTION_DATA_WRITE_ACK, characteristic, status)

        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            Util.showSaveLog(TAG, "onCharacteristicChanged")
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
        }

        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            super.onDescriptorRead(gatt, descriptor, status)
            Util.showSaveLog(TAG, "onDescriptorRead $status")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            Util.showSaveLog(TAG, "onDescriptorWrite $status")

        }

        override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
            super.onReliableWriteCompleted(gatt, status)
            Util.showSaveLog(TAG, "onReliableWriteCompleted $status")

        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            super.onReadRemoteRssi(gatt, rssi, status)
            Util.showSaveLog(TAG, "onReadRemoteRssi $status")

        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Util.showSaveLog(TAG, "onMtuChanged $status")
        }
    }

    val supportedGattServices: List<BluetoothGattService>?
        get() = if (mBluetoothGatt == null) null else mBluetoothGatt!!.services

    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic, status: Int) {
        val intent = Intent(action)
        val data = characteristic.value
        Util.showSaveLog("------characteristic", "characteristic write " + String(data))
        intent.putExtra(CHARACTERISTIC_ID, characteristic.uuid.toString())
        intent.putExtra(EXTRA_DATA, String(data))
        intent.putExtra(EXTRA_STATUS, status)
        sendBroadcast(intent)
    }

    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic) {
        val intent = Intent(action)
        Util.showSaveLog("------characteristic", "" + characteristic.value)

        intent.putExtra(CHARACTERISTIC_ID, characteristic.uuid.toString())
        val data = characteristic.value
        if (data == null || data.size <= 0) {
            intent.putExtra(EXTRA_DATA, ByteArray(0))
        } else {
            intent.putExtra(EXTRA_DATA, String(data).trim { it <= ' ' })
        }
        Util.showSaveLog("broadcastUpdate", "EXTRA_DATA trim: " + String(data!!).trim { it <= ' ' })
        /*if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for(byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            intent.putExtra(EXTRA_DATA, new String(data));
            Util.showSaveLog("broadcastUpdate","EXTRA_DATA : "+new String(data));
            //Util.showSaveLog("------characteristic","stringBuilder.toString() : "+stringBuilder.toString());
        }*/
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }

    override fun onUnbind(intent: Intent): Boolean {
        close()
        Util.showSaveLog(TAG, "mBluetoothGatt onUnbind")
        return super.onUnbind(intent)
    }

    fun close() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Util.showSaveLog(TAG, "mBluetoothGatt null")
            return
        }
        Util.showSaveLog(TAG, "Bluetooth adapter close")
        mBluetoothGatt!!.close()
        mBluetoothGatt = null
    }

    fun disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Util.showSaveLog(TAG, "mBluetoothGatt null")
            return
        }
        Util.showSaveLog(TAG, "Bluetooth adapter disconnect")
        mBluetoothGatt!!.disconnect()
    }

    fun getBluetoothAdapter(): BluetoothAdapter? {
        if (mBluetoothAdapter == null) {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            mBluetoothAdapter = bluetoothManager.adapter
        }
        return mBluetoothAdapter
    }

    fun initialize(): Boolean {
        mBluetoothAdapter = getBluetoothAdapter()
        return true
    }

    fun connect(address: String, isReconnect: Boolean): Boolean {
        //Try to use existing connection
        val bluetoothDevice = mBluetoothAdapter!!.getRemoteDevice(address)
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        if (mBluetoothAdapter != null && address == bluetoothAddress && mBluetoothGatt != null) {

            if (bluetoothManager.getConnectionState(bluetoothDevice, GATT_SERVER) == BluetoothGatt.STATE_CONNECTED) {
                Util.showSaveLog(TAG, "Already Connected")
                var intentAction: String
                intentAction = ACTION_GATT_CONNECTED
                broadcastUpdate(intentAction)
                if (isDiscovered && !isReconnect)
                    intentAction = ACTION_GATT_SERVICES_DISCOVERED
                broadcastUpdate(intentAction)
                if (!isReconnect)
                    mBluetoothGatt!!.discoverServices()
                return true
            }
            if (mBluetoothGatt!!.connect()) {
                mConnectionState = STATE_CONNECTING
                return true
            } else {
                return false
            }
        }
        if (bluetoothDevice == null) {
            Util.showSaveLog(TAG, "Device not found")
            return false
        }

        //mBluetoothGatt = bluetoothDevice.connectGatt(this, false, bluetoothGattCallback);
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            mBluetoothGatt = bluetoothDevice.connectGatt(this, false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE)
        else
            mBluetoothGatt = bluetoothDevice.connectGatt(applicationContext, false, bluetoothGattCallback)
        //refreshDeviceCache(mBluetoothGatt);
        bluetoothAddress = address
        mConnectionState = STATE_CONNECTING
        return true
    }


    fun readCharacteristic(seviceUUID: String,charUUID: String) {
        try {

            var serviceUuid = UUID.fromString(seviceUUID)

            Util.showSaveLog("broadcastUpdate", "ServiceUuid : " + serviceUuid.toString())

            val characteristicUuid = UUID.fromString(charUUID)
            Util.showSaveLog("broadcastUpdate", "characteristicUuid : " + characteristicUuid.toString())
            if (mBluetoothGatt != null) {
                if (verifyServiceAvailable(serviceUuid)) {
                    val bluetoothGattService = mBluetoothGatt!!.getService(serviceUuid)
                    if (verifyCharacteristicAvailable(serviceUuid, characteristicUuid)) {
                        val gattCharacteristic = bluetoothGattService.getCharacteristic(characteristicUuid)
                        if (gattCharacteristic != null)
                            mBluetoothGatt!!.readCharacteristic(gattCharacteristic)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun verifyServiceAvailable(serviceUuid: UUID): Boolean {
        if (supportedGattServices == null) return false
        //Util.showSaveLog("verifyServiceAvailable", "verifyServiceAvailable : " + serviceUuid.toString());
        // Loops through available GATT Services.
        for (gattService in supportedGattServices!!) {
            val serviceUUID = gattService.uuid.toString()
            if (serviceUUID.equals(serviceUuid.toString(), ignoreCase = true))
                return true
        }

        return false

    }

    private fun verifyCharacteristicAvailable(serviceUuid: UUID, characteristicUuid: UUID): Boolean {

        for (gattCharacteristic in mBluetoothGatt!!.getService(serviceUuid).characteristics) {
            val charUuid = gattCharacteristic.uuid.toString()
            if (charUuid.equals(characteristicUuid.toString(), ignoreCase = true))
                return true
        }
        return false

    }

    fun setCharacteristicNotification(serviceUuid: String,charUUID: String, enabled: Boolean) {
        if (mBluetoothGatt != null) {
            var ServiceUuid = UUID.fromString(serviceUuid)
            Util.showSaveLog("broadcastUpdate", "ServiceUuid : " + ServiceUuid.toString())

            val characteristicUuid = UUID.fromString(charUUID)
            val gattCharacteristic = mBluetoothGatt!!.getService(ServiceUuid).getCharacteristic(characteristicUuid)
            mBluetoothGatt!!.setCharacteristicNotification(gattCharacteristic, enabled)
        }
    }

    private fun refreshDeviceCache(localBluetoothGatt: BluetoothGatt): Boolean {
        try {
            val localMethod = localBluetoothGatt.javaClass.getMethod("refresh", *arrayOfNulls(0))
            if (localMethod != null) {
                val bool = (localMethod.invoke(localBluetoothGatt, *arrayOfNulls(0)) as Boolean)
                Util.showSaveLog(TAG, "while refreshing device : $bool")
                return bool
            }
        } catch (localException: Exception) {
            Util.showSaveLog(TAG, "An exception occured while refreshing device")
        }

        return false
    }


    fun writeCharacteristic(serviceUUID: String,charUUID: String, value: String): Boolean {
        if (mBluetoothGatt != null) {

            Util.showSaveLog("broadcastUpdate", "ServiceUuid : " + charUUID)

            val serviceUuid = UUID.fromString(serviceUUID)
            val characteristicUuid = UUID.fromString(charUUID)
            if (mBluetoothGatt!!.getService(serviceUuid) != null) {
                val gattCharacteristic = mBluetoothGatt!!.getService(serviceUuid).getCharacteristic(characteristicUuid)
                gattCharacteristic.setValue(value)
                return mBluetoothGatt!!.writeCharacteristic(gattCharacteristic)
            }
            return false
        }
        return false
    }

    inner class LocalBinder : Binder() {
        val service: BluetoothLEService
            get() = this@BluetoothLEService
    }

    companion object {

        val ACTION_GATT_CONNECTED = "com.sss.blecommunicator.ACTION_GATT_CONNECTED"
        val ACTION_GATT_DISCONNECTED = "com.sss.blecommunicator.ACTION_GATT_DISCONNECTED"
        val ACTION_GATT_SERVICES_DISCOVERED = "com.sss.blecommunicator.ACTION_GATT_SERVICES_DISCOVERED"
        val ACTION_DATA_AVAILABLE = "com.sss.blecommunicator.ACTION_DATA_AVAILABLE"
        val ACTION_DATA_WRITE_ACK = "com.sss.blecommunicator.ACTION_DATA_WRITE_ACK"
        val CHARACTERISTIC_ID = "com.sss.blecommunicator.CHARACTERISTIC_ID"
        val EXTRA_DATA = "com.sss.blecommunicator.EXTRA_DATA"
        val EXTRA_STATUS = "com.sss.blecommunicator.EXTRA_DATA"

        private val TAG = "BluetoothLEService"
        private val STATE_DISCONNECT = 0
        private val STATE_CONNECTING = 1
        private val STATE_CONNECTED = 2
    }
}
