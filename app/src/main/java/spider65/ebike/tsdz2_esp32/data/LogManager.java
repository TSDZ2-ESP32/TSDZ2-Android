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

import static spider65.ebike.tsdz2_esp32.TSDZConst.STATUS_ADV_SIZE;

public class LogManager {

    public interface LogResultListener {
        void logIntervalsResult(List<TimeInterval> intervals);
        void logDataResult(List<LogStatusEntry> statusList);
    }

    public static class LogStatusEntry {
        public TSDZ_Status status = new TSDZ_Status();
        public long time;
    }

    public static class TimeInterval {
        public long startTime, endTime;
        TimeInterval(long t1, long t2) {
            startTime = t1;
            endTime = t2;
        }
    }

    private static final int MSG_STATUS_LOG = 1;
    private static final int MSG_QUERY_DATA = 3;
    private static final int MSG_QUERY_INTERVALS = 4;

    private static final String TAG = "LogManager";
    private static final String STATUS_LOG_FILENAME = "status";

    // max time interval between two log entries in a single log file. If more, a new log file is started.
    private static final long MAX_LOG_PAUSE = 1000 * 60 * 20;
    // Max nr of Log entries
    private static final int MAX_LOG_FILES = 20;
    // if there are more than MAX_LOG_FILES log entries, delete the entries older than 7 days (in msec)
    private static final long MAX_LOG_HISTORY = 1000 * 60 * 60 * 24 * 7;
    // max single log file time is 6 hours (in msec)
    private static final long MAX_FILE_HISTORY = 1000 * 60 * 60 * 6;

    private static LogManager mLogManager = null;

    // loq query listener
    private LogResultListener mListener;

    private File fileStatus;
    private RandomAccessFile rafStatus;
    private TimeInterval statusLogInterval;

    // Timestamp of last saved log record
    private long lastStatusTimestamp = 0;
    // current log buffer
    private StatusBuffer statusBuffer = null;

    private final Handler mHandler;

    public static LogManager initLogs() {
        if (mLogManager == null) {
            mLogManager = new LogManager();
        }
        return mLogManager;
    }

