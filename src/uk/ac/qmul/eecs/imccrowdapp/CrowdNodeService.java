package uk.ac.qmul.eecs.imccrowdapp;

import java.util.Timer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class CrowdNodeService extends Service {
	private static final String TAG = "CrowdNodeService";
	
	// TODO: Make dynamic
	private static final int captureDataPeriodMicroSeconds = 1000;
		
	Timer captureDataTimer;
	
	ServerConnection serverConnection;
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate");

		// TASK: Start logging data
		
		// We do this in onCreate rather than onStart, as this is the one thing this service does, and any more calls to start it are redundant. Akin to singleton.	
		serverConnection = new ServerConnection();
		serverConnection.setEndPointURL(this.getString(R.string.serverEndPoint));
		serverConnection.startSession();
		serverConnection.startFileUploads();
		
		if (captureDataTimer != null) 
		{
			captureDataTimer.cancel();
			captureDataTimer = null;
		}
		
		captureDataTimer = new Timer();
		captureDataTimer.schedule(new DataLoggerTask(this), 0, captureDataPeriodMicroSeconds);
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy");
		
		// TASK: Stop logging and uploading data
		
		serverConnection = null;
		
		captureDataTimer.cancel();
		captureDataTimer = null;
		
	}
	
	@Override
	public void onStart(Intent intent, int startid) {
		Log.d(TAG, "onStart");
		
	}
}