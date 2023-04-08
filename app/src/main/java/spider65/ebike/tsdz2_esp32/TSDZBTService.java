package spider65.ebike.tsdz2_esp32;

import android.annotation.SuppressLint;
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
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.UUID;

import androidx.core.app.NotificationCompat;
import org.greenrobot.eventbus.EventBus;

import spider65.ebike.tsdz2_esp32.data.TSDZ_Config;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static spider65.ebike.tsdz2_esp32.TSDZConst.STATUS_ADV_SIZE;


public class TSDZBTService extends Service {

    private static final String TAG = "TSDZBTService";

    public static String TSDZ_SERVICE = "000000ff-0000-1000-8000-00805f9b34fb";
    public static String TSDZ_CHARACTERISTICS_STATUS = "0000ff01-0000-1000-8000-00805f9b34fb";
    public static String TSDZ_CHARACTERISTICS_CONFIG = "0000ff03-0000-1000-8000-00805f9b34fb";
    public static String TSDZ_CHARACTERISTICS_COMMAND = "0000ff04-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public final static UUID UUID_TSDZ_SERVICE = UUID.fromString(TSDZ_SERVICE);
    public final static UUID UUID_STATUS_CHARACTERISTIC = UUID.fromString(TSDZ_CHARACTERISTICS_STATUS);
    public final static UUID UUID_CONFIG_CHARACTERISTIC = UUID.fromString(TSDZ_CHARACTERISTICS_CONFIG);
    public final static UUID UUID_COMMAND_CHARACTERISTIC = UUID.fromString(TSDZ_CHARACTERISTICS_COMMAND);

    public static final String ADDRESS_EXTRA = "ADDRESS";
    public static final String VALUE_EXTRA = "VALUE";

    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";



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
    private BluetoothGattCharacteristic tsdz_config_char = null;
    private BluetoothGattCharacteristic tsdz_command_char = null;