    private LogManager() {
        final IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(TSDZBTService.CONNECTION_LOST_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.SERVICE_STOPPED_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.TSDZ_STATUS_BROADCAST);

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
                    case MSG_QUERY_DATA:
                        List<LogStatusEntry> statusList = getStatusData(msg.arg1,msg.arg2);
                        synchronized (this) {
                            if (mListener != null)
                                mListener.logDataResult(statusList);
                        }
                        break;
                    case MSG_QUERY_INTERVALS:
                        List<TimeInterval> result = getLogIntervals();
                        synchronized (this) {
                            if (mListener != null)
                                mListener.logIntervalsResult(result);
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
                    case TSDZBTService.CONNECTION_LOST_BROADCAST:
                        Log.d(TAG, "CONNECTION_LOST_BROADCAST - flush Logs");
                    case TSDZBTService.SERVICE_STOPPED_BROADCAST:
                        Log.d(TAG, "SERVICE_STOPPED_BROADCAST - flush Logs");
                        if (statusBuffer != null) {
                            Message msg = mHandler.obtainMessage(MSG_STATUS_LOG, statusBuffer);
                            mHandler.sendMessage(msg);
                            statusBuffer = null;
                        }
                        break;
                    case TSDZBTService.TSDZ_STATUS_BROADCAST:
                        now = System.currentTimeMillis();
                        // log 1 msg/sec
                        if ((now - lastStatusTimestamp) <= 900)
                            return;
                        lastStatusTimestamp = now;
                        data = intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA);
                        if (data.length != STATUS_ADV_SIZE) {
                            Log.w(TAG, "TSDZ_STATUS_BROADCAST: Wrong data size!");
                            return;
                        }
                        // StatusBuffer could be overwritten if a new notification arrives before statusBuffer
                        // is written to the log. To avoid this potential problem, a recycling buffer pool is used.
                        if (statusBuffer == null)
                            statusBuffer = StatusBuffer.obtain();
                        if (statusBuffer.addRecord(data, now)) {
                            // statusBuffer is full, write it to disk
                            Message msg = mHandler.obtainMessage(MSG_STATUS_LOG, statusBuffer);
                            mHandler.sendMessage(msg);
                            statusBuffer = null;
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

    public void queryLogIntervals() {
        if (statusBuffer != null) {
            Message msg = mHandler.obtainMessage(MSG_STATUS_LOG, statusBuffer);
            mHandler.sendMessage(msg);
            statusBuffer = null;
        }
        Message msg = mHandler.obtainMessage(MSG_QUERY_INTERVALS);
        mHandler.sendMessage(msg);
    }

    public void queryLogData(int minuteFrom, int minuteTo) {
        if (statusBuffer != null) {
            Message msg = mHandler.obtainMessage(MSG_STATUS_LOG, statusBuffer);
            mHandler.sendMessage(msg);
            statusBuffer = null;
        }
        Message msg = mHandler.obtainMessage(MSG_QUERY_DATA, minuteFrom, minuteTo);
        mHandler.sendMessage(msg);
    }

    // Return a sorted array of status or debug files according to the prefix parameter
    // first is the older, last is the newer
    private File[] getFiles(String prefix) {
        final File folder = MyApp.getInstance().getFilesDir();
        final File[] files = folder.listFiles( (dir, name) ->
                name.matches( prefix + ".log\\.\\d+\\.\\d+$" ));
        if (files != null)
            Arrays.sort(files, (f1, f2) -> {
                final long t1 = Long.parseLong(f1.getName().substring(f1.getName().lastIndexOf('.') + 1));
                final long t2 = Long.parseLong(f2.getName().substring(f2.getName().lastIndexOf('.') + 1));
                if (t1 > t2)
                    return 1;
                else if (t1 < t2)
                    return -1;
                return 0;
            });

        return files;
    }

    private void saveStatusLog(long startTime, long endTime, byte[] data, int length) {
        Log.d(TAG, "saveStatusLog");

        if (startTime == 0 || endTime == 0 || length == 0)
            return;

        // check if log was paused for more than MAX_LOG_PAUSE
        // or the file log interval is more than MAX_FILE_HISTORY
        // if yes, start a new log file
        if ((statusLogInterval.endTime != 0) &&
                (((startTime - statusLogInterval.endTime)   > MAX_LOG_PAUSE) ||
                 ((startTime - statusLogInterval.startTime) > MAX_FILE_HISTORY)))
            swapStatusFile();

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

    private void swapStatusFile() {
        Log.d(TAG, "swapStatusFile");
        final File[] files = getFiles(STATUS_LOG_FILENAME);

        // If there are more than MAX_LOG_FILES, remove log file with all data older than MAX_LOG_HISTORY
        if ((files != null) && (files.length > MAX_LOG_FILES)) {
            long now = System.currentTimeMillis();
            for (int i = 0; i < (files.length - MAX_LOG_FILES); i++) {
                File f = files[i];
                long endTime = Long.parseLong(f.getName().substring(f.getName().lastIndexOf('.') + 1));
                if ((now - endTime) > MAX_LOG_HISTORY) {
                    Log.d(TAG,"Removing file: "+f.getName()+" File end Time:"+endTime+" now="+now);
                    if (!f.delete()) {
                        Log.e(TAG, "Can't remove " + f.getAbsolutePath());
                    }
                }
            }
        }
        // rename current log file to status.log.startTime.endTime and
        // create the new log file status.log
        File f2 = new File(MyApp.getInstance().getFilesDir(), STATUS_LOG_FILENAME+".log." +
                statusLogInterval.startTime + "." + statusLogInterval.endTime);
        try {
            rafStatus.close();
            Log.d(TAG,"Renaming file: "+fileStatus.getName()+" to:"+f2.getName());
            if (!fileStatus.renameTo(f2))
                Log.e(TAG,"Failed to rename file to " + f2.getName() );
            newStatusLogFile();
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
                Log.d(TAG,"initLogFiles: status.log found. startTime=" +
                        sdf.format(new Date(statusLogInterval.startTime)) + " endTime=" +
                        sdf.format(new Date(statusLogInterval.endTime)));
            } catch (Exception e) {
               Log.e(TAG,"initLogFiles", e);
            }
        } else {
            // create new status.log file
            newStatusLogFile();
        }
    }

    // Verify if the log file is correctly aligned and return the first and last log timestamps
    private TimeInterval checkLogFile(@NotNull RandomAccessFile f, long logEntrySize) throws IOException {
        TimeInterval ret = new TimeInterval(0,0);
        long l = f.length();

        // Check if the Log File is aligned and truncate if not
        if ((l % logEntrySize) > 0) {
            Log.w(TAG,"checkLogFile: file size wrong!");
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

    private static final SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ITALY);

    private ArrayList<TimeInterval> getLogIntervals () {
        ArrayList<TimeInterval> ret = new ArrayList<>();
        TimeInterval ti;
        try {
            final File[] files = getFiles(STATUS_LOG_FILENAME);
            long startTime, endTime;
            if (files != null) {
                for (File file : files) {
                    String[] s = file.getName().split("\\.");
                    startTime = Long.parseLong(s[2]);
                    endTime = Long.parseLong(s[3]);
                    //Log.d(TAG, "File: " + file.getName() + " from:" + startTime + " to:" + endTime);
                    ti = new TimeInterval(startTime, endTime);
                    //Log.d(TAG, "getLogIntervals: New Interval from " + ti.startTime + " to " + ti.endTime);
                    ret.add(0,ti);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

        if (statusLogInterval.startTime != 0) {
            ti = new TimeInterval(statusLogInterval.startTime, statusLogInterval.endTime);
            //Log.d(TAG, "getLogIntervals: New Interval from " + ti.startTime + " to " + ti.endTime);
            ret.add(0,ti);
        }
        return ret;
    }

    private ArrayList<LogStatusEntry> getStatusData (int fromMinute, int toMinute) {
        ArrayList<LogStatusEntry> ret = new ArrayList<>();
        byte[] data = new byte[STATUS_ADV_SIZE];
        long fromTime = (long)fromMinute * 60L * 1000L;
        long toTime = (long)toMinute * 60L * 1000L;
        Log.d(TAG, "getStatusData - fromTime=" + sdf.format(new Date(fromTime)) + " toTime=" + sdf.format(new Date(toTime)));

        long t;
        // check if requested interval is in current log file
        if ((statusLogInterval.startTime != 0) &&
                (fromTime <= statusLogInterval.endTime && 
                        toTime >= statusLogInterval.startTime)) {
            try {
                try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(fileStatus)))){
                    while (dis.available() > 0) {
                        t = dis.readLong();
                        if (t > toTime) {
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

        final File[] files = getFiles(STATUS_LOG_FILENAME);
        long startTime, endTime;
        if (files != null) {
            for (File file : files) {
                String[] s = file.getName().split("\\.");
                startTime = Long.parseLong(s[2]);
                endTime   = Long.parseLong(s[3]);
                if (fromTime <= endTime && toTime >= startTime) {
                    try {
                        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                            while (dis.available() > 0) {
                                t = dis.readLong();
                                if (t > toTime) {
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
            }
        }
        return ret;
    }
}