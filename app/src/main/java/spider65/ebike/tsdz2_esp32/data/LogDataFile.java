package spider65.ebike.tsdz2_esp32.data;

import android.util.Log;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;

import spider65.ebike.tsdz2_esp32.MyApp;

import static spider65.ebike.tsdz2_esp32.TSDZConst.DEBUG_ADV_SIZE;
import static spider65.ebike.tsdz2_esp32.TSDZConst.STATUS_ADV_SIZE;

public class LogDataFile {

    public class LogStatusEntry {
        public byte[] status = new byte[STATUS_ADV_SIZE];
        public long time;
    }

    public class LogDebugEntry {
        public byte[] debug = new byte[DEBUG_ADV_SIZE];
        public long time;
    }

    private class TimeInterval {
        long startTime, endTime;
        TimeInterval(long t1, long t2) {
            startTime = t1;
            endTime = t2;
        }
    }

    private final Object statusLock = new Object();
    private final Object debugLock = new Object();

    private static final String TAG = "LogDataFile";
    private static final String STATUS_LOG_FILENAME = "status";
    private static final String DEBUG_LOG_FILENAME = "debug";
    private static final long MAX_LOG_HISTORY = 1000 * 60 * 60 * 24 * 7; // log file retention is 1 week (in msec)
    private static final long MAX_FILE_HISTORY = 1000 * 60 * 60 * 2; // max single log file time is 2 hours (in msec)

    private static LogDataFile mLogDataFile = null;

    private File fileDebug,fileStatus;
    private RandomAccessFile rafDebug, rafStatus;
    private TimeInterval statusLogInterval, debugLogInterval;


    public static synchronized LogDataFile getLogDataFile() {
        if (mLogDataFile == null) {
            mLogDataFile = new LogDataFile();
        }
        return mLogDataFile;
    }

    private LogDataFile () {
        initLogFiles();
    }

