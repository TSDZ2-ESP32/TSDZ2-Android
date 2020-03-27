package spider65.ebike.tsdz2_esp32;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.util.List;
import java.util.UUID;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import spider65.ebike.tsdz2_esp32.data.DebugBuffer;
import spider65.ebike.tsdz2_esp32.data.LogDataFile;
import spider65.ebike.tsdz2_esp32.data.StatusBuffer;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Config;

import static spider65.ebike.tsdz2_esp32.TSDZConst.DEBUG_ADV_SIZE;
import static spider65.ebike.tsdz2_esp32.TSDZConst.STATUS_ADV_SIZE;


public class TSDZBTService extends Service {

    private static final String TAG = "TSDZBTService";

    public static final int MSG_STATUS_LOG = 1;
    public static final int MSG_DEBUG_LOG = 2;

    public static String TSDZ_SERVICE = "000000ff-0000-1000-8000-00805f9b34fb";
    public static String TSDZ_CHARACTERISTICS_STATUS = "0000ff01-0000-1000-8000-00805f9b34fb";
    public static String TSDZ_CHARACTERISTICS_DEBUG = "0000ff02-0000-1000-8000-00805f9b34fb";
    public static String TSDZ_CHARACTERISTICS_CONFIG = "0000ff03-0000-1000-8000-00805f9b34fb";
    public static String TSDZ_CHARACTERISTICS_COMMAND = "0000ff04-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public final static UUID UUID_TSDZ_SERVICE = UUID.fromString(TSDZ_SERVICE);
    public final static UUID UUID_STATUS_CHARACTERISTIC = UUID.fromString(TSDZ_CHARACTERISTICS_STATUS);
    public final static UUID UUID_DEBUG_CHARACTERISTIC = UUID.fromString(TSDZ_CHARACTERISTICS_DEBUG);
    public final static UUID UUID_CONFIG_CHARACTERISTIC = UUID.fromString(TSDZ_CHARACTERISTICS_CONFIG);
    public final static UUID UUID_COMMAND_CHARACTERISTIC = UUID.fromString(TSDZ_CHARACTERISTICS_COMMAND);

    public static final String ADDRESS_EXTRA = "ADDRESS";
    public static final String VALUE_EXTRA = "VALUE";

    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";

    public static final String SERVICE_STARTED_BROADCAST = "SERVICE_STARTED";
    public static final String SERVICE_STOPPED_BROADCAST = "SERVICE_STOPPED";
    public static final String CONNECTION_SUCCESS_BROADCAST = "CONNECTION_SUCCESS";
    public static final String CONNECTION_FAILURE_BROADCAST = "CONNECTION_FAILURE";
    public static final String CONNECTION_LOST_BROADCAST = "CONNECTION_LOST";
    public static final String TSDZ_STATUS_BROADCAST = "TSDZ_STATUS";
    public static final String TSDZ_DEBUG_BROADCAST = "TSDZ_DEBUG";
    public static final String TSDZ_COMMAND_BROADCAST = "TSDZ_COMMAND";
    public static final String TSDZ_CFG_READ_BROADCAST = "TSDZ_CFG_READ";
    public static final String TSDZ_CFG_WRITE_BROADCAST = "TSDZ_CFG_WRITE";

    private static final int MAX_CONNECTION_RETRY = 10;
    private static TSDZBTService mService = null;

    private BluetoothAdapter mBluetoothAdapter;
    private String address;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private ConnectionState mConnectionState = ConnectionState.DISCONNECTED;

    private boolean stopped = false;
    private int connectionRetry = 0;

    private BluetoothGattCharacteristic tsdz_status_char = null;
    private BluetoothGattCharacteristic tsdz_debug_char = null;
    private BluetoothGattCharacteristic tsdz_config_char = null;
    private BluetoothGattCharacteristic tsdz_command_char = null;

    private Handler mHandler = null;
    private final LogDataFile mLogDataFile;

