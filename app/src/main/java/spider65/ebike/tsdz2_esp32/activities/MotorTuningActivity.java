package spider65.ebike.tsdz2_esp32.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.FileOutputStream;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;


// Run motor (without load) with a fixed duty cycle and move rotor offset angle in order to find the best one (max ERPS)
// The result should be a table with ERPS and corresponding best offset angle
public class MotorTuningActivity extends AppCompatActivity {
    private static final byte CMD_HALL_DATA = 0x07;
    private static final byte CMD_MOTOR_TEST = 0x0B;
    private static final byte TEST_STOP = 0;
    private static final byte TEST_START = 1;

    private static final int MAX_DUTY_CYCLE = 250;
    private static final int MAX_ANGLE = 5;

    private static final int CREATE_FILE_REQUEST_CODE = 40;

    private enum TestStatus {
        stopped,
        starting,
        running
    }

    private IntentFilter mIntentFilter = new IntentFilter();
    TextView erpsTV, angleValTV, resultsTV;
    CheckBox autoTuneCB;
    EditText dcValET, dcStepET, dcMaxET, angleMaxET, stepSamplesET;
    Button startBT, stopBT, saveBT, dcAddBT, dcSubBT, angleAddBT, angleSubBT;
    TSDZBTService service;

    boolean autoTune = false;
    int currentDC;
    byte currentAngle;
    int maxDC, stepDC, maxAngle, stepSamples;
    TestStatus status = TestStatus.stopped;
    RollingAverage avg = new RollingAverage(256);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motor_tuning);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        erpsTV = findViewById(R.id.erpsTV);
        dcValET = findViewById(R.id.dcValET);
        angleValTV = findViewById(R.id.angleValTV);
        autoTuneCB = findViewById(R.id.autoTuneCB);
        dcStepET = findViewById(R.id.dcStepET);
        dcMaxET = findViewById(R.id.dcMaxET);
        angleMaxET = findViewById(R.id.angleMaxET);
        stepSamplesET = findViewById(R.id.stepSamplesET);
        dcAddBT = findViewById(R.id.dcAddBT);
        dcSubBT = findViewById(R.id.dcSubBT);
        angleAddBT = findViewById(R.id.angleAddBT);
        angleSubBT = findViewById(R.id.angleSubBT);
        startBT = findViewById(R.id.startButton);
        stopBT = findViewById(R.id.stopButton);
        saveBT = findViewById(R.id.saveButton);
        resultsTV = findViewById(R.id.resultsTV);
        resultsTV.setMovementMethod(new ScrollingMovementMethod());

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
        if ((status != TestStatus.stopped) && (service != null)) {
            service.writeCommand(new byte[]{CMD_MOTOR_TEST, TEST_STOP});
            status = TestStatus.stopped;
        }
        super.onStop();
    }

    public void onButtonClick(View view) {
        int val;
        switch (view.getId()) {
            case R.id.startButton:
                startTest();
                break;
            case R.id.stopButton:
                String s = "DutyCycle: " + currentDC
                        + " Angle: " + currentAngle
                        + " ERPS: " + String.format(Locale.getDefault(), "%.2f", 250000D/avg.getAverage())
                        + "\n";
                resultsTV.append(s);
                stopTest();
                break;
            case R.id.exitButton:
                if (stopTest())
                    finish();
                break;
            case R.id.saveButton:
                saveFile();
                break;
            case R.id.dcAddBT:
                val = Integer.parseInt(dcValET.getText().toString());
                if (val <= (MAX_DUTY_CYCLE-5)) {
                    val += 1;
                    dcValET.setText(String.valueOf(val));
                    if (status != TestStatus.stopped)
                        startTest();
                }
                break;
            case R.id.dcSubBT:
                val = Integer.parseInt(dcValET.getText().toString());
                if (val >= 10) {
                    val -= 1;
                    dcValET.setText(String.valueOf(val));
                    if (status != TestStatus.stopped)
                        startTest();
                }
                break;
            case R.id.angleAddBT:
                val = Integer.parseInt(angleValTV.getText().toString());
                if (val < MAX_ANGLE) {
                    angleValTV.setText(String.valueOf(++val));
                    if (status != TestStatus.stopped)
                        startTest();
                }
                break;
            case R.id.angleSubBT:
                val = Integer.parseInt(angleValTV.getText().toString());
                if (val > -MAX_ANGLE) {
                    angleValTV.setText(String.valueOf(--val));
                    if (status != TestStatus.stopped)
                        startTest();
                }
                break;
            case R.id.autoTuneCB:
                autoTune = ((CheckBox) view).isChecked();
                if (autoTune) {
                    dcStepET.setEnabled(true);
                    dcMaxET.setEnabled(true);
                    angleMaxET.setEnabled(true);
                    stepSamplesET.setEnabled(true);
                } else {
                    dcStepET.setEnabled(false);
                    dcMaxET.setEnabled(false);
                    angleMaxET.setEnabled(false);
                    stepSamplesET.setEnabled(false);
                }
                break;
        }
    }

    private void startTest() {
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error));
            return;
        }
        currentDC  = Integer.parseInt(dcValET.getText().toString());
        if (autoTune) {
            stepDC = Integer.parseInt(dcStepET.getText().toString());
            if (stepDC > 128) {
                showDialog(getString(R.string.warning), "D.C. Step should be less than 129");
                return;
            }
            maxDC = Integer.parseInt(dcMaxET.getText().toString());
            if (maxDC > MAX_DUTY_CYCLE) {
                showDialog(getString(R.string.warning), "D.C. Max should be less than " + MAX_DUTY_CYCLE+1);
                return;
            }
            maxAngle = Integer.parseInt(angleMaxET.getText().toString());
            if (maxAngle > MAX_ANGLE) {
                showDialog(getString(R.string.warning), "Angle (+/-) should be less than " + MAX_ANGLE+1);
                return;
            }
            stepSamples = Integer.parseInt(stepSamplesET.getText().toString());
            if (stepSamples > 1024){
                showDialog(getString(R.string.warning), "Step Samples should be less than 1025");
                return;
            }
            currentAngle = (byte)(-maxAngle & 0xff);
            avg = new RollingAverage(stepSamples);
        } else {
            currentAngle = (byte)(Integer.parseInt(angleValTV.getText().toString()) & 0xff);
            avg = new RollingAverage(20);
        }
        service.writeCommand(new byte[] {CMD_MOTOR_TEST,
                TEST_START,
                (byte)(currentDC & 0xff),
                currentAngle});
    }

    private boolean stopTest() {
        resetTimer();
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error));
            return false;
        }
        service.writeCommand(new byte[] {CMD_MOTOR_TEST, TEST_STOP});
        return true;
    }

    private void stepDone() {
        status = TestStatus.starting;
        String s = "DutyCycle: " + currentDC
                + " Angle: " + currentAngle
                + " ERPS: " + String.format(Locale.getDefault(), "%.2f", 250000D/avg.getAverage())
                + "\n";
        resultsTV.append(s);

        currentAngle++;
        if (currentAngle > maxAngle) {
            currentDC += stepDC;
            if (currentDC > maxDC) {
                stopTest();
                return;
            }
            currentAngle = (byte)(-maxAngle & 0xff);
        }
        avg = new RollingAverage(stepSamples);
        service.writeCommand(new byte[] {CMD_MOTOR_TEST,
                TEST_START,
                (byte)(currentDC & 0xff),
                currentAngle});
    }

    private void saveFile()
    {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "motorTest.txt");
        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        Uri currentUri;

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == CREATE_FILE_REQUEST_CODE) {
                if (resultData != null) {
                    currentUri =  resultData.getData();
                    writeFileContent(currentUri);
                }
            }
        }
    }

    private void writeFileContent(Uri uri)
    {
        ParcelFileDescriptor pdf = null;
        FileOutputStream fileOutputStream = null;
        try{
            pdf = this.getContentResolver().openFileDescriptor(uri, "w");
            if (pdf == null) {
                Toast.makeText(getApplicationContext(), "Unable to save the data", Toast.LENGTH_LONG).show();
                return;
            }
            fileOutputStream = new FileOutputStream(pdf.getFileDescriptor());
            String textContent = resultsTV.getText().toString();
            fileOutputStream.write(textContent.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fileOutputStream != null)
                    fileOutputStream.close();
                if (pdf != null)
                    pdf.close();
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
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
            if (TSDZBTService.TSDZ_COMMAND_BROADCAST.equals(intent.getAction())) {
                if (data[0] == CMD_MOTOR_TEST) {
                    switch(data[1]) {
                        case TEST_START:
                            if (data[2] != (byte)0x0) {
                                showDialog(getString(R.string.error), getString(R.string.motorTestStartError));
                            } else {
                                // start calibration procedure 5sec after motor startup
                                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                status = TestStatus.starting;
                                scheduleTimer();
                                startBT.setEnabled(false);
                                autoTuneCB.setEnabled(false);
                                saveBT.setEnabled(false);
                                if (autoTune) {
                                    dcAddBT.setEnabled(false);
                                    dcSubBT.setEnabled(false);
                                    angleAddBT.setEnabled(false);
                                    angleSubBT.setEnabled(false);
                                }
                                stopBT.setEnabled(true);
                            }
                            break;
                        case TEST_STOP:
                            if (data[2] != (byte)0x0) {
                                showDialog(getString(R.string.error), getString(R.string.motorTestStopError));
                            } else {
                                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                status = TestStatus.stopped;
                                resetTimer();
                                startBT.setEnabled(true);
                                saveBT.setEnabled(true);
                                autoTuneCB.setEnabled(true);
                                dcAddBT.setEnabled(true);
                                dcSubBT.setEnabled(true);
                                angleAddBT.setEnabled(true);
                                angleSubBT.setEnabled(true);
                                stopBT.setEnabled(false);
                            }
                            break;
                        case (byte)0xff:
                            showDialog(getString(R.string.error), getString(R.string.commandError));
                            break;
                    }
                } else if (data[0] == CMD_HALL_DATA) {
                    if (status == TestStatus.running) {
                        // Hall counter for full ERPS revolution (sum of the 6 hall transition counters)
                        int value = ((data[2] & 255) << 8) + (data[1] & 255)
                                + (((data[4] & 255) << 8) + (data[3] & 255))
                                + (((data[6] & 255) << 8) + (data[5] & 255))
                                + (((data[8] & 255) << 8) + (data[7] & 255))
                                + (((data[10] & 255) << 8) + (data[9] & 255))
                                + (((data[12] & 255) << 8) + (data[11] & 255));
                        avg.add(value);
                        erpsTV.setText(String.format(Locale.getDefault(), "%.2f", 250000D/avg.getAverage()));
                        if (autoTune && (avg.getIndex() == 0))
                            stepDone();
                    }
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

        public double getAverage() {
            return rollover ?(double)total/(double)size:(double)total/(double)index;
        }

        public int getIndex() {
            return index;
        }
    }

    private Timer timer;
    private class TimerExpired extends TimerTask {
        @Override
        public void run(){
            status = TestStatus.running;
        }
    }

    private void scheduleTimer() {
        resetTimer();
        TimerExpired timerExpired = new TimerExpired();
        timer = new Timer();
        timer.schedule(timerExpired, 3500);
    }

    private void resetTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}