    public void addStatusData(byte[] status) {
        long time = System.currentTimeMillis();

        synchronized (statusLock) {
            if (statusLogInterval.startTime == 0)
                statusLogInterval.startTime = time;
            else if ((time - statusLogInterval.startTime) > MAX_FILE_HISTORY) {
                swapStatusFile();
                statusLogInterval.startTime = time;
            }
            statusLogInterval.endTime = time;

            try {
                rafStatus.writeLong(time);
                rafStatus.write(status);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    public void addDebugData(byte[] debug) {
        long time = System.currentTimeMillis();

        synchronized (debugLock) {
            if (debugLogInterval.startTime == 0)
                debugLogInterval.startTime = time;
            else if ((time - debugLogInterval.startTime) > MAX_FILE_HISTORY) {
                swapDebugFile();
                debugLogInterval.startTime = time;
            }
            debugLogInterval.endTime = time;

            try {
                rafDebug.writeLong(time);
                rafDebug.write(debug);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    private void swapStatusFile() {
        // remove log file with all data older than MAX_LOG_HISTORY
        final File folder = MyApp.getInstance().getFilesDir();
        final File[] files = folder.listFiles( (dir, name) ->
                name.matches( STATUS_LOG_FILENAME + ".log\\.\\d+\\.\\d+$" ));
        if (files != null) {
            Arrays.sort(files, (object1, object2) ->
                    object1.getName().compareTo(object2.getName()));
            long now = System.currentTimeMillis();
            for (File f:files) {
                String s1 = f.getName();
                String[] s = s1.split("\\.");
                long endTime = Long.valueOf(s[3]);
                if ((now - endTime) > MAX_LOG_HISTORY)
                    if (!f.delete()) {
                        Log.e(TAG, "Can't remove " + f.getAbsolutePath());
                    }
            }
        }
        // rename current log file to status.log.startTime.endTime and
        // create the new log file status.log
        File f2 = new File(MyApp.getInstance().getFilesDir(), STATUS_LOG_FILENAME+".log." +
                statusLogInterval.startTime + "." + statusLogInterval.endTime);
        try {
            rafStatus.close();
            if (!fileStatus.renameTo(f2))
                Log.e(TAG,"Failed to rename file to " + f2.getName() );
            newStatusLogFile();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private void swapDebugFile() {
        // remove log file with all data older than MAX_LOG_HISTORY
        final File folder = MyApp.getInstance().getFilesDir();
        final File[] files = folder.listFiles( (dir, name ) ->
                name.matches( DEBUG_LOG_FILENAME + ".log\\.\\d+\\.\\d+$" ));

        // remove old log files
        if (files != null) {
            Arrays.sort(files, (object1, object2) ->
                    object1.getName().compareTo(object2.getName()));
            long now = System.currentTimeMillis();
            for (File f:files) {
                String s1 = f.getName();
                String[] s = s1.split("\\.");
                long endTime = Long.valueOf(s[3]);
                if ((now - endTime) > MAX_LOG_HISTORY)
                    if (!f.delete()) {
                        Log.e(TAG, "Can't remove " + f.getAbsolutePath());
                    }
            }
        }
        // rename current log file to debug.log.startTime.endTime and
        // create the new log file debug.log
        File f2 = new File(MyApp.getInstance().getFilesDir(), DEBUG_LOG_FILENAME+".log." +
                debugLogInterval.startTime + "." + debugLogInterval.endTime);
        try {
            rafDebug.close();
            if (!fileDebug.renameTo(f2))
                Log.e(TAG,"Failed to rename file to " + f2.getName() );
            newDebugLogFile();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    // Get last used logfiles and initialize statusLogInterval and debugLogInterval according
    // to file contents
    private void initLogFiles() {
        final File folder = MyApp.getInstance().getFilesDir();
        File[] files = folder.listFiles( (dir, name ) -> name.matches( STATUS_LOG_FILENAME + ".log" ));
        if (files != null && files.length > 0) {
            // file status.log already exist
            fileStatus = files[0];
            try {
                rafStatus = new RandomAccessFile(fileStatus, "rw");
                statusLogInterval = checkLogFile(rafStatus,8+STATUS_ADV_SIZE);
            } catch (Exception e) {
               Log.e(TAG,"initLogFiles", e);
            }
        } else {
            // create new status.log file
            newStatusLogFile();
        }
        files = folder.listFiles( (dir, name) -> name.matches( DEBUG_LOG_FILENAME + ".log" ));
        if (files != null && files.length > 0) {
            // file debug.log already exist
            fileDebug = files[0];
            try {
                rafDebug = new RandomAccessFile(fileDebug, "rw");
                debugLogInterval = checkLogFile(rafDebug,8+DEBUG_ADV_SIZE);
            } catch (Exception e) {
                Log.e(TAG,"initLogFiles", e);
            }
        } else {
            // create new debug.log file
            newDebugLogFile();
        }
    }

    // Verify if the log file is correctly aligned and return the first and last log timestamps
    private TimeInterval checkLogFile(RandomAccessFile f, long logEntrySize) throws IOException {
        TimeInterval ret = new TimeInterval(0,0);
        long l = f.length();

        // Check if the Log File is aligned and truncate if not
        if ((l % logEntrySize) > 0) {
            l -= (l % logEntrySize);
            f.setLength(l);
        }

        if (l == 0)
            return ret;

        // read timestamp of first and last records
        f.seek(0);
        ret.startTime = f.readLong();
        f.seek(l - logEntrySize);
        ret.endTime = f.readLong();
        // set file pointer to End of File
        f.seek(f.length());

        return ret;
    }

    private void newStatusLogFile() {
        statusLogInterval = new TimeInterval(0,0);
        fileStatus = new File(MyApp.getInstance().getFilesDir(), STATUS_LOG_FILENAME+".log");
        try {
            rafStatus = new RandomAccessFile(fileStatus, "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void newDebugLogFile() {
        debugLogInterval = new TimeInterval(0,0);
        fileDebug = new File(MyApp.getInstance().getFilesDir(), DEBUG_LOG_FILENAME+".log");
        try {
            rafDebug = new RandomAccessFile(fileDebug, "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public ArrayList<LogStatusEntry> getStatusData(long fromTime, long toTime) {

        ArrayList<LogStatusEntry> ret = new ArrayList<>();

        if ((fromTime < 0) || (toTime <= fromTime))
            return ret;

        synchronized (statusLock) {
            try {
                DataInputStream dis = null;

                final File folder = MyApp.getInstance().getFilesDir();
                final File[] files = folder.listFiles( (dir, name ) ->
                        name.matches( STATUS_LOG_FILENAME + ".log\\.\\d+\\.\\d+$" ));
                long t,startTime,endTime;
                if (files != null) {
                    Arrays.sort(files, (object1, object2) ->
                            object1.getName().compareTo(object2.getName()));

                    for (File file : files) {
                        String[] s = file.getName().split(".");
                        startTime = Long.valueOf(s[2]);
                        endTime   = Long.valueOf(s[3]);
                        if (fromTime <= endTime && toTime >= startTime) {
                            try {
                                dis = new DataInputStream(new FileInputStream(file));
                                while (dis.available() > 0) {
                                    t = dis.readLong();
                                    if (t >= toTime) {
                                        return ret;
                                    } else if (t >= fromTime) {
                                        LogStatusEntry entry = new LogStatusEntry();
                                        entry.time = t;
                                        if (dis.read(entry.status) == STATUS_ADV_SIZE)
                                            ret.add(entry);
                                        else
                                            Log.e(TAG, "getStatusData read error");
                                    } else
                                        dis.skipBytes(STATUS_ADV_SIZE);
                                }
                            } finally {
                                if (dis != null) {
                                    dis.close();
                                    dis = null;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }

            try {
                long t;
                if (statusLogInterval.startTime > toTime)
                    return ret;

                long pos = rafStatus.getFilePointer();
                rafStatus.seek(0);
                while (rafStatus.getFilePointer() < rafStatus.length()) {
                    t = rafStatus.readLong();
                    if (t >= toTime) {
                        break;
                    } else if (t >= fromTime) {
                        LogStatusEntry entry = new LogStatusEntry();
                        entry.time = t;
                        if (rafStatus.read(entry.status) == STATUS_ADV_SIZE)
                            ret.add(entry);
                        else
                            Log.e(TAG, "getStatusData read error");
                    } else
                        rafStatus.skipBytes(STATUS_ADV_SIZE);
                }
                rafStatus.seek(pos);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }

        return ret;
    }

    public ArrayList<LogDebugEntry> getDebugData(long fromTime, long toTime) {

        ArrayList<LogDebugEntry> ret = new ArrayList<>();

        if ((fromTime < 0) || (toTime <= fromTime))
            return ret;

        synchronized (debugLock) {
            try {
                FileInputStream fis;
                DataInputStream dis = null;

                final File folder = MyApp.getInstance().getFilesDir();
                final File[] files = folder.listFiles((dir, name) ->
                        name.matches(DEBUG_LOG_FILENAME + ".log\\.\\d+\\.\\d+$"));
                long t, startTime, endTime;
                if (files != null) {
                    Arrays.sort(files, (object1, object2) ->
                            object1.getName().compareTo(object2.getName()));

                    for (File file : files) {
                        String[] s = file.getName().split(".");
                        startTime = Long.valueOf(s[2]);
                        endTime = Long.valueOf(s[3]);
                        if (fromTime <= endTime && toTime >= startTime) {
                            try {
                                fis = new FileInputStream(file);
                                dis = new DataInputStream(fis);
                                while (dis.available() > 0) {
                                    t = dis.readLong();
                                    if (t >= toTime) {
                                        return ret;
                                    } else if (t >= fromTime) {
                                        LogDebugEntry entry = new LogDebugEntry();
                                        entry.time = t;
                                        if (dis.read(entry.debug) == DEBUG_ADV_SIZE)
                                            ret.add(entry);
                                        else
                                            Log.e(TAG, "getDebugData read error");
                                    } else
                                        dis.skipBytes(DEBUG_ADV_SIZE);
                                }
                            } finally {
                                if (dis != null) {
                                    dis.close();
                                    dis = null;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }

            try {
                long t;
                long pos = rafDebug.getFilePointer();
                rafDebug.seek(0);
                while (rafDebug.getFilePointer() < rafDebug.length()) {
                    t = rafDebug.readLong();
                    if (t >= toTime) {
                        break;
                    } else if (t >= fromTime) {
                        LogDebugEntry entry = new LogDebugEntry();
                        entry.time = t;
                        if (rafDebug.read(entry.debug) == DEBUG_ADV_SIZE)
                            ret.add(entry);
                        else
                            Log.e(TAG, "getDebugData read error");
                    } else
                        rafDebug.skipBytes(DEBUG_ADV_SIZE);
                }
                rafDebug.seek(pos);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
        return ret;
    }
}
