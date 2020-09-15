package spider65.ebike.tsdz2_esp32;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;
import spider65.ebike.tsdz2_esp32.activities.BluetoothSetupActivity;
import spider65.ebike.tsdz2_esp32.activities.ChartActivity;
import spider65.ebike.tsdz2_esp32.activities.ESP32ConfigActivity;
import spider65.ebike.tsdz2_esp32.activities.TSDZCfgActivity;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Debug;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Status;
import spider65.ebike.tsdz2_esp32.ota.Esp32_Ota;
import spider65.ebike.tsdz2_esp32.ota.Stm8_Ota;
import spider65.ebike.tsdz2_esp32.utils.OnSwipeListener;

import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static java.util.Arrays.copyOfRange;
import static spider65.ebike.tsdz2_esp32.TSDZConst.CMD_GET_APP_VERSION;
import static spider65.ebike.tsdz2_esp32.TSDZConst.DEBUG_ADV_SIZE;
import static spider65.ebike.tsdz2_esp32.TSDZConst.STATUS_ADV_SIZE;
import static spider65.ebike.tsdz2_esp32.activities.BluetoothSetupActivity.KEY_DEVICE_MAC;


public class MainActivity extends AppCompatActivity implements View.OnTouchListener {

    private static final String TAG = "MainActivity";

    private static final String KEY_SCREEN_ON = "SCREEN_ON";

    private TextView mTitle;
    private boolean serviceRunning;
    private  FloatingActionButton fabButton;
    private MainPagerAdapter mainPagerAdapter;

    private static final int REQUEST_ENABLE_BLUETOOTH = 0;
    private static final int APP_PERMISSION_REQUEST = 1;

    IntentFilter mIntentFilter = new IntentFilter();

    private ViewPager viewPager;
    private byte[] lastStatusData = new byte[STATUS_ADV_SIZE];
    private byte[] lastDebugData = new byte[DEBUG_ADV_SIZE];

    private TSDZ_Status status = new TSDZ_Status();
    private TSDZ_Debug debug = new TSDZ_Debug();

