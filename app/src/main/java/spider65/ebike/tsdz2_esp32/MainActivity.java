package spider65.ebike.tsdz2_esp32;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import spider65.ebike.tsdz2_esp32.activities.TSDZCfgActivity;
import spider65.ebike.tsdz2_esp32.ota.Esp32_Ota;
import spider65.ebike.tsdz2_esp32.fragments.OnFragmentInteractionListener;
import spider65.ebike.tsdz2_esp32.ota.Stm8_Ota;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity implements OnFragmentInteractionListener {

    private static final String TAG = "MainActivity";
    private TextView mTitle;
    private boolean serviceRunning;
    private  FloatingActionButton fabButton;
    private SectionsPagerAdapter sectionsPagerAdapter;

    private static final int REQUEST_ENABLE_BLUETOOTH = 0;
    private static final int APP_PERMISSION_REQUEST = 1;

    IntentFilter mIntentFilter = new IntentFilter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_main);
        sectionsPagerAdapter = new SectionsPagerAdapter(this, getSupportFragmentManager());
        ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPagerAdapter);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                sectionsPagerAdapter.selected(position);
                switch (position) {
                    case 0:
                        mTitle.setText(R.string.status);
                        break;
                    case 1:
                        mTitle.setText(R.string.debug);
                        break;
                    case 2:
                        break;
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
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

        fabButton = findViewById(R.id.fab);
        fabButton.setOnClickListener((View) -> {
                Intent intent = new Intent(MainActivity.this, TSDZBTService.class);
                if (serviceRunning) {
                    intent.setAction(TSDZBTService.ACTION_STOP_FOREGROUND_SERVICE);
                } else{
                    intent.setAction(TSDZBTService.ACTION_START_FOREGROUND_SERVICE);
                    intent.putExtra(TSDZBTService.ADDRESS_EXTRA, MyApp.getPreferences().getString(BluetoothSetupActivity.KEY_DEVICE_MAC, null));
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

        checkBT();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (MyApp.getPreferences().getString(BluetoothSetupActivity.KEY_DEVICE_NAME, null) == null) {
            fabButton.setEnabled(false);
            Toast.makeText(this, "Please select the bluetooth device to connect", Toast.LENGTH_LONG).show();
        } else
            fabButton.setEnabled(true);
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
        Log.d(TAG, "onCreateOptionsMenu");
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
        } else {
            menu.findItem(R.id.bikeOTA).setEnabled(false);
            menu.findItem(R.id.espOTA).setEnabled(false);
            menu.findItem(R.id.showVersion).setEnabled(false);
            menu.findItem(R.id.config).setEnabled(false);
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
                TSDZBTService.getBluetoothService().writeCommand(new byte[] {(byte)0x02});
                return true;											
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onFragmentInteraction(int fragment) {
        // TODO
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

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive " + intent.getAction());
            if (intent.getAction() == null)
                return;
            switch (intent.getAction()) {
                case TSDZBTService.SERVICE_STARTED_BROADCAST:
                    fabButton.setImageResource(android.R.drawable.ic_media_pause);
                    mTitle.setCompoundDrawablesWithIntrinsicBounds(
                            R.mipmap.bt_connecting, 0, 0, 0);
                    serviceRunning = true;
					invalidateOptionsMenu();
                    break;
                case TSDZBTService.SERVICE_STOPPED_BROADCAST:
                    fabButton.setImageResource(android.R.drawable.ic_media_play);
                    mTitle.setCompoundDrawablesWithIntrinsicBounds(
                    R.mipmap.bt_disconnected, 0, 0, 0);
                    serviceRunning = false;
					invalidateOptionsMenu();
                    break;
                case TSDZBTService.CONNECTION_SUCCESS_BROADCAST:
                    mTitle.setCompoundDrawablesWithIntrinsicBounds(
					R.mipmap.bt_connected, 0, 0, 0);
					invalidateOptionsMenu();
					break;
                case TSDZBTService.CONNECTION_FAILURE_BROADCAST:
                    Toast.makeText(getApplicationContext(), "TSDZ-ESP32 Connection Failure.", Toast.LENGTH_LONG).show();
                    mTitle.setCompoundDrawablesWithIntrinsicBounds(
					R.mipmap.bt_connecting, 0, 0, 0);
					invalidateOptionsMenu();
					break;
                case TSDZBTService.CONNECTION_LOST_BROADCAST:
                    Toast.makeText(getApplicationContext(), "TSDZ-ESP32 Connection Lost.", Toast.LENGTH_LONG).show();
                    mTitle.setCompoundDrawablesWithIntrinsicBounds(
					R.mipmap.bt_connecting, 0, 0, 0);
					invalidateOptionsMenu();
					break;
				case TSDZBTService.TSDZ_COMMAND_BROADCAST:
                    try {
                        String version = new String(intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA), "UTF-8");
                        Toast.makeText(getApplicationContext(), getResources().getString(R.string.show_version) + " : " + version, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    };
}