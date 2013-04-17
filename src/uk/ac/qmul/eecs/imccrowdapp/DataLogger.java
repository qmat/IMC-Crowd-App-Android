package uk.ac.qmul.eecs.imccrowdapp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.EnumMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

class SensorData
{
	static int	SensorDataVersion = 1;
	enum SensorDataType {accForceX, accForceY, accForceZ, accPitch, accRoll, locLatitude, locLongitude, locAccuracy, comHeading, comX, comY, comZ };
    
    private Date timeStamp;
    private EnumMap<SensorDataType, Float> sensorDataMap;
    
    void add(SensorDataType sensorDataType, float value)
    {
    	// Timestamp if its the first reading
    	if (sensorDataMap.size() == 0) 
    	{
    		timeStamp = new Date();
    	}
    	
    	sensorDataMap.put(sensorDataType, value);
    }

    void clear()
    {
    	timeStamp = null;
    	sensorDataMap.clear();
    }
    
    SensorData()
    {
    	sensorDataMap = new EnumMap<SensorDataType, Float>(SensorDataType.class);
    }
    
    JSONObject toJSON()
    {
    	// This crashes, unfortunately. So, the long way.
    	// JSONObject json = new JSONObject(sensorDataMap);
    	JSONObject json = new JSONObject();
    	try 
    	{
	    	json.put("timeStamp", String.valueOf(timeStamp.getTime())); 
	    	for (SensorDataType key : sensorDataMap.keySet())
	    	{
	    		json.put(key.toString(), sensorDataMap.get(key));
	    	}
		} 
    	catch (JSONException e) 
    	{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	return json;
    }
    
    // TODO: More compact form ie. { time: xxx, accXYZPR: [x,y,z,p,r], locLtLnA: [lt, ln, a], comHXYZA
}

class DataLogger {

	private static final String TAG = "DataLogger";

	// Local Broadcast Notifications
	static final String TAGLogFileWrittenBroadcast = "LogFileWrittenBroadcast";
	static final String TAGLogFileWrittenExtraFilePath = "LogFileWrittenExtraFilePath";
	
	
	private int sensorDataArraySize;
	private SensorData[] sensorDataArray;
	private int sensorDataArrayIndex;
	private String logsFolder;
	private Context context;
	
	DataLogger(Context inContext)
	{	
		sensorDataArraySize = 10; // TODO: make newFileInterval settable
		sensorDataArray = new SensorData[sensorDataArraySize];
		for (int i = 0; i < sensorDataArraySize; i++)
		{
			sensorDataArray[i] = new SensorData();
		}
		
		sensorDataArrayIndex = 0;
		logsFolder = null;
		
		context = inContext;
		
		LocalBroadcastManager.getInstance(context).registerReceiver(onNewLogDirReceiver, new IntentFilter(CrowdNodeService.TAGNewLogDirectoryBroadcast));
	}
	
	void close()
	{
		LocalBroadcastManager.getInstance(context).unregisterReceiver(onNewLogDirReceiver);
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

    void captureData()
	{
		Log.d(TAG, "Run");
		
	    // TASK: Capture sensor data
		sensorDataArray[sensorDataArrayIndex].clear();
		
		// TODO: Real sensor data capture!
        sensorDataArray[sensorDataArrayIndex].add(SensorData.SensorDataType.comX, 0.123f);
        sensorDataArray[sensorDataArrayIndex].add(SensorData.SensorDataType.comY, 0.234f);
        sensorDataArray[sensorDataArrayIndex].add(SensorData.SensorDataType.comZ, 0.345f);
        
        sensorDataArrayIndex++;
        
        // TASK: Handle full buffer
        
        if (sensorDataArrayIndex >= sensorDataArraySize)
        {
        	Log.d(TAG, "Handling full buffer");
        	
		    // TASK: Write logged sensor data to file
		    if (logsFolder != null)
		    {   
		        // Write sensorData out to file in JSON format
		    	JSONArray json = new JSONArray();
		    	for (int i = 0; i < sensorDataArrayIndex; i++)
		    	{
		    		json.put(sensorDataArray[i].toJSON());
		    	}
		    	String jsonString = json.toString();
		    	
		    	Date nowDate = new Date();
		    	String filePath = logsFolder + File.separator + String.valueOf(nowDate.getTime()); 
		    	
	    		try 
	    		{
					FileWriter sensorDataFileWriter = new FileWriter(filePath);
					sensorDataFileWriter.write(jsonString);
					sensorDataFileWriter.flush();			
				
					// TASK: Notify host app that there is new file for upload etc.
			        
					Log.d(TAG, "Written sensor data -- \n file: " + filePath + "\n content: " + jsonString);
					
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
}