package spider65.ebike.tsdz2_esp32.ota;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import nl.lxtreme.binutils.hex.IntelHexReader;
import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.utils.Utils;

import static android.view.View.INVISIBLE;
import static java.util.Arrays.copyOfRange;
import static spider65.ebike.tsdz2_esp32.TSDZConst.CMD_GET_APP_VERSION;
import static spider65.ebike.tsdz2_esp32.TSDZConst.CMD_STM8S_OTA_START;
import static spider65.ebike.tsdz2_esp32.TSDZConst.CMD_STM8_OTA_STATUS;


public class Stm8_Ota extends AppCompatActivity implements ProgressInputStreamListener {

    private static final String TAG = "Stm8_Ota";

    private static final String PORT = "8089";

    private static final int MAX_BIN_SIZE = 0x8000;
    private static final int START_ADDRESS = 0x8000;
    private static final int MAX_ADDRESS = 0xFFFF;

    private File updateFile = null;
    private WifiManager.LocalOnlyHotspotReservation reservation = null;
    private boolean wifiState;
    private boolean apAlreadyOn = false;

    private String ssid,pwd;

    private HttpdServer httpdServer = null;

    private Button selFileButton, startUpdateBT;
    private TextView currVerTV, fileNameTV,messageTV;
    private ProgressBar progressBar;

    private HotSpotCallback hotSpotCallback = null;

    private UpdateProgess updateInProgress = UpdateProgess.notStarted;

    private enum UpdateProgess {
        notStarted,
        started,
        rebooting,
        uploading,
        uploaded,
        writeinit,
        writeInitialized,
        writing
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stm8_ota);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        currVerTV = findViewById(R.id.currVerTV);

        selFileButton = findViewById(R.id.selFileButton);
        selFileButton.setOnClickListener((View view) -> performFileSearch());

