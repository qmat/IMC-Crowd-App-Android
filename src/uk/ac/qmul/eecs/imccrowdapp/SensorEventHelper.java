package uk.ac.qmul.eecs.imccrowdapp;

import java.util.Locale;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.util.Log;

public class SensorEventHelper {
	static final String toJSONString(SensorEvent event)
	{
		String jsonString;
		
		switch (event.sensor.getType())
        {
            case Sensor.TYPE_ACCELEROMETER:
            	jsonString = String.format(Locale.US, "{\"t\":%d, \"acc\":[%f,%f,%f]}", event.timestamp, event.values[0], event.values[1], event.values[2]);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
            	jsonString = String.format(Locale.US, "{\"t\":%d, \"mag\":[%f,%f,%f]}", event.timestamp, event.values[0], event.values[1], event.values[2]);
                break;
            case Sensor.TYPE_GYROSCOPE:
            	jsonString = String.format(Locale.US, "{\"t\":%d, \"gyr\":[%f,%f,%f]}", event.timestamp, event.values[0], event.values[1], event.values[2]);
                break;
            case Sensor.TYPE_LIGHT:
            	jsonString = String.format(Locale.US, "{\"t\":%d, \"lig\":%f}", event.timestamp, event.values[0]);
                break;
            case Sensor.TYPE_PRESSURE:
            	jsonString = String.format(Locale.US, "{\"t\":%d, \"pre\":%f}", event.timestamp, event.values[0]);
                break;
            case Sensor.TYPE_PROXIMITY:
            	jsonString = String.format(Locale.US, "{\"t\":%d, \"pro\":%f}", event.timestamp, event.values[0]);
                break;
            case Sensor.TYPE_GRAVITY:
            	jsonString = String.format(Locale.US, "{\"t\":%d, \"gra\":[%f,%f,%f]}", event.timestamp, event.values[0], event.values[1], event.values[2]);
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
            	jsonString = String.format(Locale.US, "{\"t\":%d, \"lin\":[%f,%f,%f]}", event.timestamp, event.values[0], event.values[1], event.values[2]);
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
            	jsonString = String.format(Locale.US, "{\"t\":%d, \"rot\":[%f,%f,%f]}", event.timestamp, event.values[0], event.values[1], event.values[2]);
                break;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
            	jsonString = String.format(Locale.US, "{\"t\":%d, \"hum\":%f}", event.timestamp, event.values[0]);
                break;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
            	jsonString = String.format(Locale.US, "{\"t\":%d, \"tem\":%f}", event.timestamp, event.values[0]);
                break;
            default:
            	jsonString = "";
            	Log.w("SensorEventHelper", "Sensor type " + event.sensor.getType() + " (" + event.sensor.getName() + ") unknown");
        }
		
		return jsonString;
	}
	
	static final long microSecondsElapsedSinceLastCalledForType(SensorEvent event)
	{
		int type = event.sensor.getType();
		
		long elapsed = (event.timestamp - lastTimestamp[type]) / 1000;
		
		lastTimestamp[type] = event.timestamp;
		
		return elapsed;
	}
	
	private static long[] lastTimestamp = new long[20]; // This will break. There isn't a max int for type specified. Currently its 13, TYPE_AMBIENT_TEMPERATURE
}
