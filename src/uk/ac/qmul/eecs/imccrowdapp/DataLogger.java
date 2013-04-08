package uk.ac.qmul.eecs.imccrowdapp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.EnumMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

class SensorData
{
	static int	SensorDataVersion = 1;
	public enum SensorDataType {accForceX, accForceY, accForceZ, accPitch, accRoll, locLatitude, locLongitude, locAccuracy, comHeading, comX, comY, comZ };
    
    private Date timeStamp;
    private EnumMap<SensorDataType, Float> sensorDataMap;
    
    public void add(SensorDataType sensorDataType, float value)
    {
    	// Timestamp if its the first reading
    	if (sensorDataMap.size() == 0) 
    	{
    		timeStamp = new Date();
    	}
    	
    	sensorDataMap.put(sensorDataType, value);
    }

    public void clear()
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
    	JSONObject json = new JSONObject(sensorDataMap);
    	try 
    	{
			json.put("timeStamp", String.valueOf(timeStamp.getTime())); 
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

public class DataLogger {

	private static final String TAG = "DataLogger";
	private int sensorDataArraySize;
	private SensorData[] sensorDataArray; // TODO: Want to create circular buffer so memory isn't constantly allocated and deallocated. Need to confirm this is actually doing that, suspect it isn't if objects are pointers (spot the non-java person learning java).
	private int sensorDataArrayIndex;
	private String logsFolder;
	
	DataLogger()
	{
		sensorDataArraySize = 10; // TODO: make newFileInterval settable
		sensorDataArray = new SensorData[sensorDataArraySize];
		for (int i = 0; i < sensorDataArraySize; i++)
		{
			sensorDataArray[i] = new SensorData();
		}
		
		sensorDataArrayIndex = 0;
		logsFolder = null;
	}
	
	public void setLogsFolder(String folderPath)
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

	public void captureData()
	{
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
		    	String filePath = logsFolder + File.pathSeparator + String.valueOf(nowDate.getTime()); 
		    	
	    		try 
	    		{
					FileWriter sensorDataFileWriter = new FileWriter(filePath);
					sensorDataFileWriter.write(jsonString);
				
					// TASK: Notify host app that there is new file for upload etc.
			        
					// ofNotifyEvent(onLogFileWritten, filename);
				} 
	    		catch (IOException e) 
	    		{
					// TODO Auto-generated catch block
					Log.w(TAG, "Failed to write log.");
					e.printStackTrace();
				}
		    }
		    
	        // Clear sensorData for new samples
	        
		    sensorDataArrayIndex = -1;   
	    }
	}
}
