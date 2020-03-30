package spider65.ebike.tsdz2_esp32.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import spider65.ebike.tsdz2_esp32.MyApp;
import spider65.ebike.tsdz2_esp32.TSDZBTService;

import static spider65.ebike.tsdz2_esp32.TSDZConst.DEBUG_ADV_SIZE;
import static spider65.ebike.tsdz2_esp32.TSDZConst.STATUS_ADV_SIZE;

public class LogManager {

    public interface LogResultListener {
        void logQueryResult(List<LogStatusEntry> statusList, List<LogDebugEntry>  debugList);
    }

    public class LogStatusEntry {
        public TSDZ_Status status = new TSDZ_Status();
        public long time;
    }

    public class LogDebugEntry {
        public TSDZ_Debug debug = new TSDZ_Debug();
        public long time;
    }

    private class TimeInterval {
        long startTime, endTime;
        TimeInterval(long t1, long t2) {
            startTime = t1;
            endTime = t2;
        }
    }

    private static final int MSG_STATUS_LOG = 1;
    private static final int MSG_DEBUG_LOG = 2;
    private static final int MSG_QUERY = 3;

    private static final String TAG = "LogManager";
    private static final String STATUS_LOG_FILENAME = "status";
    private static final String DEBUG_LOG_FILENAME = "debug";
    private static final long MAX_LOG_HISTORY = 1000 * 60 * 60 * 24 * 5; // log file retention is 2 days (in msec)
    private static final long MAX_FILE_HISTORY = 1000 * 60 * 60 * 1; // max single log file time is 1 hour (in msec)

    private static LogManager mLogManager = null;

    // loq query listener
    private LogResultListener mListener;

    private File fileDebug,fileStatus;
    private RandomAccessFile rafDebug, rafStatus;
    private TimeInterval statusLogInterval, debugLogInterval;

    // Timestamp of last saved log record
    private long lastStatusTimestamp = 0;
    private long lastDebugTimestamp = 0;
    // current log buffer
    private StatusBuffer statusBuffer = null;
    private DebugBuffer debugBuffer = null;

    private final Handler mHandler;

    public static LogManager initLogs() {
        if (mLogManager == null) {
            mLogManager = new LogManager();
        }
        return mLogManager;
    }

