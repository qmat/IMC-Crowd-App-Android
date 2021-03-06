package uk.ac.qmul.eecs.imccrowdapp;

import java.io.File;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class CrowdNodeService extends Service {
	private static final String TAG = "CrowdNodeService";
	
	// Preference Keys
	private static final String TAGSessionID = "SessionID";
	
	// Notification Tag
	private static final int idForForeground = 1;
	
	// Local Broadcast Notifications
	static final String TAGServiceStatusBroadcast = "ServiceStatusBroadcast";
	static final String TAGServiceStatusExtraActive = "ServiceStatusExtraActive";
	static final String TAGNewLogDirectoryBroadcast = "NewLogDirectoryBroadcast";
	static final String TAGNewLogDirectoryExtraPath = "NewLogDirectoryExtraPath";
	
	// Determine if service is running, available within package
	private static CrowdNodeService instance = null;
	static boolean isInstanceCreated() { return instance != null; }
	static boolean hasInstanceEverBeenCreated = false;
	
	private WakeLock wakeLock;
	
	private ServerConnection serverConnection;
	private DataLogger dataLogger;
	
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
		
		// TASK: Register for messages from ServerConnection, DataLogger etc.
		LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
		localBroadcastManager.registerReceiver(onNewSessionIDReceiver, new IntentFilter(ServerConnection.TAGNewSessionBroadcast));
		localBroadcastManager.registerReceiver(onDataLogFileWrittenReceiver, new IntentFilter(DataLogger.TAGLogFileWrittenBroadcast));
		
		// TASK: Start logging data
		
		// We do this in onCreate rather than onStart, as this is the one thing this service does, and any more calls to start it are redundant. Akin to singleton.	
		serverConnection = new ServerConnection(instance);
		serverConnection.setEndPointURL(this.getString(R.string.serverEndPoint));
		
		// Get sessionID of last use
		SharedPreferences settings = getSharedPreferences(TAG, MODE_PRIVATE);
		String sessionID = settings.getString(TAGSessionID, "");
		if (serverConnection.validateSessionID(sessionID))
		{
			serverConnection.setSessionID(sessionID);
		}
		
		serverConnection.startSession();
		
        dataLogger = new DataLogger(instance);
        dataLogger.startLogging();
		
		// TASK: Run as foreground service
		
		// ie. service won't be culled by Android if low on resources, running it is an overt user intent
		startForeground(idForForeground, createServiceNotification(getString(R.string.crowdNodeServiceNotificationText)));
		
		// TASK: Notify whoever cares (likely UI)
		
		Intent intent = new Intent(TAGServiceStatusBroadcast);
		intent.putExtra(TAGServiceStatusExtraActive, true);
		LocalBroadcastManager.getInstance(instance).sendBroadcast(intent);
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy");
		
		// TASK: Stop logging
		dataLogger.stopLogging();
		
		// TASK: Notify whoever
		
		Intent intent = new Intent(TAGServiceStatusBroadcast);
		intent.putExtra(TAGServiceStatusExtraActive, false);
		LocalBroadcastManager.getInstance(instance).sendBroadcast(intent);
		
		// TASK: Serve our WTF ANDROID is service running query
		instance = null;
		
		// TASK: Revert Android things
		
		// Allow phone to sleep again
		wakeLock.release();
		wakeLock = null;
		
		// Stop service's foreground'ness and remove notification
		stopForeground(true);
		
		// TASK: Unregister for messages from ServerConnection, DataLogger etc.
		LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
		localBroadcastManager.unregisterReceiver(onNewSessionIDReceiver);
		localBroadcastManager.unregisterReceiver(onDataLogFileWrittenReceiver);
	}
	
	@Override
	public void onStart(Intent intent, int startid) {
		Log.d(TAG, "onStart");
		
	}
	
	private BroadcastReceiver onNewSessionIDReceiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent)
	    {
	    	Log.d(TAG, "onNewSessionIDReceiver");
			
	    	// Ignore messages received while starting / stopping
	    	if (instance == null) return;
	    	
	    	// TASK: Get data directory we can write to
			
			File filesDir = getExternalFilesDir("sensorData");
			if (filesDir == null)
			{
				Log.w(TAG, "Could not use external files dir, falling back to internal");
				filesDir = getFilesDir();
			}
			if (filesDir == null)
			{
				Log.e(TAG, "Could not use files dir.");
				// TODO: Alert dialog and quit?
			}
			
			// TASK: Make subdirectory with sessionID
			
			File sessionLogDir = new File(filesDir, intent.getStringExtra(ServerConnection.TAGNewSessionExtraSessionID));
			
			boolean sessionLogDirExists = sessionLogDir.canWrite();
					
			if (!sessionLogDirExists)
			{
				sessionLogDirExists = sessionLogDir.mkdir();
			}
	
			// TASK: Use log folder
			
			if (sessionLogDirExists)
			{
				Intent newLogDirIntent = new Intent(TAGNewLogDirectoryBroadcast);
				newLogDirIntent.putExtra(TAGNewLogDirectoryExtraPath, sessionLogDir.getPath());
				LocalBroadcastManager.getInstance(instance).sendBroadcast(newLogDirIntent);
				
				// WTF. serverConnection is null on destroy and create.
			    instance.serverConnection.startFileUploadsAsync(); // TODO: Do this after initial network activity dies down
			}
		    
		    // TASK: Store it
			SharedPreferences settings = getSharedPreferences(TAG, MODE_PRIVATE);  
			SharedPreferences.Editor prefEditor = settings.edit();  
			prefEditor.putString(TAGSessionID, intent.getStringExtra(ServerConnection.TAGNewSessionExtraSessionID));  
			prefEditor.commit(); 
			
			// TASK: Update service notification if we have an active session
			if (intent.getBooleanExtra(ServerConnection.TAGNewSessionExtraSessionActive, false))
			{
				Notification notification = createServiceNotification(getString(R.string.crowdNodeServiceNotificationText) + " " + getString(R.string.crowdNodeServiceNotificationServerConnectedText));
				NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
				notificationManager.notify(idForForeground, notification);
			}
	    }
	};
	
	private BroadcastReceiver onDataLogFileWrittenReceiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent)
	    {
	    	Log.d(TAG, "onDataLogFileWrittenReceiver");
	    	
	    	// Ignore messages received while starting / stopping
	    	if (instance == null) return;
	    	
			instance.serverConnection.addFileForUpload(intent.getStringExtra(DataLogger.TAGLogFileWrittenExtraFilePath));
	    }
	};
	
    private Notification createServiceNotification(String text) {
    	NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
											    	       .setSmallIcon(R.drawable.ic_launcher)
											    	       .setContentTitle("IMC Crowd App")
											    	       .setContentText(text)
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