package uk.ac.qmul.eecs.imccrowdapp;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

class DataLogger implements SensorEventListener {
	
	private static final String TAG = "DataLogger";

	// Local Broadcast Notifications
	static final String TAGLogFileWrittenBroadcast = "LogFileWrittenBroadcast";
	static final String TAGLogFileWrittenExtraFilePath = "LogFileWrittenExtraFilePath";
		
	private int sensorDataArraySize;
	private String[] sensorDataArray;
	private int sensorDataArrayIndex;
	private String logsFolder;
	
	private int sensorReadInterval;
	
	final private SensorManager sensorManager;
	final private Context context;
	
	HandlerThread dataLoggerThread = new HandlerThread("dataLoggerThread");
	
	DataLogger(Context inContext)
	{	
		sensorDataArraySize = 1000; // TODO: make newFileInterval settable
		sensorDataArray = new String[sensorDataArraySize];
		
		sensorDataArrayIndex = 0;
		logsFolder = null;
		
		context = inContext;
		
		sensorManager = (SensorManager)context.getSystemService(context.SENSOR_SERVICE);

		LocalBroadcastManager.getInstance(context).registerReceiver(onNewLogDirReceiver, new IntentFilter(CrowdNodeService.TAGNewLogDirectoryBroadcast));
	}
	
	void close()
	{
		LocalBroadcastManager.getInstance(context).unregisterReceiver(onNewLogDirReceiver);
	}
	
	@SuppressWarnings("deprecation")
	void startLogging()
	{
		int hertz = 50;
		sensorReadInterval = 1000000 / hertz;
		
		// Start the thread the SensorEvents are going to be delivered and processed on
		dataLoggerThread.start();
		Handler handler = new Handler(dataLoggerThread.getLooper());
		
		for (Sensor sensor : sensorManager.getSensorList(Sensor.TYPE_ALL))
		{
			// Ignore depreciated sensors
			if (sensor.getType() == Sensor.TYPE_ORIENTATION) continue;
			
			// Start listening
			boolean ok = 	sensorManager.registerListener(this, sensor, sensorReadInterval, handler);
			if (ok) 		Log.d(TAG, "Listening for sensor: " + sensor.getName());
			else			Log.w(TAG, "Failed to register for sensor: " + sensor.getName());
		}
	}
	
	void stopLogging()
	{
		sensorManager.unregisterListener(this);
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
		
		synchronized(this)
		{
			if (sensorDataArrayIndex >= sensorDataArraySize)
			{
				Log.w(TAG, "sensorDataArrayIndex out of bounds. Resetting");
				sensorDataArrayIndex = 0;
			}
			
			// INFO: Do not store SensorEvents! The system recycles them or somesuch, leading to much developer pain.
			
			sensorDataArray[sensorDataArrayIndex] = SensorEventHelper.toJSONString(event);
			
			sensorDataArrayIndex++;
	        
	        // TASK: Handle full buffer
	        
	        if (sensorDataArrayIndex >= sensorDataArraySize) flushData();
		}
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
		        for (int i = 0; i < sensorDataArrayIndex; i++)
		        {
		        	if (i > 0)
		        	{
		        		sensorDataFileWriter.write(",");
		        		sensorDataFileWriter.newLine();
		        	}
		        	sensorDataFileWriter.write(sensorDataArray[i]);
		        	sensorDataArray[i] = null;
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
        
	    sensorDataArrayIndex = 0;
	}
}