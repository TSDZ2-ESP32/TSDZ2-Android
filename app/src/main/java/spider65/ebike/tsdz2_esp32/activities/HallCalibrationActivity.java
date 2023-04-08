package spider65.ebike.tsdz2_esp32.activities;

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
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Locale;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.TSDZConst;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Config;
import spider65.ebike.tsdz2_esp32.utils.Cramer;
import spider65.ebike.tsdz2_esp32.utils.LinearRegression;
import spider65.ebike.tsdz2_esp32.utils.RollingAverage;

import static spider65.ebike.tsdz2_esp32.TSDZConst.CMD_HALL_DATA;
import static spider65.ebike.tsdz2_esp32.TSDZConst.CMD_MOTOR_TEST;
import static spider65.ebike.tsdz2_esp32.TSDZConst.TEST_START;
import static spider65.ebike.tsdz2_esp32.TSDZConst.TEST_STOP;

public class HallCalibrationActivity extends AppCompatActivity {
    private static final String TAG = "HallCalibration";

    private static final int SETUP_STEPS = 30;
    private static final int AVG_SIZE = 100;

    private final TSDZ_Config cfg = new TSDZ_Config();

    private TextView erpsTV, hallValuesTV, hallUpDnDiffTV;
    private final TextView[] angleTV = new TextView[6];
    private final TextView[] offsetTV = new TextView[6];
    private final TextView[] errorTV = new TextView[6];
    private Button resetBT, cancelBT, saveBT;
    private ProgressBar progressBar;
    private TextView progressTV;

    private boolean calibrationRunning = false;

    private int msgCounter;
    private int step = 0;
    private final byte[] dutyCycles = {30, 70, 110, (byte)(160 & 0xff)};
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

    private final int[] phaseAngles = new int[6];
    private final int[] hallTOffset = new int[6];


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
        hallValuesTV = findViewById(R.id.hallValuesTV);
        hallUpDnDiffTV = findViewById(R.id.hallUpDnDiffTV);
        resetBT = findViewById(R.id.resetBT);
        cancelBT = findViewById(R.id.exitButton);
        saveBT = findViewById(R.id.saveButton);
        progressBar = findViewById(R.id.progressBar);
        progressTV = findViewById(R.id.progressTV);

        if (TSDZBTService.getBluetoothService() == null
                || TSDZBTService.getBluetoothService().getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED)
            showDialog(getString(R.string.error), getString(R.string.connection_error), true);
        else
            TSDZBTService.getBluetoothService().readCfg();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onBackPressed() {
        if (calibrationRunning) {
            showDialog(getString(R.string.warning), getString(R.string.exitMotorRunningError), false);
        } else {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
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
        if (view.getId() == R.id.startStopBT) {
            if (calibrationRunning) {
                stopCalib();
                return;
            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.warning);
            builder.setMessage(R.string.warningMotorStart);
            builder.setPositiveButton(R.string.ok, (dialog, which) -> {
                step = 0;
                for (TextView tv:angleTV)
                    tv.setText(getString(R.string.dash));
                for (TextView tv:offsetTV)
                    tv.setText(getString(R.string.dash));
                for (TextView tv:errorTV)
                    tv.setText(getString(R.string.dash));
                startCalib();
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.show();
        } else if (view.getId() == R.id.exitButton) {
            if (calibrationRunning && TSDZBTService.getBluetoothService() != null)
                TSDZBTService.getBluetoothService().writeCommand(new byte[] {CMD_MOTOR_TEST,TEST_STOP});
            calibrationRunning = false;
            finish();
        } else if (view.getId() == R.id.saveButton) {
            if (TSDZBTService.getBluetoothService() != null) {
                System.arraycopy(phaseAngles, 0, cfg.ui8_hall_ref_angles, 0, 6);
                System.arraycopy(hallTOffset, 0, cfg.ui8_hall_counter_offset, 0, 6);
                TSDZBTService.getBluetoothService().writeCfg(cfg);
            } else
                showDialog(getString(R.string.error), getString(R.string.connection_error), false);
        } else if (view.getId() == R.id.resetBT) {
            for (int i=0; i<6; i++) {
                angleTV[i].setText(R.string.dash);
                offsetTV[i].setText(R.string.dash);
                errorTV[i].setText(R.string.dash);
            }
            for (int i=0; i<6; i++) {
                double v = Math.round(((30D + 60D * (double) i) * (256D / 360D)
                        + TSDZConst.DEFAULT_ROTOR_OFFSET
                        - TSDZConst.DEFAULT_PHASE_ANGLE));
                if (v < 0) v += 256;
                phaseAngles[i] = (int)v;
            }
            System.arraycopy(TSDZConst.DEFAULT_HALL_OFFSET,0,hallTOffset,0,hallTOffset.length);
            refresValues();
            saveBT.setEnabled(true);
            showDialog("", getString(R.string.defaultLoaded), false);

        }
    }

    private void refresValues() {
        StringBuilder s = new StringBuilder();
        for (int i=0; i<6; i++) {
            if (i > 0)
                s.append(" ");
            s.append(String.format(Locale.getDefault(), "%d", phaseAngles[i]));
        }
        hallValuesTV.setText(s.toString());

        s = new StringBuilder();
        for (int i=0; i<6; i++) {
            if (i > 0)
                s.append(" ");
            s.append(String.format(Locale.getDefault(), "%d", hallTOffset[i]));
        }
        hallUpDnDiffTV.setText(s.toString());
    }

    private void updateUI() {
        if (calibrationRunning) {
            cancelBT.setEnabled(false);
            resetBT.setEnabled(false);
            saveBT.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);
            progressTV.setVisibility(View.VISIBLE);
            if (step == 0)
                progressTV.setText("0%");
        } else {
            cancelBT.setEnabled(true);
            resetBT.setEnabled(true);
            progressBar.setVisibility(View.INVISIBLE);
            progressTV.setVisibility(View.INVISIBLE);

        }
    }

    private void startCalib() {
        if (TSDZBTService.getBluetoothService() == null
                || TSDZBTService.getBluetoothService().getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error), false);
            return;
        }
        msgCounter = 0;
        for (RollingAverage ra: avg)
            ra.reset();
        TSDZBTService.getBluetoothService().writeCommand(new byte[] {CMD_MOTOR_TEST, TEST_START, dutyCycles[step], (byte)cfg.ui8_phase_angle_adj});
    }

