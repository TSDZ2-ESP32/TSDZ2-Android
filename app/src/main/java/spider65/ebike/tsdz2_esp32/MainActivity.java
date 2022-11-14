package spider65.ebike.tsdz2_esp32;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
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
import spider65.ebike.tsdz2_esp32.activities.MotorTestActivity;
import spider65.ebike.tsdz2_esp32.activities.ShowDebugInfo;
import spider65.ebike.tsdz2_esp32.activities.TSDZCfgActivity;
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
import static spider65.ebike.tsdz2_esp32.TSDZConst.STATUS_ADV_SIZE;
import static spider65.ebike.tsdz2_esp32.activities.BluetoothSetupActivity.KEY_DEVICE_MAC;


public class MainActivity extends AppCompatActivity implements View.OnTouchListener {

    private static final String TAG = "MainActivity";

    public static final String KEY_SCREEN_ON = "SCREEN_ON";

    private TextView mTitle;
    private boolean serviceRunning;
    private  FloatingActionButton fabButton;
    private MainPagerAdapter mainPagerAdapter;

    private static final int APP_PERMISSION_REQUEST = 1;

    IntentFilter mIntentFilter = new IntentFilter();

    private byte[] lastStatusData = new byte[STATUS_ADV_SIZE];

    private final TSDZ_Status status = new TSDZ_Status();

    private TextView modeLevelTV;
    private TextView statusTV;
    private ImageView brakeIV;
    private ImageView streetModeIV;

    private GestureDetector gestureDetector;

    private enum BTStatus {
        Disconnected,
        Connecting,
        Connected
    }

    private BTStatus btStatus = BTStatus.Disconnected;
    private boolean commError = false;

    @SuppressLint("ClickableViewAccessibility")
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

