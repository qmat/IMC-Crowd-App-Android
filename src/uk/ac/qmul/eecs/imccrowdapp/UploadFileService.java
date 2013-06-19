package uk.ac.qmul.eecs.imccrowdapp;

import java.io.File;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class UploadFileService extends IntentService
{
	// Local Broadcast Notifications
	static final String TAGUploadFileServiceBroadcast = "UploadFileServiceBroadcast";
	static final String TAGUploadFileServiceHandlingExtra = "UploadFileServiceHandlingExtra";
	
	ServerConnection serverConnection;
	
	public UploadFileService() 
	{
	    super("uploadFileService");
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		// setEndPoint cannot be in constructor
		serverConnection = new ServerConnection(this);
		serverConnection.setEndPointURL(this.getString(R.string.serverEndPoint));
	}
	
	
	@Override
	protected void onHandleIntent(Intent intent) 
	{
		// TODO: Make filesDir a static property of CNS
		
		File filesDir = getExternalFilesDir("sensorData");
		if (filesDir == null)
		{
			filesDir = getFilesDir();
		}
		
		if (filesDir.isDirectory())
		{
			Intent startIntent = new Intent(TAGUploadFileServiceBroadcast);
			startIntent.putExtra(TAGUploadFileServiceHandlingExtra, true);
			LocalBroadcastManager.getInstance(this).sendBroadcast(startIntent);
			
			// TASK: Get sessionID for upload (needed if uploading from 'No Session')
			boolean sessionActive = serverConnection.startSessionBlocking();
			
			if (sessionActive)
			{
				// TASK: Get subfolders, which should correspond to CrowdNodeService sessions
				File[] sessionFolders = filesDir.listFiles();
				
				for (File sessionFolder : sessionFolders)
				{
					if (!sessionFolder.isDirectory()) continue;
					
					serverConnection.scanFolderForUpload(sessionFolder.getPath());
					serverConnection.doFileUploadsBlocking();
					
					sessionFolder.delete();
				}
			}
			
			Intent endIntent = new Intent(TAGUploadFileServiceBroadcast);
			endIntent.putExtra(TAGUploadFileServiceHandlingExtra, false);
			LocalBroadcastManager.getInstance(this).sendBroadcast(endIntent);
		}
	}
}
