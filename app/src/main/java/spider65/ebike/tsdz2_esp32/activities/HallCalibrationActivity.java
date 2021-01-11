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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Locale;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Config;
import spider65.ebike.tsdz2_esp32.utils.LinearRegression;
import spider65.ebike.tsdz2_esp32.utils.RollingAverage;

public class HallCalibrationActivity extends AppCompatActivity {
    private static final String TAG = "HallCalibration";
    private static final byte CMD_HALL_DATA = 0x07;
    private static final byte CMD_MOTOR_TEST = 0x0B;
    private static final byte TEST_STOP = 0;
    private static final byte TEST_START = 1;
    private static final byte CALIB_ANGLE_ADJ = 0;
    private static final int SETUP_STEPS = 15;
    private static final int AVG_SIZE = 150;

    private static final int PHASE_OFFSET = 10;
    private static final int PHASE_ANGLE = 64;

    private final TSDZ_Config cfg = new TSDZ_Config();
    private boolean saveData = false;
    private final IntentFilter mIntentFilter = new IntentFilter();

    private TextView erpsTV;
    private final TextView[] angleTV = new TextView[6];
    private final TextView[] offsetTV = new TextView[6];
    private final TextView[] errorTV = new TextView[6];
    private Button startBT,stopBT, cancelBT, saveBT;
    private ProgressBar progressBar;
    private TextView progressTV;

    private boolean calibrationRunning = false;

    private int msgCounter;
    private int step = 0;
    private final byte[] dutyCycles = {25, 50, 100, (byte)(150 & 0xff)};
    private final RollingAverage[] avg = {
            new RollingAverage(AVG_SIZE),
            new RollingAverage(AVG_SIZE),
            new RollingAverage(AVG_SIZE),
            new RollingAverage(AVG_SIZE),
            new RollingAverage(AVG_SIZE),
            new RollingAverage(AVG_SIZE)};

