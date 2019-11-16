package spider65.ebike.tsdz2_esp32.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Debug;
import spider65.ebike.tsdz2_esp32.utils.Utils;

import static spider65.ebike.tsdz2_esp32.TSDZConst.CALIBRATION_SAVE;
import static spider65.ebike.tsdz2_esp32.TSDZConst.CALIBRATION_START;
import static spider65.ebike.tsdz2_esp32.TSDZConst.CALIBRATION_STOP;
import static spider65.ebike.tsdz2_esp32.TSDZConst.CMD_CADENCE_CALIBRATION;

public class CadenceSensorCalibrationActivity extends AppCompatActivity {
    private static final String TAG = "CadenceSensorCalib";

    private IntentFilter mIntentFilter = new IntentFilter();
    TextView percentTV;
    Button startBT,stopBT, saveBT;
    float percent = 0;
    boolean calibrationRunning = false;
    TSDZBTService service;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadence_sensor_calibration);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        percentTV = findViewById(R.id.percentTV);
        startBT = findViewById(R.id.startButton);
        stopBT = findViewById(R.id.stopButton);
        saveBT = findViewById(R.id.okButton);

        mIntentFilter.addAction(TSDZBTService.TSDZ_DEBUG_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.TSDZ_COMMAND_BROADCAST);
        service = TSDZBTService.getBluetoothService();
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, mIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    }

    public void onButtonClick(View view) {
        switch (view.getId()) {
            case R.id.startButton:
                startCalib();
                break;
            case R.id.stopButton:
                stopCalib();
                break;
            case R.id.okButton:
                saveValue();
                break;
            case R.id.cancelButton:
                if (calibrationRunning && service != null)
                    service.writeCommand(new byte[] {CMD_CADENCE_CALIBRATION,CALIBRATION_STOP});
                finish();
                break;
        }
    }

    private void startCalib() {
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error));
            return;
        }
        service.writeCommand(new byte[] {CMD_CADENCE_CALIBRATION,CALIBRATION_START});
    }

    private void stopCalib() {
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error));
            return;
        }
        service.writeCommand(new byte[] {CMD_CADENCE_CALIBRATION,CALIBRATION_STOP});
    }

    private void saveValue() {
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error));
            return;
        }
        byte lsb,msb;
        int val = (int)(percent*10.);
        lsb = (byte)val;
        msb = (byte)(val >>> 8);
        service.writeCommand(new byte[] {CMD_CADENCE_CALIBRATION,CALIBRATION_SAVE,lsb,msb});
    }


    private void showDialog (String title, String message) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (title != null)
            builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null)
                return;
            byte[] data = intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA);
            if (TSDZBTService.TSDZ_DEBUG_BROADCAST.equals(intent.getAction())) {
                Log.d(TAG,"TSDZ_DEBUG_BROADCAST: " + Utils.bytesToHex(data));
                TSDZ_Debug debugData = new TSDZ_Debug();
                debugData.setData(data);
                percent = debugData.cadencePulseHighPercentage;
                percentTV.setText(String.valueOf(percent));
            } else if (TSDZBTService.TSDZ_COMMAND_BROADCAST.equals(intent.getAction())) {
                Log.d(TAG,"TSDZ_COMMAND_BROADCAST: " + Utils.bytesToHex(data));
                if (data[0] == CMD_CADENCE_CALIBRATION) {
                    switch(data[1]) {
                        case CALIBRATION_START:
                            if (data[2] != (byte)0x0) {
                                showDialog(getString(R.string.error), getString(R.string.cadenceStartError));
                            } else {
                                calibrationRunning = true;
                                startBT.setEnabled(false);
                                saveBT.setEnabled(false);
                                stopBT.setEnabled(true);
                            }
                            break;
                        case CALIBRATION_STOP:
                            if (data[2] != (byte)0x0) {
                                showDialog(getString(R.string.error), getString(R.string.cadenceStopError));
                            } else {
                                calibrationRunning = false;
                                startBT.setEnabled(true);
                                stopBT.setEnabled(false);
                                saveBT.setEnabled(true);
                            }
                            break;
                        case CALIBRATION_SAVE:
                            if (data[2] != (byte)0x0) {
                                showDialog(getString(R.string.error), getString(R.string.cadenceSaveError));
                            } else {
                                finish();
                            }
                            break;
                        case (byte)0xff:
                            showDialog(getString(R.string.error), getString(R.string.commandError));
                            break;
                    }
                }
            }
        }
    };
}
