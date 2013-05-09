package uk.ac.qmul.eecs.imccrowdapp;

import java.util.Locale;

import android.location.Location;

public class LocationHelper {
	static final String toJSONString(Location location)
	{
		StringBuilder jsonString = new StringBuilder();
		
		// Note: getElapsedRealtimeNanos doesn't match sensorEvent timestamps
		jsonString.append(String.format(Locale.US, "{\"t\":%d, \"loc\":[%f,%f,%f,%f]}", location.getTime()*1000000 , location.getLongitude(), location.getLatitude(), location.getAltitude(), location.getAccuracy()));
		
		return jsonString.toString();
	}
}