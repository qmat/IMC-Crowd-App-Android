package uk.ac.qmul.eecs.imccrowdapp;

import java.util.Timer;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

public class CrowdNodeService extends Service {
	private static final String TAG = "CrowdNodeService";
	private static final int idForForeground = 1;
	
	// Determine if service is running, available within package
	private static CrowdNodeService instance = null;
	static boolean isInstanceCreated() { return instance != null; }
	static boolean hasInstanceEverBeenCreated = false;
	
	// TODO: Make dynamic
	private static final int captureDataPeriodMicroSeconds = 1000;
	
	private WakeLock wakeLock;
	
	Timer captureDataTimer;
	
	ServerConnection serverConnection;
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate");

		// TASK: Serve our WTF ANDROID is-service-running query
		hasInstanceEverBeenCreated = true;
		instance = this;
		
		// TASK: Don't let phone sleep so that the service can keep actively running
		
		// If you hold a partial wake lock, the CPU will continue to run, regardless of any display timeouts or the state of the screen and even after the user presses the power button. 
		PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wakeLock.acquire();
		
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
		
		// TASK: Run as foreground service
		
		// ie. service won't be culled by Android if low on resources, running it is an overt user intent
		startForeground(idForForeground, createServiceNotification());
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy");
		
		// TASK: Serve our WTF ANDROID is service running query
		instance = null;
		
		// TASK: Revert Android things
		
		// Allow phone to sleep again
		wakeLock.release();
		wakeLock = null;
		
		// Stop service's foreground'ness and remove notification
		stopForeground(true);
		
		// TASK: Stop logging and uploading data
		
		serverConnection = null;
		
		captureDataTimer.cancel();
		captureDataTimer = null;
		
	}
	
	@Override
	public void onStart(Intent intent, int startid) {
		Log.d(TAG, "onStart");
		
	}
	
    private Notification createServiceNotification() {
    	Notification.Builder notificationBuilder = new Notification.Builder(this)
											    	       .setSmallIcon(R.drawable.ic_launcher)
											    	       .setContentTitle("IMC Crowd App")
											    	       .setContentText("Sensing active")
    	 												   .setProgress(0, 0, true);

    	// TASK: Set action for notification
    	Intent resultIntent = new Intent(this, MainActivity.class);
    	
    	// Don't relaunch the activity if its currently the foreground app
    	resultIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
    	
    	PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, 0);
    	
    	notificationBuilder.setContentIntent(resultPendingIntent);

        return notificationBuilder.build();
    }
}