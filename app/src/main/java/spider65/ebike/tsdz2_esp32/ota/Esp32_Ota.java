package spider65.ebike.tsdz2_esp32.ota;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Handler;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;


import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import androidx.core.content.ContextCompat;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

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

    private Button selFileButton, startUpdateBT;
    private TextView fileNameTV, currVerTV, newVerTV, messageTV;
    private ProgressBar progressBar;

    private HotSpotCallback hotSpotCallback = null;

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
        startUpdateBT.setOnClickListener((View view) -> startAP());
        startUpdateBT.setEnabled(false);

        fileNameTV = findViewById(R.id.fileNameTV);
        currVerTV = findViewById(R.id.currVerTV);
        newVerTV = findViewById(R.id.newVerTV);

        currVerTV.setText(getString(R.string.current_version, mainAppVersion));
        newVerTV.setText(getString(R.string.new_version, ""));
        fileNameTV.setText(getString(R.string.file_name, ""));

        Button cancelButton = findViewById(R.id.exitButton);
        cancelButton.setOnClickListener((View view) -> cancel());
        messageTV = findViewById(R.id.progerssTV);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(INVISIBLE);

        if (Build.VERSION.SDK_INT < 26) {
            if (!Settings.System.canWrite(getApplicationContext())) {
                 Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()));
                 startActivity(intent);
             }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Location Permission not granted");
            builder.setMessage("Ota update cannot be done");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener((DialogInterface dialog) -> finish());
            builder.show();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (httpdServer != null) {
            httpdServer.stop();
            httpdServer = null;
        }
        EventBus.getDefault().unregister(this);
        stopAP();
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        // get current ESP32 SW version
        TSDZBTService.getBluetoothService().writeCommand(new byte[] {CMD_GET_APP_VERSION});
    }

    @Override
    public void onBackPressed() {
        cancel();
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
        updateInProgress = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        selFileButton.setEnabled(false);
        startUpdateBT.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        messageTV.setText(getString(R.string.waitingUploadStart));

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int wifiState = wifiManager.getWifiState();
        Log.d(TAG, "WiFi State = " + wifiState);
        if (wifiState !=  WifiManager.WIFI_STATE_ENABLED) {
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
                if (setWifiApState(true))
                    startUpdate();
                else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(getString(R.string.error_wifi));
                    builder.setMessage(getString(R.string.error_access_point));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener((DialogInterface dialog) -> finish());
                    builder.show();
                }
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

    private final ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent  resultData = result.getData();
                    if (resultData != null) {
                        checkFile(resultData.getData());
                    }
                }
            });

    public void performFileSearch() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        activityResultLauncher.launch(intent);
    }

    public static void createFileFromStream(InputStream ins, File destination) {
        try (OutputStream os = new FileOutputStream(destination)) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = ins.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            os.flush();
        } catch (Exception ex) {
            Log.e("Save File", ex.getMessage());
            ex.printStackTrace();
        }
    }

    void checkFile(Uri uri) {
        try {
            File f = new File(getFilesDir().getPath() + File.separatorChar + "ësp32.bin");
            try (InputStream ins = getContentResolver().openInputStream(uri)) {
                createFileFromStream(ins, f);
            } catch (Exception ex) {
                Log.e("Save File", ex.getMessage());
                ex.printStackTrace();
            }

            Log.i(TAG, "Filename: " + getFileName(uri));
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
            fileNameTV.setText(getString(R.string.file_name, getFileName(uri)));
            if (imageInfo.signed) {
                showDialog(getString(R.string.warning), getString(R.string.cannot_change_pin, imageInfo.btPin), false);
            } else {
                showPinChangeDialog();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @SuppressLint("Range")
    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                assert cursor != null;
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
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

    private boolean setWifiApState(boolean enable) {
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
            return false;
        }
        return true;
    }

    private void startUpdate() {
        try {
            httpdServer = new HttpdServer(updateFile, this);
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
    }

    private void stopUpdate() {
        updateInProgress = false;
        stopAP();
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
            startUpdate();
        }
        public void onFailed(int reason) {
            Log.d(TAG, "onFailed:" + reason);
        }
        public void onStopped() {
            Log.d(TAG, "onStopped");
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onMessageEvent(TSDZBTService.BTServiceEvent event) {
        //Log.d(TAG, "onReceive " + event.eventType);
        switch (event.eventType) {
            case TSDZ_COMMAND:
                byte[] data = event.data;
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
            case CONNECTION_SUCCESS:
                if (updateInProgress) {
                    messageTV.setText(getString(R.string.rebootDone));
                    final Handler handler = new Handler();
                    handler.postDelayed(() ->
                            TSDZBTService.getBluetoothService().writeCommand(new byte[] {CMD_GET_APP_VERSION})
                            ,3000);
                }
                break;
            case CONNECTION_FAILURE:
            case CONNECTION_LOST:
                break;
        }
    }
}