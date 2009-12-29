/***************************************************************************
 *   Copyright (C) 2009  Casey Link <unnamedrambler@gmail.com>             *
 *   Copyright (C) 2007-2008 sibyl project http://code.google.com/p/sibyl/ *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 3 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, write to the                         *
 *   Free Software Foundation, Inc.,                                       *
 *   51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.         *
 ***************************************************************************/

package com.mp3tunes.android.player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import com.binaryelysium.mp3tunes.api.Album;
import com.binaryelysium.mp3tunes.api.Artist;
import com.binaryelysium.mp3tunes.api.Locker;
import com.binaryelysium.mp3tunes.api.Playlist;
import com.binaryelysium.mp3tunes.api.Token;
import com.binaryelysium.mp3tunes.api.Track;
import com.binaryelysium.mp3tunes.api.results.DataResult;
import com.binaryelysium.mp3tunes.api.results.SearchResult;
import com.mp3tunes.android.player.LockerCache;

/**
 * This class is essentially a wrapper for storing MP3tunes locker data in an
 * sqlite databse. It acts as a local cache of the metadata in a user's locker.
 * 
 * It is also used to handle the current playlist.
 * 
 */
public class LockerDb
{

    private LockerCache mCache;
    private Context mContext;
    private Locker mLocker;
    private SQLiteDatabase mDb;
    
    public static final String UNKNOWN_STRING = "Unknown";
    
    private SQLiteStatement mInsertArtistFromTrack;
    private SQLiteStatement mInsertAlbumFromTrack;
    private SQLiteStatement mInsertTrack;
    private SQLiteStatement mInsertArtist;
    private SQLiteStatement mInsertAlbum;
    private SQLiteStatement mInsertPlaylist;
    private SQLiteStatement mInsertToken;
    
    private SQLiteStatement mUpdateArtist;
    private SQLiteStatement mUpdateAlbum;
    private SQLiteStatement mUpdatePlaylist;
    private SQLiteStatement mUpdateToken;
    
    private SQLiteStatement mArtistExists;
    private SQLiteStatement mAlbumExists;
    private SQLiteStatement mTrackExists;
    private SQLiteStatement mPlaylistExists;
    private SQLiteStatement mTokenExists;
    
    public static final String KEY_ID             = "_id";
    public static final String KEY_PLAY_URL       = "play_url";
    public static final String KEY_DOWNLOAD_URL   = "download_url";
    public static final String KEY_TITLE          = "title";
    public static final String KEY_TRACK          = "track";
    public static final String KEY_ARTIST_ID      = "artist_id";
    public static final String KEY_ARTIST_NAME    = "artist_name";
    public static final String KEY_ALBUM_ID       = "album_id";
    public static final String KEY_ALBUM_NAME     = "album_name";
    public static final String KEY_TRACK_LENGTH   = "track_length";
    public static final String KEY_COVER_URL      = "cover_url";
    public static final String KEY_ALBUM_COUNT    = "album_count";
    public static final String KEY_TRACK_COUNT    = "track_count";
    public static final String KEY_YEAR           = "year";
    public static final String KEY_PLAYLIST_NAME  = "playlist_name";
    public static final String KEY_FILE_COUNT     = "file_count";
    public static final String KEY_FILE_NAME      = "file_name";
    public static final String KEY_PLAYLIST_ORDER = "playlist_order";
    public static final String KEY_POS            = "pos";
    public static final String KEY_TRACK_ID       = "track_id";
    public static final String KEY_PLAYLIST_ID    = "playlist_id";
    public static final String KEY_PLAYLIST_INDEX = "playlist_index";
    public static final String KEY_TYPE           = "type";
    public static final String KEY_TOKEN          = "token";
    public static final String KEY_COUNT          = "count";
    
    public static final String TABLE_TRACK            = "track";
    public static final String TABLE_ARTIST           = "artist";
    public static final String TABLE_ALBUM            = "album";
    public static final String TABLE_PLAYLIST         = "playlist";
    public static final String TABLE_PLAYLIST_TRACKS  = "playlist_tracks";
    public static final String TABLE_TOKEN            = "token";
    public static final String TABLE_CURRENT_PLAYLIST = "current_playlist";

