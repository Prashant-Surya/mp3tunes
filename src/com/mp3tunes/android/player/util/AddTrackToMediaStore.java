package com.mp3tunes.android.player.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

import com.binaryelysium.mp3tunes.api.HttpClientCaller;
import com.binaryelysium.mp3tunes.api.Locker;
import com.binaryelysium.mp3tunes.api.Track;
import com.binaryelysium.mp3tunes.api.HttpClientCaller.CreateStreamCallback;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.activity.Player;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.RemoteViews;

public class AddTrackToMediaStore extends AsyncTask<Void, Void, Boolean>
{
    Track           mTrack;
    Context         mContext;
    boolean         mScanning;
    boolean         mResult;
    String          mFileName;
    String          mFilePath;
    
    MediaScannerConnection mConnection;
    
    private static final int NOTIFY_ID = 10911252; // mp3 + 1 in ascii

    class Progress implements HttpClientCaller.Progress
    {
        int mProgress = 0;
        
        public void run(long progress, long total)
        {
            int p = (int)((progress * 100) / total);
            if (mProgress == p) return;
            mProgress = p;
            sendStartedNotification(mTrack, true, p, 100);
        }
        
    }
    
    public AddTrackToMediaStore(Track track, Context context)
    {
        mTrack   = track;
        mContext = context;
    }
    
    @Override
    protected Boolean doInBackground(Void... params)
    {   
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Log.w("Mp3Tunes", "No external storage mounted. state: " + Environment.getExternalStorageState());
            return false;
        }
        Log.w("Mp3Tunes", "Begining get track");
        String fileKey = mTrack.getFileKey();
        if (fileKey == null) return false;
        
        sendStartedNotification(mTrack, true, 0, 0);
        Log.w("Mp3Tunes", "File key: " + fileKey);
        
