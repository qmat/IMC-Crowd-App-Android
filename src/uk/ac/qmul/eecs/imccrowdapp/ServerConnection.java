package uk.ac.qmul.eecs.imccrowdapp;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

class FileToUpload {
    String          path;
    String          folderName;
    String          fileName;
    int             uploadStartCount;
    java.util.Date	uploadStartTime;
    boolean         uploadInProgress;
}

class ServerConnection {
    // Handles all communication to the server
	
	
	private static final String TAG = "ServerConnection";
	
	// Local Broadcast Notifications
	static final String TAGNewSessionBroadcast = "NewSessionBroadcast";
	static final String TAGNewSessionExtraSessionID = "NewSessionExtraSessionID";
	static final String TAGNewSessionExtraSessionActive = "NewSessionExtraSessionActive";
	
	private static AsyncHttpClient httpClient = new AsyncHttpClient(); 
    private String endPointURL;
    private String sessionID;
    private boolean sessionActive;
    private List<FileToUpload> uploadQueue;
    private boolean shouldUpload;
    
    private Context context;
    
	ServerConnection(Context inContext)
	{
	    setEndPointURL("localhost:8888");
	    
	    setSessionID(null);
	    
	    shouldUpload = false;
	    
	    uploadQueue = new LinkedList<FileToUpload>();
	    
	    context = inContext;
	}
   
	void setEndPointURL(String inURL)
	{
		// Ensure no trailing slash
		if(inURL.charAt(inURL.length()-1)=='/')
		{
			inURL = inURL.substring(0, inURL.length()-2);
		}
		
		Log.v(TAG, "Setting end point URL to " + inURL);
		endPointURL = inURL;
	}
	
	boolean setSessionID(String inSessionID)
	{
	    // TASK: Assign new sessionID if tests valid.
		
		String newSessionID;
		boolean sessionIDChanged;
		
	    if (validateSessionID(inSessionID))
	    {
	    	Log.v(TAG, "New sessionID: " + inSessionID);
	        
	        newSessionID = inSessionID;
	    }
	    else
	    {
	    	newSessionID = "No Session";
	    }
	   
	    sessionIDChanged = !newSessionID.equals(sessionID);
	    
	    if (sessionIDChanged)
	    {
	    	sessionID = newSessionID;
	    	
		    Intent intent = new Intent(TAGNewSessionBroadcast);
			intent.putExtra(TAGNewSessionExtraSessionID, sessionID);
			intent.putExtra(TAGNewSessionExtraSessionActive, sessionActive);
			LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
	    }
	    
	    return sessionIDChanged;
	}
    
	boolean validateSessionID(String UUID)
	{
		// Test as per IMC Crowd Server
		// https://github.com/qmat/IMC-Crowd-Server/blob/master/requestHandlers.js
		
		boolean success = true;
		success = success && UUID != null;
		success = success && (UUID.length() == 32);
		success = success && (UUID.charAt(13) == 'x');
		
		Log.d(TAG, "validateSessionID for " + UUID + " tests " + success);
				
		return success;
	}
	
