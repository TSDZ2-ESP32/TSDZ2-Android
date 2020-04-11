package spider65.ebike.tsdz2_esp32.ota;

import android.Manifest;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.obsez.android.lib.filechooser.ChooserDialog;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.utils.Utils;

import static android.view.View.INVISIBLE;
import static java.util.Arrays.copyOfRange;
import static spider65.ebike.tsdz2_esp32.TSDZConst.CMD_ESP_OTA_START;
import static spider65.ebike.tsdz2_esp32.TSDZConst.CMD_GET_APP_VERSION;
import static spider65.ebike.tsdz2_esp32.TSDZConst.CMD_ESP_OTA_STATUS;


public class Esp32_Ota extends AppCompatActivity implements ProgressInputStreamListener {

    private static final String TAG = "Esp32_Ota";

    private static final String MAIN_APP_NAME = "TSDZ2-ESP32-Main";

    private static final String PORT = "8089";

    private File updateFile = null;
    Esp32AppImageTool.EspImageInfo imageInfo = null;

    private WifiManager.LocalOnlyHotspotReservation reservation = null;
    private boolean apAlreadyOn = false;
    private boolean wifiState;

    private String ssid,pwd;

    private HttpdServer httpdServer = null;
    private IntentFilter mIntentFilter = new IntentFilter();

    private Button selFileButton, startUpdateBT;
    private TextView fileNameTV, currVerTV, newVerTV, messageTV;
    private ProgressBar progressBar;

    private HotSpotCallback hotSpotCallback = null;

    private static final int READ_EXTERNAL_STORAGE_PERMISION_REQUEST = 3;

    private boolean updateInProgress = false;

