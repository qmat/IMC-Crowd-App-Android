package uk.ac.qmul.eecs.imccrowdapp;

import java.util.Locale;

import android.location.Location;

public class LocationHelper {
	static final String toJSONString(Location location)
	{
		return String.format(Locale.US, "{\"t\":%d, \"loc\":[%f,%f,%f,%f]}", location.getTime()*1000000 , location.getLongitude(), location.getLatitude(), location.getAltitude(), location.getAccuracy());
	}
}