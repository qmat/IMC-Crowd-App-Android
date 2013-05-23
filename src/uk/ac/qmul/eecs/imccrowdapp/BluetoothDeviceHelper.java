package uk.ac.qmul.eecs.imccrowdapp;

import java.util.Locale;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;

public class BluetoothDeviceHelper {
	static final String toJSONString(BluetoothDevice bluetoothDevice)
	{
		return String.format(Locale.US, "{\"t\":%d, \"btd\":[%s,%s]}", System.currentTimeMillis()*1000000, bluetoothDevice.getAddress());
	}
}

// This is an alternative helper that takes the BluetoothDevice.ACTION_FOUND intent directly
// Reason being RSSI is returned as an optional extra of the delivered intent and not the BluetoothDevice that is extracted from it
class BluetoothDeviceActionFoundIntentHelper {
	static final String toJSONString(Intent intent)
	{
		// Get RSSI, returning max value of a short if not found (RSSI is negative, dB?)
		short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MAX_VALUE);
		BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		
		if (rssi == Short.MAX_VALUE)
			return String.format(Locale.US, "{\"t\":%d, \"btd\":[\"%s\"]}", System.currentTimeMillis()*1000000, bluetoothDevice.getAddress());
		else
			return String.format(Locale.US, "{\"t\":%d, \"btd\":[\"%s\",%d]}", System.currentTimeMillis()*1000000, bluetoothDevice.getAddress(), rssi);
	}
}