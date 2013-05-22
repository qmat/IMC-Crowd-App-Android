package uk.ac.qmul.eecs.imccrowdapp;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

class DataLogger extends BroadcastReceiver implements SensorEventListener {
	
	private static final String TAG = "DataLogger";

	// Local Broadcast Notifications
	static final String TAGLogFileWrittenBroadcast = "LogFileWrittenBroadcast";
	static final String TAGLogFileWrittenExtraFilePath = "LogFileWrittenExtraFilePath";
		
	private int dataLogSize;
	private String[] dataLogArray;
	private int dataLogArrayIndex;
	private String logsFolder;
	
	private int sensorReadInterval;
	
	final private SensorManager sensorManager;
	final private WifiManager wifiManager;
	final private LocationManager locationManager;
	final private BluetoothAdapter bluetoothAdapter;
	
	final private Context context;
		
	// Threads to handle callbacks and write log files out on
	// Note: sensorLoggerThread on Nexus4/4.2.2 gets saturated by Gyro SensorEvents and so is unsuitable for anything else
	HandlerThread sensorLoggerThread = new HandlerThread("sensorLoggerThread");
	//HandlerThread dataLoggerThread = new HandlerThread("dataLoggerThread");
	
	DataLogger(Context inContext)
	{	
		dataLogSize = 1000; // TODO: make newFileInterval settable
		dataLogArray = new String[dataLogSize];
		
		dataLogArrayIndex = 0;
		logsFolder = null;
		
		context = inContext;
		
		sensorManager = (SensorManager)context.getSystemService(context.SENSOR_SERVICE);
		wifiManager = (WifiManager)context.getSystemService(context.WIFI_SERVICE);
		locationManager = (LocationManager)context.getSystemService(context.LOCATION_SERVICE);
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		
		LocalBroadcastManager.getInstance(context).registerReceiver(onNewLogDirReceiver, new IntentFilter(CrowdNodeService.TAGNewLogDirectoryBroadcast));
	}
	
	void close()
	{
		LocalBroadcastManager.getInstance(context).unregisterReceiver(onNewLogDirReceiver);
	}
	
	@SuppressWarnings("deprecation")
	void startLogging()
	{
		// TASK: Start sensors
		
		int hertz = 50;
		sensorReadInterval = 1000000 / hertz;
				
		// Start the thread the SensorEvents are going to be delivered and processed on
		sensorLoggerThread.start();
		Handler sensorHandler = new Handler(sensorLoggerThread.getLooper());
		
		for (Sensor sensor : sensorManager.getSensorList(Sensor.TYPE_ALL))
		{
			// Ignore depreciated sensors
			if (sensor.getType() == Sensor.TYPE_ORIENTATION) continue;
			
			// Start listening
			boolean ok = 	sensorManager.registerListener(this, sensor, sensorReadInterval, sensorHandler);
			if (ok) 		Log.d(TAG, "Listening for sensor: " + sensor.getName());
			else			Log.w(TAG, "Failed to register for sensor: " + sensor.getName());
		}
		
		// TASK: Start WiFi scans
		
		//dataLoggerThread.start();
		//Handler dataHandler = new Handler(dataLoggerThread.getLooper());
		
		// FIXME: Registering on handler thread results in much more infrequent entries. WTF?
		context.registerReceiver(this, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
		//context.registerReceiver(this, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), null, dataHandler);
		
		// Note this requires the CHANGE_WIFI_STATE permission as well. <shrugs>
		wifiManager.startScan();
		
		// TASK: Start location
		
		// Register the listener with the Location Manager to receive location updates.
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener); // Cell tower and WiFi base stations
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener); // GPS
		//locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener, dataLoggerThread.getLooper()); // Cell tower and WiFi base stations
		//locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener, dataLoggerThread.getLooper()); // GPS
		
		//TASK: Start Bluetooth Scanner
		
