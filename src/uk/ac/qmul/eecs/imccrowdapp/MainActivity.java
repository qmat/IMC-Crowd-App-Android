package uk.ac.qmul.eecs.imccrowdapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.Menu;

public class MainActivity extends Activity {

	private final static String TAG = "IMCCrowdApp";
	// Note on logging. Android sucks. Most accurate and concise: http://ogre.ikratko.com/archives/993
	// To debug, you need to set the level on your development machine using "adb shell setprop log.tag.<YOUR_LOG_TAG> <LEVEL>" for all tags you want
	// ie. in the terminal
	// "/Path To Android that might have spaces in it so we're in quotes here/adt-bundle-mac-x86_64-20130219/sdk/platform-tools/adb" shell setprop log.tag.IMCCrowdApp VERBOSE
	// To release, these Log calls will need to be qualified with "if (BuildConfig.DEBUG) Log..."
	
	private WakeLock wakeLock;
		
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// INFO: Stay alive strategy - Use PARTIAL_WAKE_LOCK and have our node of the crowd running as a service. 
		// The power switch needn't affect us, and we can choose whether to start/stop the service with the GUI, or have it run fully in the background with start/stop control via any instance of the app.
		
		// If you hold a partial wake lock, the CPU will continue to run, regardless of any display timeouts or the state of the screen and even after the user presses the power button. 
		PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
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
		
		// FIXME: As the activity is restarted on orientation change and stopped on screen dim, we need to start the service on first launch, if its not running already, and have a button to stop it. AFAIK there's no way to see the reason behind the onStop being called, to stop the service only on direct user intervention.
		
		// TASK: Don't let the phone sleep
		wakeLock.acquire();
		
		// TASK: Start our node of the (assumed) crowd (the user is among)
		startService(new Intent(this, CrowdNodeService.class));
	}
	
	@Override
	public void onStop() {
		
		stopService(new Intent(this, CrowdNodeService.class));
		
		wakeLock.release();
		
		super.onStop();
	}
}