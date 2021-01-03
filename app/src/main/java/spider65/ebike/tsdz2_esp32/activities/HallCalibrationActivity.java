package spider65.ebike.tsdz2_esp32.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Locale;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.utils.Utils;

public class HallCalibrationActivity extends AppCompatActivity {
    private static final String TAG = "HallCalibration";
    private static final byte CMD_HALL_DATA = 0x07;
    private static final byte CMD_MOTOR_TEST = 0x0B;
    private static final byte TEST_STOP = 0;
    private static final byte TEST_START = 1;
    private static final byte CALIB_DUTY_CYCLE = 25;
    private static final byte CALIB_ANGLE_ADJ = 0;

    private IntentFilter mIntentFilter = new IntentFilter();
    TextView erpsTV,hal1TV,hal2TV,hal3TV,hal4TV,hal5TV,hal6TV;
    TextView hal1rTV,hal2rTV,hal3rTV,hal4rTV,hal5rTV,hal6rTV;
    Button startBT,stopBT, cancelBT;
    boolean calibrationRunning = false;
    TSDZBTService service;
    RollingAverage[] avg = new RollingAverage[6];


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
        hal1rTV = findViewById(R.id.hal1rTV);
        hal2rTV = findViewById(R.id.hal2rTV);
        hal3rTV = findViewById(R.id.hal3rTV);
        hal4rTV = findViewById(R.id.hal4rTV);
        hal5rTV = findViewById(R.id.hal5rTV);
        hal6rTV = findViewById(R.id.hal6rTV);
        startBT = findViewById(R.id.startButton);
        stopBT = findViewById(R.id.stopButton);
        cancelBT = findViewById(R.id.cancelButton);

        mIntentFilter.addAction(TSDZBTService.TSDZ_COMMAND_BROADCAST);
        service = TSDZBTService.getBluetoothService();
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error));
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onBackPressed() {
        if (calibrationRunning) {
            showDialog(getString(R.string.warning), getString(R.string.exitHallError));
        } else {
            finish();
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
        super.onStop();
        if (calibrationRunning && service != null)
            service.writeCommand(new byte[] {CMD_MOTOR_TEST,TEST_STOP});
        calibrationRunning = false;
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
                if (calibrationRunning && service != null)
                    service.writeCommand(new byte[] {CMD_MOTOR_TEST,TEST_STOP});
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
        for (int i=0; i<avg.length; i++)
            avg[i] = new RollingAverage(256);

        service.writeCommand(new byte[] {CMD_MOTOR_TEST, TEST_START, CALIB_DUTY_CYCLE, CALIB_ANGLE_ADJ});
    }

    private void stopCalib() {
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error));
            return;
        }
        service.writeCommand(new byte[] {CMD_MOTOR_TEST,TEST_STOP});
    }

    private void showDialog (String title, String message) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (title != null)
            builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.ok, null);
        builder.show();
    }

    private void updateResult() {
        double total = 0;
        for (RollingAverage rollingAverage : avg) total += rollingAverage.getAverage();
        hal1rTV.setText(String.format(Locale.getDefault(),"%.2f", 60D*(double)avg[0].getAverage()/total));
        hal2rTV.setText(String.format(Locale.getDefault(),"%.2f", 60D*(double)avg[1].getAverage()/total));
        hal3rTV.setText(String.format(Locale.getDefault(),"%.2f", 60D*(double)avg[2].getAverage()/total));
        hal4rTV.setText(String.format(Locale.getDefault(),"%.2f", 60D*(double)avg[3].getAverage()/total));
        hal5rTV.setText(String.format(Locale.getDefault(),"%.2f", 60D*(double)avg[4].getAverage()/total));
        hal6rTV.setText(String.format(Locale.getDefault(),"%.2f", 60D*(double)avg[5].getAverage()/total));
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TSDZBTService.TSDZ_COMMAND_BROADCAST.equals(intent.getAction())) {
                byte[] data = intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA);
                Log.d(TAG,"TSDZ_COMMAND_BROADCAST: " + Utils.bytesToHex(data));
                if (data[0] == CMD_MOTOR_TEST) {
                    switch(data[1]) {
                        case TEST_START:
                            if (data[2] != (byte)0x0) {
                                showDialog(getString(R.string.error), getString(R.string.motorTestStartError));
                            } else {
                                // start calibration procedure 5sec after motor startup
                                calibrationRunning = true;
                                startBT.setEnabled(false);
                                cancelBT.setEnabled(false);
                                stopBT.setEnabled(true);
                            }
                            break;
                        case TEST_STOP:
                            if (data[2] != (byte)0x0) {
                                showDialog(getString(R.string.error), getString(R.string.motorTestStopError));
                            } else {
                                calibrationRunning = false;
                                startBT.setEnabled(true);
                                cancelBT.setEnabled(true);
                                stopBT.setEnabled(false);
                            }
                            updateResult();
                            break;
                        case (byte)0xff:
                            showDialog(getString(R.string.error), getString(R.string.commandError));
                            break;
                    }
                } else if (data[0] == CMD_HALL_DATA) {
                    // Hall counter for full ERPS revolution (sum of the 6 hall transition counters)
                    int val = ((data[2] & 255) << 8) + (data[1] & 255)
                            + (((data[4] & 255) << 8) + (data[3] & 255))
                            + (((data[6] & 255) << 8) + (data[5] & 255))
                            + (((data[8] & 255) << 8) + (data[7] & 255))
                            + (((data[10] & 255) << 8) + (data[9] & 255))
                            + (((data[12] & 255) << 8) + (data[11] & 255));
                    erpsTV.setText(String.format(Locale.getDefault(), "%.2f", 250000D/(double)val));

                    avg[0].add(((data[2] & 255) << 8) + (data[1] & 255));
                    hal1TV.setText(String.valueOf(avg[0].getAverage()));
                    avg[1].add(((data[4] & 255) << 8) + (data[3] & 255));
                    hal2TV.setText(String.valueOf(avg[1].getAverage()));
                    avg[2].add(((data[6] & 255) << 8) + (data[5] & 255));
                    hal3TV.setText(String.valueOf(avg[2].getAverage()));
                    avg[3].add(((data[8] & 255) << 8) + (data[7] & 255));
                    hal4TV.setText(String.valueOf(avg[3].getAverage()));
                    avg[4].add(((data[10] & 255) << 8) + (data[9] & 255));
                    hal5TV.setText(String.valueOf(avg[4].getAverage()));
                    avg[5].add(((data[12] & 255) << 8) + (data[11] & 255));
                    hal6TV.setText(String.valueOf(avg[5].getAverage()));
                }
            }
        }
    };

    private static class RollingAverage {

        private int size;
        private long total = 0;
        private int index = 0;
        private final int[] samples;
        private boolean rollover = false;

        public RollingAverage(int size) {
            this.size = size;
            samples = new int[size];
            for (int i = 0; i < size; i++) samples[i] = 0;
        }

        public void add(int x) {
            total -= samples[index];
            samples[index] = x;
            total += x;
            if (++index == size) {
                index = 0; // cheaper than modulus
                rollover = true;
            }
        }

        public long getAverage() {
            return rollover ?total/size:total/index;
        }
    }
}