    String mainAppVersion = "-";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp32_ota);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        updateFile = null;

        selFileButton = findViewById(R.id.selFileButton);
        selFileButton.setOnClickListener((View view) -> performFileSearch());
        selFileButton.setEnabled(true);

        startUpdateBT = findViewById(R.id.startUpdateButton);
        startUpdateBT.setOnClickListener((View view) -> startUpdate());
        startUpdateBT.setEnabled(false);

        fileNameTV = findViewById(R.id.fileNameTV);
        currVerTV = findViewById(R.id.currVerTV);
        newVerTV = findViewById(R.id.newVerTV);

        currVerTV.setText(getString(R.string.current_version, mainAppVersion));
        newVerTV.setText(getString(R.string.new_version, ""));
        fileNameTV.setText(getString(R.string.file_name, ""));

        Button cancelButton = findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener((View view) -> cancel());
        messageTV = findViewById(R.id.progerssTV);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(INVISIBLE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    READ_EXTERNAL_STORAGE_PERMISION_REQUEST);
        }

        if (Build.VERSION.SDK_INT < 26) {
            if (!Settings.System.canWrite(getApplicationContext())) {
                 Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()));
                 startActivityForResult(intent, 200);
             }
        }

        mIntentFilter.addAction(TSDZBTService.TSDZ_COMMAND_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.CONNECTION_SUCCESS_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.CONNECTION_FAILURE_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.CONNECTION_LOST_BROADCAST);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (httpdServer != null) {
            httpdServer.stop();
            httpdServer = null;
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        stopAP();
    }

    @Override
    public void onStart() {
        super.onStart();
        startAP();
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, mIntentFilter);
        // get current ESP32 SW version
        TSDZBTService.getBluetoothService().writeCommand(new byte[] {CMD_GET_APP_VERSION});
    }

    @Override
    public void onBackPressed() {
        cancel();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == READ_EXTERNAL_STORAGE_PERMISION_REQUEST) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Permission request failed");
                builder.setMessage("Ota update cannot be done");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener((DialogInterface dialog) -> finish());
                builder.show();
            }
        }
    }

    private void cancel() {
        if (updateInProgress) {
            AlertDialog alertDialog = new AlertDialog.Builder(Esp32_Ota.this).create();
            alertDialog.setTitle(getString(R.string.warning));
            alertDialog.setMessage(getString(R.string.exit_warning));
            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK",
                    (DialogInterface dialog, int which) -> {
                        dialog.dismiss();
                        Esp32_Ota.this.finish();
                    });
            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel",
                    (DialogInterface dialog, int which) -> dialog.dismiss());
            alertDialog.show();
        } else {
            Esp32_Ota.this.finish();
        }
    }

    private void startAP() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            showDialog(getString(R.string.error), getString(R.string.enable_wifi), true);
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            if (hotSpotCallback == null)
                hotSpotCallback = new HotSpotCallback();
            wifiManager.startLocalOnlyHotspot(hotSpotCallback, null);
        } else {
            if (isApOn()) {
                apAlreadyOn = true;
            } else {
                setWifiApState(true);
            }
        }
    }

    public void stopAP() {
        if (Build.VERSION.SDK_INT >= 26) {
            if (reservation != null) {
                reservation.close();
                reservation = null;
            }
        } else {
            // Stop Access Point if was activated
            if (!apAlreadyOn)
                setWifiApState(false);
        }
    }


    /**
     * Fires an intent to spin up the "file chooser" UI and select an image.
     */
    public void performFileSearch() {
        new ChooserDialog(this)
            .withFilterRegex(false, false, ".*\\.bin$")
            .withStartFile(Environment.getExternalStorageDirectory().getAbsolutePath())
            .withStringResources(getString(R.string.title_select_file,"bin"),
                        getString(R.string.choose), getString(R.string.cancel))
            .withChosenListener( (String path, File pathFile) -> checkFile( pathFile ))
            .build()
            .show();
    }

    void checkFile(File f) {
        try {
            Log.i(TAG, "Filename: " + f.getName());
            imageInfo = Esp32AppImageTool.checkFile(f);
            if (imageInfo == null) {
                showDialog(getString(R.string.error), getString(R.string.fileNotValid), false);
                return;
            }
            if (!imageInfo.appName.equals(MAIN_APP_NAME))  {
                showDialog(getString(R.string.error), getString(R.string.wrong_app_name), false);
                imageInfo = null;
                return;
            }

            updateFile = f;
            newVerTV.setText(getString(R.string.new_version, imageInfo.appVersion));
            startUpdateBT.setEnabled(true);
            fileNameTV.setText(getString(R.string.file_name, updateFile.getName()));
            if (imageInfo.signed) {
                showDialog(getString(R.string.warning), getString(R.string.cannot_change_pin, imageInfo.btPin), false);
            } else {
                showPinChangeDialog();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //check whether wifi hotspot on or off
    private boolean isApOn() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            Method method = wifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
            method.setAccessible(true);
            return (Boolean) method.invoke(wifiManager);
        }
        catch (Throwable ignored) {}
        return false;
    }

    private void setWifiApState(boolean enable) {
        WifiManager wifimanager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            if (enable) {
                wifiState = wifimanager.isWifiEnabled();
                wifimanager.setWifiEnabled(false);
                Method wifiApConfigurationMethod = wifimanager.getClass().getMethod("getWifiApConfiguration");
                WifiConfiguration cfg = (WifiConfiguration)wifiApConfigurationMethod.invoke(wifimanager);
                ssid = cfg.SSID;
                pwd = cfg.preSharedKey;
                Log.i(TAG, "WifiConfiguration SSID: " + cfg.SSID + " PWD: " + cfg.preSharedKey);
            }

            Method wifiControlMethod = wifimanager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class,boolean.class);
            wifiControlMethod.invoke(wifimanager, null, enable);

            // When stopping Access Point, reset WiFi state to previous value
            if (!enable && wifiState) {
                wifimanager.setWifiEnabled(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
    }

    private void startUpdate() {
        try {
            httpdServer = new HttpdServer(updateFile, this, this);
            httpdServer.start();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            showDialog(getString(R.string.error), e.getMessage(), false);
            return;
        }
        byte[] command = new byte[ssid.length()+pwd.length()+PORT.length()+3];
        command[0] = CMD_ESP_OTA_START;
        int pos = 1;
        System.arraycopy(ssid.getBytes(),0,command,pos,ssid.getBytes().length);
        pos += ssid.getBytes().length;
        command[pos++] = '|';
        System.arraycopy(pwd.getBytes(),0,command,pos,pwd.getBytes().length);
        pos += pwd.getBytes().length;
        command[pos++] = '|';
        System.arraycopy(PORT.getBytes(),0,command,pos,PORT.getBytes().length);
        Log.i(TAG, "Update start: "+ new String(command));
        TSDZBTService.getBluetoothService().writeCommand(command);
        updateInProgress = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        selFileButton.setEnabled(false);
        startUpdateBT.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        messageTV.setText(getString(R.string.updateStarted));
    }

    private void stopUpdate() {
        updateInProgress = false;
        if (httpdServer != null) {
            httpdServer.stop();
            httpdServer = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        selFileButton.setEnabled(true);
        startUpdateBT.setEnabled(true);
        progressBar.setVisibility(INVISIBLE);
        messageTV.setText("");
    }

    private void showDialog (String title, String message, boolean exit) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (title != null)
            builder.setTitle(title);
        builder.setMessage(message);
        if (exit) {
            builder.setOnCancelListener((dialog) -> Esp32_Ota.this.finish());
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> Esp32_Ota.this.finish());
        } else
            builder.setPositiveButton(android.R.string.ok, null);
        builder.show().setCanceledOnTouchOutside(false);
    }

    private void showPinChangeDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.pin_change,imageInfo.btPin));
        builder.setNegativeButton(R.string.no, null);
        builder.setPositiveButton(R.string.yes, (DialogInterface dialog, int which) -> {
                dialog.dismiss();
                final AlertDialog.Builder b2 = new AlertDialog.Builder(this);
                b2.setMessage(getString(R.string.bt_pin_input));
                final View v = getLayoutInflater().inflate( R.layout.dialog_input_pin, null);
                final EditText input = v.findViewById(R.id.pinET);
                b2.setView(v);
                b2.setNegativeButton(R.string.cancel, null);
                b2.setPositiveButton(R.string.ok, (DialogInterface d2, int w2) -> {
                    String s = input.getText().toString();
                    if (s.length() < 1){
                        showDialog(getString(R.string.error), getString(R.string.pin_input_error), false);
                    } else {
                        d2.dismiss();
                        int pin = Integer.parseInt(input.getText().toString());
                        Log.d(TAG, "New pin is " + pin);
                        if (!Esp32AppImageTool.updateFile(updateFile, imageInfo, pin)) {
                            showDialog(getString(R.string.error), getString(R.string.image_update_error), false);
                        }
                    }
                });
                Dialog d = b2.show();
                d.setCanceledOnTouchOutside(false);
                TextView messageText = d.findViewById(android.R.id.message);
                if (messageText != null)
                    messageText.setGravity(Gravity.CENTER);
            }
        );
        builder.show().setCanceledOnTouchOutside(false);
    }

    @Override
    public void progress(int percent) {
        Esp32_Ota.this.runOnUiThread( () -> messageTV.setText(getString(R.string.uploading, percent)));
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private class HotSpotCallback extends WifiManager.LocalOnlyHotspotCallback {
        @Override
        public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
            Log.d(TAG, "onStarted");
            WifiConfiguration cfg = reservation.getWifiConfiguration();
            Esp32_Ota.this.reservation = reservation;
            ssid = cfg.SSID;
            pwd = cfg.preSharedKey;
        }
        public void onFailed(int reason) {
            Log.d(TAG, "onFailed:" + reason);
        }
        public void onStopped() {
            Log.d(TAG, "onStopped");
        }
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive " + intent.getAction());
            if (intent.getAction() == null)
                return;
            switch (intent.getAction()) {
                case TSDZBTService.TSDZ_COMMAND_BROADCAST:
                    byte[] data = intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA);
                    Log.d(TAG, "TSDZ_COMMAND_BROADCAST Data: " + Utils.bytesToHex(data));
                    switch (data[0]) {
                        // Start Update response
                        case CMD_ESP_OTA_START:
                            // check if update started
                            if (data[1] != (byte)0x0) {
                                stopUpdate();
                                showDialog(getString(R.string.error), getString(R.string.updateError), false);
                            }
                            break;
                        // Get Version response
                        case CMD_GET_APP_VERSION:
                            String tmp = new String(copyOfRange(data, 1, data.length), StandardCharsets.UTF_8);
                            String[] versions = tmp.split("\\|");
                            if (versions.length != 2) {
                                Log.e(TAG, "CMD_GET_APP_VERSION: wrong string");
                                return;
                            }
                            mainAppVersion = versions[1];
                            if (updateInProgress) {
                                stopUpdate();
                                showDialog(getString(R.string.rebootDone), getString(R.string.new_version, mainAppVersion), false);
                            }
                            currVerTV.setText(getString(R.string.current_version, mainAppVersion));
                            break;
                        case CMD_ESP_OTA_STATUS:
                            if (data[1] != 0) {
                                showDialog(getString(R.string.rebootDone), getString(R.string.upload_error, data[1]), false);
                                stopUpdate();
                            } else
                                messageTV.setText(getString(R.string.waitingReboot));
                            break;
                    }
                    break;
                case TSDZBTService.CONNECTION_SUCCESS_BROADCAST:
                    if (updateInProgress) {
                        messageTV.setText(getString(R.string.rebootDone));
                        final Handler handler = new Handler();
                        handler.postDelayed(() ->
                                TSDZBTService.getBluetoothService().writeCommand(new byte[] {CMD_GET_APP_VERSION})
                                ,3000);
                    }
                    break;
                case TSDZBTService.CONNECTION_FAILURE_BROADCAST:
                case TSDZBTService.CONNECTION_LOST_BROADCAST:
                    break;
            }
        }
    };
}
