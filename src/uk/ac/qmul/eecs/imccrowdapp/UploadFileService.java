package uk.ac.qmul.eecs.imccrowdapp;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

public class UploadFileService extends IntentService
{
	// Local Broadcast Notifications
	static final String TAGUploadFileServiceBroadcast = "UploadFileServiceBroadcast";
	static final String TAGUploadFileServiceHandlingExtra = "UploadFileServiceHandlingExtra";
	
	ServerConnection serverConnection;
	
	public UploadFileService() 
	{
	    super("uploadFileService");
	    serverConnection = new ServerConnection(this);
	}
	 
	@Override
	protected void onHandleIntent(Intent intent) 
	{
		Intent startIntent = new Intent(TAGUploadFileServiceBroadcast);
		startIntent.putExtra(TAGUploadFileServiceHandlingExtra, true);
		LocalBroadcastManager.getInstance(this).sendBroadcast(startIntent);
		
		serverConnection.scanFolderForUpload(intent.getStringExtra("folder"));
//		serverConnection.doFileUploadsBlocking();
		
		Intent endIntent = new Intent(TAGUploadFileServiceBroadcast);
		endIntent.putExtra(TAGUploadFileServiceHandlingExtra, false);
		LocalBroadcastManager.getInstance(this).sendBroadcast(endIntent);
	}
}
