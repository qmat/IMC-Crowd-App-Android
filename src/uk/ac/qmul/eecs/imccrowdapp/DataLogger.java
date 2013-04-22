package uk.ac.qmul.eecs.imccrowdapp;

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
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

class DataLogger implements SensorEventListener {

	private static final String TAG = "DataLogger";

	// Local Broadcast Notifications
	static final String TAGLogFileWrittenBroadcast = "LogFileWrittenBroadcast";
	static final String TAGLogFileWrittenExtraFilePath = "LogFileWrittenExtraFilePath";
		
	private int sensorEventArraySize;
	private SensorEvent[] sensorEventArray;
	private int sensorEventArrayIndex;
	private String logsFolder;
		
	final private SensorManager sensorManager;
	final private Context context;
	
	DataLogger(Context inContext)
	{	
		sensorEventArraySize = 1000; // TODO: make newFileInterval settable
		sensorEventArray = new SensorEvent[sensorEventArraySize];
		
		sensorEventArrayIndex = 0;
		logsFolder = null;
		
		context = inContext;
		
		sensorManager = (SensorManager)context.getSystemService(context.SENSOR_SERVICE);

		LocalBroadcastManager.getInstance(context).registerReceiver(onNewLogDirReceiver, new IntentFilter(CrowdNodeService.TAGNewLogDirectoryBroadcast));
	}
	
	void close()
	{
		LocalBroadcastManager.getInstance(context).unregisterReceiver(onNewLogDirReceiver);
	}
	
	void startLogging()
	{
		int rateInMicroseconds = 10000;
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ALL), rateInMicroseconds);
	}
	
	void stopLogging()
	{
		sensorManager.unregisterListener(this);
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
	public void onSensorChanged(SensorEvent event) {
		// FIXME: Seeing duplicate lines in log. But this isn't showing duplicate lines coming in. Uh-oh.
		if (!SensorEventHelper.isNew(event))
		{
			Log.d(TAG, "Duplicate SensorEvent");
			return;
		}
		
		if (sensorEventArrayIndex >= sensorEventArraySize)
		{
			Log.w(TAG, "sensorDataArrayIndex out of bounds. Resetting");
			sensorEventArrayIndex = 0;
		}
		
		sensorEventArray[sensorEventArrayIndex] = event;
		
		sensorEventArrayIndex++;
        
        // TASK: Handle full buffer
        
        if (sensorEventArrayIndex >= sensorEventArraySize) flushData();
	}
	
	void flushData()
	{
		Log.d(TAG, "Handling full buffer");
    	
	    // TASK: Write logged sensor data to file
	    if (logsFolder != null)
	    {   
	    	StringBuilder stringBuilder = new StringBuilder();
	    	String separator = System.getProperty("line.separator");
	        for (int i = 0; i < sensorEventArrayIndex; i++)
	        {
	        	stringBuilder.append(SensorEventHelper.toLogFileEntry(sensorEventArray[i]));
	        	stringBuilder.append(separator);
	        	sensorEventArray[i] = null;
	        }
	        String outputString = stringBuilder.toString();
	        stringBuilder = null;
	        
	    	Date nowDate = new Date();
	    	String filePath = logsFolder + File.separator + String.valueOf(nowDate.getTime()); 
	    	
    		try 
    		{
				FileWriter sensorDataFileWriter = new FileWriter(filePath);
				sensorDataFileWriter.write(outputString);
				sensorDataFileWriter.flush();			
			
				// TASK: Notify host app that there is new file for upload etc.
		        
				Log.d(TAG, "Written sensor data -- \n file: " + filePath + "\n content: " + outputString);
				
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
        
	    sensorEventArrayIndex = 0;
	}
}