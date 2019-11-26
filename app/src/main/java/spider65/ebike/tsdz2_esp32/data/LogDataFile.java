package spider65.ebike.tsdz2_esp32.data;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;

import spider65.ebike.tsdz2_esp32.MyApp;

import static spider65.ebike.tsdz2_esp32.TSDZConst.DEBUG_ADV_SIZE;
import static spider65.ebike.tsdz2_esp32.TSDZConst.STATUS_ADV_SIZE;

public class LogDataFile {

    public class LogStatusEntry {
        public byte[] status;
        public long time;

        LogStatusEntry () {
            status = new byte[STATUS_ADV_SIZE];
        }
    }

    public class LogDebugEntry {
        public byte[] debug;
        public long time;

        LogDebugEntry () {
            debug = new byte[DEBUG_ADV_SIZE];
        }
    }

    private static final String TAG = "LogDataFile";
    private static final String STATUS_LOG_FILENAME = "status.log";
    private static final String DEBUG_LOG_FILENAME = "debug.log";


    private File fileDebug,fileStatus;
    private DataOutputStream osDebug, osStatus;

    private int debugSamples, statusSamples;
    private static final int MAX_SAMPLES = 3600;

    private static LogDataFile mLogDataFile = null;


    public static LogDataFile getLogDataFile() {
        if (mLogDataFile == null) {
            mLogDataFile = new LogDataFile();
        }
        return mLogDataFile;
    }

