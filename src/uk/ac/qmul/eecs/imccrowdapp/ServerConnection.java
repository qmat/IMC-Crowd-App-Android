package uk.ac.qmul.eecs.imccrowdapp;

import android.util.Log;

import java.util.Iterator;
import java.util.List;
import java.util.Date;
import java.io.File;
import java.io.FileNotFoundException;

import org.json.*;

import com.loopj.android.http.*; // http://loopj.com/android-async-http/

class FileToUpload {
    String          path;
    String          folderName;
    String          fileName;
    int             uploadStartCount;
    java.util.Date	uploadStartTime;
    boolean         uploadInProgress;
}

public class ServerConnection {
    
	private static final String TAG = "ServerConnection";
	
	private static AsyncHttpClient httpClient = new AsyncHttpClient(); 
    private String endPointURL;
    private String sessionID;
    private boolean sessionActive;
    private List<FileToUpload> uploadQueue;
    private boolean shouldUpload;
    
    private String URLWithPath(String inPath)
    {
    	if (inPath == "") 
    	{
    		inPath = "localhost:8888";
    	}
    	return endPointURL + inPath;
    }
    
    private void uploadFile(FileToUpload fileToUpload)
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
        
        httpClient.post(URLWithPath("/uploadData"), params, new JsonHttpResponseHandler() {
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
                    uploadQueue.remove(uploadedFilePath);
                    
                    // Delete file
                    File file = new File(uploadedFilePath);
                    boolean deleteSuccess = file.delete();
                    if (!deleteSuccess) Log.w(TAG, "Failed to remove file: " + uploadedFilePath);
                }
                else
                {
                    Log.w(TAG, "Upload data returned an upload file confirmation not in queue");
                }
            }
        
            @Override
            public void onFailure(Throwable e, String response) {
            	Log.w(TAG, "uploadData failed with response: " + response);
            }
        });
    }
	
	public ServerConnection()
	{
	    setEndPointURL("");
	    
	    sessionID = "";
	    sessionActive = false;
	    
	    shouldUpload = false;
	}
   
	public void setEndPointURL(String inURL)
	{
		// FIXME: strings made sense in oF, now in java should be using url.normalize etc?
		if(inURL.charAt(inURL.length()-1)!=File.separatorChar){
			inURL += File.separator;
		}
		
		endPointURL = inURL;
	}
	
	public void setSessionID(String inSessionID)
	{
	    // TASK: Assign new sessionID if tests valid.
	    
	    // For now...
	    if (inSessionID.length() > 0)
	    {
	    	Log.v(TAG, "New sessionID: " + inSessionID);
	        
	        sessionID = inSessionID;
	        sessionActive = true;
	        
	        //FIXME: ofNotifyEvent(onNewSessionID, inSessionID);
	    }
	    else
	    {
	    	Log.v(TAG, "new session ID not valid, ignoring");
	    }
	}
        
	public void startSession()
	{
        Date nowDate = new java.util.Date();
        
        RequestParams params = new RequestParams();
        params.put("sessionID", sessionID);
        params.put("time", nowDate.toString());
        
        httpClient.post(URLWithPath("registerID"), params, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(String responseString) {
                // For now, response body is plain text assigned sessionID
                setSessionID(responseString);
            }
        
            @Override
            public void onFailure(Throwable e, String response) {
            	Log.w(TAG, "registerID failed with response: " + response);
            }
        });
	}
	
	//public ofEvent<String> onNewSessionID;
    
	public void addFileForUpload(String filePath)
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
	    
	    if (shouldUpload) startFileUploads();
	}
	
	public void scanFolderForUpload(String path)
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
    
	public void startFileUploads()
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
	            uploadFile(fileToUpload);
	        }
	    }
	}
    

}