    private LogManager() {
        final IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(TSDZBTService.SERVICE_STOPPED_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.TSDZ_STATUS_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.TSDZ_DEBUG_BROADCAST);

        // to avoid performance issues (e.g. log file switch can take some time), data logging
        // is asyncronous and handled by a dedicated thread
        final HandlerThread handlerThread = new HandlerThread("LogDataThread");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg){
                switch (msg.what) {
                    case MSG_STATUS_LOG:
                        StatusBuffer sb = (StatusBuffer)msg.obj;
                        saveStatusLog(sb.startTime, sb.endTime, sb.data, sb.position);
                        StatusBuffer.recycle(sb);
                        break;
                    case MSG_DEBUG_LOG:
                        DebugBuffer db = (DebugBuffer)msg.obj;
                        saveDebugLog(db.startTime, db.endTime, db.data, db.position);
                        DebugBuffer.recycle(db);
                        break;
                    case MSG_QUERY:
                        List<LogStatusEntry> statusList = getStatusData(msg.arg1,msg.arg2);
                        List<LogDebugEntry>  debugList  = getDebugData (msg.arg1,msg.arg2);
                        synchronized (this) {
                            if (mListener != null)
                                mListener.logQueryResult(statusList, debugList);
                        }
                        break;
                }
            }
        };

        initLogFiles();

        final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, @NotNull Intent intent) {
                if (intent.getAction() == null)
                    return;
                byte[] data;
                long now;
                switch (intent.getAction()) {
                    case TSDZBTService.SERVICE_STOPPED_BROADCAST:
                        Log.d(TAG, "SERVICE_STOPPED_BROADCAST - flush Logs");
                        if (statusBuffer != null) {
                            Message msg = mHandler.obtainMessage(MSG_STATUS_LOG, statusBuffer);
                            mHandler.sendMessage(msg);
                            statusBuffer = StatusBuffer.obtain();
                        }
                        if (debugBuffer != null) {
                            Message msg = mHandler.obtainMessage(MSG_DEBUG_LOG, debugBuffer);
                            mHandler.sendMessage(msg);
                            debugBuffer = DebugBuffer.obtain();
                        }
                        break;
                    case TSDZBTService.TSDZ_STATUS_BROADCAST:
                        now = System.currentTimeMillis();
                        // log 1 msg/sec
                        if ((now - lastStatusTimestamp) <= 900)
                            return;
                        lastStatusTimestamp = now;
                        data = intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA);
                        // statusBuffer could be overwritten if a new notification arrives before statusBuffer
                        // is written to the log. To avoid this potential problem, a recycling buffer pool is used.
                        if (statusBuffer == null)
                            statusBuffer = StatusBuffer.obtain();
                        if (statusBuffer.addRecord(data, now)) {
                            Message msg = mHandler.obtainMessage(MSG_STATUS_LOG, statusBuffer);
                            mHandler.sendMessage(msg);
                            statusBuffer = StatusBuffer.obtain();
                        }
                        break;
                    case TSDZBTService.TSDZ_DEBUG_BROADCAST:
                        now = System.currentTimeMillis();
                        // log 1 msg/sec
                        if ((now - lastDebugTimestamp) <= 900)
                            return;
                        lastDebugTimestamp = now;
                        data = intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA);
                        // debugBuffer could be overwritten if a new notification arrives before debugBuffer
                        // is written to the log. To avoid this potential problem, a recycling buffer pool is used.
                        if (debugBuffer == null)
                            debugBuffer = DebugBuffer.obtain();
                        if (debugBuffer.addRecord(data, now)) {
                            Message msg = mHandler.obtainMessage(MSG_DEBUG_LOG, debugBuffer);
                            mHandler.sendMessage(msg);
                            debugBuffer = DebugBuffer.obtain();
                        }
                        break;
                }
            }
        };
        LocalBroadcastManager.getInstance(MyApp.getInstance()).registerReceiver(mMessageReceiver, mIntentFilter);
    }

    public void setListener (LogResultListener listener) {
        synchronized (this) {
            mListener = listener;
        }
    }

    public void queryLogData(int minuteFrom, int minuteTo) {
        if (mListener == null)
            return;
        if (statusBuffer != null) {
            Message msg = mHandler.obtainMessage(MSG_STATUS_LOG, statusBuffer);
            mHandler.sendMessage(msg);
            statusBuffer = StatusBuffer.obtain();
        }
        if (debugBuffer != null) {
            Message msg = mHandler.obtainMessage(MSG_DEBUG_LOG, debugBuffer);
            mHandler.sendMessage(msg);
            debugBuffer = DebugBuffer.obtain();
        }
        Message msg = mHandler.obtainMessage(MSG_QUERY, minuteFrom, minuteTo);
        mHandler.sendMessage(msg);
    }

    private void saveStatusLog(long startTime, long endTime, byte[] data, int length) {
        Log.d(TAG, "saveStatusLog");
        if (statusLogInterval.startTime == 0)
            statusLogInterval.startTime = startTime;
        statusLogInterval.endTime = endTime;
        try {
            rafStatus.write(data, 0, length);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        if ((endTime - statusLogInterval.startTime) > MAX_FILE_HISTORY) {
            swapStatusFile();
        }
    }

    private void saveDebugLog(long startTime, long endTime, byte[] data, int length) {
        Log.d(TAG, "saveDebugLog");
        if (debugLogInterval.startTime == 0)
            debugLogInterval.startTime = startTime;
        debugLogInterval.endTime = endTime;
        try {
            rafDebug.write(data, 0, length);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        if ((endTime - debugLogInterval.startTime) > MAX_FILE_HISTORY) {
            swapDebugFile();
        }
    }

    private void swapStatusFile() {
        Log.d(TAG, "swapStatusFile");
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
        Log.d(TAG, "swapDebugFile");
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
                rafStatus = new RandomAccessFile(fileStatus, "rwd");
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
                rafDebug = new RandomAccessFile(fileDebug, "rwd");
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
    private TimeInterval checkLogFile(@NotNull RandomAccessFile f, long logEntrySize) throws IOException {
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
            rafStatus = new RandomAccessFile(fileStatus, "rwd");
        } catch (FileNotFoundException e) {
           Log.e(TAG, "newStatusLogFile", e);
        }
    }

    private void newDebugLogFile() {
        debugLogInterval = new TimeInterval(0,0);
        fileDebug = new File(MyApp.getInstance().getFilesDir(), DEBUG_LOG_FILENAME+".log");
        try {
            rafDebug = new RandomAccessFile(fileDebug, "rwd");
        } catch (FileNotFoundException e) {
            Log.e(TAG, "newStatusLogFile", e);
        }
    }

    private static final SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ITALY);

    private ArrayList<LogStatusEntry> getStatusData (int fromMinute, int toMinute) {
        ArrayList<LogStatusEntry> ret = new ArrayList<>();
        byte[] data = new byte[STATUS_ADV_SIZE];
        long fromTime = (long)fromMinute * 60L * 1000L;
        long toTime = (long)toMinute * 60L * 1000L;
        Log.d(TAG, "getStatusData - fromTime=" + sdf.format(new Date(fromTime)) + " toTime=" + sdf.format(new Date(toTime)));

        try {
            FileInputStream fis;
            DataInputStream dis = null;

            final File folder = MyApp.getInstance().getFilesDir();
            final File[] files = folder.listFiles((dir, name) ->
                    name.matches(STATUS_LOG_FILENAME + ".log\\.\\d+\\.\\d+$"));
            long t, startTime, endTime;
            if (files != null) {
                Arrays.sort(files, (object1, object2) ->
                        object1.getName().compareTo(object2.getName()));

                for (File file : files) {
                    String[] s = file.getName().split("\\.");
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
                                    int n = dis.read(data);
                                    if (n == STATUS_ADV_SIZE) {
                                        LogStatusEntry entry = new LogStatusEntry();
                                        entry.time = t;
                                        entry.status.setData(data);
                                        ret.add(entry);
                                    } else if (n > 0)
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
            if (statusLogInterval.startTime>toTime || statusLogInterval.endTime<fromTime) {
                return ret;
            }

            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(fileStatus)))){
                while (dis.available() > 0) {
                    t = dis.readLong();
                    if (t >= toTime) {
                        return ret;
                    } else if (t >= fromTime) {
                        int n = dis.read(data);
                        if (n == STATUS_ADV_SIZE) {
                            LogStatusEntry entry = new LogStatusEntry();
                            entry.time = t;
                            entry.status.setData(data);
                            ret.add(entry);
                        } else if (n > 0)
                            Log.e(TAG, "getStatusData read error");
                    } else
                        dis.skipBytes(STATUS_ADV_SIZE);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        return ret;
    }

    private ArrayList<LogDebugEntry> getDebugData (int fromMinute, int toMinute) {
        ArrayList<LogDebugEntry> ret = new ArrayList<>();
        byte[] data = new byte[DEBUG_ADV_SIZE];
        long fromTime = (long)fromMinute * 60L * 1000L;
        long toTime = (long)toMinute * 60L * 1000L;
        Log.d(TAG, "getDebugData - fromTime=" + sdf.format(new Date(fromTime)) + " toTime=" + sdf.format(new Date(toTime)));

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
                    String[] s = file.getName().split("\\.");
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
                                    int n = dis.read(data);
                                    if (n == DEBUG_ADV_SIZE) {
                                        LogDebugEntry entry = new LogDebugEntry();
                                        entry.time = t;
                                        entry.debug.setData(data);
                                        ret.add(entry);
                                    } else if (n > 0)
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
            if (debugLogInterval.startTime>toTime || debugLogInterval.endTime<fromTime) {
                return ret;
            }

            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(fileDebug)))){
                while (dis.available() > 0) {
                    t = dis.readLong();
                    if (t >= toTime) {
                        return ret;
                    } else if (t >= fromTime) {
                        int n = dis.read(data);
                        if (n == DEBUG_ADV_SIZE) {
                            LogDebugEntry entry = new LogDebugEntry();
                            entry.time = t;
                            entry.debug.setData(data);
                            ret.add(entry);
                        } else if (n > 0)
                            Log.e(TAG, "getDebugData read error");
                    } else
                        dis.skipBytes(DEBUG_ADV_SIZE);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        return ret;
    }
}