package spider65.ebike.tsdz2_esp32.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import spider65.ebike.tsdz2_esp32.MyApp;
import spider65.ebike.tsdz2_esp32.R;

import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class BluetoothSetupActivity extends AppCompatActivity {

    private static final String TAG = "BluetoothSetupActivity";

    public static final String KEY_DEVICE_NAME = "DEVICE_NAME";
    public static final String KEY_DEVICE_MAC = "DEVICE_MAC";

    private ArrayList<String> deviceList = new ArrayList<>();
    private ArrayList<BluetoothDevice> btDeviceList = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private String selectedDevice;
    private String selectedMac;

    private Button scanButton;
    private TextView deviceTV;

    private static final long SCAN_PERIOD = 10000; // millisecond
    private Handler mHandler = new Handler();

    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner btScanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_setup);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        deviceTV = findViewById(R.id.deviceTextView);
        selectedDevice = MyApp.getPreferences().getString(KEY_DEVICE_NAME, null);
        updateDeviceTV();
        scanButton = findViewById(R.id.scanButton);
        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                scanButton.setEnabled(false);
                startScanning();
            }
        });

        Button okButton = findViewById(R.id.okButton);
        okButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (selectedMac != null) {
                    SharedPreferences.Editor editor = MyApp.getPreferences().edit();
                    editor.putString(KEY_DEVICE_NAME, selectedDevice);
                    editor.putString(KEY_DEVICE_MAC, selectedMac);
                    editor.apply();
                }
                finish();
            }
        });

        Button cancelButton = findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        ListView listView = findViewById(R.id.devicesListView);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedDevice = btDeviceList.get(position).getName();
                selectedMac = btDeviceList.get(position).getAddress();
                updateDeviceTV();
            }
        });

        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void updateDeviceTV() {
        if (selectedDevice != null) {
            deviceTV.setText(selectedDevice);
        }
    }

    public void startScanning() {
        deviceList.clear();
        btDeviceList.clear();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.startScan(leScanCallback);
            }
        });

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScanning();
            }
        }, SCAN_PERIOD);
    }

    public void stopScanning() {
        System.out.println("stopping scanning");
        scanButton.setEnabled(true);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.stopScan(leScanCallback);
            }
        });
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "onScanResult " + result.getDevice().getName());
            String devName = result.getDevice().getName();
            if (devName != null && !devName.isEmpty() && !deviceList.contains(devName)) {
                deviceList.add(devName);
                btDeviceList.add(result.getDevice());
                adapter.notifyDataSetChanged();
            }
        }
        @Override
        public void onScanFailed(int errorCode) {
            Toast.makeText(BluetoothSetupActivity.this, "LE Scan Error", Toast.LENGTH_LONG);
            scanButton.setEnabled(true);
        }
    };
}