        Locker l = new Locker();
        try {
            mConnection = new MediaScannerConnection(mContext, mClient);
            mConnection.connect();
            
            Log.w("Mp3Tunes", "File key: " + fileKey);
            if (!l.getTrack(fileKey, mStreamCallback, new Progress())) {
                Log.w("Mp3Tunes", "Failed to download file");
            }
            
            Log.w("Mp3Tunes", "File written");
            while (!mConnection.isConnected()) {}
            Log.w("Mp3Tunes", "Begining scan");
            mScanning = true;
            mConnection.scanFile(mFilePath, null);
            while (mScanning) {}
            Log.w("Mp3Tunes", "Scanning Done");
            sendFinishedNotification(mTrack, mResult);
            return mResult;
        } catch (Exception e) {
            e.printStackTrace();
        }
        sendFinishedNotification(mTrack, false);
        return false;
    }

    private void sendFinishedNotification(Track t, boolean status)
    {
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager nm = (NotificationManager)mContext.getSystemService(ns);
        
        int icon = R.drawable.down2;
        long when = System.currentTimeMillis();
        CharSequence tickerText;
        
        if (status)
            tickerText = t.getTitle() + " added to phone";
        else
            tickerText = "Failed to add " + t.getTitle() + " to phone";
        
        Notification notification = new Notification(icon, tickerText, when);
        Intent        intent        = new Intent(mContext, Player.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        notification.setLatestEventInfo(mContext, "Mp3Tunes", tickerText, contentIntent);
        
        nm.notify(NOTIFY_ID, notification);
        nm.cancel(NOTIFY_ID);
    }
    
    private void sendStartedNotification(Track t, boolean status, int progress, int total)
    {
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager nm = (NotificationManager)mContext.getSystemService(ns);
        
        int icon = R.drawable.down2;
        long when = System.currentTimeMillis();
        CharSequence tickerText;
        
        if (status)
            tickerText = "Adding " + t.getTitle() + " to phone";
        else 
            tickerText = t.getTitle() + " is already on your phone";
        
        Notification notification = new Notification(icon, tickerText, when);
        RemoteViews contentView   = new RemoteViews(mContext.getPackageName(), R.layout.progress_notification_view);
        contentView.setImageViewResource(R.id.notification_image, R.drawable.logo_statusbar);
        contentView.setTextViewText(R.id.notification_text, "Downloading " + mTrack.getTitle());
        contentView.setProgressBar(R.id.notification_progress_bar, total, progress, (total == 0 && progress == 0));
        notification.contentView = contentView;
        Intent        intent        = new Intent(mContext, Player.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        //notification.setLatestEventInfo(mContext, "Mp3Tunes", tickerText, contentIntent);
        notification.contentIntent = contentIntent;
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        nm.notify(NOTIFY_ID, notification);
        if (!status) nm.cancel(NOTIFY_ID);
    }
    
    static public String getTrackUrl(Track track, Context context)
    {
        ContentResolver cr = context.getContentResolver();
        Uri media  = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String where = MediaStore.Audio.Media.ARTIST + "=? AND " +
                       MediaStore.Audio.Media.ALBUM  + "=? AND " +
                       MediaStore.Audio.Media.TITLE  + "=?";
           
        String[] whereArgs  = new String[] {track.getArtistName(), track.getAlbumTitle(), track.getTitle()};
        String[] projection = new String[] {MediaStore.Audio.Media.DATA};
            
        Cursor cursor = cr.query(media, projection, where, whereArgs, null);
        if (cursor.moveToFirst()) {
            String url = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
            cursor.close();
            return url;
        }
        cursor.close();
        return null;
    }
    
    static public boolean isInStore(Track track, Context context)
    {
        ContentResolver cr = context.getContentResolver();
        Uri media  = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String where = MediaStore.Audio.Media.ARTIST + "=? AND " +
                       MediaStore.Audio.Media.ALBUM  + "=? AND " +
                       MediaStore.Audio.Media.TITLE  + "=?";
     
        String[] whereArgs  = new String[] {track.getArtistName(), track.getAlbumTitle(), track.getTitle()};
        Cursor cursor;
        cursor = cr.query(media, null, where, whereArgs, null);
        if (cursor.getCount() > 0) {
            cursor.close();
            return true;
        }
        cursor.close();
        
        return false;
    }
    
    MediaScannerConnection.MediaScannerConnectionClient mClient = 
        new MediaScannerConnection.MediaScannerConnectionClient()
    {

        public void onMediaScannerConnected()
        {
            Log.w("Mp3Tunes", "Connected");
            //mConnection.scanFile(mFileName, null);
        }

        public void onScanCompleted(String path, Uri uri)
        {
            if (uri != null) {
                mResult = true;
                Log.w("Mp3Tunes", "Scan Successful: uri: " + uri.toString());
            } else {
                Log.w("Mp3Tunes", "Scan Failed");
                mResult = false;
            }
            mScanning = false;
        }
        
    };
    
    private File makeMp3TunesDir()
    {
        File storageDir = Environment.getExternalStorageDirectory();
        Log.w("Mp3Tunes", "External Storage dir: " + storageDir.getAbsolutePath());
        if (storageDir.isDirectory()) {
            File mp3tunesDir = new File(storageDir, "mp3tunes/music");
            Log.w("Mp3Tunes", "mp3tunes dir: " + mp3tunesDir.getAbsolutePath());
            if (mp3tunesDir.isDirectory()) {
                Log.w("Mp3Tunes", "mp3tunes dir exists");
                return mp3tunesDir;
            } else {
                Log.w("Mp3Tunes", "making mp3tunes dir");
                if (mp3tunesDir.mkdirs()) {
                    return mp3tunesDir;
                }
            }
        }
        Log.w("Mp3Tunes", "Make Mp3Tunes directory failed");
        return null;
    }
    
    CreateStreamCallback mStreamCallback = new CreateStreamCallback()
    {
        public OutputStream createStream()
        {
            try {
                File mp3tunesDir = makeMp3TunesDir();
                if (mp3tunesDir != null) {
                    File outputFile = new File(mp3tunesDir, mFileName);
                    OutputStream s = new FileOutputStream(outputFile);
                    mFilePath = outputFile.getAbsolutePath();
                    return s;
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }

        public void handleContentType(String contentType)
        {
            mFileName = (mTrack.getArtistName() + "-" + mTrack.getTitle()).replaceAll(" ", "_");
            mFileName = mFileName.replace("/", "_slash_").replace(".", "_dot_");
            Log.w("Mp3Tunes", "File name: " + mFileName);
            if (contentType.equals("audio/mpeg")) {
                mFileName += ".mp3";
            } else if (contentType.equals("audio/mp4")) {
                mFileName += ".mp4";
            } else if (contentType.equals("audio/ogg")) {
                mFileName += ".ogg";
            } else if (contentType.equals("audio/vorbis")) {
                mFileName += ".ogg";
            } else if (contentType.equals("application/ogg")) {
                mFileName += ".ogg";
            } else if (contentType.equals("audio/x-ms-wma")) {
                mFileName += ".wma";
            } else if (contentType.equals("video/quicktime")) {
                Log.w("Mp3Tunes", "inserting a video file video/quicktime");
                mFileName += ".mp4";
            } else if (contentType.equals("video/mp4")) {
                Log.w("Mp3Tunes", "inserting a video file video/mp4");
                mFileName += ".mp4";
            } else if (contentType.equals("video/x-ms-wmv")) {
                Log.w("Mp3Tunes", "inserting a video file video/x-ms-wmv");
                mFileName += ".wmv";
            }
            		
        }
        
    };
    
}