    private TextView modeLevelTV;
    private TextView statusTV;
    private ImageView brakeIV;
    private ImageView streetModeIV;

    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_main);

        boolean screenOn = MyApp.getPreferences().getBoolean(KEY_SCREEN_ON, false);
        if (screenOn)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mainPagerAdapter = new MainPagerAdapter(this, getSupportFragmentManager(), status, debug);
        viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(mainPagerAdapter);
        viewPager.setOnTouchListener(this);

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 0:
                        mTitle.setText(R.string.status);
                        break;
                    case 1:
                        mTitle.setText(R.string.debug);
                        break;
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        });

        gestureDetector = new GestureDetector(this,new OnSwipeListener(){
            @Override
            public boolean onSwipe(Direction direction) {
                if (direction==Direction.up){
                    // Log.d(TAG, "onSwipe: up");
                    Intent myIntent = new Intent(MainActivity.this, ChartActivity.class);
                    MainActivity.this.startActivity(myIntent);
                    return false;
                }
                if (direction==Direction.down){
                    Log.d(TAG, "onSwipe: down");
                    return false;
                }
                return false;
            }
        });

        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayShowTitleEnabled(false);
        mTitle = toolbar.findViewById(R.id.toolbar_title);
        mTitle.setText(R.string.status);

        modeLevelTV = findViewById(R.id.modeLevelTV);
        statusTV = findViewById(R.id.statusTV);
        statusTV.setOnClickListener(v -> {
            int val;
            try {
                val = Integer.parseInt(((TextView) v).getText().toString());
            } catch (NumberFormatException e) {
                return;
            }
            String title = null ,message = null;
            switch (val) {
                case TSDZConst.ERROR_MOTOR_BLOCKED:
                    title = getString(R.string.error_motor_blocked);
                    message = getString(R.string.check_motor_blocked);
                    break;
                case TSDZConst.ERROR_TORQUE_SENSOR:
                    title = getString(R.string.error_torque_sensor);
                    message = getString(R.string.check_torque_sensor);
                    break;
                case TSDZConst.ERROR_LOW_CONTROLLER_VOLTAGE:
                    title = getString(R.string.error_low_voltage);
                    message = getString(R.string.check_low_voltage);
                    break;
                case TSDZConst.ERROR_OVERVOLTAGE:
                    title = getString(R.string.error_high_voltage);
                    message = getString(R.string.check_high_voltage);
                    break;
                case TSDZConst.ERROR_TEMPERATURE_LIMIT:
                    title = getString(R.string.error_limit_temperature);
                    message = getString(R.string.check_limit_temperature);
                    break;
                case TSDZConst.ERROR_TEMPERATURE_MAX:
                    title = getString(R.string.error_stop_temperature);
                    message = getString(R.string.check_stop_temperature);
                    break;
            }
            if (title != null) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(title);
                builder.setMessage(message);
                builder.show();
            }
        });
        brakeIV = findViewById(R.id.brakeIV);
        streetModeIV = findViewById(R.id.streetModeIV);
        registerForContextMenu(streetModeIV);

        fabButton = findViewById(R.id.fab);
        fabButton.setOnClickListener((View) -> {
            if (!checkDevice()) {
                Toast.makeText(this, "Please select the bluetooth device to connect", Toast.LENGTH_LONG).show();
                return;
            }
            Intent intent = new Intent(MainActivity.this, TSDZBTService.class);
            if (serviceRunning) {
                intent.setAction(TSDZBTService.ACTION_STOP_FOREGROUND_SERVICE);
            } else{
                intent.setAction(TSDZBTService.ACTION_START_FOREGROUND_SERVICE);
                intent.putExtra(TSDZBTService.ADDRESS_EXTRA, MyApp.getPreferences().getString(KEY_DEVICE_MAC, null));
            }
            if (Build.VERSION.SDK_INT >= 26)
                startForegroundService(intent);
            else
                startService(intent);
        });

        checkPermissions();

        mIntentFilter.addAction(TSDZBTService.SERVICE_STARTED_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.SERVICE_STOPPED_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.CONNECTION_SUCCESS_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.CONNECTION_FAILURE_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.CONNECTION_LOST_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.TSDZ_COMMAND_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.TSDZ_STATUS_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.TSDZ_DEBUG_BROADCAST);

        checkBT();
    }

    @Override
    public void onCreateContextMenu (ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId() != R.id.streetModeIV)
            return;
        TSDZBTService service = TSDZBTService.getBluetoothService();
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED)
            return;

        // create context menu for Street Mode Icon long press
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_street_mode, menu);
        menu.setHeaderTitle(getResources().getString(R.string.street_mode));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item){
        // manage Street Mode icon context menu selection
        TSDZBTService service = TSDZBTService.getBluetoothService();
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED)
            return false;

        switch (item.getItemId()) {
            case R.id.lcd_master:
                service.writeCommand(new byte[] {TSDZConst.CMD_STREET_MODE, TSDZConst.STREET_MODE_LCD_MASTER});
                break;
            case R.id.force_off:
                service.writeCommand(new byte[] {TSDZConst.CMD_STREET_MODE, TSDZConst.STREET_MODE_FORCE_OFF});
                break;
            case R.id.force_on:
                service.writeCommand(new byte[] {TSDZConst.CMD_STREET_MODE, TSDZConst.STREET_MODE_FORCE_ON});
                break;
            default:
                return false;
        }
        return true;
    }

    private boolean checkDevice() {
        String mac = MyApp.getPreferences().getString(KEY_DEVICE_MAC, null);
        if (mac != null) {
            final BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            final BluetoothAdapter btAdapter = btManager.getAdapter();
            BluetoothDevice selectedDevice = btAdapter.getRemoteDevice(mac);
            return selectedDevice.getBondState() == BluetoothDevice.BOND_BONDED;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUIStatus();
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, mIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        MenuItem item = menu.findItem(R.id.screenONCB);
        item.setChecked(MyApp.getPreferences().getBoolean(KEY_SCREEN_ON, false));

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        TSDZBTService service = TSDZBTService.getBluetoothService();
        if (service != null && service.getConnectionStatus() == TSDZBTService.ConnectionState.CONNECTED) {
            menu.findItem(R.id.bikeOTA).setEnabled(true);
            menu.findItem(R.id.espOTA).setEnabled(true);
            menu.findItem(R.id.showVersion).setEnabled(true);
            menu.findItem(R.id.config).setEnabled(true);
            menu.findItem(R.id.esp32Config).setEnabled(true);
        } else {
            menu.findItem(R.id.bikeOTA).setEnabled(false);
            menu.findItem(R.id.espOTA).setEnabled(false);
            menu.findItem(R.id.showVersion).setEnabled(false);
            menu.findItem(R.id.config).setEnabled(false);
            menu.findItem(R.id.esp32Config).setEnabled(false);
        }
        return true;
    }
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        int id = item.getItemId();
        switch (id) {
            case R.id.espOTA:
                intent = new Intent(this, Esp32_Ota.class);
                startActivity(intent);
                return true;
            case R.id.bikeOTA:
                intent = new Intent(this, Stm8_Ota.class);
                startActivity(intent);
                return true;
            case R.id.config:
                intent = new Intent(this, TSDZCfgActivity.class);
                startActivity(intent);
                return true;
            case R.id.btSetup:
                intent = new Intent(this, BluetoothSetupActivity.class);
                startActivity(intent);
                return true;
            case R.id.showVersion:
                TSDZBTService.getBluetoothService().writeCommand(new byte[] {TSDZConst.CMD_GET_APP_VERSION});
                return true;
            case R.id.esp32Config:
                intent = new Intent(this, ESP32ConfigActivity.class);
                startActivity(intent);
                return true;
            case R.id.screenONCB:
                boolean isChecked = !item.isChecked();
                item.setChecked(isChecked);
                SharedPreferences.Editor editor = MyApp.getPreferences().edit();
                editor.putBoolean(KEY_SCREEN_ON, isChecked);
                editor.apply();
                if (isChecked)
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode != RESULT_OK) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Bluetooth activation failed");
                builder.setMessage("Since bluetooth is not active, this app will not be able to run.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener((DialogInterface) -> finish());
                builder.show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == APP_PERMISSION_REQUEST) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Permission request failed");
                builder.setMessage("Application will end.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener((DialogInterface) -> finish());
                builder.show();
            }
        }
    }

    private void refreshView() {
        if (status.brake)
            brakeIV.setVisibility(View.VISIBLE);
        else
            brakeIV.setVisibility(View.INVISIBLE);

        if (status.status != 0) {
            statusTV.setVisibility(View.VISIBLE);
            statusTV.setText(String.valueOf(status.status));
        } else
            statusTV.setVisibility(View.INVISIBLE);

        if (status.streetMode)
            streetModeIV.setImageResource(R.mipmap.street_icon_on);
        else
            streetModeIV.setImageResource(R.mipmap.street_icon_off);

        switch (status.ridingMode) {
            case OFF_MODE:
                modeLevelTV.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.off_mode_icon, 0, 0, 0);
                modeLevelTV.setText("0");
                break;
            case eMTB_ASSIST_MODE:
                modeLevelTV.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.emtb_mode_icon, 0, 0, 0);
                modeLevelTV.setText(String.valueOf(status.assistLevel));
                break;
            case WALK_ASSIST_MODE:
                modeLevelTV.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.walk_mode_icon, 0, 0, 0);
                modeLevelTV.setText(String.valueOf(status.assistLevel));
                break;
            case POWER_ASSIST_MODE:
                modeLevelTV.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.power_mode_icon, 0, 0, 0);
                modeLevelTV.setText(String.valueOf(status.assistLevel));
                break;
            case TORQUE_ASSIST_MODE:
                modeLevelTV.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.torque_mode_icon, 0, 0, 0);
                modeLevelTV.setText(String.valueOf(status.assistLevel));
                break;
            case CADENCE_ASSIST_MODE:
                modeLevelTV.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.cadence_mode_icon, 0, 0, 0);
                modeLevelTV.setText(String.valueOf(status.assistLevel));
                break;
            case CRUISE_MODE:
                modeLevelTV.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.cruise_mode_icon, 0, 0, 0);
                modeLevelTV.setText(String.valueOf(status.assistLevel));
                break;
        }
    }

    private void updateUIStatus() {
        if (TSDZBTService.getBluetoothService() != null) {
            fabButton.setImageResource(android.R.drawable.ic_media_pause);
            serviceRunning = true;
            if (TSDZBTService.getBluetoothService().getConnectionStatus() == TSDZBTService.ConnectionState.CONNECTED)
                mTitle.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.bt_connected, 0, 0, 0);
            else
                mTitle.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.bt_connecting, 0, 0, 0);
        } else {
            fabButton.setImageResource(android.R.drawable.ic_media_play);
            mTitle.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.bt_disconnected, 0, 0, 0);
            serviceRunning = false;
        }
    }

    private void checkBT() {
        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = btManager.getAdapter();
        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        }
    }

    private void checkPermissions() {
        // Make sure we have access coarse location enabled, if not, prompt the user to enable it
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    APP_PERMISSION_REQUEST);
        }
    }

    // Version packet format is "%s|%s|%d".
    // First string is the ESP32 Main FW version, second is ESP32 OTA FW version and last integer
    // is the Bike Controller FW version.
    // The two strings are up to 8 char and the last integer is between 0 and 127.
    private void showVersions(byte[] data) {
        String s = new String(copyOfRange(data, 1, data.length), StandardCharsets.UTF_8);
        Log.d(TAG, "Version string is: " + s);
        String[] versions = s.split("\\|");
        if (versions.length != 2) {
            Log.e(TAG, "showVersions: wrong string");
            return;
        }
        if ("255".equals(versions[0]))
            versions[0] = "n/a";
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.fw_versions));
        String message = getString(R.string.esp32_fw_version, versions[1]) + "\n" +
                    getString(R.string.tsdz_fw_version, versions[0]);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //Log.d(TAG, "onReceive " + intent.getAction());
            if (intent.getAction() == null)
                return;
            byte [] data;
            switch (intent.getAction()) {
                case TSDZBTService.SERVICE_STARTED_BROADCAST:
                    Log.d(TAG, "SERVICE_STARTED_BROADCAST");
                    fabButton.setImageResource(android.R.drawable.ic_media_pause);
                    TSDZBTService service = TSDZBTService.getBluetoothService();
                    if (service != null && service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED)
                        mTitle.setCompoundDrawablesWithIntrinsicBounds(
                                R.mipmap.bt_connecting, 0, 0, 0);
                    serviceRunning = true;
					invalidateOptionsMenu();
                    break;
                case TSDZBTService.SERVICE_STOPPED_BROADCAST:
                    Log.d(TAG, "SERVICE_STOPPED_BROADCAST");
                    fabButton.setImageResource(android.R.drawable.ic_media_play);
                    mTitle.setCompoundDrawablesWithIntrinsicBounds(
                    R.mipmap.bt_disconnected, 0, 0, 0);
                    serviceRunning = false;
					invalidateOptionsMenu();
                    break;
                case TSDZBTService.CONNECTION_SUCCESS_BROADCAST:
                    Log.d(TAG, "CONNECTION_SUCCESS_BROADCAST");
                    mTitle.setCompoundDrawablesWithIntrinsicBounds(
					R.mipmap.bt_connected, 0, 0, 0);
					invalidateOptionsMenu();
					break;
                case TSDZBTService.CONNECTION_FAILURE_BROADCAST:
                    Log.d(TAG, "CONNECTION_FAILURE_BROADCAST");
                    Toast.makeText(getApplicationContext(), "TSDZ-ESP32 Connection Failure.", Toast.LENGTH_LONG).show();
                    mTitle.setCompoundDrawablesWithIntrinsicBounds(
					R.mipmap.bt_connecting, 0, 0, 0);
					invalidateOptionsMenu();
					break;
                case TSDZBTService.CONNECTION_LOST_BROADCAST:
                    Log.d(TAG, "CONNECTION_LOST_BROADCAST");
                    Toast.makeText(getApplicationContext(), "TSDZ-ESP32 Connection Lost.", Toast.LENGTH_LONG).show();
                    mTitle.setCompoundDrawablesWithIntrinsicBounds(
					R.mipmap.bt_connecting, 0, 0, 0);
					invalidateOptionsMenu();
					break;
				case TSDZBTService.TSDZ_COMMAND_BROADCAST:
                    data = intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA);
                    if (data[0] == CMD_GET_APP_VERSION)
				        showVersions(data);
                    break;
                case TSDZBTService.TSDZ_STATUS_BROADCAST:
                    data = intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA);
                    if (!Arrays.equals(lastStatusData, data)) {
                        if (status.setData(data)) {
                            System.arraycopy(data, 0, lastStatusData, 0, STATUS_ADV_SIZE);
                            // refresh Bottom data, and Status Fragmnt if visibile
                            refreshView();
                            if (viewPager.getCurrentItem() == 0)
                                mainPagerAdapter.getMyFragment(viewPager.getCurrentItem()).refreshView();
                        }
                    }
                    break;
                case TSDZBTService.TSDZ_DEBUG_BROADCAST:
                    data = intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA);
                    if (!Arrays.equals(lastDebugData, data)) {
                        // refresh Debug Fragment if visibile
                        if (debug.setData(data)) {
                            System.arraycopy(data, 0, lastDebugData, 0, DEBUG_ADV_SIZE);
                            if (viewPager.getCurrentItem() == 1)
                                mainPagerAdapter.getMyFragment(viewPager.getCurrentItem()).refreshView();
                        }
                    }
                    break;
            }
        }
    };

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return false;
    }
}