    private final double[][] px = new double[6][4];
    private final double[][] py = new double[6][4];
    private final LinearRegression[] linearRegression = new LinearRegression[6];


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hal_sensor_calibration);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        erpsTV = findViewById(R.id.erpsTV);

        angleTV[0] = findViewById(R.id.angle1TV);
        angleTV[1] = findViewById(R.id.angle2TV);
        angleTV[2] = findViewById(R.id.angle3TV);
        angleTV[3] = findViewById(R.id.angle4TV);
        angleTV[4] = findViewById(R.id.angle5TV);
        angleTV[5] = findViewById(R.id.angle6TV);
        offsetTV[0] = findViewById(R.id.offset1TV);
        offsetTV[1] = findViewById(R.id.offset2TV);
        offsetTV[2] = findViewById(R.id.offset3TV);
        offsetTV[3] = findViewById(R.id.offset4TV);
        offsetTV[4] = findViewById(R.id.offset5TV);
        offsetTV[5] = findViewById(R.id.offset7TV);
        errorTV[0] = findViewById(R.id.err1TV);
        errorTV[1] = findViewById(R.id.err2TV);
        errorTV[2] = findViewById(R.id.err3TV);
        errorTV[3] = findViewById(R.id.err4TV);
        errorTV[4] = findViewById(R.id.err5TV);
        errorTV[5] = findViewById(R.id.err6TV);
        startBT = findViewById(R.id.startButton);
        stopBT = findViewById(R.id.stopButton);
        cancelBT = findViewById(R.id.exitButton);
        saveBT = findViewById(R.id.saveButton);
        progressBar = findViewById(R.id.progressBar);
        progressTV = findViewById(R.id.progressTV);

        mIntentFilter.addAction(TSDZBTService.TSDZ_COMMAND_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.TSDZ_CFG_READ_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.TSDZ_CFG_WRITE_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.CONNECTION_LOST_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.CONNECTION_FAILURE_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.SERVICE_STOPPED_BROADCAST);

        if (TSDZBTService.getBluetoothService() == null
                || TSDZBTService.getBluetoothService().getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            startBT.setEnabled(false);
            showDialog(getString(R.string.error), getString(R.string.connection_error));
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onBackPressed() {
        if (calibrationRunning) {
            showDialog(getString(R.string.warning), getString(R.string.exitMotorRunningError));
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
        if (calibrationRunning && TSDZBTService.getBluetoothService() != null)
            TSDZBTService.getBluetoothService().writeCommand(new byte[] {CMD_MOTOR_TEST,TEST_STOP});
        calibrationRunning = false;
        updateUI();
        super.onStop();
    }

    public void onButtonClick(View view) {
        if (view.getId() == R.id.startButton) {
            step = 0;
            for (TextView tv:angleTV)
                tv.setText(getString(R.string.dash));
            for (TextView tv:offsetTV)
                tv.setText(getString(R.string.dash));
            for (TextView tv:errorTV)
                tv.setText(getString(R.string.dash));
            startCalib();
        } else if (view.getId() == R.id.stopButton) {
            stopCalib();
        } else if (view.getId() == R.id.exitButton) {
            if (calibrationRunning && TSDZBTService.getBluetoothService() != null)
                TSDZBTService.getBluetoothService().writeCommand(new byte[] {CMD_MOTOR_TEST,TEST_STOP});
            calibrationRunning = false;
            finish();
        } else if (view.getId() == R.id.saveButton) {
            if (TSDZBTService.getBluetoothService() != null) {
                TSDZBTService.getBluetoothService().readCfg();
                saveData = true;
            }
        }
    }

    private void updateUI() {
        if (calibrationRunning) {
            startBT.setEnabled(false);
            cancelBT.setEnabled(false);
            saveBT.setEnabled(false);
            stopBT.setEnabled(true);
            progressBar.setVisibility(View.VISIBLE);
            progressTV.setVisibility(View.VISIBLE);
            if (step == 0)
                progressTV.setText("0%");
        } else {
            startBT.setEnabled(true);
            cancelBT.setEnabled(true);
            progressBar.setVisibility(View.INVISIBLE);
            progressTV.setVisibility(View.INVISIBLE);
            progressBar.setVisibility(View.INVISIBLE);
            progressTV.setVisibility(View.INVISIBLE);
        }
    }

    private void startCalib() {
        if (TSDZBTService.getBluetoothService() == null
                || TSDZBTService.getBluetoothService().getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error));
            return;
        }
        msgCounter = 0;
        for (RollingAverage ra: avg)
            ra.reset();
        TSDZBTService.getBluetoothService().writeCommand(new byte[] {CMD_MOTOR_TEST, TEST_START, dutyCycles[step], CALIB_ANGLE_ADJ});
    }

    private void stopCalib() {
        calibrationRunning = false;
        updateUI();
        if (TSDZBTService.getBluetoothService() == null
                || TSDZBTService.getBluetoothService().getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED)
            showDialog(getString(R.string.error), getString(R.string.connection_error));
        else
            TSDZBTService.getBluetoothService().writeCommand(new byte[] {CMD_MOTOR_TEST,TEST_STOP});
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
        for (int i=0; i<6; i++) {
            linearRegression[i] = new LinearRegression(px[i], py[i]);
            angleTV[i].setText(String.format(Locale.getDefault(),"%.1f", linearRegression[i].slope()*360));
            offsetTV[i].setText(String.format(Locale.getDefault(),"%.1f", linearRegression[i].intercept()));
            errorTV[i].setText(String.format(Locale.getDefault(),"%.2f", linearRegression[i].R2()));
        }

        if (valuesOK()) {
            showDialog("", getString(R.string.calibrationDataValid));
            saveBT.setEnabled(true);
        } else {
            showDialog(getString(R.string.error), getString(R.string.calibrationDataNotValid));
            saveBT.setEnabled(false);
        }
    }

    private boolean valuesOK() {
        for (int i=0; i<6; i++)
            if (linearRegression[i].R2() < 0.9)
                return false;
        return true;
    }

    private void nextStep(long x) {
        Log.d(TAG, "nextStep: " + step);
        for (int i=0; i<6; i++) {
            px[i][step] = x;
            py[i][step] = avg[i].getAverage();
        }
        if (++step == 4) {
            stopCalib();
            updateResult();
        } else
            startCalib();
    }

    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null)
                return;
            switch (intent.getAction()) {
                case TSDZBTService.CONNECTION_LOST_BROADCAST:
                case TSDZBTService.SERVICE_STOPPED_BROADCAST:
                case TSDZBTService.CONNECTION_FAILURE_BROADCAST:
                    calibrationRunning = false;
                    updateUI();
                    Toast.makeText(getApplicationContext(), "BT Connection Lost.", Toast.LENGTH_LONG).show();
                    break;
                case TSDZBTService.TSDZ_CFG_READ_BROADCAST:
                    if (saveData) {
                        saveData = false;
                        if (!cfg.setData(intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA))) {
                            showDialog(getString(R.string.error), getString(R.string.calibrationSaveError));
                            break;
                        }
                        // TODO

                        double[] calcRefAngles = {0d,0d,0d,0d,0d,0d};
                        double value = 0;
                        double error = 0;
                        for (int i=1; i<6; i++) {
                            value += linearRegression[i].slope()*256D;
                            error += value - ((double)i*256D/6D);
                            calcRefAngles[i] = value;
                        }
                        error /= 6D;
                        double offset = 0;
                        for (int i=0; i<6; i++) {
                            calcRefAngles[i] = calcRefAngles[i] - error + 256D/12D;
                            cfg.ui8_hall_ref_angles[i] = (byte)((Math.round(calcRefAngles[i]) + PHASE_OFFSET - PHASE_ANGLE) & 0xff);
                            offset += Math.abs(linearRegression[i].intercept());
                        }
                        cfg.ui8_hall_counter_offset_up = (byte)(Math.round(offset/6D) + 8);
                        cfg.ui8_hall_counter_offset_down = 8;

                        Log.d(TAG,"ui8_hall_ref_angles:" + (cfg.ui8_hall_ref_angles[0] & 0xff)
                                + "," + (cfg.ui8_hall_ref_angles[1] & 0xff)
                                + "," + (cfg.ui8_hall_ref_angles[2] & 0xff)
                                + "," + (cfg.ui8_hall_ref_angles[3] & 0xff)
                                + "," + (cfg.ui8_hall_ref_angles[4] & 0xff)
                                + "," + (cfg.ui8_hall_ref_angles[5] & 0xff)
                        );
                        Log.d(TAG, "ui8_hall_counter_offset_up:" + cfg.ui8_hall_counter_offset_up);
                        Log.d(TAG, "ui8_hall_counter_offset_down:" + cfg.ui8_hall_counter_offset_down);

                        if (TSDZBTService.getBluetoothService() != null)
                            TSDZBTService.getBluetoothService().writeCfg(cfg);
                    }
                    break;
                case TSDZBTService.TSDZ_CFG_WRITE_BROADCAST:
                    if (intent.getBooleanExtra(TSDZBTService.VALUE_EXTRA,false))
                        showDialog("", getString(R.string.calibrationApplied));
                    else
                        showDialog(getString(R.string.error), getString(R.string.calibrationSaveError));
                    break;
                case TSDZBTService.TSDZ_COMMAND_BROADCAST:
                    byte[] data = intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA);
                    //Log.d(TAG,"TSDZ_COMMAND_BROADCAST: " + Utils.bytesToHex(data));
                    if (data[0] == CMD_MOTOR_TEST) {
                        switch(data[1]) {
                            case TEST_START:
                                if (data[2] != (byte)0x0)
                                    showDialog(getString(R.string.error), getString(R.string.motorTestStartError));
                                else
                                    calibrationRunning = true;
                                updateUI();
                                break;
                            case TEST_STOP:
                                if (data[2] != (byte)0x0) {
                                    showDialog(getString(R.string.error), getString(R.string.motorTestStopError));
                                } else {
                                    stopBT.setEnabled(false);
                                }
                                break;
                            case (byte)0xff:
                                showDialog(getString(R.string.error), getString(R.string.commandError));
                                break;
                        }
                    } else if (data[0] == CMD_HALL_DATA) {
                        if (!calibrationRunning || (msgCounter++ < SETUP_STEPS)) {
                            return;
                        }
                        long sum = 0;
                        for (int i=0; i<6; i++) {
                            avg[i].add(((data[i*2+2] & 255) << 8) + (data[i*2+1] & 255));
                            sum += avg[i].getAverage();
                        }
                        erpsTV.setText(String.format(Locale.getDefault(), "%.2f", 250000D/sum));
                        if (avg[0].getIndex() == 0)
                            nextStep(sum);
                        else {
                            long progress = (100 * (step * AVG_SIZE + avg[0].getIndex())) / (4 * AVG_SIZE);
                            progressTV.setText(String.format(Locale.getDefault(), "%d%%", progress));
                        }
                    }
                    break;
            }
        }
    };
}