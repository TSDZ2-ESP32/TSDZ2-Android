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
import android.os.Handler;
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
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import nl.lxtreme.binutils.hex.IntelHexReader;
import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.utils.Utils;

import static android.view.View.INVISIBLE;
import static java.util.Arrays.copyOfRange;
import static spider65.ebike.tsdz2_esp32.TSDZConst.CMD_GET_APP_VERSION;
import static spider65.ebike.tsdz2_esp32.TSDZConst.CMD_STM8S_OTA_START;
import static spider65.ebike.tsdz2_esp32.TSDZConst.CMD_STM_OTA_STATUS;


public class Stm8_Ota extends AppCompatActivity implements ProgressInputStreamListener {

    private static final String TAG = "Stm8_Ota";

    private static final int MAX_BIN_SIZE = 0x8000;
    private static final int START_ADDRESS = 0x8000;
    private static final int MAX_ADDRESS = 0xFFFF;

    private File updateFile = null;
    private WifiManager.LocalOnlyHotspotReservation reservation = null;
    private boolean wifiState;

    private String ssid,pwd;
    private Set<String> prevSet;

    private HttpdServer httpdServer = null;
    private IntentFilter mIntentFilter = new IntentFilter();

    private Button selFileButton, startUpdateBT;
    private TextView fileNameTV,messageTV;
    private ProgressBar progressBar;

    private HotSpotCallback hotSpotCallback = null;

    private static final int READ_EXTERNAL_STORAGE_PERMISION_REQUEST = 3;

    private boolean updateInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stm8_ota);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        selFileButton = findViewById(R.id.selFileButton);
        selFileButton.setOnClickListener((View view) -> performFileSearch());

        startUpdateBT = findViewById(R.id.startUpdateButton);
        startUpdateBT.setOnClickListener((View view) -> startUpdate());
        startUpdateBT.setEnabled(false);
        fileNameTV = findViewById(R.id.fileNameTV);
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
        if (Build.VERSION.SDK_INT >= 26) {
            prevSet = getAddresses();
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (hotSpotCallback == null)
                hotSpotCallback = new HotSpotCallback();
            wifiManager.startLocalOnlyHotspot(hotSpotCallback, null);
        } else {
            if (isApOn()) {
                wifiState = true;
                setWifiApState(false);
                AlertDialog alertDialog = new AlertDialog.Builder(Stm8_Ota.this).create();
                alertDialog.setTitle("Warning");
                alertDialog.setMessage("Access Point was ON\n Wait some seconds and then press OK.");
                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK",
                        (DialogInterface dialog, int which) -> {
                            dialog.dismiss();
                            prevSet = getAddresses();
                            setWifiApState(true);
                        });
                alertDialog.show();
            } else {
                prevSet = getAddresses();
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
        String hostAddress = getNewAddresses(prevSet);
        String url = "http:" + "//" + hostAddress + ":8089";
        byte[] command = new byte[ssid.length()+pwd.length()+url.length()+3];
        command[0] = CMD_STM8S_OTA_START;
        int pos = 1;
        System.arraycopy(ssid.getBytes(),0,command,pos,ssid.getBytes().length);
        pos += ssid.getBytes().length;
        command[pos++] = '|';
        System.arraycopy(pwd.getBytes(),0,command,pos,pwd.getBytes().length);
        pos += pwd.getBytes().length;
        command[pos++] = '|';
        System.arraycopy(url.getBytes(),0,command,pos,url.getBytes().length);
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
        builder.setPositiveButton(android.R.string.ok, null);
        if (exit)
            builder.setOnCancelListener( (dialog) -> Stm8_Ota.this.finish());
        builder.show();
    }

    Set<String> getAddresses() {
        Set<String> set = new HashSet<>();
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && !inetAddress.getHostAddress().contains(":")) {
                        set.add(inetAddress.getHostAddress());
                        Log.i(TAG, "if: " + intf.getDisplayName() + " - Host addr: " + inetAddress.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            Log.e("Error occurred  ", e.toString());
        }
        return set;
    }

    String getNewAddresses(Set<String> prevSet) {
        Set<String> newSet = getAddresses();
        newSet.removeAll(prevSet);
        Log.i(TAG, "newSet length = " + newSet.size());
        if (newSet.isEmpty())
            return null;
        else {
            String ret = newSet.iterator().next();
            Log.i(TAG, "Host Address : " + ret);
            return ret;
        }
    }

    @Override
    public void progress(int percent) {
        Stm8_Ota.this.runOnUiThread( () -> messageTV.setText(getString(R.string.uploading, percent)));
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
                            String tmp = new String(copyOfRange(data, 1, data.length), StandardCharsets.UTF_8);
                            String[] out = tmp.split("\\|");
                            if (out.length != 2) {
                                Log.e(TAG, "CMD_GET_APP_VERSION: wrong string");
                                return;
                            }
                            if ("ERR".equals(out[1]) || "EMP".equals(out[1]))
                                showDialog(getString(R.string.error), getString(R.string.partition_not_found), true);
                            break;
                        case CMD_STM_OTA_STATUS:
                            int phase = ((data[1] & 0x80) == 0) ? 0:1;
                            int status = data[1] & 0x7f;
                            Log.d(TAG, "CMD_STM_OTA_STATUS: phase=" + phase + " status=" + status);
                            if (status > 0) { // Error!
                                showDialog(getString(R.string.error), getString(R.string.update_error, phase, status), false);
                                stopUpdate();
                            } else if (phase == 0) { // phase 0 (upload) done
                                messageTV.setText(getString(R.string.upload_completed));
                                showDialog(getString(R.string.update_start_procedure), getString(R.string.update_start_description), false);
                            } else { // phase 1 (upgrade) done
                                messageTV.setText(getString(R.string.upgrade_ok));
                                showDialog(getString(R.string.upgrade_ok), getString(R.string.stm8_upgrade_message), false);
                                stopUpdate();
                            }
                            break;
                    }
                    break;
                case TSDZBTService.CONNECTION_SUCCESS_BROADCAST:
                    if (updateInProgress) {
                        final Handler handler = new Handler();
                        handler.postDelayed(() ->
                                TSDZBTService.getBluetoothService().writeCommand(new byte[] {CMD_STM_OTA_STATUS})
                                ,500);
                    }
                    break;
                case TSDZBTService.CONNECTION_FAILURE_BROADCAST:
                case TSDZBTService.CONNECTION_LOST_BROADCAST:
                    break;
            }
        }
    };
}
