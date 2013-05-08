package uk.ac.qmul.eecs.imccrowdapp;

import java.util.List;
import java.util.Locale;

import android.net.wifi.ScanResult;

public class ScanResultHelper {
	static final String toJSONString(List<ScanResult> scanResultList)
	{
		StringBuilder jsonString = new StringBuilder();
		Separator separator = new Separator();
		
		jsonString.append(String.format(Locale.US, "{\"t\":%d, \"wfi\":[", System.currentTimeMillis()*1000000));
		for (ScanResult scanResult : scanResultList)
		{
			jsonString.append(separator.s());
			jsonString.append(String.format(Locale.US, "{\"%s\":%d}", scanResult.BSSID, scanResult.level));
		}
		jsonString.append("]}");
		
		return jsonString.toString();
	}
}

class Separator
{
	private int counter;
	private String sepString;
	Separator() {counter = 0; sepString = ", ";}
	
	String s()
	{
		return counter++ > 0 ? sepString : "";
	}
}