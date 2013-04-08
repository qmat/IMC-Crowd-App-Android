package uk.ac.qmul.eecs.imccrowdapp;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.Menu;

public class MainActivity extends Activity {

	private final static String TAG = "IMCCrowdApp";
	
	private WakeLock wakeLock;
	private DataLoggingManager dataLoggingManager;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// TASK: Keep App running
		
		// PARTIAL_WAKE_LOCK will allow screen and keyboard to dim, but app/phone will still run
		// If you hold a partial wake lock, the CPU will continue to run, regardless of any display timeouts or the state of the screen and even after the user presses the power button. 
		PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wakeLock.acquire();
		
		// TASK: Start logging data
		dataLoggingManager = new DataLoggingManager(this.getApplicationContext());
		dataLoggingManager.start(100);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public void onStop() {
		dataLoggingManager.stop();
		wakeLock.release();
	}
}


class DataLoggingManager
{
	Context context;
	Timer timer;
	
	DataLoggingManager(Context inContext)
	{
		context = inContext;
	}
	
	class DataLogTask extends TimerTask
	{
		private static final String TAG = "DataLogTask";
		
		DataLogger dataLogger;
		ServerConnection serverConnection;
		
		DataLogTask()
		{
			dataLogger = new DataLogger();
			
			File filesDir = context.getExternalFilesDir("sensorData");
			if (filesDir == null)
			{
				Log.w(TAG, "Could not use external files dir, falling back to internal");
				filesDir = context.getFilesDir();
			}
			if (filesDir == null)
			{
				Log.e(TAG, "Could not use files dir.");
				// TODO: Alert dialog and quit?
			}
			dataLogger.setLogsFolder(filesDir.getPath());
			
			serverConnection = new ServerConnection();
//			serverConnection.setEndPointURL(""); // TODO: Pull string from strings file kept locally, not in public source control
//			serverConnection.startSession();
//			serverConnection.startFileUploads();
		}
		
		public void run() 
		{
			Log.v("DataLogTask", "Run called");
		}
	}
	
	public void start(int periodMicroSeconds)
	{
		if (timer != null) 
		{
			timer.cancel();
			timer = null;
		}
		
		timer = new Timer();
		timer.schedule(new DataLogTask(), 0, periodMicroSeconds);
	}
	
	public void stop()
	{
		timer.cancel();
		timer = null;
	}
}