        startUpdateBT = findViewById(R.id.startUpdateButton);
        startUpdateBT.setOnClickListener((View view) -> startUpdate());
        startUpdateBT.setEnabled(false);
        fileNameTV = findViewById(R.id.fileNameTV);
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
        startAP();
        EventBus.getDefault().register(this);
        // get current ESP32 SW version
        TSDZBTService.getBluetoothService().writeCommand(new byte[] {CMD_GET_APP_VERSION});
    }

    @Override
    public void onBackPressed() {
        cancel();
    }

    private void cancel() {
        if (updateInProgress != UpdateProgess.notStarted) {
            AlertDialog alertDialog = new AlertDialog.Builder(Stm8_Ota.this).create();
            alertDialog.setTitle(getString(R.string.warning));
            alertDialog.setMessage(getString(R.string.exit_warning));
            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK",
                    (DialogInterface dialog, int which) -> {
                        dialog.dismiss();
                        Stm8_Ota.this.finish();
                    });
            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel",
                    (DialogInterface dialog, int which) -> dialog.dismiss());
            alertDialog.show();
        } else {
            Stm8_Ota.this.finish();
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

    private static final int FILE_SELECT_CODE = 11;
    public void performFileSearch() {
        /*
        ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK) {
                        Intent  resultData = result.getData();
                        if (resultData != null) {
                            checkFile(resultData.getData());
                        }
                    }
                });
        */

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent,FILE_SELECT_CODE);
        // activityResultLauncher.launch(intent);
    }

    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == FILE_SELECT_CODE) {
            if (data != null) {
                checkFile(data.getData());
            }
        }
    }

    void checkFile(Uri uri) {
        try {
            File outFile = null;

            Reader reader = new InputStreamReader(getContentResolver().openInputStream(uri));
            final IntelHexReader provider = new IntelHexReader(reader);

            byte[] binData = new byte[MAX_BIN_SIZE];
            boolean[] dataCheck = new boolean[MAX_BIN_SIZE];

            long address;
            long startAddress = 0xfffffff;
            long endAddress = 0x0;
            int error = 0;
            int readByte;

            try {
                while ((readByte = provider.readByte()) != -1) {
                    address = provider.getAddress();

                    // complete Start Segment Address record type
                    if (address <= 0x0004)
                        continue;

                    if ((address == START_ADDRESS) && ((readByte != 0x82) && (readByte != 0xAC))) {
                        error = 2; // firmware is not valid
                        break;
                    }
                    if (address > MAX_ADDRESS || address < START_ADDRESS) {
                        error = 3; // firmware address out of range;
                        break;
                    }
                    binData[(int)address-START_ADDRESS] = (byte)readByte;
                    dataCheck[(int)address-START_ADDRESS] = true;

                    if (address < startAddress)
                        startAddress = address;
                    if (address > endAddress)
                        endAddress = address;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                provider.close();
            }

            if (startAddress != START_ADDRESS)
                error = 3;
            else {
                for (int i=0; i<=(endAddress-startAddress);i++)
                    if (!dataCheck[i]) {
                        error = 1; // address not contiguous
                        break;
                    }
            }

            if (error == 0) {
                outFile = new File(getFilesDir(), "stm8.bin");
                FileOutputStream out = new FileOutputStream(outFile);
                out.write(binData, 0, (int)(endAddress-startAddress+1));
                out.close();
            }

            switch (error) {
                case 1:
                    showDialog(getString(R.string.error), getString(R.string.address_not_contiguous), false);
                    break;
                case 2:
                    showDialog(getString(R.string.error), getString(R.string.fileNotValid), false);
                    break;
                case 3:
                    showDialog(getString(R.string.error), getString(R.string.wrong_start_address), false);
                    break;
                case 0:
                    updateFile = outFile;
                    startUpdateBT.setEnabled(true);
                    String fileName = getFileName(uri);
                    fileNameTV.setText(getString(R.string.file_name, fileName));
                    Log.i(TAG, "Filename: " + fileName);
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

            if (!enable && wifiState) {
                wifimanager.setWifiEnabled(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
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
        command[0] = CMD_STM8S_OTA_START;
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
        updateInProgress = UpdateProgess.started;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        selFileButton.setEnabled(false);
        startUpdateBT.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        messageTV.setText(getString(R.string.waitingUploadStart));
    }

    private void stopUpdate() {
        updateInProgress = UpdateProgess.notStarted;
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
            builder.setOnCancelListener((dialog) -> Stm8_Ota.this.finish());
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> Stm8_Ota.this.finish());
        } else
            builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    @Override
    public void progress(int percent) {
        Stm8_Ota.this.runOnUiThread( () -> {
            updateInProgress = UpdateProgess.uploading;
            messageTV.setText(getString(R.string.uploading, percent));});
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private class HotSpotCallback extends WifiManager.LocalOnlyHotspotCallback {
        @Override
        public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
            WifiConfiguration cfg = reservation.getWifiConfiguration();
            Stm8_Ota.this.reservation = reservation;
            ssid = cfg.SSID;
            pwd = cfg.preSharedKey;
            Log.d(TAG, "SSID: " + ssid);
            Log.d(TAG, "PWD: " + ssid);
        }
    }

    void showStatus(int status, int value) {
        switch (status) {
            case 0:
                messageTV.setText(getString(R.string.upgrade_ok));
                showDialog(getString(R.string.upgrade_ok), getString(R.string.stm8_upgrade_message), false);
                stopUpdate();
                break;
            case 1:
                messageTV.setText(getString(R.string.write_init));
                updateInProgress = UpdateProgess.writeinit;
                break;
            case 2:
                messageTV.setText(getString(R.string.start_programming));
                updateInProgress = UpdateProgess.writeInitialized;
                break;
            case 3:
                messageTV.setText(getString(R.string.writing, value));
                updateInProgress = UpdateProgess.writing;
                break;
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
                    case CMD_STM8S_OTA_START:
                        Log.d(TAG, "CMD_STM8S_OTA_START");
                        // check if update started
                        if (data[1] != (byte)0x0) {
                            stopUpdate();
                            showDialog(getString(R.string.error), getString(R.string.updateError), false);
                        }
                        break;
                    // Get Version response
                    case CMD_GET_APP_VERSION:
                        Log.d(TAG, "CMD_GET_APP_VERSION");
                        String s = new String(copyOfRange(data, 1, data.length), StandardCharsets.UTF_8);
                        String[] versions = s.split("\\|");
                        if (versions.length != 2) {
                            Log.e(TAG, "CMD_GET_APP_VERSION: wrong string");
                            return;
                        }
                        if ("255".equals(versions[0]))
                            versions[0] = "n/a";
                        currVerTV.setText(getString(R.string.current_version, versions[0]));
                        break;
                    case CMD_STM8_OTA_STATUS:
                        int status = data[1];
                        int value = data[2];
                        Log.d(TAG, "CMD_STM_OTA_STATUS: status=" + status + " value=" + value);
                        if (data[1] == 4) { // Error!
                            showDialog(getString(R.string.error), getString(R.string.update_error, value), false);
                            stopUpdate();
                            break;
                        }
                        showStatus(status, value);
                        break;
                }
                break;
            case CONNECTION_SUCCESS:
                Log.d(TAG, "CONNECTION_SUCCESS_BROADCAST");
                if (updateInProgress == UpdateProgess.uploading) {
                    messageTV.setText(getString(R.string.uploadDone));
                    updateInProgress = UpdateProgess.uploaded;
                }
                break;
            case CONNECTION_FAILURE:
                Log.d(TAG, "CONNECTION_FAILURE_BROADCAST");
                // TODO
                break;
            case CONNECTION_LOST:
                Log.d(TAG, "CONNECTION_LOST_BROADCAST");
                if (updateInProgress == UpdateProgess.started) {
                    messageTV.setText(getString(R.string.rebooting));
                    updateInProgress = UpdateProgess.rebooting;
                }
                break;
        }
    }
}