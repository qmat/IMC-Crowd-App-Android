package uk.ac.qmul.eecs.imccrowdapp;

import java.util.Locale;

import android.bluetooth.BluetoothDevice;


public class BluetoothDeviceHelper {
	static final String toJSONString(BluetoothDevice bluetoothDevice)
	{
		return String.format(Locale.US, "{\"t\":%d, \"btd\":[%s,%s]}", System.currentTimeMillis()*1000000, bluetoothDevice.getAddress());
	}
}
