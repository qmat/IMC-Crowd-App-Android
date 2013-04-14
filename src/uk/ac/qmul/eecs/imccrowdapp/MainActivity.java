package uk.ac.qmul.eecs.imccrowdapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
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
		
		// TASK: Set service button state
		// FIXME: This is often incorrect on first launch as service is still starting. Need a broadcast to tell the app?
		crowdNodeServiceToggleButton.setChecked(CrowdNodeService.isInstanceCreated());
	}
	
	public void onCrowdNodeServiceToggleClicked(View view) {
	    // Is the toggle on?
	    boolean on = ((ToggleButton) view).isChecked();
	    
	    if (on) {
	    	startService(new Intent(this, CrowdNodeService.class));
	    } else {
	    	stopService(new Intent(this, CrowdNodeService.class));
	    }
	}
}