        mainPagerAdapter = new MainPagerAdapter(this, getSupportFragmentManager(), status);
        ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(mainPagerAdapter);
        viewPager.setOnTouchListener(this);

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 0:
                        mTitle.setText(R.string.status_data);
                        break;
                    case 1:
                        mTitle.setText(R.string.debug_data);
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
        mTitle.setText(R.string.status_data);
        mTitle.setOnClickListener(v -> {
            if (status.controllerCommError || status.lcdCommError) {
                String title = getString(R.string.error);
                String message = "";
                if (status.controllerCommError)
                    message = getString(R.string.error_controller_comm);
                if (status.lcdCommError) {
                    if (!message.isEmpty())
                        message += "\n";
                    message += getString(R.string.error_lcd_comm);
                }
                final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(title);
                builder.setMessage(message);
                builder.setPositiveButton(getString(R.string.ok), null);
                builder.show();
            }
        });

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
                case TSDZConst.ERROR_BATTERY_OVERCURRENT:
                    title = getString(R.string.error_battery_overcurrent);
                    message = getString(R.string.check_battery_overcurrent);
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
                builder.setPositiveButton(getString(R.string.ok), null);
                builder.show();
            }
        });
        brakeIV = findViewById(R.id.brakeIV);
        modeLevelTV = findViewById(R.id.modeLevelTV);
        registerForContextMenu(modeLevelTV);
        streetModeIV = findViewById(R.id.streetModeIV);
        registerForContextMenu(streetModeIV);

        fabButton = findViewById(R.id.fab);
        fabButton.setOnClickListener((View) -> {
            String mac = MyApp.getPreferences().getString(KEY_DEVICE_MAC, null);
            if (mac == null) {
                Toast.makeText(this, "Please select the bluetooth device to connect", Toast.LENGTH_LONG).show();
                return;
            }
            Intent intent = new Intent(MainActivity.this, TSDZBTService.class);
            if (serviceRunning) {
                intent.setAction(TSDZBTService.ACTION_STOP_FOREGROUND_SERVICE);
            } else{
                intent.setAction(TSDZBTService.ACTION_START_FOREGROUND_SERVICE);
                intent.putExtra(TSDZBTService.ADDRESS_EXTRA, mac);
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

        checkBT();
        updateUIStatus();
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
            menu.findItem(R.id.motorTest).setEnabled(true);
            menu.findItem(R.id.showDebug).setEnabled(true);
        } else {
            menu.findItem(R.id.bikeOTA).setEnabled(false);
            menu.findItem(R.id.espOTA).setEnabled(false);
            menu.findItem(R.id.showVersion).setEnabled(false);
            menu.findItem(R.id.config).setEnabled(false);
            menu.findItem(R.id.esp32Config).setEnabled(false);
            menu.findItem(R.id.motorTest).setEnabled(false);
            menu.findItem(R.id.showDebug).setEnabled(false);
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
            case R.id.showDebug:
                intent = new Intent(this, ShowDebugInfo.class);
                startActivity(intent);
                return true;
            case R.id.motorTest:
                intent = new Intent(this, MotorTestActivity.class);
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
    public void onCreateContextMenu (ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        TSDZBTService service = TSDZBTService.getBluetoothService();
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED)
            return;

        MenuInflater inflater;
        switch (v.getId()) {
            case R.id.streetModeIV:
                // create context menu for Street Mode Icon long press
                inflater = getMenuInflater();
                inflater.inflate(R.menu.menu_street_mode, menu);
                menu.setHeaderTitle(getResources().getString(R.string.street_mode));
                break;
            case R.id.modeLevelTV:
                // create context menu for Assist Mode Icon long press
                inflater = getMenuInflater();
                inflater.inflate(R.menu.menu_assist_mode, menu);
                menu.setHeaderTitle(getResources().getString(R.string.assist_mode));
                break;
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item){
        TSDZBTService service = TSDZBTService.getBluetoothService();
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED)
            return false;

        switch (item.getItemId()) {
            // manage Street Mode context menu selection
            case R.id.street_lcd_master:
                service.writeCommand(new byte[] {TSDZConst.CMD_STREET_MODE, TSDZConst.STREET_MODE_LCD_MASTER});
                break;
            case R.id.street_force_off:
                service.writeCommand(new byte[] {TSDZConst.CMD_STREET_MODE, TSDZConst.STREET_MODE_FORCE_OFF});
                break;
            case R.id.street_force_on:
                service.writeCommand(new byte[] {TSDZConst.CMD_STREET_MODE, TSDZConst.STREET_MODE_FORCE_ON});
                break;
            // manage Assist Mode context menu selection
            case R.id.assist_lcd_master:
                service.writeCommand(new byte[] {TSDZConst.CMD_ASSIST_MODE, TSDZConst.ASSIST_MODE_LCD_MASTER});
                break;
            case R.id.assist_power:
                service.writeCommand(new byte[] {TSDZConst.CMD_ASSIST_MODE, TSDZConst.ASSIST_MODE_FORCE_POWER});
                break;
            case R.id.assist_emtb:
                service.writeCommand(new byte[] {TSDZConst.CMD_ASSIST_MODE, TSDZConst.ASSIST_MODE_FORCE_EMTB});
                break;
            case R.id.assist_torque:
                service.writeCommand(new byte[] {TSDZConst.CMD_ASSIST_MODE, TSDZConst.ASSIST_MODE_FORCE_TORQUE});
                break;
            case R.id.assist_cadence:
                service.writeCommand(new byte[] {TSDZConst.CMD_ASSIST_MODE, TSDZConst.ASSIST_MODE_FORCE_CADENCE});
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == APP_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if ((ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
                        && (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                        && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED))
                    return;
            } else {
                if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                        && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED))
                    return;
            }

            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Permission request failed");
            builder.setMessage("Application will end.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener((DialogInterface) -> finish());
            builder.show();
        }
    }

    private boolean s_brake;
    private short s_status = 0;
    private boolean s_controllerCommError;
    private boolean s_lcdCommError;
    private boolean s_streetMode;
    private TSDZ_Status.RidingMode s_ridingMode = TSDZ_Status.RidingMode.OFF_MODE;
    private short s_assistLevel = 0;

    private void refreshView() {
        if (status.brake != s_brake) {
            s_brake = status.brake;
            if (status.brake)
                brakeIV.setVisibility(View.VISIBLE);
            else
                brakeIV.setVisibility(View.INVISIBLE);
        }

        if (status.status != s_status) {
            s_status = status.status;
            if (status.status != 0) {
                statusTV.setVisibility(View.VISIBLE);
                statusTV.setText(String.valueOf(status.status));
            } else
                statusTV.setVisibility(View.INVISIBLE);
        }

        if ((status.controllerCommError != s_controllerCommError) || (status.lcdCommError != s_lcdCommError)) {
            s_controllerCommError = status.controllerCommError;
            s_lcdCommError = status.lcdCommError;
            if ((status.controllerCommError || status.lcdCommError) && !commError) {
                commError = true;
                updateStatusIcons();
            } else if ((!status.controllerCommError && !status.lcdCommError) && commError) {
                commError = false;
                updateStatusIcons();
            }
        }

        if (status.streetMode != s_streetMode) {
            s_streetMode = status.streetMode;
            if (status.streetMode)
                streetModeIV.setImageResource(R.mipmap.street_icon_on);
            else
                streetModeIV.setImageResource(R.mipmap.street_icon_off);
        }

        if ((status.ridingMode != s_ridingMode) || (status.assistLevel != s_assistLevel)) {
            s_ridingMode = status.ridingMode;
            s_assistLevel = status.assistLevel;
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
    }

    private void updateStatusIcons() {
        switch (btStatus) {
            case Disconnected:
                if (commError)
                    mTitle.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.bt_disconnected, 0, R.drawable.comm_error, 0);
                else
                    mTitle.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.bt_disconnected, 0, 0, 0);
                break;
            case Connecting:
                if (commError)
                    mTitle.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.bt_connecting, 0, R.drawable.comm_error, 0);
                else
                    mTitle.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.bt_connecting, 0, 0, 0);
                break;
            case Connected:
                if (commError)
                    mTitle.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.bt_connected, 0, R.drawable.comm_error, 0);
                else
                    mTitle.setCompoundDrawablesWithIntrinsicBounds(R.mipmap.bt_connected, 0, 0, 0);
                break;
        }
    }


    private void updateUIStatus() {
        if (TSDZBTService.getBluetoothService() != null) {
            fabButton.setImageResource(android.R.drawable.ic_media_pause);
            serviceRunning = true;
            if (TSDZBTService.getBluetoothService().getConnectionStatus() == TSDZBTService.ConnectionState.CONNECTED)
                btStatus = BTStatus.Connected;
            else
                btStatus = BTStatus.Connecting;
        } else {
            fabButton.setImageResource(android.R.drawable.ic_media_play);
            serviceRunning = false;
            btStatus = BTStatus.Disconnected;
        }
        updateStatusIcons();
    }


    private void checkBT() {
        ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK) {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Bluetooth activation failed");
                    builder.setMessage("Since bluetooth is not active, this app will not be able to run.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener((DialogInterface) -> finish());
                    builder.show();
                }
            });

        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = btManager.getAdapter();
        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activityResultLauncher.launch(enableIntent);
        }
    }

    private static final String[] BLE_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
    };

    @RequiresApi(api = Build.VERSION_CODES.S)
    private static final String[] ANDROID_12_BLE_PERMISSIONS = new String[]{
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if ((ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                    || (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                    || (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED))
                ActivityCompat.requestPermissions(this, ANDROID_12_BLE_PERMISSIONS, APP_PERMISSION_REQUEST);
        } else {
            if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    || (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
                ActivityCompat.requestPermissions(this, BLE_PERMISSIONS, APP_PERMISSION_REQUEST);
            }
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

    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
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
                    if (service != null && service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
                        btStatus = BTStatus.Connecting;
                        updateStatusIcons();
                    }
                    serviceRunning = true;
					invalidateOptionsMenu();
                    break;
                case TSDZBTService.SERVICE_STOPPED_BROADCAST:
                    Log.d(TAG, "SERVICE_STOPPED_BROADCAST");
                    fabButton.setImageResource(android.R.drawable.ic_media_play);
                    btStatus = BTStatus.Disconnected;
                    updateStatusIcons();
                    serviceRunning = false;
					invalidateOptionsMenu();
                    break;
                case TSDZBTService.CONNECTION_SUCCESS_BROADCAST:
                    Log.d(TAG, "CONNECTION_SUCCESS_BROADCAST");
                    btStatus = BTStatus.Connected;
                    updateStatusIcons();
					invalidateOptionsMenu();
					break;
                case TSDZBTService.CONNECTION_FAILURE_BROADCAST:
                    Log.d(TAG, "CONNECTION_FAILURE_BROADCAST");
                    Toast.makeText(getApplicationContext(), "TSDZ-ESP32 Connection Failure.", Toast.LENGTH_LONG).show();
                    btStatus = BTStatus.Connecting;
                    updateStatusIcons();
					invalidateOptionsMenu();
					break;
                case TSDZBTService.CONNECTION_LOST_BROADCAST:
                    Log.d(TAG, "CONNECTION_LOST_BROADCAST");
                    Toast.makeText(getApplicationContext(), "TSDZ-ESP32 Connection Lost.", Toast.LENGTH_LONG).show();
                    btStatus = BTStatus.Connecting;
                    updateStatusIcons();
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
                            lastStatusData = data;
                            refreshView();
                            //mainPagerAdapter.getMyFragment(viewPager.getCurrentItem()).refreshView(status);
                            mainPagerAdapter.getMyFragment(0).refreshView(status);
                            mainPagerAdapter.getMyFragment(1).refreshView(status);
                        }
                    }
                    break;
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return false;
    }
}