	void startSession()
	{
        Date nowDate = new java.util.Date();
        
        RequestParams params = new RequestParams();
        params.put("sessionID", sessionID);
        params.put("time", nowDate.toString());
                
        httpClient.post(urlStringWithPath("/registerID"), params, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(String responseString) {
                // For now, response body is plain text assigned sessionID
            	Log.v(TAG, "registerID succeeded with response: " + responseString);
                
            	// TASK: Set new session ID. 
            	// We have to set sessionActive first, as the change of sessionID fires the broadcast.
            	if (validateSessionID(responseString)) sessionActive = true;
            	setSessionID(responseString);
            }
        
            @Override
            public void onFailure(Throwable e, String responseString) {
            	Log.w(TAG, "registerID failed with response: " + responseString);
            	sessionActive = false;
            }
        });
	}
    
    
	void addFileForUpload(String filePath)
	{
	    File fileObject = new File(filePath);
	    
	    FileToUpload newFileToUpload = new FileToUpload();
	    newFileToUpload.path = filePath;
	    newFileToUpload.folderName = fileObject.getParentFile().getName();
	    newFileToUpload.fileName = fileObject.getName();;
	    newFileToUpload.uploadStartCount = 0;
	    newFileToUpload.uploadStartTime = null;
	    newFileToUpload.uploadInProgress = false;
	    
	    uploadQueue.add(newFileToUpload);
	    
	    if (shouldUpload) startFileUploadsAsync();
	}
	
	void scanFolderForUpload(String path)
	{
	    File directory = new File(path);
	    File[] files = directory.listFiles();
	    
	    // TASK: Add any new files in folder to upload queue
	    for(File file : files)
	    {
	        // Is this one already in the queue?
	    	Iterator<FileToUpload> iterator = uploadQueue.iterator();
	    	FileToUpload fileInQueue = null;
	        while ( iterator.hasNext() )
	        {
	        	fileInQueue = iterator.next();
	            if (fileInQueue.path.equals(file.getPath())) break;
	        	fileInQueue = null;
	        }
	        
	        // If we did not find the file in our queue, add it to the back
	        if (fileInQueue == null)
	        {
	            addFileForUpload(file.getPath());
	        }
	    }
	}
    
	void startFileUploadsAsync()
	{
	    // TASK: Start upload from front of queue. Upload of next happens on successful upload via responseHandler()
	    
	    shouldUpload = true;
	    
	    if (!uploadQueue.isEmpty())
	    {
	        // Should put some heuristics here to bypass files that are blocking upload
	        // Have startCount and startTime to consider
	        FileToUpload fileToUpload = uploadQueue.get(0);
	        if (!fileToUpload.uploadInProgress)
	        {
	            uploadFileAsync(fileToUpload);
	        }
	        else
	        {
	        	Log.w(TAG, "startFileUploads - uploadQueue has uploadInProgress");
	        }
	    }
	}
	
	void doFileUploadsBlocking()
	{
	    while (!uploadQueue.isEmpty())
	    {
	        FileToUpload fileToUpload = uploadQueue.get(0);
	        
	        if (!fileToUpload.uploadInProgress)
	        {
	        	uploadFileBlocking(fileToUpload);
	        }
	        else
	        {
	        	Log.w(TAG, "startFileUploads - uploadQueue has uploadInProgress");
	        }
	    }
	}
	
	private void uploadFileAsync(FileToUpload fileToUpload)
    {
        if (!sessionActive)
        {
        	Log.v(TAG, "Session not active, aborting uploadData");
            return;
        }
        
        Date nowDate = new java.util.Date();
        
        RequestParams params = new RequestParams();
        
        params.put("uploadSessionID", sessionID);
        params.put("time", nowDate.toString());
        params.put("folder", fileToUpload.folderName);
        
        File file = new File(fileToUpload.path);
        try {
			params.put("file", file);
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
                
        fileToUpload.uploadStartCount++;
        fileToUpload.uploadStartTime = nowDate;
        fileToUpload.uploadInProgress = true;
        
        httpClient.post(urlStringWithPath("/uploadData"), params, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(JSONObject responseJSON) {
                // TASK: Confirm upload with queue
                String logSessionID = "";
                String fileName = "";
                boolean success = false;
				try {
				    logSessionID = responseJSON.getString("logSessionID");
	                fileName = responseJSON.getString("fileName");
	                success = responseJSON.getBoolean("success");
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                
                Log.v(TAG, "Received upload response from sessionID: " + logSessionID + " : " + fileName + " with success " + success);
                
                Iterator<FileToUpload> iterator = uploadQueue.iterator();
                FileToUpload fileInQueue = null;
                while ( iterator.hasNext() )
                {
                	fileInQueue = iterator.next();
                    if (fileInQueue.fileName.equals(fileName) && fileInQueue.folderName.equals(logSessionID))
                    {
                        break;
                    }
                    fileInQueue = null;
                }

                if (fileInQueue != null)
                {
                    String uploadedFilePath = fileInQueue.path;
                    
                    // Remove from upload queue
                    uploadQueue.remove(fileInQueue);
                    
                    // Delete file
                    File file = new File(uploadedFilePath);
                    boolean deleteSuccess = file.delete();
                    if (!deleteSuccess) Log.w(TAG, "Failed to remove file: " + uploadedFilePath);
                    
                    // Go upload next in queue
                    if (shouldUpload) startFileUploadsAsync();
                }
                else
                {
                    Log.w(TAG, "Upload data returned an upload file confirmation not in queue");
                }
            }
        
            @Override
            public void onFailure(Throwable e, String response) {
            	Log.w(TAG, "uploadData failed with response: " + response);
            	
            	// FIXME: Update queue info, ie. uploading false, upload start time null.
            	// How to do this, as we dont't have reference any more.
            }
        });
    }
	
	private void uploadFileBlocking(FileToUpload fileToUpload)
    {
		Date nowDate = new java.util.Date();
		
		final String end = "\r\n";
		final String twoHyphens = "--";
		final String boundary = "*****++++++************++++++++++++";
		
		URL url = urlWithPath("/uploadData");
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection)url.openConnection();
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}

		conn.setDoInput(true);
		conn.setDoOutput(true);
		conn.setUseCaches(false);
		try {
			conn.setRequestMethod("POST");
		} catch (ProtocolException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}

		conn.setRequestProperty("Connection", "Keep-Alive");
		conn.setRequestProperty("Charset", "UTF-8");
		conn.setRequestProperty("Content-Type", "multipart/form-data;boundary="+ boundary);
		
		DataOutputStream ds = null;
		try {
			ds = new DataOutputStream(conn.getOutputStream());
			ds.writeBytes(twoHyphens + boundary + end);
			ds.writeBytes("Content-Disposition: form-data; name=\"uploadSessionID\""+end+end+sessionID+end);
			ds.writeBytes(twoHyphens + boundary + end);
			ds.writeBytes("Content-Disposition: form-data; name=\"time\""+end+end+nowDate.toString()+end);
			ds.writeBytes(twoHyphens + boundary + end);
			ds.writeBytes("Content-Disposition: form-data; name=\"folder\""+end+end+fileToUpload.folderName+end);
			ds.writeBytes(twoHyphens + boundary + end);
			ds.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\"" + fileToUpload.path +"\"" + end);
			ds.writeBytes(end);
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
                
        fileToUpload.uploadStartCount++;
        fileToUpload.uploadStartTime = nowDate;
        fileToUpload.uploadInProgress = true;
        
        FileInputStream fStream = null;
		try {
			fStream = new FileInputStream(fileToUpload.path);
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int length = -1;

        try {
			while((length = fStream.read(buffer)) != -1) {
			  ds.write(buffer, 0, length);
			}
	        ds.writeBytes(end);
	        ds.writeBytes(twoHyphens + boundary + twoHyphens + end);
	        /* close streams */
	        fStream.close();
	        ds.flush();
	        ds.close();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

        
        try {
			if(conn.getResponseCode() == HttpURLConnection.HTTP_OK)
			{
				StringBuffer b = new StringBuffer();
				InputStream is = conn.getInputStream();
				byte[] data = new byte[bufferSize];
				int leng = -1;
				while((leng = is.read(data)) != -1) {
				  b.append(new String(data, 0, leng));
				}
				String result = b.toString();
				
				Log.d(TAG, "HTTP_OK Result: " + result);
				
//            // TASK: Confirm upload with queue
//            String logSessionID = "";
//            String fileName = "";
//            boolean success = false;
//			try {
//			    logSessionID = responseJSON.getString("logSessionID");
//                fileName = responseJSON.getString("fileName");
//                success = responseJSON.getBoolean("success");
//			} catch (JSONException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//            
//            Log.v(TAG, "Received upload response from sessionID: " + logSessionID + " : " + fileName + " with success " + success);
//            
//            Iterator<FileToUpload> iterator = uploadQueue.iterator();
//            FileToUpload fileInQueue = null;
//            while ( iterator.hasNext() )
//            {
//            	fileInQueue = iterator.next();
//                if (fileInQueue.fileName.equals(fileName) && fileInQueue.folderName.equals(logSessionID))
//                {
//                    break;
//                }
//                fileInQueue = null;
//            }
//
//            if (fileInQueue != null)
//            {
//                String uploadedFilePath = fileInQueue.path;
//                
//                // Remove from upload queue
//                uploadQueue.remove(fileInQueue);
//                
//                // Delete file
//                File file = new File(uploadedFilePath);
//                boolean deleteSuccess = file.delete();
//                if (!deleteSuccess) Log.w(TAG, "Failed to remove file: " + uploadedFilePath);
//                
//                // Go upload next in queue
//                if (shouldUpload) startFileUploads();
//            }
//            else
//            {
//                Log.w(TAG, "Upload data returned an upload file confirmation not in queue");
//            }

			}
			else
			{
				String failureResponse = null;
				try {
					failureResponse = conn.getResponseMessage();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				Log.w(TAG, "uploadData failed with response: " + failureResponse);
				
				// FIXME: Update queue info, ie. uploading false, upload start time null.
				// How to do this, as we dont't have reference any more.
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
	
    private String urlStringWithPath(String inPath)
    {
		// Ensure leading slash
		if(inPath.charAt(0)!='/')
		{
			inPath = "/" + inPath;
		}
    	
    	return endPointURL + inPath;
    }
    
    private URL urlWithPath(String inPath)
    {
    	URL returnURL = null;
    	
		try {
			returnURL = new URL(urlStringWithPath(inPath));
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return returnURL;
    }
}