    public static TSDZBTService getBluetoothService() {
        return mService;
    }

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    public TSDZBTService() {
        Log.d(TAG, "TSDZBTService()");
        mLogDataFile = LogDataFile.getLogDataFile();
    }


    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        // to avoid performance issues (e.g. log file switch can take some time), data logging
        // is asyncronous and handled by a dedicated thread
        final HandlerThread mHandlerThread = new HandlerThread("LogDataThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg){
                switch (msg.what) {
                    case MSG_STATUS_LOG:
                        StatusBuffer sb = (StatusBuffer)msg.obj;
                        mLogDataFile.addStatusData(sb.startTime, sb.endTime, sb.data, sb.position);
                        StatusBuffer.recycle(sb);
                        break;
                    case MSG_DEBUG_LOG:
                        DebugBuffer db = (DebugBuffer)msg.obj;
                        mLogDataFile.addDebugData(db.startTime, db.endTime, db.data, db.position);
                        DebugBuffer.recycle(db);
                        break;
                }
            }
        };
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (mHandler != null) {
            mHandler.getLooper().quitSafely();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // A client is binding to the service with bindService()
        throw new UnsupportedOperationException();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        if(intent != null)
        {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_START_FOREGROUND_SERVICE:
                        address = intent.getStringExtra(ADDRESS_EXTRA);
                        if ((address != null) && connect(address))
                            startForegroundService();
                        else {
                            disconnect();
                            stopped = true;
                            Intent bi = new Intent(CONNECTION_FAILURE_BROADCAST);
                            LocalBroadcastManager.getInstance(this).sendBroadcast(bi);
                            stopSelf();
                        }
                        break;
                    case ACTION_STOP_FOREGROUND_SERVICE:
                        stopped = true;
                        flushLogs();
                        disconnect();
                        stopForegroundService();
                        break;
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /* Used to build and start foreground service. */
    private void startForegroundService()
    {
        Log.d(TAG, "startForegroundService");

        // Create notification default intent.
        Intent intent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        String channelId = getString(R.string.app_name);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setDescription(channelId);
            NotificationManager service = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            service.createNotificationChannel(notificationChannel);
        }

        // Create notification builder.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId);
        builder.setContentTitle(getText(R.string.notification_title));
        //builder.setContentText(getText(R.string.notification_message));
        builder.setTicker(getText(R.string.notification_title));
        builder.setWhen(System.currentTimeMillis());
        builder.setSmallIcon(R.drawable.ic_bike_notification);
        builder.setPriority(Notification.PRIORITY_DEFAULT);
        builder.setContentIntent(pendingIntent);

        // Add Disconnect button intent in notification.
        Intent stopIntent = new Intent(this, TSDZBTService.class);
        stopIntent.setAction(ACTION_STOP_FOREGROUND_SERVICE);
        PendingIntent pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, 0);
        NotificationCompat.Action prevAction = new NotificationCompat.Action(android.R.drawable.ic_media_pause, "Disconnect", pendingStopIntent);
        builder.addAction(prevAction);

        // Build the notification.
        Notification notification = builder.build();

        // Start foreground service.
        startForeground(1, notification);

        Intent bi = new Intent(SERVICE_STARTED_BROADCAST);
        LocalBroadcastManager.getInstance(this).sendBroadcast(bi);
        mService = this;
    }

    private void stopForegroundService()
    {
        Log.d(TAG, "stopForegroundService");

        // Stop foreground service and remove the notification.
        stopForeground(true);

        // Stop the foreground service.
        stopSelf();

        Intent bi = new Intent(SERVICE_STOPPED_BROADCAST);
        LocalBroadcastManager.getInstance(this).sendBroadcast(bi);
        mService = null;
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = ConnectionState.CONNECTED;
                connectionRetry = 0;
                Log.i(TAG, "onConnectionStateChange: Connected");
                // Discover services after successful connection.
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = ConnectionState.DISCONNECTED;
                Log.i(TAG, "onConnectionStateChange: Disconnected");
                if (!stopped)
                    if (connectionRetry++ > MAX_CONNECTION_RETRY) {
                        disconnect();
                        stopForegroundService();
                    } else {
                        connect(address);
                        Intent bi = new Intent(CONNECTION_LOST_BROADCAST);
                        LocalBroadcastManager.getInstance(TSDZBTService.this).sendBroadcast(bi);
                    }
                else {
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> services = gatt.getServices();
                Log.i(TAG, "Services: " + services.toString());
                for (BluetoothGattService s:services) {
                    if (s.getUuid().equals(UUID_TSDZ_SERVICE)) {
                        List<BluetoothGattCharacteristic> lc = s.getCharacteristics();
                        for (BluetoothGattCharacteristic c:lc) {
                            if (c.getUuid().equals(UUID_STATUS_CHARACTERISTIC)) {
                                tsdz_status_char = c;
                                Log.d(TAG, "UUID_STATUS_CHARACTERISTIC enable notifications");
                            } else if(c.getUuid().equals(UUID_DEBUG_CHARACTERISTIC)) {
                                tsdz_debug_char = c;
                                Log.d(TAG, "UUID_DEBUG_CHARACTERISTIC enable notifications");
                            } else if(c.getUuid().equals(UUID_CONFIG_CHARACTERISTIC)) {
                                tsdz_config_char = c;
                            } else if(c.getUuid().equals(UUID_COMMAND_CHARACTERISTIC)) {
                                tsdz_command_char = c;
                                Log.d(TAG, "UUID_COMMAND_CHARACTERISTIC enable notifications");
                            }
                        }
                    }
                }
                if (tsdz_status_char == null || tsdz_debug_char == null || tsdz_config_char == null
                        || tsdz_command_char == null) {
                    Intent bi = new Intent(CONNECTION_FAILURE_BROADCAST);
                    // TODO bi.putExtra("MESSAGE", "Error Detail");
                    LocalBroadcastManager.getInstance(TSDZBTService.this).sendBroadcast(bi);
                    Log.e(TAG, "onServicesDiscovered Characteristic not found!");
                    disconnect();
                    return;
                }
                // setCharacteristicNotification is asynchronous. Before to make a new call we
                // must wait the end of the previous in the callback onDescriptorWrite
                setCharacteristicNotification(tsdz_status_char,true);
                Intent bi = new Intent(CONNECTION_SUCCESS_BROADCAST);
                LocalBroadcastManager.getInstance(TSDZBTService.this).sendBroadcast(bi);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            //Log.d(TAG, "onDescriptorWrite:" + descriptor.getCharacteristic().getUuid().toString() +
            //        " - " + descriptor.getUuid().toString());
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.getUuid().equals(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG))) {
                    boolean enable = (descriptor.getValue()[0] & 0x01) == 1;
                    //Log.d(TAG, "onDescriptorWrite: value = " + descriptor.getValue()[0]);
                    if (descriptor.getCharacteristic().getUuid().equals(UUID_STATUS_CHARACTERISTIC))
                        setCharacteristicNotification(tsdz_debug_char,enable);
                    else if (descriptor.getCharacteristic().getUuid().equals(UUID_DEBUG_CHARACTERISTIC))
                        setCharacteristicNotification(tsdz_command_char,enable);
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            //Log.d(TAG, "onCharacteristicRead:" + characteristic.getUuid().toString());
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (UUID_CONFIG_CHARACTERISTIC.equals(characteristic.getUuid())) {
                    Intent bi = new Intent(TSDZ_CFG_READ_BROADCAST);
                    bi.putExtra(VALUE_EXTRA, characteristic.getValue());
                    LocalBroadcastManager.getInstance(TSDZBTService.this).sendBroadcast(bi);
                }
            } else {
                Log.e(TAG, "Characteristic read Error: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            //Log.d(TAG, "onCharacteristicChanged:" + characteristic.getUuid().toString());
            byte [] data = characteristic.getValue();
            if (UUID_STATUS_CHARACTERISTIC.equals(characteristic.getUuid())) {
                if (data.length == STATUS_ADV_SIZE) {
                    Intent bi = new Intent(TSDZ_STATUS_BROADCAST);
                    bi.putExtra(VALUE_EXTRA, data);
                    LocalBroadcastManager.getInstance(TSDZBTService.this).sendBroadcast(bi);
                    sendStatusLog(data);
                } else {
                    Log.e(TAG, "Wrong Status Advertising Size: " + data.length);
                }
            } else if (UUID_DEBUG_CHARACTERISTIC.equals(characteristic.getUuid())) {
                if (data.length == DEBUG_ADV_SIZE) {
                    Intent bi = new Intent(TSDZ_DEBUG_BROADCAST);
                    bi.putExtra(VALUE_EXTRA, characteristic.getValue());
                    LocalBroadcastManager.getInstance(TSDZBTService.this).sendBroadcast(bi);
                    sendDebugLog(data);
                } else {
                    Log.e(TAG, "Wrong Debug Advertising Size: " + data.length);
                }
            } else if (UUID_COMMAND_CHARACTERISTIC.equals(characteristic.getUuid())) {
                Intent bi = new Intent(TSDZ_COMMAND_BROADCAST);
                bi.putExtra(VALUE_EXTRA, characteristic.getValue());
                LocalBroadcastManager.getInstance(TSDZBTService.this).sendBroadcast(bi);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                         int status) {
            //Log.d(TAG, "onCharacteristicWrite:" + characteristic.getUuid().toString());
            if (UUID_CONFIG_CHARACTERISTIC.equals(characteristic.getUuid())) {
                Intent bi = new Intent(TSDZ_CFG_WRITE_BROADCAST);
                if (status == BluetoothGatt.GATT_SUCCESS)
                    bi.putExtra(VALUE_EXTRA, true);
                else
                    bi.putExtra(VALUE_EXTRA, false);
                LocalBroadcastManager.getInstance(TSDZBTService.this).sendBroadcast(bi);
            }
        }
    };

    private StatusBuffer statusBuffer = null;
    private DebugBuffer debugBuffer = null;
    private void sendStatusLog(byte[] data) {
        if (stopped)
            return;
        // statusBuffer could be overwritten if a new notification arrives before statusBuffer
        // is written to the log. To avoid this potential problem, a recycling buffer pool is used.
        if (statusBuffer == null)
            statusBuffer = StatusBuffer.obtain();
        if (statusBuffer.addRecord(data)) {
            Message msg = mHandler.obtainMessage(MSG_STATUS_LOG, statusBuffer);
            mHandler.sendMessage(msg);
            statusBuffer = StatusBuffer.obtain();
        }
    }
    private void sendDebugLog(byte[] data) {
        if (stopped)
            return;
        // debugBuffer could be overwritten if a new notification arrives before debugBuffer
        // is written to the log. To avoid this potential problem, a recycling buffer pool is used.
        if (debugBuffer == null)
            debugBuffer = DebugBuffer.obtain();
        if (debugBuffer.addRecord(data)) {
            Message msg = mHandler.obtainMessage(MSG_DEBUG_LOG, debugBuffer);
            mHandler.sendMessage(msg);
            debugBuffer = DebugBuffer.obtain();
        }
    }
    private void flushLogs() {
        Log.d(TAG, "flushLogs");
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
    }

    private boolean connect(String address) {
        Log.d(TAG, "connect");
        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Previously connected device.  Try to reconnect.
        if (address.equals(mBluetoothDeviceAddress)  && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = ConnectionState.CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = ConnectionState.CONNECTING;
        return true;
    }

    private void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        if (mConnectionState != ConnectionState.DISCONNECTED)
            mBluetoothGatt.disconnect();
    }

    public ConnectionState getConnectionStatus() {
        return mConnectionState;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     */
    public void readCfg() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null || tsdz_command_char == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(tsdz_config_char);
    }

    public void writeCfg(TSDZ_Config cfg) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null || tsdz_command_char == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        tsdz_config_char.setValue(cfg.toByteArray());
        mBluetoothGatt.writeCharacteristic(tsdz_config_char);
    }

    public void writeCommand(byte[] command) {
        //Log.d(TAG,"Sending command: " + Utils.bytesToHex(command));
        if (mBluetoothAdapter == null || mBluetoothGatt == null || tsdz_command_char == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        tsdz_command_char.setValue(command);
        mBluetoothGatt.writeCharacteristic(tsdz_command_char) ;
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
    }
}