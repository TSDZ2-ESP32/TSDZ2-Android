package spider65.ebike.tsdz2_esp32.activities;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import spider65.ebike.tsdz2_esp32.MyApp;
import spider65.ebike.tsdz2_esp32.R;

import android.os.Handler;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Objects;

import static android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED;

public class BluetoothSetupActivity extends AppCompatActivity {

    private static final String TAG = "BluetoothSetupActivity";

    public static final String KEY_DEVICE_NAME = "DEVICE_NAME";
    public static final String KEY_DEVICE_MAC = "DEVICE_MAC";

    private final ArrayList<String> deviceList = new ArrayList<>();
    private final ArrayList<BluetoothDevice> btDeviceList = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private String selectedDeviceString;
    private BluetoothDevice selectedDevice;

    private Button scanButton;
    private TextView deviceTV;

    private static final long SCAN_PERIOD = 10000; // millisecond
    private final Handler mHandler = new Handler();

    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner btScanner;

    private final IntentFilter filter = new IntentFilter();

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_setup);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);

        deviceTV = findViewById(R.id.deviceTextView);
        scanButton = findViewById(R.id.scanButton);
        scanButton.setOnClickListener(v -> {
            scanButton.setEnabled(false);
            startScanning();
        });

        Button okButton = findViewById(R.id.okButton);
        okButton.setOnClickListener(v -> {
            stopScanning();
            if (selectedDevice != null) {
                if (selectedDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
                    if (!selectedDevice.createBond())
                        exitDialog(getString(R.string.error), getString(R.string.pairing_error));
                } else {
                    // device is already bonded
                    SharedPreferences.Editor editor = MyApp.getPreferences().edit();
                    editor.putString(KEY_DEVICE_NAME, selectedDeviceString);
                    editor.putString(KEY_DEVICE_MAC, selectedDevice.getAddress());
                    editor.apply();
                    BluetoothSetupActivity.this.exitDialog(null, getString(R.string.pairing_done));
                }
            } else
                finish();
        });

        Button cancelButton = findViewById(R.id.exitButton);
        cancelButton.setOnClickListener(v -> {
            stopScanning();
            finish();
        });

        ListView listView = findViewById(R.id.devicesListView);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            selectedDeviceString = deviceList.get(position);
            selectedDevice = btDeviceList.get(position);
            updateDeviceTV();
        });

        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();
        checkDevice();

        filter.addAction(ACTION_BOND_STATE_CHANGED);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mMessageReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mMessageReceiver);
    }

    @SuppressLint("MissingPermission")
    private void checkDevice() {
        String mac = MyApp.getPreferences().getString(KEY_DEVICE_MAC, null);
        if (mac != null) {
            selectedDevice = btAdapter.getRemoteDevice(mac);
            if (selectedDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                selectedDeviceString = MyApp.getPreferences().getString(KEY_DEVICE_NAME, null);
                updateDeviceTV();
                selectedDevice = null;
            }
        }
    }

    private void exitDialog(String title, String message) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (title != null)
            builder.setTitle(title);
        builder.setMessage(message);

        builder.setOnCancelListener((dialog) -> BluetoothSetupActivity.this.finish());
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> BluetoothSetupActivity.this.finish());

        builder.show();
    }

    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //Log.d(TAG, "onReceive " + intent.getAction());
            if (ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                switch (state) {
                    case BluetoothDevice.BOND_BONDED:
                        Log.d(TAG, "ACTION_BOND_STATE_CHANGED: BOND_BONDED");
                        SharedPreferences.Editor editor = MyApp.getPreferences().edit();
                        editor.putString(KEY_DEVICE_NAME, selectedDeviceString);
                        editor.putString(KEY_DEVICE_MAC, selectedDevice.getAddress());
                        editor.apply();
                        BluetoothSetupActivity.this.exitDialog(null, getString(R.string.pairing_done));
                        break;
                    case BluetoothDevice.BOND_NONE:
                        Log.d(TAG, "ACTION_BOND_STATE_CHANGED: BOND_BONDED");
                        BluetoothSetupActivity.this.exitDialog(getString(R.string.error), getString(R.string.pairing_error));
                        break;
                    default:
                        Log.d(TAG, "ACTION_BOND_STATE_CHANGED: state = " + state);
                        break;
                }
            }
        }
    };

    private void updateDeviceTV() {
        if (selectedDeviceString != null) {
            deviceTV.setText(selectedDeviceString);
        }
    }

    @SuppressLint("MissingPermission")
    public void startScanning() {
        deviceList.clear();
        btDeviceList.clear();
        AsyncTask.execute(() -> btScanner.startScan(leScanCallback));

        mHandler.postDelayed(this::stopScanning, SCAN_PERIOD);
    }

    @SuppressLint("MissingPermission")
    public void stopScanning() {
        scanButton.setEnabled(true);
        AsyncTask.execute(() -> btScanner.stopScan(leScanCallback));
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "onScanResult " + result.getDevice().getName() + " - " + result.getDevice().getAddress());
            String devName = result.getDevice().getName();
            if (devName != null && !devName.isEmpty()) {
                devName = devName.concat(" - ").concat(result.getDevice().getAddress());
                if (!deviceList.contains(devName)) {
                    deviceList.add(devName);
                    btDeviceList.add(result.getDevice());
                    adapter.notifyDataSetChanged();
                }
            }
        }
        @Override
        public void onScanFailed(int errorCode) {
            Toast.makeText(BluetoothSetupActivity.this, "LE Scan Error", Toast.LENGTH_LONG).show();
            scanButton.setEnabled(true);
        }
    };
}