    public static TSDZBTService getBluetoothService() {
        return mService;
    }

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }

    public enum BTEventType {
        SERVICE_STARTED,
        SERVICE_STOPPED,
        CONNECTION_SUCCESS,
        CONNECTION_FAILURE,
        CONNECTION_LOST,
        TSDZ_STATUS,
        TSDZ_COMMAND,
        TSDZ_CFG_READ,
        TSDZ_CFG_WRITE_OK,
        TSDZ_CFG_WRITE_KO
    }

    /* Event Bus event sent to notify new BT events */
    public static class BTServiceEvent {
        public BTEventType eventType;
        public byte[] data;
        BTServiceEvent(BTEventType type, byte[] data) {
            eventType = type;
            this.data = data;
        }

    }


    public TSDZBTService() {
        Log.d(TAG, "TSDZBTService()");
    }


    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
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
                            EventBus.getDefault().post(new BTServiceEvent(BTEventType.CONNECTION_FAILURE, null));
                            stopSelf();
                        }
                        break;
                    case ACTION_STOP_FOREGROUND_SERVICE:
                        stopped = true;
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
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

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
        PendingIntent pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Action prevAction = new NotificationCompat.Action(android.R.drawable.ic_media_pause, "Disconnect", pendingStopIntent);
        builder.addAction(prevAction);

        // Build the notification.
        Notification notification = builder.build();

        // Start foreground service.
        startForeground(1, notification);

        EventBus.getDefault().post(new BTServiceEvent(BTEventType.SERVICE_STARTED, null));
        mService = this;
    }

    private void stopForegroundService()
    {
        Log.d(TAG, "stopForegroundService");

        // Stop foreground service and remove the notification.
        stopForeground(true);

        EventBus.getDefault().post(new BTServiceEvent(BTEventType.SERVICE_STOPPED, null));
        mService = null;

        // Stop the foreground service.
        stopSelf();
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
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
                        EventBus.getDefault().post(new BTServiceEvent(BTEventType.CONNECTION_LOST, null));
                    }
                else {
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;
                }
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == GATT_SUCCESS) {
                List<BluetoothGattService> services = gatt.getServices();
                Log.i(TAG, "Services: " + services.toString());
                for (BluetoothGattService s:services) {
                    if (s.getUuid().equals(UUID_TSDZ_SERVICE)) {
                        List<BluetoothGattCharacteristic> lc = s.getCharacteristics();
                        for (BluetoothGattCharacteristic c:lc) {
                            if (c.getUuid().equals(UUID_STATUS_CHARACTERISTIC)) {
                                tsdz_status_char = c;
                                Log.d(TAG, "UUID_STATUS_CHARACTERISTIC enable notifications");
                            } else if(c.getUuid().equals(UUID_CONFIG_CHARACTERISTIC)) {
                                tsdz_config_char = c;
                            } else if(c.getUuid().equals(UUID_COMMAND_CHARACTERISTIC)) {
                                tsdz_command_char = c;
                                Log.d(TAG, "UUID_COMMAND_CHARACTERISTIC enable notifications");
                            }
                        }
                    }
                }
                if (tsdz_status_char == null || tsdz_config_char == null || tsdz_command_char == null) {
                    EventBus.getDefault().post(new BTServiceEvent(BTEventType.CONNECTION_FAILURE, null));
                    Log.e(TAG, "onServicesDiscovered Service not found!");
                    disconnect();
                    return;
                }
                // All requests are asynchronous, Before to do a new call we must wait the
                // end of the previous (wait for onMtuChanged() callback)
                mBluetoothGatt.requestMtu(Math.min(Math.max(23, STATUS_ADV_SIZE+3), 512));
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onMtuChanged (BluetoothGatt gatt, int mtu, int status) {
            if ((status != BluetoothGatt.GATT_SUCCESS) || (mtu < (STATUS_ADV_SIZE + 3))) {
                EventBus.getDefault().post(new BTServiceEvent(BTEventType.CONNECTION_FAILURE, null));
                Log.e(TAG, "onMtuChanged: MTU Setup FAILED");
                disconnect();
                return;
            }
            Log.d(TAG, "MTU Configuration success");
            // setCharacteristicNotification is asynchronous. Before to make a new call we
            // must wait the end of the previous (callback onDescriptorWrite)
            setCharacteristicNotification(tsdz_status_char,true);
        }


        @SuppressLint("MissingPermission")
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            Log.d(TAG, "onDescriptorWrite:" + descriptor.getCharacteristic().getUuid().toString() +
                    " - " + descriptor.getUuid().toString() + " - " + status);
            if (status == GATT_SUCCESS) {
                if (descriptor.getUuid().equals(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG))) {
                    boolean enable = (descriptor.getValue()[0] & 0x01) == 1;
                    //Log.d(TAG, "onDescriptorWrite: value = " + descriptor.getValue()[0]);
                    if (descriptor.getCharacteristic().getUuid().equals(UUID_STATUS_CHARACTERISTIC))
                        setCharacteristicNotification(tsdz_command_char,enable);
                    else if (descriptor.getCharacteristic().getUuid().equals(UUID_COMMAND_CHARACTERISTIC))
                        if (mConnectionState == ConnectionState.CONNECTING) {
                            mConnectionState = ConnectionState.CONNECTED;
                            EventBus.getDefault().post(new BTServiceEvent(BTEventType.CONNECTION_SUCCESS, null));
                        } else {
                            // DISCONNECTING
                            mBluetoothGatt.disconnect();
                        }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            //Log.d(TAG, "onCharacteristicRead:" + characteristic.getUuid().toString());
            if (status == GATT_SUCCESS) {
                if (UUID_CONFIG_CHARACTERISTIC.equals(characteristic.getUuid())) {
                    EventBus.getDefault().post(new BTServiceEvent(BTEventType.TSDZ_CFG_READ, characteristic.getValue()));
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
                    EventBus.getDefault().post(new BTServiceEvent(BTEventType.TSDZ_STATUS, data));
                } else {
                    Log.e(TAG, "Wrong Status Advertising Size: " + data.length);
                }
            } else if (UUID_COMMAND_CHARACTERISTIC.equals(characteristic.getUuid())) {
                EventBus.getDefault().post(new BTServiceEvent(BTEventType.TSDZ_COMMAND, data));
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                         int status) {
            //Log.d(TAG, "onCharacteristicWrite:" + characteristic.getUuid().toString());
            if (UUID_CONFIG_CHARACTERISTIC.equals(characteristic.getUuid())) {
                if (status == GATT_SUCCESS)
                    EventBus.getDefault().post(new BTServiceEvent(BTEventType.TSDZ_CFG_WRITE_OK, null));
                else
                    EventBus.getDefault().post(new BTServiceEvent(BTEventType.TSDZ_CFG_WRITE_KO, null));
            }
        }
    };

    @SuppressLint("MissingPermission")
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
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback, TRANSPORT_LE);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = ConnectionState.CONNECTING;
        return true;
    }

    @SuppressLint("MissingPermission")
    private void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        if (mConnectionState == ConnectionState.CONNECTED) {
            mConnectionState = ConnectionState.DISCONNECTING;
            setCharacteristicNotification(tsdz_status_char,false);
        } else
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
    @SuppressLint("MissingPermission")
    public void readCfg() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null || tsdz_command_char == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(tsdz_config_char);
    }

    @SuppressLint("MissingPermission")
    public void writeCfg(TSDZ_Config cfg) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null || tsdz_command_char == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        tsdz_config_char.setValue(cfg.toByteArray());
        mBluetoothGatt.writeCharacteristic(tsdz_config_char);
    }

    @SuppressLint("MissingPermission")
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
    @SuppressLint("MissingPermission")
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
        if (enabled)
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        else
            descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
    }
}