    private LogDataFile () {
        debugSamples = 0;
        statusSamples = 0;
        try {
            fileDebug = new File(MyApp.getInstance().getFilesDir(), DEBUG_LOG_FILENAME);
            FileOutputStream fos = new FileOutputStream(fileDebug, true);
            osDebug = new DataOutputStream(fos);
            fileStatus = new File(MyApp.getInstance().getFilesDir(), STATUS_LOG_FILENAME);
            fos = new FileOutputStream(fileStatus, true);
            osStatus = new DataOutputStream(fos);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public void addStatusData(byte[] status) {
        long time = System.currentTimeMillis();

        try {
            osStatus.writeLong(time);
            osStatus.write(status);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        if (++statusSamples == MAX_SAMPLES) {
            swapStatusFile(time);
            statusSamples = 0;
        }
    }

    public void addDebugData(byte[] debug) {
        long time = System.currentTimeMillis();

        try {
            osDebug.writeLong(time);
            osDebug.write(debug);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        if (++debugSamples == MAX_SAMPLES) {
            swapDebugFile(time);
            debugSamples = 0;
        }
    }


    private void swapStatusFile(long time) {
        // remove old datalog files
        final File folder = MyApp.getInstance().getFilesDir();
        final File[] files = folder.listFiles( new FilenameFilter() {
            @Override
            public boolean accept( final File dir, final String name ) {
                return name.matches( STATUS_LOG_FILENAME + "\\.\\d+$" );
            }
        } );
        for ( final File file : files ) {
            if ( !file.delete() ) {
                Log.e(TAG, "Can't remove " + file.getAbsolutePath() );
            }
        }

        File f2 = new File(MyApp.getInstance().getFilesDir(), STATUS_LOG_FILENAME+"." + time);

        try {
            osStatus.close();
            fileStatus.renameTo(f2);
            fileStatus = new File(MyApp.getInstance().getFilesDir(), STATUS_LOG_FILENAME);
            FileOutputStream is = new FileOutputStream(fileStatus);
            osStatus = new DataOutputStream(is);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private void swapDebugFile(long time) {
        // remove old datalog files
        final File folder = MyApp.getInstance().getFilesDir();
        final File[] files = folder.listFiles( new FilenameFilter() {
            @Override
            public boolean accept( final File dir, final String name ) {
                return name.matches( DEBUG_LOG_FILENAME + "\\.\\d+$" );
            }
        } );
        for ( final File file : files ) {
            if ( !file.delete() ) {
                Log.e(TAG, "Can't remove " + file.getAbsolutePath() );
            }
        }

        File f2 = new File(MyApp.getInstance().getFilesDir(), DEBUG_LOG_FILENAME+"." + time);
        try {
            osDebug.close();
            fileDebug.renameTo(f2);
            fileDebug = new File(MyApp.getInstance().getFilesDir(), DEBUG_LOG_FILENAME);
            FileOutputStream is = new FileOutputStream(fileDebug);
            osDebug = new DataOutputStream(is);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    ArrayList<LogStatusEntry> getStatusData(long fromTime, long toTime) {

        ArrayList<LogStatusEntry> ret = new ArrayList<>();

        if ((fromTime < 0) || (toTime <= fromTime))
            return ret;

        try {
            File f;
            FileInputStream fis;
            DataInputStream dis;

            final File folder = MyApp.getInstance().getFilesDir();
            final File[] files = folder.listFiles( new FilenameFilter() {
                @Override
                public boolean accept( final File dir, final String name ) {
                    return name.matches( STATUS_LOG_FILENAME + "\\.\\d+$" );
                }
            } );

            long t = 0;
            if (files.length > 0) {
                File file = files[0];
                int idx = file.getName().lastIndexOf(".");
                String s = file.getName().substring(idx+1);
                t = Long.parseLong(s);
                if (fromTime < t) {
                    fis = new FileInputStream(file);
                    dis = new DataInputStream(fis);
                    while (dis.available() > 0) {
                        t = dis.readLong();
                        if (t >= toTime) {
                            dis.close();
                            break;
                        } else if (t >= fromTime) {
                            LogStatusEntry entry = new LogStatusEntry();
                            entry.time = t;
                            dis.read(entry.status);
                            ret.add(entry);
                        }
                    }
                }
            }

            if (t >= toTime)
                return ret;

            f = new File(MyApp.getInstance().getFilesDir(), STATUS_LOG_FILENAME);
            fis = new FileInputStream(f);
            dis = new DataInputStream(fis);
            while (dis.available() > 0) {
                t = dis.readLong();
                if (t >= toTime) {
                    dis.close();
                    break;
                } else if (t >= fromTime) {
                    LogStatusEntry entry = new LogStatusEntry();
                    entry.time = t;
                    dis.read(entry.status);
                    ret.add(entry);
                } else
                    dis.skipBytes(STATUS_ADV_SIZE);
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        return ret;
    }

    ArrayList<LogDebugEntry> getDebugData(long fromTime, long toTime) {

        ArrayList<LogDebugEntry> ret = new ArrayList<>();

        if ((fromTime < 0) || (toTime < fromTime))
            return ret;

        try {
            long prevFileTime = 0;
            File f;
            FileInputStream fis;
            DataInputStream dis;

            final File folder = MyApp.getInstance().getFilesDir();
            final File[] files = folder.listFiles( new FilenameFilter() {
                @Override
                public boolean accept( final File dir, final String name ) {
                    return name.matches( DEBUG_LOG_FILENAME + "\\.\\d+$" );
                }
            } );

            long t = 0;
            if (files.length > 0) {
                File file = files[0];
                int idx = file.getName().lastIndexOf(".");
                String s = file.getName().substring(idx+1);
                prevFileTime = Long.parseLong(s);
                if (fromTime < prevFileTime) {
                    fis = new FileInputStream(file);
                    dis = new DataInputStream(fis);
                    while (dis.available() > 0) {
                        t = dis.readLong();
                        if (t >= toTime) {
                            dis.close();
                            break;
                        } else if (t >= fromTime) {
                            LogDebugEntry entry = new LogDebugEntry();
                            entry.time = t;
                            dis.read(entry.debug);
                            ret.add(entry);
                        } else
                            dis.skipBytes(DEBUG_ADV_SIZE);
                    }
                }
            }

            if (prevFileTime > toTime)
                return ret;

            f = new File(MyApp.getInstance().getFilesDir(), DEBUG_LOG_FILENAME);
            fis = new FileInputStream(f);
            dis = new DataInputStream(fis);
            while (dis.available() > 0) {
                LogDebugEntry entry = new LogDebugEntry();
                entry.time = dis.readLong();
                dis.read(entry.debug);
                if (entry.time > toTime) {
                    dis.close();
                    break;
                } else if (entry.time > fromTime) {
                    ret.add(entry);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        return ret;
    }
}
