package com.sss.blecommunicator

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.ParcelUuid
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import com.sss.blecommunicator.Util.Companion.mBluetoothAdapter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

internal var mBluetoothDevices = ArrayList<BluetoothDevice>()

class DevicesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_devices)
    }



    fun searchMAX(isEnabled: Boolean) {
        doSeachOperation(isEnabled)
    }

    private fun doSeachOperation(isEnabled: Boolean) {
        mBluetoothDevices.clear()
            if (Util.isBluetoothEnabled()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // From LOLLIPOP and newer versions
                    if (ContextCompat.checkSelfPermission(this.applicationContext,
                                    android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        startScanningAPI21(isEnabled)
                    } else {
                        ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                                MY_PERMISSIONS_REQUEST_BLE_LOCATION)
                    }
                } else {
                    startScanningAPI18(isEnabled)
                }
            } else {
                //Util.funcEnableBLE(this@OnBoardingMain, true)
            }
        }
    }

     fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        // User chose not to enable Bluetooth.
        when (requestCode) {
            REQUEST_ENABLE_BT -> if (resultCode == Activity.RESULT_CANCELED) {
                finish()
                return
            }
            RegisteringNetworkFragment.REQUEST_LOCATION -> when (resultCode) {
                Activity.RESULT_OK ->
                    // All required changes were successfully made
                    if (getCurrentFragment().getTag()!!.equals(RegisteringNetworkFragment::class.java!!.getSimpleName(), ignoreCase = true)) {
                        val fragment = currentFragment as RegisteringNetworkFragment
                        fragment.locationEnabled(true)
                    }
                Activity.RESULT_CANCELED ->
                    // The user was asked to change settings, but chose not to
                    onBackPress()
                else -> {
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }


    fun onRequestPermissionsResult(requestCode: Int,
                                   permissions: Array<String>, grantResults: IntArray) {
        val MY_PERMISSIONS_REQUEST_BLE_LOCATION = 1002
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_BLE_LOCATION ->
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    searchMAX(true)
                    Util.showSaveLog("BLE", "Scan started")

                } else {
                    searchMAX(true)
                    Util.showSaveLog("BLE", "Pie Denied")
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
            RegisteringNetworkFragment.REQUEST_CODE_FOR_WIFI_PERMISSION -> {
                for (grantResult in grantResults) {
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        onBackPress()
                        return
                    }
                }
                if (RegisteringNetworkFragment::class.java!!.getSimpleName().equals(getCurrentFragment().getTag()!!, ignoreCase = true)) {
                    val fragment = currentFragment as RegisteringNetworkFragment
                    fragment.permissionGranted(true)
                }
            }
        }// other 'case' lines to check for other
        // permissions this app might request.
    }


    private fun displayGattServices(gattServices: List<BluetoothGattService>?) {
        if (gattServices == null) return

        // Loops through available GATT Services.
        for (gattService in gattServices) {
            val uuid = gattService.uuid.toString()
            Util.showSaveLog("SERVICES DISCOVERD  : ", "Service disovered: $uuid\n")
            ArrayList<HashMap<String, String>>()
            val gattCharacteristics = gattService.characteristics
            if (uuid.equals(BLEGattAttributes.SERVICE_OBA_UUID, ignoreCase = true)) {// Loops through available Characteristics.
                ourGattService = gattService
                for (gattCharacteristic in gattCharacteristics) {
                    val charUuid = gattCharacteristic.uuid.toString()
                    val charName = BLEGattAttributes.lookup(charUuid)
                    Util.showSaveLog("CHARS DISCOVERD  : ", charName + " : " + charUuid + "\n")
                }
            }
        }


    }

    /**
     * Scan BLE for Connection
     *
     * @param enable Enable scan
     */
    private val mLeConnectionScanCallback = BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
        //Util.showSaveLog("Invoking Gatt","Invoking device");
    }

    /**
     * Scan BLE devices on Android API 18 to 20
     *
     * @param enable Enable scan
     */
    private val mLeScanCallback = BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
        //Util.showSaveLog("BluetoothGattCallback", "------ device getName: " + device.getName());
        // Util.showSaveLog("BluetoothGattCallback", "------ device getUuids: " + device.getUuids());
        // Util.showSaveLog("BluetoothGattCallback", "------ device getAddress: " + device.getAddress());
        if (parseUuids(scanRecord))
            if (!mBluetoothDevices.contains(device))
                mBluetoothDevices.add(device)
    }

    // From Api 18 to 20
    private fun startScanningAPI18(enable: Boolean) {

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(mBleStateReceiver, filter)
        isBLEstate_Register = true
        val mHandler = Handler()
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed({
                mScanning = false
                AppStatusApplication.getInstance().getBluetoothAdapter().stopLeScan(mLeScanCallback)
                showHideProgressDialog(false)
                checkRouterAvaialbility()
            }, SCAN_PERIOD)

            mScanning = true
            showHideProgressDialog(true)
            mBluetoothDevices.clear()
            AppStatusApplication.getInstance().getBluetoothAdapter().startLeScan(mLeScanCallback)
        } else {
            mScanning = false
            showHideProgressDialog(false)
            AppStatusApplication.getInstance().getBluetoothAdapter().stopLeScan(mLeScanCallback)
        }
    }

    //Filtering for API18
    private fun parseUuids(advertisedData: ByteArray?): Boolean {
        var isMatched = false
        if (advertisedData == null)
            return false
        val buffer = ByteBuffer.wrap(advertisedData).order(ByteOrder.LITTLE_ENDIAN)
        while (buffer.remaining() > 2) {
            var length = buffer.get()
            if (length.toInt() == 0) break

            val type = buffer.get()
            when (type) {
                0x02 // Partial list of 16-bit UUIDs
                    , 0x03 // Complete list of 16-bit UUIDs
                -> while (length >= 2) {
                    val uuid = UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", buffer.short)).toString()
                    if (!isMatched)
                        isMatched = uuid.equals(BLEGattAttributes.SERVICE_OBA_UUID, ignoreCase = true)
                    //Util.showSaveLog("UUID", isMatched+ " STATUS "+uuid + " : UUID : " +BLEGattAttributes.SERVICE_OBA_UUID);
                    length -= 2
                }

                0x06 // Partial list of 128-bit UUIDs
                    , 0x07 // Complete list of 128-bit UUIDs
                -> while (length >= 16) {
                    val lsb = buffer.long
                    val msb = buffer.long
                    Util.showSaveLog("UUID", "UUID 2 : " + UUID(msb, lsb))
                    val uuid = UUID(msb, lsb).toString()
                    if (!isMatched)
                        isMatched = uuid.equals(BLEGattAttributes.SERVICE_OBA_UUID, ignoreCase = true)
                    length -= 16
                    //Util.showSaveLog("UUID", isMatched+ " STATUS "+uuid + " : UUID : " +BLEGattAttributes.SERVICE_OBA_UUID);
                }

                else -> {
                }
            }
        }

        return isMatched
    }


    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startScanningAPI21(enable: Boolean) {

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!mBluetoothDevices.contains(result.device)) {
                    Util.showSaveLog("BluetoothGattCallback", "mBluetoothDevice getAddress : " + result.device.address)
                    mBluetoothDevices.add(result.device)
                }
                super.onScanResult(callbackType, result)
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                super.onBatchScanResults(results)
                showHideProgressDialog(false)
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Util.showSaveLog(TAG, "Scanning Failed $errorCode")
                showHideProgressDialog(false)
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(mBleStateReceiver, filter)
        isBLEstate_Register = true

        bluetoothLeScanner = AppStatusApplication.getInstance().getBluetoothAdapter().getBluetoothLeScanner()
        val mHandler = Handler()
        if (enable) {

            mHandler.postDelayed({
                mScanning = false
                showHideProgressDialog(false)
                bluetoothLeScanner.stopScan(scanCallback)
                checkRouterAvaialbility()
            }, SCAN_PERIOD)
            mScanning = true
            mBluetoothDevices.clear()
            val scanFilters = ArrayList<ScanFilter>()
            val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
            val scanFilter = ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BLEGattAttributes.SERVICE_OBA_UUID)).build()
            scanFilters.add(scanFilter)
            bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback)
        } else {
            mScanning = false
            bluetoothLeScanner.stopScan(scanCallback)
        }
    }

}
