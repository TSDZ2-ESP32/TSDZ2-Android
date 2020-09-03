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

import java.util.Timer;
import java.util.TimerTask;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Debug;
import spider65.ebike.tsdz2_esp32.utils.Utils;

public class HalSensorCalibrationActivity extends AppCompatActivity {
    private static final String TAG = "CadenceSensorCalib";
    private static final byte CMD_HAL_CALIBRATION = 7;
    private static final byte CALIBRATION_STOP = 0;
    private static final byte CALIBRATION_ON = 1;
    private static final byte CALIBRATION_START = 2;

    private IntentFilter mIntentFilter = new IntentFilter();
    TextView erpsTV,hal1TV,hal2TV,hal3TV,hal4TV,hal5TV,hal6TV;
    Button startBT,stopBT, cancelBT;
    boolean calibrationRunning = false;
    TSDZBTService service;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hal_sensor_calibration);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        erpsTV = findViewById(R.id.erpsTV);
        hal1TV = findViewById(R.id.hal1TV);
        hal2TV = findViewById(R.id.hal2TV);
        hal3TV = findViewById(R.id.hal3TV);
        hal4TV = findViewById(R.id.hal4TV);
        hal5TV = findViewById(R.id.hal5TV);
        hal6TV = findViewById(R.id.hal6TV);
        startBT = findViewById(R.id.startButton);
        stopBT = findViewById(R.id.stopButton);
        cancelBT = findViewById(R.id.cancelButton);

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

    @Override
    protected void onStop() {
        resetTimer();
        if (calibrationRunning && service != null)
            service.writeCommand(new byte[] {CMD_HAL_CALIBRATION,CALIBRATION_STOP});
        calibrationRunning = false;
        finish();
        super.onStop();
    }

    public void onButtonClick(View view) {
        switch (view.getId()) {
            case R.id.startButton:
                startCalib();
                break;
            case R.id.stopButton:
                stopCalib();
                break;
            case R.id.cancelButton:
                resetTimer();
                if (calibrationRunning && service != null)
                    service.writeCommand(new byte[] {CMD_HAL_CALIBRATION,CALIBRATION_STOP});
                calibrationRunning = false;
                finish();
                break;
        }
    }

    private void startCalib() {
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error));
            return;
        }
        service.writeCommand(new byte[] {CMD_HAL_CALIBRATION,CALIBRATION_ON});
    }

    private void stopCalib() {
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error));
            return;
        }
        service.writeCommand(new byte[] {CMD_HAL_CALIBRATION,CALIBRATION_STOP});
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
                int val;
                erpsTV.setText(String.valueOf(debugData.motorERPS));
                val = ((debugData.adcThrottle & 255) << 8) + (debugData.throttle & 255);
                hal1TV.setText(String.valueOf(val));
                hal2TV.setText(String.valueOf(debugData.torqueSensorValue));
                val = ((debugData.rxcErrors & 255) << 8) + (debugData.rxlErrors & 255);
                hal3TV.setText(String.valueOf(val));
                hal4TV.setText(String.valueOf(debugData.pTorque*100));
                hal5TV.setText(String.valueOf(debugData.pcbTemperature*10));
                hal6TV.setText(String.valueOf(debugData.notUsed));
            } else if (TSDZBTService.TSDZ_COMMAND_BROADCAST.equals(intent.getAction())) {
                Log.d(TAG,"TSDZ_COMMAND_BROADCAST: " + Utils.bytesToHex(data));
                if (data[0] == CMD_HAL_CALIBRATION) {
                    switch(data[1]) {
                        case CALIBRATION_ON:
                            if (data[2] != (byte)0x0) {
                                showDialog(getString(R.string.error), getString(R.string.cadenceStartError));
                            } else {
                                // start calibration procedure 5sec after motor startup
                                scheduleTimer();
                                calibrationRunning = true;
                                startBT.setEnabled(false);
                                stopBT.setEnabled(true);
                            }
                            break;
                        case CALIBRATION_STOP:
                            if (data[2] != (byte)0x0) {
                                showDialog(getString(R.string.error), getString(R.string.cadenceStopError));
                            } else {
                                resetTimer();
                                calibrationRunning = false;
                                startBT.setEnabled(true);
                                stopBT.setEnabled(false);
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

    private Timer timer;
    private class TimerExpired extends TimerTask {
        @Override
        public void run(){
            Log.w(TAG, "Timeout!");
            service.writeCommand(new byte[] {CMD_HAL_CALIBRATION,CALIBRATION_START});
        }
    }

    private void scheduleTimer() {
        resetTimer();
        TimerExpired timerExpired = new TimerExpired();
        timer = new Timer();
        timer.schedule(timerExpired, 5000);
    }

    private void resetTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}