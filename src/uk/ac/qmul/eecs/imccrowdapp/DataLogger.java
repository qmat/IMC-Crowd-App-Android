package uk.ac.qmul.eecs.imccrowdapp;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;

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
		
	HandlerThread sensorLoggerThread = new HandlerThread("sensorLoggerThread");
	HandlerThread dataLoggerThread = new HandlerThread("dataLoggerThread");
	
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
				
		// TASK: Start the threads the sensor messages are going to be delivered and processed on
		// This includes writing out log files, so shouldn't be main thread
		
		// Note: sensorLoggerThread on Nexus4/4.2.2 gets saturated by Gyro SensorEvents and so is unsuitable for anything else...
		sensorLoggerThread.start();
		Handler sensorHandler = new Handler(sensorLoggerThread.getLooper());

		// ...hence also dataLoggerThread for everything non-SensorEvent
		dataLoggerThread.start();
		Handler dataHandler = new Handler(dataLoggerThread.getLooper());
		
		for (Sensor sensor : sensorManager.getSensorList(Sensor.TYPE_ALL))
		{
			// Ignore depreciated sensors
			if (sensor.getType() == Sensor.TYPE_ORIENTATION) continue;
			
			// Start listening
			boolean ok = 	sensorManager.registerListener(this, sensor, sensorReadInterval, sensorHandler);
			
			// Log sensor name we are listening to
			if (ok) 		addToDataLog(String.format(Locale.US, "{\"active\":\"%s\"}", sensor.getName()));
			else			Log.w(TAG, "Failed to register for sensor: " + sensor.getName());
		}

		// TASK: Start location
		
		if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
		{
			// Register the listener with the Location Manager to receive location updates.
			locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener, dataLoggerThread.getLooper()); // Cell tower and WiFi base stations
			
			// Log sensor we're listening to
			addToDataLog("{\"active\":\"Location via Network\"}");
		}
		
		if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
		{
			// Register the listener with the Location Manager to receive location updates.
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener, dataLoggerThread.getLooper()); // GPS
			
			// Log sensor we're listening to
			addToDataLog("{\"active\":\"Location via GPS\"}");
		}
		// TASK: Start WiFi scans
		
		if (wifiManager.isWifiEnabled())
		{
			// Register for results of startScan
			context.registerReceiver(this, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), null, dataHandler);
			
			// Note this requires the CHANGE_WIFI_STATE permission as well. <shrugs>
			wifiManager.startScan();
			
			// Log sensor we're listening to
			addToDataLog("{\"active\":\"Wifi\"}");
		}
		
		//TASK: Start Bluetooth scans
		
		if (bluetoothAdapter != null) 
		{
			// Register for results of startDiscovery
			context.registerReceiver(this, new IntentFilter(BluetoothDevice.ACTION_FOUND), null, dataHandler);
			
			// Register to find out when discovery ends so we can start it anew
			context.registerReceiver(this, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED), null, dataHandler);
			
			// Start a discovery scan.
			bluetoothAdapter.startDiscovery();
			
			// Log sensor we're listening to
			addToDataLog("{\"active\":\"Bluetooth\"}");
		}
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

	// BroadcastReceiver implementation
    public void onReceive(Context c, Intent intent) 
    {
    	String action = intent.getAction();
    	
    	if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) 
    	{
	    	addToDataLog(ScanResultHelper.toJSONString(wifiManager.getScanResults()));
	    	
	        // TASK: Set new wifi scan off
	    	wifiManager.startScan();
	    }
    	else if (action.equals(BluetoothDevice.ACTION_FOUND))
    	{   		
    		// This is an alternative helper that takes the intent directly, to also get RSSI in the log.
    		addToDataLog(BluetoothDeviceActionFoundIntentHelper.toJSONString(intent));
    	}
    	else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
    	{
    		// TASK: Set a new bluetooth scan off
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