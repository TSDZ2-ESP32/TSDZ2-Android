package spider65.ebike.tsdz2_esp32.ota;

import android.Manifest;
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
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.obsez.android.lib.filechooser.ChooserDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
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
    private IntentFilter mIntentFilter = new IntentFilter();

    private Button selFileButton, startUpdateBT;
    private TextView currVerTV, fileNameTV,messageTV;
    private ProgressBar progressBar;

    private HotSpotCallback hotSpotCallback = null;

    private static final int READ_EXTERNAL_STORAGE_PERMISION_REQUEST = 3;

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


    /**
     * Fires an intent to spin up the "file chooser" UI and select an image.
     */
    public void performFileSearch() {
        new ChooserDialog(this)
            .withFilterRegex(false, false, ".*\\.hex$")
            .withStartFile(Environment.getExternalStorageDirectory().getAbsolutePath())
            .withStringResources(getString(R.string.title_select_file,"bin"),
                        getString(R.string.choose), getString(R.string.cancel))
            .withChosenListener( (String path, File pathFile) -> checkFile( pathFile ))
            .build()
            .show();
    }
    void checkFile(File f) {
        try {
            FileReader fileReader = new FileReader(f);
            File outFile = null;

            final IntelHexReader provider = new IntelHexReader(fileReader);

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
                fileReader.close();
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
                    fileNameTV.setText(getString(R.string.file_name, f.getName()));
                    Log.i(TAG, "Filename: " + f.getName());
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

    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
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
                case TSDZBTService.CONNECTION_SUCCESS_BROADCAST:
                    Log.d(TAG, "CONNECTION_SUCCESS_BROADCAST");
                    if (updateInProgress == UpdateProgess.uploading) {
                        messageTV.setText(getString(R.string.uploadDone));
                        updateInProgress = UpdateProgess.uploaded;
                    }
                    break;
                case TSDZBTService.CONNECTION_FAILURE_BROADCAST:
                    Log.d(TAG, "CONNECTION_FAILURE_BROADCAST");
                    // TODO
                    break;
                case TSDZBTService.CONNECTION_LOST_BROADCAST:
                    Log.d(TAG, "CONNECTION_LOST_BROADCAST");
                    if (updateInProgress == UpdateProgess.started) {
                        messageTV.setText(getString(R.string.rebooting));
                        updateInProgress = UpdateProgess.rebooting;
                    }
                    break;
            }
        }
    };
}
