package com.sss.blecommunicator

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.support.v4.content.ContextCompat.getSystemService
import android.util.Log

class Util {

    companion object {

        var mBluetoothAdapter: BluetoothAdapter? = null

        fun showSaveLog(TAG: String, message: String) {
            Log.e(TAG,message)
        }

        fun getBluetoothAdapter(context: Context): BluetoothAdapter? {

            if (mBluetoothAdapter == null) {
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                mBluetoothAdapter = bluetoothManager.adapter
            }
            return mBluetoothAdapter
        }

        fun isBluetoothEnabled(): Boolean {
            return mBluetoothAdapter!!.isEnabled()
        }

    }



}
