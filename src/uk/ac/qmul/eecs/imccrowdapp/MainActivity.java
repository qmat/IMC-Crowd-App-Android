package uk.ac.qmul.eecs.imccrowdapp;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends Activity {

	private final static String TAG = "IMCCrowdApp";
	// Note on logging. Android sucks. Most accurate and concise: http://ogre.ikratko.com/archives/993
	// To debug, you need to set the level on your development machine using "adb shell setprop log.tag.<YOUR_LOG_TAG> <LEVEL>" for all tags you want
	// ie. in the terminal
	// "/Path To Android that might have spaces in it so we're in quotes here/adt-bundle-mac-x86_64-20130219/sdk/platform-tools/adb" shell setprop log.tag.IMCCrowdApp VERBOSE
	// To release, these Log calls will need to be qualified with "if (BuildConfig.DEBUG) Log..."
	
	ToggleButton crowdNodeServiceToggleButton;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		crowdNodeServiceToggleButton = (ToggleButton) findViewById(R.id.crowdNodeServiceToggleButton);
		
		// TASK: Start our node of the (assumed) crowd (the user is among)
		// Only doing this on first launch, afterwards toggle should set state.
		if (!CrowdNodeService.hasInstanceEverBeenCreated) 
		{
			startService(new Intent(this, CrowdNodeService.class));
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public void onStart() {
		super.onStart();
		
		// TASK: Set Toggle state
		
		// Set state now we're starting
		crowdNodeServiceToggleButton.setChecked(CrowdNodeService.isInstanceCreated());
		
		// Keep in sync from hereon out
		LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
		localBroadcastManager.registerReceiver(onCrowdNodeServiceStatusChange, new IntentFilter(CrowdNodeService.TAGServiceStatusBroadcast));
		localBroadcastManager.registerReceiver(onDataLogFileWritten, new IntentFilter(DataLogger.TAGLogFileWrittenBroadcast));
		
		// TASK: Set upload state
		scanLogFolders();
		localBroadcastManager.registerReceiver(onUploadFileServiceBroadcast, new IntentFilter(UploadFileService.TAGUploadFileServiceBroadcast));
	}
	
	@Override
	public void onStop() {
		LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
		localBroadcastManager.unregisterReceiver(onCrowdNodeServiceStatusChange);
		
		super.onStop();
	}
	
	// Toggle button declared in activity_main.xml has android:onClick="onCrowdNodeServiceToggleClicked"
	public void onCrowdNodeServiceToggleClicked(View view) {
	    // Is the toggle on?
	    boolean on = ((ToggleButton) view).isChecked();
	    
	    Button uploadLogsButton = (Button) findViewById(R.id.uploadLogsButton);
	    
	    // TASK: Set service state from button action
	    if (on) {
	    	startService(new Intent(this, CrowdNodeService.class));
	    	
	    	uploadLogsButton.setEnabled(false);
	    } else {
	    	stopService(new Intent(this, CrowdNodeService.class));
	    	
	    	// See if there are still uploaded logs
	    	if (scanLogFolders() > 0)
	    	{
	    		uploadLogsButton.setEnabled(true);
	    	}
	    }
	}
	
	public void onUploadLogsButtonClicked(View view) {
	    
	    // TASK: Find data directory as per CrowdNodeService.
	    
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
		
		// TASK: Get subfolders, which should correspond to CrowdNodeService sessions
		String[] sessionFolderNames = filesDir.list();
		
		for (String sessionFolderName : sessionFolderNames)
		{
			String folderPath = filesDir.getPath() + File.separator + sessionFolderName;
			Log.d(TAG, "sessionPath " + folderPath);
			
			Intent folderToUploadIntent = new Intent(this, UploadFileService.class);
			folderToUploadIntent.putExtra("folder", folderPath);
			
			startService(folderToUploadIntent);
		}
		
	}
	
	
	private BroadcastReceiver onCrowdNodeServiceStatusChange = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent)
	    {
	    	crowdNodeServiceToggleButton.setChecked(intent.getBooleanExtra(CrowdNodeService.TAGServiceStatusExtraActive, false));
	    }
    };
    
	private BroadcastReceiver onDataLogFileWritten = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent)
	    {
	    	// Update log file upload section if CNS is toggled on (file can still be writing in background when button turned off)
	    	ToggleButton crowdNodeServiceToggleButton = (ToggleButton) findViewById(R.id.crowdNodeServiceToggleButton);
	    	if (crowdNodeServiceToggleButton.isChecked())
	    	{
		    	Calendar now = Calendar.getInstance();
		    	TextView infoText = (TextView) findViewById(R.id.uploadLogsInfoTextView);
	
		    	infoText.setText(String.format(Locale.US, "Log written at %02d:%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND)));
	    	}
	    }
    };
    
	private BroadcastReceiver onUploadFileServiceBroadcast = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent)
	    {
	    		ProgressBar uploadLogsProgressBar = (ProgressBar) findViewById(R.id.uploadLogsProgressBar);
	    		
	    		int visibility = intent.getBooleanExtra(UploadFileService.TAGUploadFileServiceHandlingExtra, false) ? View.VISIBLE : View.INVISIBLE;
	    		
	    		uploadLogsProgressBar.setVisibility(visibility);
	    }
    };
    
    private int scanLogFolders()
    {
    	
		
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
		
		// TASK: Get subfolders, which should correspond to CrowdNodeService sessions
		File[] sessionFolders = filesDir.listFiles();
		
		int sessionFolderCount = sessionFolders.length;
		int sessionFileTotal = 0;
		
		for (File file : sessionFolders)
		{
			sessionFileTotal += file.list().length;
		}
		
		TextView infoText = (TextView) findViewById(R.id.uploadLogsInfoTextView);
		infoText.setText(sessionFileTotal + " logs in " + sessionFolderCount + " sessions");
		
		Log.d(TAG, "sessionFolderCount: " + sessionFolderCount + " sessionFileTotal: " + sessionFileTotal);
		
		return sessionFileTotal;
    }
}