    private void stopCalib() {
        calibrationRunning = false;
        updateUI();
        if (TSDZBTService.getBluetoothService() == null
                || TSDZBTService.getBluetoothService().getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED)
            showDialog(getString(R.string.error), getString(R.string.connection_error), false);
        else
            TSDZBTService.getBluetoothService().writeCommand(new byte[] {CMD_MOTOR_TEST,TEST_STOP});
    }

    private void showDialog (String title, String message, boolean exit) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (title != null)
            builder.setTitle(title);
        builder.setMessage(message);
        if (exit) {
            builder.setOnCancelListener((dialog) -> finish());
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> finish());
        } else
            builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }


    /*
    * F1=Hall1 Tfall, R1=Hall1 TRise, F2=Hall2 TFall etc..
    * The 6 linear equations calculated with the linear regression are dependant and have
    * infinite solutions.
    * Lets set F1=0 and then calculate all the other values 6 times for each equation
    * combination and then calculate the average for each value.
    * At the end add an offset to all values so that the total average value is TSDZConst.DEFAULT_AVG_OFFSET.
    */
    private void calculateOffsets() {
        final double[][] A1 = {
                { 0, 0,-1, 0, 1, 0}, // -F3 + R2 = linearRegression[0].intercept();
                { 1, 0, 0, 0,-1, 0}, //  F1 - R2 = linearRegression[1].intercept();
                {-1, 0, 0, 0, 0, 1}, // -F1 + R3 = linearRegression[2].intercept();
                { 0, 1, 0, 0, 0,-1}, //  F2 - R3 = linearRegression[3].intercept();
                { 0,-1, 0, 1, 0, 0}, // -F2 + R1 = linearRegression[4].intercept();
                { 0, 0, 1,-1, 0, 0}  //  F3 - R1 = linearRegression[5].intercept();
        };
        final double[] Ak = {1,0,0,0,0,0}; // F1 = 0

        double[][] A = new double[6][];
        double[] b = new double[6];

        double[] result = {0,0,0,0,0,0};

        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (j == i) {
                    A[j] = Ak;
                    b[j] = 0;
                } else {
                    A[j] = A1[j];
                    b[j] = linearRegression[j].intercept();
                }
            }
            Cramer cramer = new Cramer(A, b);
            double[] solution = cramer.solve();
            for (int k = 0; k < solution.length; k++) {
                result[k] += solution[k];
            }
        }

        for (int i=0; i<6; i++)
            result[i] = result[i] / 6;

        double avgOffset = 0;
        for (int i=0; i<6; i++)
            avgOffset += result[i];
        avgOffset /= 6;
        double diffOffset = TSDZConst.DEFAULT_AVG_OFFSET - avgOffset;
        for (int i=0; i<6; i++) {
            result[i] += diffOffset;
        }

        hallTOffset[0] = (int)Math.round(result[4]); // R2 - delay for Hall state 6
        hallTOffset[1] = (int)Math.round(result[0]); // F1 - delay for Hall state 2
        hallTOffset[2] = (int)Math.round(result[5]); // R3 - delay for Hall state 3
        hallTOffset[3] = (int)Math.round(result[1]); // F2 - delay for Hall state 1
        hallTOffset[4] = (int)Math.round(result[3]); // R1 - delay for Hall state 5
        hallTOffset[5] = (int)Math.round(result[2]); // F3 - delay for Hall state 4
    }

    private void calculateAngles() {
        double[] calcRefAngles = {0d,0d,0d,0d,0d,0d};
        double value = 0;
        double error = 0;
        // Calculate the Hall incremental angles setting Hall state 6 = 0 degrees.
        // Calculate the average offset error in regard to the reference positions.
        for (int i=1; i<6; i++) {
            value += linearRegression[i].slope()*256D;
            error += value - ((double)i*256D/6D);
            calcRefAngles[i] = value;
        }
        error /= 6D;

        // Calculate the Phase reference angles applying the error correction and the absolute
        // reference position correction.
        for (int i=0; i<6; i++) {
            // add 30 degree and subtract the calculated error
            calcRefAngles[i] = calcRefAngles[i] - error + 256D/12D;
            // Add rotor offset and subtract phase angle (90 degree)
            int v = (int)Math.round(calcRefAngles[i])
                    + TSDZConst.DEFAULT_ROTOR_OFFSET
                    - TSDZConst.DEFAULT_PHASE_ANGLE;
            if (v<0) v += 256;
            phaseAngles[i] = v;
        }
    }

    private void updateResult() {
        for (int i=0; i<6; i++) {
            linearRegression[i] = new LinearRegression(px[i], py[i]);
            angleTV[i].setText(String.format(Locale.getDefault(),"%.1f", linearRegression[i].slope()*360));
            offsetTV[i].setText(String.format(Locale.getDefault(),"%.1f", linearRegression[i].intercept()));
            errorTV[i].setText(String.format(Locale.getDefault(),"%.2f", linearRegression[i].R2()));
        }

        if (valuesOK()) {
            showDialog("", getString(R.string.calibrationDataValid), false);
            saveBT.setEnabled(true);
        } else {
            showDialog(getString(R.string.error), getString(R.string.calibrationDataNotValid), false);
            saveBT.setEnabled(false);
        }

        calculateAngles();
        calculateOffsets();

        Log.d(TAG,"ui8_hall_ref_angles:" + (phaseAngles[0] & 0xff)
                + "," + (phaseAngles[1] & 0xff)
                + "," + (phaseAngles[2] & 0xff)
                + "," + (phaseAngles[3] & 0xff)
                + "," + (phaseAngles[4] & 0xff)
                + "," + (phaseAngles[5] & 0xff)
        );
        Log.d(TAG, "hallTOffset:" + (hallTOffset[0] & 0xff)
                + "," + (hallTOffset[1] & 0xff)
                + "," + (hallTOffset[2] & 0xff)
                + "," + (hallTOffset[3] & 0xff)
                + "," + (hallTOffset[4] & 0xff)
                + "," + (hallTOffset[5] & 0xff)
        );
        refresValues();
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

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onMessageEvent(TSDZBTService.BTServiceEvent event) {
        switch (event.eventType) {
            case CONNECTION_LOST:
            case SERVICE_STOPPED:
            case CONNECTION_FAILURE:
                calibrationRunning = false;
                updateUI();
                Toast.makeText(getApplicationContext(), "BT Connection Lost.", Toast.LENGTH_LONG).show();
                break;
            case TSDZ_CFG_READ:
                if (!cfg.setData(event.data)) {
                    showDialog(getString(R.string.error), getString(R.string.calibrationSaveError), false);
                    break;
                }

                System.arraycopy(cfg.ui8_hall_ref_angles, 0, phaseAngles, 0, 6);
                System.arraycopy(cfg.ui8_hall_counter_offset, 0, hallTOffset, 0, 6);
                refresValues();
                break;
            case TSDZ_CFG_WRITE_OK:
                showDialog("", getString(R.string.calibrationApplied), true);
                break;
            case TSDZ_CFG_WRITE_KO:
                showDialog(getString(R.string.error), getString(R.string.calibrationSaveError), false);
                break;
            case TSDZ_COMMAND:
                byte[] data = event.data;
                //Log.d(TAG,"TSDZ_COMMAND_BROADCAST: " + Utils.bytesToHex(data));
                if (data[0] == CMD_MOTOR_TEST) {
                    switch(data[1]) {
                        case TEST_START:
                            if (data[2] != (byte)0x0)
                                showDialog(getString(R.string.error), getString(R.string.motorTestStartError), false);
                            else
                                calibrationRunning = true;
                            updateUI();
                            break;
                        case TEST_STOP:
                            if (data[2] != (byte)0x0) {
                                showDialog(getString(R.string.error), getString(R.string.motorTestStopError), false);
                            }
                            break;
                        case (byte)0xff:
                            showDialog(getString(R.string.error), getString(R.string.commandError), false);
                            break;
                    }
                } else if (data[0] == CMD_HALL_DATA) {
                    if (!calibrationRunning) {
                        return;
                    }
                    msgCounter++;
                    long progress = (100 * ((long) step * (AVG_SIZE + SETUP_STEPS) + msgCounter)) / (4 * (AVG_SIZE + SETUP_STEPS));
                    progressTV.setText(String.format(Locale.getDefault(), "%d%%", progress));
                    if (msgCounter <= SETUP_STEPS) {
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
                }
                break;
        }
    }
}