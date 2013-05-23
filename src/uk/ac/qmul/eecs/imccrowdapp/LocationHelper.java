package uk.ac.qmul.eecs.imccrowdapp;

import java.util.Locale;

import android.location.Location;
import android.location.LocationManager;

public class LocationHelper {
	static final String toJSONString(Location location)
	{
		String provider = location.getProvider().equals(LocationManager.GPS_PROVIDER) ? "gps" : "loc";
		return String.format(Locale.US, "{\"t\":%d, \"%s\":[%f,%f,%f,%f]}", location.getTime()*1000000 , provider, location.getLongitude(), location.getLatitude(), location.getAltitude(), location.getAccuracy());
	}
}