    public LockerDb( Context context, Locker locker )
    {
        // Open the database
        mDb = (new LockerDbHelper(context, null)).getWritableDatabase();
        if (mDb == null)
        {
            throw new SQLiteDiskIOException("Error creating database");
        }
        
        mInsertArtistFromTrack = mDb.compileStatement("INSERT INTO artist   (_id, artist_name) VALUES (?, ?)");
        mInsertAlbumFromTrack  = mDb.compileStatement("INSERT INTO album    (_id, album_name, artist_id)  VALUES (?, ?, ?)");
        mInsertTrack           = mDb.compileStatement("INSERT INTO track    (_id, play_url, download_url, title, track, artist_name, album_name, artist_id, album_id, track_length, cover_url)  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
    	mInsertAlbum           = mDb.compileStatement("INSERT INTO album    (_id, album_name, artist_name, artist_id, year, track_count, cover_url)  VALUES (?, ?, ?, ?, ?, ?, ?)");
    	mInsertArtist          = mDb.compileStatement("INSERT INTO artist   (_id, artist_name, album_count, track_count) VALUES (?, ?, ?, ?)");
    	mInsertPlaylist        = mDb.compileStatement("INSERT INTO playlist (_id, playlist_name, file_count, file_name, playlist_order) VALUES (?, ?, ?, ?, ?)");
    	mInsertToken           = mDb.compileStatement("INSERT INTO token    (type, token, count) VALUES (?, ?, ?)");
    	
    	mUpdateArtist   = mDb.compileStatement("UPDATE artist   SET artist_name=?, album_count=?, track_count=? WHERE _id=?");
    	mUpdateAlbum    = mDb.compileStatement("UPDATE album    SET album_name=?, artist_name=?, artist_id=?, year=?, track_count=?, cover_url=? WHERE _id=?");
    	mUpdatePlaylist = mDb.compileStatement("UPDATE playlist SET playlist_name=?, file_count=?, file_name=?, playlist_order=? WHERE _id=?");
    	mUpdateToken    = mDb.compileStatement("UPDATE token    SET count=? WHERE token=? AND type=?");
    	
        mArtistExists   = mDb.compileStatement("SELECT " + KEY_ID + " FROM artist   WHERE " + KEY_ID + "=?");
        mAlbumExists    = mDb.compileStatement("SELECT " + KEY_ID + " FROM album    WHERE " + KEY_ID + "=?");
        mTrackExists    = mDb.compileStatement("SELECT " + KEY_ID + " FROM track    WHERE " + KEY_ID + "=?");
        mPlaylistExists = mDb.compileStatement("SELECT " + KEY_ID + " FROM playlist WHERE " + KEY_ID + "=?");
        mTokenExists    = mDb.compileStatement("SELECT " + KEY_COUNT + " FROM " + KEY_TOKEN + " WHERE " + KEY_TOKEN + "=? AND " + KEY_TYPE + "=?");

        mLocker = locker;
        mContext = context;
        mCache = LockerCache.loadCache( context, 86400000  ); // 1 day
    }

    public void close()
    {
        mCache.saveCache( mContext );
        if ( mDb != null )
            mDb.close();
    }

    public void clearDB()
    {
        mCache.clearCache();
        mDb.delete( TABLE_TRACK, null, null );
        mDb.delete( TABLE_ALBUM, null, null );
        mDb.delete( TABLE_ARTIST, null, null );
        mDb.delete( TABLE_PLAYLIST, null, null );
        mDb.delete( TABLE_PLAYLIST_TRACKS, null, null );
        mDb.delete( TABLE_TOKEN, null, null );
        mDb.delete( TABLE_CURRENT_PLAYLIST, null, null );
    }

    public Cursor getTableList( Music.Meta type )
    {
        try
        {
            switch ( type )
            {
            case TRACK:
                if ( !mCache.isCacheValid( LockerCache.TRACK ) ) { 
                    refreshTracks();
                    Cursor c = queryTracks();
                return c;
                }
            case ALBUM:
                if ( !mCache.isCacheValid( LockerCache.ALBUM ) )
                    refreshAlbums();
                return queryAlbums();
            case ARTIST:
                if ( !mCache.isCacheValid( LockerCache.ARTIST ) ) {
                    System.out.println("artist cache not valid refreshing");
                    refreshArtists();
                }
                return queryArtists();
            case PLAYLIST:
                if ( !mCache.isCacheValid( LockerCache.PLAYLIST ) )
                    refreshPlaylists();
                return queryPlaylists();
            default:
                return null;
            }
        }
        catch ( SQLiteException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch ( IOException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return null;
    }
    
    public Token[] getTokens( Music.Meta type )
    {
        try
        {
            Cursor c;
            switch ( type )
            {
            case TRACK:
                if ( !mCache.isCacheValid( LockerCache.TRACK_TOKENS ) )
                    refreshTokens( type );
                c = queryTokens("track");
                break;
            case ALBUM:
                if ( !mCache.isCacheValid( LockerCache.ALBUM_TOKENS ) )
                    refreshTokens( type );
                c = queryTokens("album");
                break;
            case ARTIST:
                if ( !mCache.isCacheValid( LockerCache.ARTIST_TOKENS ) )
                    refreshTokens( type );
                c = queryTokens("artist");
                break;
            default:
                return null;
            }
            Token[] t = new Token[c.getCount()];
            while( c.moveToNext() )
            {
                t[c.getPosition()] = new Token( c.getString( 1 ), c.getInt( 2 ) );
            }
            c.close();
            return t;
        }
        catch ( SQLiteException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch ( IOException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * 
     * @param artist_id
     * @return 0: _id 1: album_name
     */
    public Cursor getAlbumsForArtist( int artist_id )
    {
        System.out.println( "querying for albums by: " + artist_id );
        Cursor c = mDb.rawQuery( "SELECT artist_name FROM artist WHERE _id=" + artist_id, null );
        if ( !c.moveToNext() ) {
            //TODO fetch the artist?
            Log.e( "Mp3tunes", "Error artist doesnt exist" );
            return null;
        }
        c.close();
        
        c = queryAlbums( artist_id ); 
        
        if( c.getCount() > 0 )
            return c;
        else
            c.close();
        try
        {
            refreshAlbumsForArtist( artist_id );
            return queryAlbums( artist_id );  
        }
        catch ( SQLiteException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch ( IOException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 
     * @param album_id
     * @return 0: _id 1: title 2: artist_name 3:artist_id 4:album_name
     *         5:album_id 6:track 7:play_url 8:download_url 9:cover_url
     */
    public Cursor getTracksForAlbum( int album_id )
    {
        System.out.println( "querying for tracks on album: " + album_id );
        Cursor c = mDb.rawQuery( "SELECT album_name FROM album WHERE _id=" + album_id, null );
        if ( !c.moveToNext() ) {
            //TODO fetch the album?
            Log.e( "Mp3tunes", "Error album doesnt exist" );
            return null;
        }
        c.close();
        
        c = queryTracksAlbum( album_id );

        if( c.getCount() > 0 )
            return c;
        else
            c.close();

        try
        {
            refreshTracksforAlbum( album_id );
            return queryTracksAlbum( album_id );  
        }
        catch ( SQLiteException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch ( IOException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * 
     * @param artist_id
     * @return 0: _id 1: title 2: artist_name 3:artist_id 4:album_name
     *         5:album_id 6:track 7:play_url 8:download_url 9:cover_url
     */
    public Cursor getTracksForArtist( int artist_id )
    {
        System.out.println( "querying for tracks of artist: " + artist_id );
        Cursor c = mDb.rawQuery( "SELECT artist_name FROM artist WHERE _id=" + artist_id, null );
        if ( !c.moveToNext() ) {
            //TODO fetch the artist?
            Log.e( "Mp3tunes", "Error album doesnt exist" );
            return null;
        }
        c.close();
        
        c = queryTracksArtist( artist_id );

        if( c.getCount() > 0 )
            return c;
        else
            c.close();

        try
        {
            refreshTracksforArtist( artist_id );
            return queryTracksAlbum( artist_id );  
        }
        catch ( SQLiteException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch ( IOException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
    
    public Cursor getTracksForPlaylist( String playlist_id )
    {
        Cursor c = mDb.rawQuery( "SELECT playlist_name FROM playlist WHERE _id='" + playlist_id + "'", null );
        if ( !c.moveToNext() ) {
            //TODO fetch the playlist?
            Log.e( "Mp3tunes", "Error playlist doesnt exist" );
            return null;
        }
        c.close();
        c = queryPlaylists( playlist_id );
        if( c.getCount() > 0 )
            return c;
        else
            c.close();
        try
        {
            refreshTracksforPlaylist( playlist_id );
            return queryPlaylists( playlist_id );  
        }
        catch ( SQLiteException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch ( IOException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
    
    public void fetchArt( int album_id )
    {
        boolean cacheEnabled = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean( "cacheart", false );
        Bitmap ret = null;
        try {
        String cacheDir = Environment.getExternalStorageDirectory() + "/mp3tunes/art/";
        String ext = ".jpg";
        if( cacheEnabled )
        {
            File f = new File( cacheDir + album_id + ext);
            if( !f.exists() && !f.canRead() )
            {
                Track[] tracks = null;
                synchronized(mLocker)
                {
                    tracks = mLocker.getTracksForAlbum( Integer.valueOf(  album_id ) ).getData();
                }
                String artUrl = null;
                for( Track t : tracks)
                {
                    artUrl = t.getAlbumArt();
                    if( artUrl != null )
                        break;
                }
                ret  = getArtwork( artUrl );
                if( ret != null && cacheEnabled ) 
                {
                    f = new File( cacheDir);
                    f.mkdirs();
                    f = new File( cacheDir + album_id + ext);
                    f.createNewFile();
                    
                    if( f.canWrite() )
                    {
                        FileOutputStream out = new FileOutputStream(f);
                        ret.compress( Bitmap.CompressFormat.JPEG, 70, out );
                        out.close();
                    }
                }
            }
        }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public DbSearchResult search( DbSearchQuery query )
    {
        try
        {
            // First, determine which types we need to refresh.
            // this construct seems complicated (which it is..)
            // but it is faster than performing three separate http calls.
            // This way we can lump the refreshes into one http call

            boolean artist = false, track = false ,album = false;
            if(query.mTracks && !mCache.isCacheValid( LockerCache.TRACK ) )
                track = true;
            if(query.mAlbums && !mCache.isCacheValid( LockerCache.ALBUM ) )
                album = true;
            if(query.mArtists && !mCache.isCacheValid( LockerCache.ARTIST ) )
                artist = true;
            
            // Perform the single http search call
            refreshSearch( query.mQuery, artist, album, track );
//            return querySearch( query.mQuery );
            DbSearchResult res = new DbSearchResult();
            if( query.mTracks )
                res.mTracks = querySearch( query.mQuery, Music.Meta.TRACK );
            if( query.mAlbums )
                res.mAlbums = querySearch( query.mQuery, Music.Meta.ALBUM );
            if( query.mArtists )
                res.mArtists = querySearch( query.mQuery, Music.Meta.ARTIST );
            
            System.out.println("Got artists: " + res.mArtists.getCount());
            System.out.println("Got tracks: " + res.mTracks.getCount());
            return res;
        }
        catch ( SQLiteException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch ( IOException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    
        
        
        return null;
    }
    
    /**
     * Inserts a track into the database cache
     * @param track
     * @throws IOException
     * @throws SQLiteException
     */
    private void insertTrack( Track track ) throws IOException, SQLiteException
    {
    
        if ( track == null )
        {
            System.out.println( "OMG TRACK NULL" );
            return;
        }
        mDb.execSQL( "BEGIN TRANSACTION" );
        
        
        try
        {
        	//cache some values
    		int    trackId     = track.getId();
    		int    number      = track.getNumber();
    		int    artistId    = track.getArtistId();
    		int    albumId     = track.getAlbumId();
    		Double duration    = track.getDuration();
    		String playUrl     = track.getPlayUrl();
    		String downloadUrl = track.getDownloadUrl();
    		String title       = track.getTitle();
    		String artistName  = track.getArtistName();
    		String albumTitle  = track.getAlbumTitle();
    		String albumArt    = track.getAlbumArt();
    		//Insert artist info to the artist table
    		if ( artistName.length() > 0 )
    		{
    			try {
    				mArtistExists.bindLong(1, artistId);
    				mArtistExists.simpleQueryForLong();
    			} catch (SQLiteDoneException e) {
    				mInsertArtistFromTrack.bindLong(1, artistId);
            		mInsertArtistFromTrack.bindString(2, artistName);
            		mInsertArtistFromTrack.execute();
    			}
        	}

    		//Insert album info to the album table
        	if ( albumTitle.length() > 0 )
        	{
        		try {
        			mAlbumExists.bindLong(1, albumId);
        			mAlbumExists.simpleQueryForLong();
        		} catch (SQLiteDoneException e) {
        			mInsertAlbumFromTrack.bindLong(1,   albumId);
        			mInsertAlbumFromTrack.bindString(2, albumTitle);
        			mInsertAlbumFromTrack.bindLong(3,   artistId);
        			mInsertAlbumFromTrack.execute();
        		}
        	}

        	//Insert track info
        	try {
        		mTrackExists.bindLong(1, trackId);
    			mTrackExists.simpleQueryForLong();
        	} catch (SQLiteDoneException e) {
        		mInsertTrack.bindLong(   1, trackId);
        		mInsertTrack.bindString( 2, playUrl);
        		mInsertTrack.bindString( 3, downloadUrl);
        		mInsertTrack.bindString( 4, title);
        		mInsertTrack.bindLong(   5, number);
        		mInsertTrack.bindString( 6, artistName);
        		mInsertTrack.bindString( 7, albumTitle);
        		mInsertTrack.bindLong(   8, artistId);
        		mInsertTrack.bindLong(   9, albumId);
        		mInsertTrack.bindDouble(10, duration);
        		if (albumArt != null)
        			mInsertTrack.bindString(11, albumArt);
        		else
        			mInsertTrack.bindString(11, UNKNOWN_STRING);
        		mInsertTrack.execute();
        	}
        	
        	mDb.execSQL( "COMMIT TRANSACTION" );
        }
        catch ( SQLiteException e )
        {
            mDb.execSQL( "ROLLBACK" );
            throw e;
        }
    }

    /*This function exists because it is quite slow to try to insert tracks one by one
     *for a big locker.  This function removes the transaction overhead as well as the 
     *function call overhead
     */
    private void insertTracks( DataResult<Track> tracks ) throws IOException, SQLiteException
    {
        if ( tracks == null )
        {
            System.out.println( "OMG TRACKS NULL" );
            return;
        }
        mDb.execSQL( "BEGIN TRANSACTION" );
        try
        {        	
        	for (Track track : tracks.getData()) {
        		//cache some values
        		int    trackId     = track.getId();
        		int    number      = track.getNumber();
        		int    artistId    = track.getArtistId();
        		int    albumId     = track.getAlbumId();
        		Double duration    = track.getDuration();
        		String playUrl     = track.getPlayUrl();
        		String downloadUrl = track.getDownloadUrl();
        		String title       = track.getTitle();
        		String artistName  = track.getArtistName();
        		String albumTitle  = track.getAlbumTitle();
        		String albumArt    = track.getAlbumArt();
        		
        		//Insert artist info to the artist table
        		if ( artistName.length() > 0 )
        		{
        			try {
        				mArtistExists.bindLong(1, artistId);
        				mArtistExists.simpleQueryForLong();
        			} catch (SQLiteDoneException e) {
        				mInsertArtistFromTrack.bindLong(1, artistId);
                		mInsertArtistFromTrack.bindString(2, artistName);
                		mInsertArtistFromTrack.execute();
        			}
            	}

        		//Insert album info to the album table
            	if ( albumTitle.length() > 0 )
            	{
            		try {
            			mAlbumExists.bindLong(1, albumId);
            			mAlbumExists.simpleQueryForLong();
            		} catch (SQLiteDoneException e) {
            			mInsertAlbumFromTrack.bindLong(1,   albumId);
            			mInsertAlbumFromTrack.bindString(2, albumTitle);
            			mInsertAlbumFromTrack.bindLong(3,   artistId);
            			mInsertAlbumFromTrack.execute();
            		}
            	}

            	//Insert track info
            	try {
            		mTrackExists.bindLong(1, trackId);
        			mTrackExists.simpleQueryForLong();
            	} catch (SQLiteDoneException e) {
            		mInsertTrack.bindLong(   1, trackId);
            		mInsertTrack.bindString( 2, playUrl);
            		mInsertTrack.bindString( 3, downloadUrl);
            		mInsertTrack.bindString( 4, title);
            		mInsertTrack.bindLong(   5, number);
            		mInsertTrack.bindString( 6, artistName);
            		mInsertTrack.bindString( 7, albumTitle);
            		mInsertTrack.bindLong(   8, artistId);
            		mInsertTrack.bindLong(   9, albumId);
            		mInsertTrack.bindDouble(10, duration);
            		if (albumArt != null)
            			mInsertTrack.bindString(11, albumArt);
            		else
            			mInsertTrack.bindString(11, UNKNOWN_STRING);
            		mInsertTrack.execute();
            	}
    
        	}
            mDb.execSQL( "COMMIT TRANSACTION" );
    
        }
        catch ( SQLiteException e )
        {
            mDb.execSQL( "ROLLBACK" );
            throw e;
        }
    }
    
    /**
     * 
     * @param artist
     * @throws IOException
     * @throws SQLiteException
     */
    private void insertArtist( Artist artist) throws IOException, SQLiteException
    {
        if ( artist == null ) {
            System.out.println( "OMG Artist NULL" );
            return;
        }
        try {
            if ( artist.getName().length() > 0 ) {
            	try {
            		mArtistExists.bindLong(1, artist.getId());
            		mArtistExists.simpleQueryForLong();
            		
            		mUpdateArtist.bindString(1, artist.getName());
            		mUpdateArtist.bindLong(2,   artist.getAlbumCount());
            		mUpdateArtist.bindLong(3,   artist.getTrackCount());
            		mUpdateArtist.bindLong(4,   artist.getId());
            		mUpdateArtist.execute();
            	} catch (SQLiteDoneException e) {
            		mInsertArtist.bindLong(1,   artist.getId());
            		mInsertArtist.bindString(2, artist.getName());
            		mInsertArtist.bindLong(3,   artist.getAlbumCount());
            		mInsertArtist.bindLong(4,   artist.getTrackCount());
            		mInsertArtist.execute();
            	}
            }
        } catch ( SQLiteException e ) {
            throw e;
        }
    }

    private void insertAlbum( Album album) throws IOException, SQLiteException
    {
        if ( album == null ) {
            System.out.println( "OMG Album NULL" );
            return;
        }
        try {
            if ( album.getName().length() > 0 ) {
            	String year;
        		if (album.getYear() != null)
        			year = album.getYear();
        		else 
        			year = UNKNOWN_STRING;
            	try {
            		mAlbumExists.bindLong(1, album.getId());
            		mAlbumExists.simpleQueryForLong();
            		
            		mUpdateAlbum.bindString(1, album.getName());
            		mUpdateAlbum.bindString(2, album.getArtistName());
            		mUpdateAlbum.bindLong(  3, album.getArtistId());
            		mUpdateAlbum.bindString(4, year);
            		mUpdateAlbum.bindLong(  5, album.getTrackCount());
            		mUpdateAlbum.bindString(6, UNKNOWN_STRING);
            		mUpdateAlbum.bindLong(  7, album.getId());
            		mUpdateAlbum.execute();
            	} catch (SQLiteDoneException e) {
            		mInsertAlbum.bindLong(  1, album.getId());
            		mInsertAlbum.bindString(2, album.getName());
            		mInsertAlbum.bindString(3, album.getArtistName());
            		mInsertAlbum.bindLong(  4, album.getArtistId());
            		mInsertAlbum.bindString(5, year);
            		mInsertAlbum.bindLong(  6, album.getTrackCount());
            		mInsertAlbum.bindString(7, UNKNOWN_STRING);
            		mInsertAlbum.execute();
            	}
            }
        } catch ( SQLiteException e ) {
        	
            throw e;
        }
    }

    private void insertPlaylist( Playlist playlist, int index) throws IOException, SQLiteException
    {
        if ( playlist == null ) {
            System.out.println( "OMG Playlist NULL" );
            return;
        }
        try {
            if ( playlist.getName().length() > 0 ) {
            	try {
            		mPlaylistExists.bindString(1, playlist.getId());
            		mPlaylistExists.simpleQueryForString();
            		
            		mUpdatePlaylist.bindString(1, playlist.getName());
            		mUpdatePlaylist.bindLong(  2, playlist.getCount());
            		mUpdatePlaylist.bindString(3, playlist.getFileName());
            		mUpdatePlaylist.bindLong(  4, index);
            		mUpdatePlaylist.bindString(6, playlist.getId());
            		mUpdatePlaylist.execute();
            	} catch (SQLiteDoneException e) {
            		mInsertPlaylist.bindString(1, playlist.getId());
            		mInsertPlaylist.bindString(2, playlist.getName());
            		mInsertPlaylist.bindLong(  3, playlist.getCount());
            		mInsertPlaylist.bindString(4, playlist.getFileName());
            		mInsertPlaylist.bindLong(  5, index);
            		mInsertPlaylist.execute();
            	}
            }
        } catch ( SQLiteException e ) {
            throw e;
        }
    }

    private void insertToken( Token token, String type ) throws IOException, SQLiteException
    {
        if ( token == null ) {
            System.out.println( "OMG Token NULL" );
            return;
        }
        try {
            if ( token.getToken().length() > 0 ) {
            	try {
            		mTokenExists.bindString(1, token.getToken());
            		mTokenExists.bindString(2, type);
            		mTokenExists.simpleQueryForLong();
            		
            		mUpdateToken.bindLong(  1, token.getCount());
            		mUpdateToken.bindString(2, token.getToken());
            		mUpdateToken.bindString(3, type);
            		mUpdateToken.execute();
            	} catch (SQLiteDoneException e) {
            		mInsertToken.bindString(1, type);
            		mInsertToken.bindString(2, token.getToken());
            		mInsertToken.bindLong(  3, token.getCount());
            		mInsertToken.execute();
            	}
            }
        } catch ( SQLiteException e ) {
            throw e;
        }
    }

    private void refreshTracks() throws SQLiteException, IOException
    {
        DataResult<Track> results = mLocker.getTracks();
        
        System.out.println( "beginning insertion of " + results.getData().length + " tracks" );
        
        insertTracks(results);
        
        //int i = 0;
        //for( Track t : results.getData() )
        //{
        //	if ((i % 10) == 0) Log.w("mp3tunes player: ", "inserted: " + Integer.toString(i) + "tracks");
        //    insertTrack( t );
        //	i++;
        //}
        System.out.println( "insertion complete" );
        mCache.setUpdate( System.currentTimeMillis(), LockerCache.TRACK );
        mCache.saveCache( mContext );
    }
    
    private void refreshPlaylists()  throws SQLiteException, IOException
    {
        DataResult<Playlist> results = mLocker.getPlaylists( false );
        System.out.println( "beginning insertion of " + results.getData().length + " playlists" );
        int i = 0;
        for ( Playlist p : results.getData() )
        {
            insertPlaylist( p, i );
            i++;
        }
        System.out.println( "insertion complete" );
        mCache.setUpdate( System.currentTimeMillis(), LockerCache.PLAYLIST );
        mCache.saveCache( mContext );
    }
    
    private void refreshArtists()  throws SQLiteException, IOException
    {
        DataResult<Artist> results = mLocker.getArtists();
        System.out.println( "beginning insertion of " + results.getData().length + " artists" );
        for ( Artist a : results.getData() )
        {
            insertArtist( a );
        }
        System.out.println( "insertion complete" );
        mCache.setUpdate( System.currentTimeMillis(), LockerCache.ARTIST );
        mCache.saveCache( mContext );
    }
    
    private void refreshAlbums()  throws SQLiteException, IOException
    {
        DataResult<Album> results = mLocker.getAlbums();
        System.out.println( "beginning insertion of " + results.getData().length + " albums" );
        for ( Album a : results.getData() )
        {
            insertAlbum( a );
        }
        System.out.println( "insertion complete" );
        mCache.setUpdate( System.currentTimeMillis(), LockerCache.ALBUM );
        mCache.saveCache( mContext );
    }
    
    private void refreshAlbumsForArtist(int artist_id)  throws SQLiteException, IOException
    {
        DataResult<Album> results = mLocker.getAlbumsForArtist( artist_id );
        System.out.println( "beginning insertion of " + results.getData().length + " albums for artist id " +artist_id  );
        for ( Album a : results.getData() )
        {
            insertAlbum( a );
        }
        System.out.println( "insertion complete" );
    }
    
    private void refreshTracksforAlbum(int album_id)  throws SQLiteException, IOException
    {
        DataResult<Track> results = mLocker.getTracksForAlbum( album_id );
        System.out.println( "beginning insertion of " + results.getData().length + " tracks for album id " +album_id );
        for( Track t : results.getData() )
        {
            insertTrack( t );
        }
        System.out.println( "insertion complete" );
    }
    
    private void refreshTracksforArtist(int artist_id)  throws SQLiteException, IOException
    {
        DataResult<Track> results = mLocker.getTracksForArtist( artist_id );
        System.out.println( "beginning insertion of " + results.getData().length + " tracks for artist id " +artist_id );
        for( Track t : results.getData() )
        {
            insertTrack( t );
        }
        System.out.println( "insertion complete" );
    }
    
    private void refreshTracksforPlaylist( String playlist_id )  throws SQLiteException, IOException
    {
        DataResult<Track> results = mLocker.getTracksForPlaylist( playlist_id );
        System.out.println( "beginning insertion of " + results.getData().length + " tracks for playlist id " +playlist_id );
        
        mDb.delete( "playlist_tracks", "playlist_id='" + playlist_id + "'", null );
        int index = 0;
        for ( Track t : results.getData() )
        {
            ContentValues cv = new ContentValues(); // TODO move this outside the loop?
            insertTrack( t );
            cv.put("playlist_id", playlist_id);
            cv.put( "track_id", t.getId() );
            cv.put("playlist_index", index);
            mDb.insert( "playlist_tracks", UNKNOWN_STRING, cv );
            index++;
        }
        System.out.println( "insertion complete" );
    }
    
    private void refreshSearch( String query, boolean artist, boolean album, boolean track )  throws SQLiteException, IOException
    {
        if( !artist && !album && !track )
            return;
        SearchResult results = mLocker.search( query, artist, album, track, 50, 0 );
        
        if( artist ) 
        {
            System.out.println( "beginning insertion of " + results.getArtists().length + " artists" );
            for ( Artist a : results.getArtists() )
            {
                insertArtist( a );
            }
        }
        if( album ) 
        {
            System.out.println( "beginning insertion of "+  results.getAlbums().length + " albums" );
            for ( Album  a : results.getAlbums() )
            {
                insertAlbum( a );
            }
        }
        if( track ) 
        {
            System.out.println( "beginning insertion of "+  results.getTracks().length + " tracks" );
            for ( Track t : results.getTracks() )
            {
                insertTrack( t );
            }
        }
        System.out.println( "insertion complete" );
    }
    
    private void refreshTokens(Music.Meta type)  throws SQLiteException, IOException
    {
        DataResult<Token> results;
        String typename;
        int cachetype;
        switch( type )
        {
            case ARTIST:
                results = mLocker.getArtistTokens();
                typename = "artist";
                cachetype = LockerCache.ARTIST_TOKENS; 
                break;
            case ALBUM:
                results = mLocker.getAlbumTokens();
                typename = "album";
                cachetype = LockerCache.ALBUM_TOKENS; 
                break;
            case TRACK:
                results = mLocker.getTrackTokens();
                typename = "track";
                cachetype = LockerCache.TRACK_TOKENS; 
                break;
            default:
                    return;
        }
        System.out.println( "beginning insertion of " + results.getData().length + " tokens" );
        for ( Token t : results.getData() )
        {
            insertToken( t, typename );
        }
        System.out.println( "insertion complete" );
        mCache.setUpdate( System.currentTimeMillis(), cachetype );
        mCache.saveCache( mContext );
    }
  
    private Cursor queryTokens( String type )
    {
        return mDb.query(TABLE_TOKEN, Music.TOKEN, "type='"+type+"'", null, null, null, KEY_TOKEN);   
    }

    private Cursor queryPlaylists()
    {
    	return mDb.query(TABLE_PLAYLIST, Music.PLAYLIST, null, null, null, null, KEY_PLAYLIST_ORDER);
    }
    
    private Cursor queryPlaylists( String playlist_id )
    {
    	StringBuilder query = new StringBuilder("SELECT DISTINCT ")
    	                   		.append(TABLE_TRACK).append(".").append(KEY_ID).append(", ") 
    	                   		.append(KEY_ID).append(", ")
    	                   		.append(KEY_TITLE).append(", ")
    	                   		.append(KEY_ARTIST_NAME).append(", ")
    	                   		.append(KEY_ARTIST_ID).append(", ") 
    	                   		.append(KEY_ALBUM_NAME).append(", ")
    	                   		.append(KEY_ALBUM_ID).append(", ") 
    	                   		.append(KEY_TRACK).append(", ")
    	                   		.append(KEY_PLAY_URL).append(", ") 
    	                   		.append(KEY_DOWNLOAD_URL).append(", ") 
    	                   		.append(KEY_TRACK_LENGTH).append(", ")
    	                   		.append(KEY_COVER_URL).append(", ")
    	                   		.append(KEY_PLAYLIST_INDEX).append(" ")
    	                   	    .append("FROM playlist ")
    	                   		.append("JOIN ").append(TABLE_PLAYLIST_TRACKS).append(" ")
    	                   		.append("ON ").append(TABLE_PLAYLIST).append(".").append(KEY_ID).append(" = ").append(TABLE_PLAYLIST_TRACKS).append(".").append(KEY_PLAYLIST_ID).append(" ")
    	                   		.append("JOIN ").append(TABLE_TRACK).append(" ") 
    	                   		.append("ON ").append(TABLE_PLAYLIST_TRACKS).append(".").append(KEY_TRACK_ID).append(" = ").append(TABLE_TRACK).append(".").append(KEY_ID).append(" ")
    	                   		.append("WHERE ").append(KEY_PLAYLIST_ID).append("='").append(playlist_id).append(" ")
    	                   		.append("ORDER BY ").append(KEY_PLAYLIST_INDEX);
        return mDb.rawQuery(query.toString(), null );
       
    }
  
    private Cursor queryArtists()
    {
        return mDb.query(TABLE_ARTIST, Music.ARTIST, null, null, null, null, "lower("+KEY_ARTIST_NAME+")" );   
    }


    private Cursor queryAlbums()
    {
        return mDb.query(TABLE_ALBUM, Music.ALBUM, null, null, null, null, "lower("+KEY_ALBUM_NAME+")" );
    }


    private Cursor queryAlbums( int artist_id )
    {
        return  mDb.query(TABLE_ALBUM, Music.ALBUM, KEY_ARTIST_ID + "=" + artist_id,
                null, null, null, "lower("+KEY_ALBUM_NAME+")");  
    }


    private Cursor queryTracks()
    {
        return mDb.query(TABLE_TRACK, Music.TRACK, null, null, null, null, "lower("+KEY_TITLE+")" );
    }

 
    private Cursor queryTracksArtist( int artist_id )
    {
        return mDb.query(TABLE_TRACK, Music.TRACK, KEY_ARTIST_ID + "=" + artist_id, null, null, null, "lower("+KEY_TRACK+")" );
    }


    private Cursor queryTracksAlbum( int album_id )
    {
        return mDb.query(KEY_TRACK, Music.TRACK, KEY_ALBUM_ID + "=" + album_id, null, null, null, "lower("+KEY_TRACK+")" );
    }


    private Cursor querySearch( String query, Music.Meta type )
        {
            String table;
            String[] columns;
            String selection;
            switch ( type )
            {
                case TRACK:
                    table = "track";
                    columns = Music.TRACK;
                    selection = "lower(title) LIKE lower('%"+query+"%')";
                    break;
                case ARTIST:
                    table = "artist";
                    columns = Music.ARTIST;
                    selection = "lower(artist_name) LIKE lower('%"+query+"%')";
                    break;
                case ALBUM:
                    table = "album";
                    columns = Music.ALBUM;
                    selection = "lower(album_name) LIKE lower('%"+query+"%')";
                    break;
                default: return null;
            }
            return mDb.query( table, columns, selection, null, null, null, null, null);
        }
    

    private Bitmap getArtwork( String urlstr )
    {

        try
        {
            URL url;

            url = new URL( urlstr );

            HttpURLConnection c = ( HttpURLConnection ) url.openConnection();
            c.setDoInput( true );
            c.connect();
            InputStream is = c.getInputStream();
            Bitmap img;
            img = BitmapFactory.decodeStream( is );
            return img;
        }
        catch ( MalformedURLException e )
        {
            Log.d( "LockerArtCache", "LockerArtCache passed invalid URL: " + urlstr );
        }
        catch ( IOException e )
        {
            Log.d( "LockerArtCache", "LockerArtCache IO exception: " + e );
        }
        return null;
    }
    
    

    public class DbSearchResult
    {
        public Cursor mArtists = null;
        public Cursor mAlbums = null;
        public Cursor mTracks = null;
    }
    

    public class DbSearchQuery
    {
        public DbSearchQuery( String query, boolean artists, boolean albums, boolean tracks )
        {
            mQuery = query;
            mArtists = artists;
            mAlbums = albums;
            mTracks = tracks;
        }
        public String mQuery;
        public boolean mArtists;
        public boolean mAlbums;
        public boolean mTracks;
    }

}