		// Do we have Bluetooth?
		if (bluetoothAdapter == null) {
		    // Device does not support Bluetooth
		} else {
			// we have bluetooth!
			
			// Ask user to turn on bluetooth if not enabled
//			if (!bluetoothAdapter.isEnabled()) 
//			{
//			    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//			    startActivityForResult(enableBtIntent, 1); // REQUEST_ENABLE_BT
//			}
			
//			// Make device discoverable constantly (0)
//			Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
//			discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
//			context.startActivity(discoverableIntent);
			
		// Scan and log devices 
			
		context.registerReceiver(this, new IntentFilter(BluetoothDevice.ACTION_FOUND));

		bluetoothAdapter.startDiscovery();
				
		} // end else if have bluetooth
	}
	
	void stopLogging()
	{
		// Stop sensing
		sensorManager.unregisterListener(this);
		context.unregisterReceiver(this);
		locationManager.removeUpdates(locationListener);
		
		// Write out current state of dataLog
		flushData();
	}
	
	private BroadcastReceiver onNewLogDirReceiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent)
	    {
	    	Log.d(TAG, "onNewLogDirReceiver");
	    	setLogsFolder(intent.getStringExtra(CrowdNodeService.TAGNewLogDirectoryExtraPath));
	    }
	};
	
	void setLogsFolder(String folderPath)
	{
	    // Only set if folder is writeable
	    File directory = new File(folderPath);
	    if (directory.isDirectory() && directory.canWrite())
	    {
	        logsFolder = folderPath;
	    }
	    else
	    {
	    	logsFolder = null;
	        Log.w(TAG, "Aborting set of logs folder and reverting no folder set. <" + folderPath + "> cannot be written to.");
	    }
	}

	synchronized void addToDataLog(String logEntry)
	{
		if (dataLogArrayIndex >= dataLogSize)
		{
			Log.w(TAG, "sensorDataArrayIndex out of bounds. Resetting");
			dataLogArrayIndex = 0;
		}
		
		// INFO: Do not store SensorEvents! The system recycles them or somesuch, leading to much developer pain.
		
		dataLogArray[dataLogArrayIndex] = logEntry;
		
		dataLogArrayIndex++;
        
        // TASK: Handle full buffer
        
        if (dataLogArrayIndex >= dataLogSize) flushData();
	}
	
	void flushData()
	{
		Log.d(TAG, "Handling full buffer");
    	
	    // TASK: Write logged sensor data to file
	    if (logsFolder != null)
	    {   
	    	Date nowDate = new Date();
	    	String filePath = logsFolder + File.separator + String.valueOf(nowDate.getTime()); 
	    	
    		try 
    		{
    			
				FileWriter fileWriter = new FileWriter(filePath);
				BufferedWriter sensorDataFileWriter = new BufferedWriter(fileWriter);
				
				// Format as per JSON Array of JSON objects
				sensorDataFileWriter.write("[");
				sensorDataFileWriter.newLine();
		        for (int i = 0; i < dataLogArrayIndex; i++)
		        {
		        	if (i > 0)
		        	{
		        		sensorDataFileWriter.write(",");
		        		sensorDataFileWriter.newLine();
		        	}
		        	sensorDataFileWriter.write(dataLogArray[i]);
		        	dataLogArray[i] = null;
		        }
		        sensorDataFileWriter.newLine();
				sensorDataFileWriter.write("]");
				
				sensorDataFileWriter.close();
    			
				// TASK: Notify host app that there is new file for upload etc.
		        
				Log.d(TAG, "Written sensor data -- \n file: " + filePath);
				
				Intent intent = new Intent(TAGLogFileWrittenBroadcast);
				intent.putExtra(TAGLogFileWrittenExtraFilePath, filePath);
				LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
			}
    		catch (IOException e) 
    		{
				// TODO Auto-generated catch block
				Log.w(TAG, "Failed to write log.");
				e.printStackTrace();
			}
	    }
	    
        // Clear sensorData for new samples
        
	    dataLogArrayIndex = 0;
	}
	
	//// HANDLE SENSORS
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) 
	{
		// SensorEvents rarely come at rate requested. We need to handle this.
		// AKA the Gyroflood.
		long elapsed = SensorEventHelper.microSecondsElapsedSinceLastCalledForType(event);
		
		if (elapsed < sensorReadInterval)
		{
			// Log.d(TAG, "SensorEvent too soon after last: " + event.sensor.getName() + " " + elapsed);
			return;
		}
		
		addToDataLog(SensorEventHelper.toJSONString(event));
	}
	
	//// HANDLE WIFI & BT
	
	// Broadcast Receiver, currently only receiving WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
    public void onReceive(Context c, Intent intent) 
    {
    	String action = intent.getAction();
    	
    	if(action.equals("android.net.wifi.SCAN_RESULTS")) 
    	{
	    	addToDataLog(ScanResultHelper.toJSONString(wifiManager.getScanResults()));
	    	
	        // TASK: Set new wifi scan off
	        
	    	wifiManager.startScan();
	    }
    	
    	if (action.equals(BluetoothDevice.ACTION_FOUND))
    	{
    		// Bluetooth RSSI comes in as an optional intent
    		// bluetoothDevice.EXTRA_RSSI
    		BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    		
    		addToDataLog(BluetoothDeviceHelper.toJSONString(device));
	        
	    	bluetoothAdapter.startDiscovery();
    	}
    }
	
    //// HANDLE LOCATION
    
    LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
          // Called when a new location is found by the network location provider.
        	addToDataLog(LocationHelper.toJSONString(location));
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {}

        public void onProviderEnabled(String provider) {}

        public void onProviderDisabled(String provider) {}
      };
}