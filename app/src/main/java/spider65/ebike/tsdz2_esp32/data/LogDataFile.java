package spider65.ebike.tsdz2_esp32.data;

import android.util.Log;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;

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

    private class TimeInterval {
        public long startTime, endTime;
        TimeInterval(long t1, long t2) {
            startTime = t1;
            endTime = t2;
        }
    }

    private static final String TAG = "LogDataFile";
    private static final String STATUS_LOG_FILENAME = "status";
    private static final String DEBUG_LOG_FILENAME = "debug";
    private static final long MAX_LOG_HISTORY = 1000 * 60 * 60 * 24 * 7; // log file retention is 1 week (in msec)
    private static final long MAX_FILE_HISTORY = 1000 * 60 * 60 * 2; // max single log file time is 2 hours (in msec)

    private static LogDataFile mLogDataFile = null;

    private File fileDebug,fileStatus;
    private RandomAccessFile rafDebug, rafStatus;
    private TimeInterval statusLogInterval, debugLogInterval;


    public static LogDataFile getLogDataFile() {
        if (mLogDataFile == null) {
            mLogDataFile = new LogDataFile();
        }
        return mLogDataFile;
    }

    private LogDataFile () {
        initLogFiles();
    }

    public synchronized void addStatusData(byte[] status) {
        long time = System.currentTimeMillis();

        if (statusLogInterval.startTime == 0)
            statusLogInterval.startTime = time;
        else if ((time - statusLogInterval.startTime) > MAX_FILE_HISTORY)
            swapStatusFile(time);
        statusLogInterval.endTime = time;

        try {
            rafStatus.writeLong(time);
            rafStatus.write(status);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public synchronized void addDebugData(byte[] debug) {
        long time = System.currentTimeMillis();

        if (debugLogInterval.startTime == 0)
            debugLogInterval.startTime = time;
        else if ((time - debugLogInterval.startTime) > MAX_FILE_HISTORY)
            swapDebugFile(time);
        debugLogInterval.endTime = time;

        try {
            rafDebug.writeLong(time);
            rafDebug.write(debug);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private synchronized void swapStatusFile(long time) {
        // remove log file with all data older than MAX_LOG_HISTORY
        final File folder = MyApp.getInstance().getFilesDir();
        final File[] files = folder.listFiles( (dir, name ) ->
                name.matches( STATUS_LOG_FILENAME + ".log\\.\\d+\\.\\d+$" ));
        if (files != null) {
            Arrays.sort(files, (object1, object2) ->
                    object1.getName().compareTo(object2.getName()));

            for (File f:files) {
                String[] s = f.getName().split(".");
                long endTime = Long.valueOf(s[3]);
                if ((time - endTime) > MAX_LOG_HISTORY)
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
            fileStatus.renameTo(f2);
            newStatusLogFile();
            statusLogInterval.startTime = time;
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private void swapDebugFile(long time) {
        // remove log file with all data older than MAX_LOG_HISTORY
        final File folder = MyApp.getInstance().getFilesDir();
        final File[] files = folder.listFiles( (dir, name ) ->
                name.matches( DEBUG_LOG_FILENAME + ".log\\.\\d+\\.\\d+$" ));

        // remove old log files
        if (files != null) {
            Arrays.sort(files, (object1, object2) ->
                    object1.getName().compareTo(object2.getName()));

            for (File f:files) {
                String[] s = f.getName().split(".");
                long endTime = Long.valueOf(s[3]);
                if ((time - endTime) > MAX_LOG_HISTORY)
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
            fileDebug.renameTo(f2);
            newDebugLogFile();
            debugLogInterval.startTime = time;
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private void initLogFiles() {
        final File folder = MyApp.getInstance().getFilesDir();
        File[] files = folder.listFiles( (dir, name ) -> name.matches( STATUS_LOG_FILENAME + ".log" ));
        if (files != null && files.length > 0) {
            // file status.log already exist
            fileStatus = files[0];
            statusLogInterval = checkLogFile(fileStatus,8+STATUS_ADV_SIZE);
            try {
                rafStatus = new RandomAccessFile(fileStatus, "rw");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            // create new status.log file
            newStatusLogFile();
        }
        files = folder.listFiles( (dir, name ) -> name.matches( DEBUG_LOG_FILENAME + ".log" ));
        if (files != null && files.length > 0) {
            // file debug.log already exist
            fileDebug = files[0];
            debugLogInterval = checkLogFile(files[0],8+DEBUG_ADV_SIZE);
            FileOutputStream os = null;
            try {
                rafDebug = new RandomAccessFile(fileDebug, "rw");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            // create new debug.log file
            newDebugLogFile();
        }
    }

    // Verify if the log file is correctly aligned and return the first and last log timestamps
    private TimeInterval checkLogFile(File f, long logEntrySize) {
        TimeInterval ret = new TimeInterval(0,0);
        long l = f.length();
        // Check if the Log File is aligned and truncate if not
        if ((l % logEntrySize) > 0) {
            l -= (l % logEntrySize);
            try (FileChannel outChan = new FileOutputStream(f, true).getChannel()) {
                outChan.truncate(l);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (l == 0)
            return ret;

        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "r");
            ret.startTime = raf.readLong();
            raf.seek(l - logEntrySize);
            ret.endTime = raf.readLong();
        } catch (IOException e) {
            Log.e(TAG,e.toString());
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
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

    public synchronized ArrayList<LogStatusEntry> getStatusData(long fromTime, long toTime) {

        ArrayList<LogStatusEntry> ret = new ArrayList<>();

        if ((fromTime < 0) || (toTime <= fromTime))
            return ret;

        try {
            FileInputStream fis;
            DataInputStream dis = null;

            final File folder = MyApp.getInstance().getFilesDir();
            final File[] files = folder.listFiles( (dir, name ) ->
                    name.matches( STATUS_LOG_FILENAME + ".log\\.\\d+\\.\\d+$" ));
            boolean stop = false;
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
                            fis = new FileInputStream(file);
                            dis = new DataInputStream(fis);
                            while (dis.available() > 0) {
                                t = dis.readLong();
                                if (t >= toTime) {
                                    dis.close();
                                    stop = true;
                                    break;
                                } else if (t >= fromTime) {
                                    LogStatusEntry entry = new LogStatusEntry();
                                    entry.time = t;
                                    dis.read(entry.status);
                                    ret.add(entry);
                                }
                            }
                        } finally {
                            if (dis != null) {
                                dis.close();
                                dis = null;
                            }
                        }
                    }
                    if (stop)
                        break;
                }
            }

            if (stop || statusLogInterval.startTime > toTime)
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
                    rafStatus.read(entry.status);
                    ret.add(entry);
                } else
                    rafStatus.skipBytes(STATUS_ADV_SIZE);
            }
            rafStatus.seek(pos);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        return ret;
    }

    public synchronized ArrayList<LogDebugEntry> getDebugData(long fromTime, long toTime) {

        ArrayList<LogDebugEntry> ret = new ArrayList<>();

        if ((fromTime < 0) || (toTime <= fromTime))
            return ret;

        try {
            FileInputStream fis;
            DataInputStream dis = null;

            final File folder = MyApp.getInstance().getFilesDir();
            final File[] files = folder.listFiles((dir, name) ->
                    name.matches(DEBUG_LOG_FILENAME + ".log\\.\\d+\\.\\d+$"));
            boolean stop = false;
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
                                    dis.close();
                                    stop = true;
                                    break;
                                } else if (t >= fromTime) {
                                    LogDebugEntry entry = new LogDebugEntry();
                                    entry.time = t;
                                    dis.read(entry.debug);
                                    ret.add(entry);
                                }
                            }
                        } finally {
                            if (dis != null) {
                                dis.close();
                                dis = null;
                            }
                        }
                    }
                    if (stop)
                        break;
                }
            }

            if (stop || debugLogInterval.startTime > toTime)
                return ret;

            long pos = rafDebug.getFilePointer();
            rafDebug.seek(0);
            while (rafDebug.getFilePointer() < rafDebug.length()) {
                t = rafDebug.readLong();
                if (t >= toTime) {
                    break;
                } else if (t >= fromTime) {
                    LogDebugEntry entry = new LogDebugEntry();
                    entry.time = t;
                    rafDebug.read(entry.debug);
                    ret.add(entry);
                } else
                    rafDebug.skipBytes(DEBUG_ADV_SIZE);
            }
            rafDebug.seek(pos);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        return ret;
    }
}
