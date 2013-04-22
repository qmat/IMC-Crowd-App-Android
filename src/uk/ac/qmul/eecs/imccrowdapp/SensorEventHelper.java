package uk.ac.qmul.eecs.imccrowdapp;

import java.util.Locale;

import android.hardware.Sensor;
import android.hardware.SensorEvent;

public class SensorEventHelper {
	static final String toLogFileEntry(SensorEvent event)
	{
		String logFileEntry;
		
		switch (event.sensor.getType())
        {
            case Sensor.TYPE_ACCELEROMETER:
            	logFileEntry = String.format(Locale.US, "t: %d, acc: %f, %f, %f", event.timestamp, event.values[0], event.values[1], event.values[2]);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
            	logFileEntry = String.format(Locale.US, "t: %d, mag: %f, %f, %f", event.timestamp, event.values[0], event.values[1], event.values[2]);
                break;
            case Sensor.TYPE_GYROSCOPE:
            	logFileEntry = String.format(Locale.US, "t: %d, gyr: %f, %f, %f", event.timestamp, event.values[0], event.values[1], event.values[2]);
                break;
            case Sensor.TYPE_LIGHT:
            	logFileEntry = String.format(Locale.US, "t: %d, lig: %f", event.timestamp, event.values[0]);
                break;
            case Sensor.TYPE_PRESSURE:
            	logFileEntry = String.format(Locale.US, "t: %d, pre: %f", event.timestamp, event.values[0]);
                break;
            case Sensor.TYPE_PROXIMITY:
            	logFileEntry = String.format(Locale.US, "t: %d, pro: %f", event.timestamp, event.values[0]);
                break;
            case Sensor.TYPE_GRAVITY:
            	logFileEntry = String.format(Locale.US, "t: %d, gra: %f, %f, %f", event.timestamp, event.values[0], event.values[1], event.values[2]);
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
            	logFileEntry = String.format(Locale.US, "t: %d, lin: %f, %f, %f", event.timestamp, event.values[0], event.values[1], event.values[2]);
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
            	logFileEntry = String.format(Locale.US, "t: %d, rot: %f, %f, %f", event.timestamp, event.values[0], event.values[1], event.values[2]);
                break;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
            	logFileEntry = String.format(Locale.US, "t: %d, hum: %f", event.timestamp, event.values[0]);
                break;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
            	logFileEntry = String.format(Locale.US, "t: %d, tem: %f", event.timestamp, event.values[0]);
                break;
            default:
            	logFileEntry = "SensorEventFormatter: Sensor type unknown";
        }
		
		return logFileEntry;
	}
	
	static final boolean isNew(SensorEvent event)
	{
		int type = event.sensor.getType();
		
		boolean isNew = event.timestamp > lastTimestamp[type];
		
		lastTimestamp[type] = event.timestamp;
		
		return isNew;
	}
	
	private static long[] lastTimestamp = new long[20]; // This will break. There isn't a max int for type specified. Currently its 13, TYPE_AMBIENT_TEMPERATURE
}
