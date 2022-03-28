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

import spider65.ebike.tsdz2_esp32.MainActivity;
import spider65.ebike.tsdz2_esp32.MyApp;
import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.utils.RollingAverage;

import static spider65.ebike.tsdz2_esp32.TSDZConst.CMD_HALL_DATA;
import static spider65.ebike.tsdz2_esp32.TSDZConst.CMD_MOTOR_TEST;
import static spider65.ebike.tsdz2_esp32.TSDZConst.TEST_START;
import static spider65.ebike.tsdz2_esp32.TSDZConst.TEST_STOP;


// Run motor (without load) with a fixed duty cycle and move rotor offset angle in order to find the best one (max ERPS)
// The result should be a table with ERPS and corresponding best offset angle
public class MotorTestActivity extends AppCompatActivity {

    private static final int MAX_DUTY_CYCLE = 254;
    private static final int MAX_ANGLE = 10;

    private static final int CREATE_FILE_REQUEST_CODE = 40;

    private enum TestStatus {
        stopped,
        starting,
        running
    }

    private final IntentFilter mIntentFilter = new IntentFilter();
    TextView erpsTV, angleValTV, resultsTV;
    EditText dcValET;
    Button startBT, stopBT, exitBT, dcAddBT, dcSubBT, angleAddBT, angleSubBT;
    TSDZBTService service;

    int currentDC;
    byte currentAngle;
    TestStatus status = TestStatus.stopped;
    RollingAverage avg = new RollingAverage(256);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motor_test);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        boolean screenOn = MyApp.getPreferences().getBoolean(MainActivity.KEY_SCREEN_ON, false);
        if (screenOn)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        erpsTV = findViewById(R.id.erpsTV);
        dcValET = findViewById(R.id.dcValET);
        angleValTV = findViewById(R.id.angleValTV);
        dcAddBT = findViewById(R.id.dcAddBT);
        dcSubBT = findViewById(R.id.dcSubBT);
        angleAddBT = findViewById(R.id.angleAddBT);
        angleSubBT = findViewById(R.id.angleSubBT);
        startBT = findViewById(R.id.startButton);
        stopBT = findViewById(R.id.stopButton);
        exitBT = findViewById(R.id.exitButton);
        resultsTV = findViewById(R.id.resultsTV);
        resultsTV.setMovementMethod(new ScrollingMovementMethod());

        mIntentFilter.addAction(TSDZBTService.TSDZ_COMMAND_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.CONNECTION_LOST_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.SERVICE_STOPPED_BROADCAST);

        service = TSDZBTService.getBluetoothService();
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.connection_error);
            builder.setOnCancelListener((dialog) -> finish());
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> finish());
            builder.show();
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onBackPressed() {
        if (status != TestStatus.stopped) {
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
        resetTimer();
        if ((status != TestStatus.stopped) && (service != null)) {
            service.writeCommand(new byte[]{CMD_MOTOR_TEST, TEST_STOP});
            status = TestStatus.stopped;
        }
        super.onStop();
    }

    public void onButtonClick(View view) {
        int val;
        if (view.getId() == R.id.startButton) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.warning);
            builder.setMessage(R.string.warningMotorStart);
            builder.setPositiveButton(R.string.ok, (dialog, which) -> startTest());
            builder.setNegativeButton(R.string.cancel, null);
            builder.show();
        } else if (view.getId() == R.id.stopButton) {
            String s = "DutyCycle: " + currentDC
                    + " Angle: " + currentAngle
                    + " ERPS: " + String.format(Locale.getDefault(), "%.2f", 250000D/avg.getAverage())
                    + "\n";
            resultsTV.append(s);
            stopTest();
        } else if (view.getId() == R.id.exitButton) {
            if (status != TestStatus.stopped && service != null)
                service.writeCommand(new byte[] {CMD_MOTOR_TEST,TEST_STOP});
            status = TestStatus.stopped;
            finish();
        } else if (view.getId() == R.id.saveButton) {
            saveFile();
        } else if (view.getId() == R.id.dcAddBT) {
            val = Integer.parseInt(dcValET.getText().toString());
            if (val < MAX_DUTY_CYCLE) {
                val += 1;
                dcValET.setText(String.valueOf(val));
                if (status != TestStatus.stopped)
                    startTest();
            }
        } else if (view.getId() == R.id.dcSubBT) {
            val = Integer.parseInt(dcValET.getText().toString());
            if (val > 5) {
                val -= 1;
                dcValET.setText(String.valueOf(val));
                if (status != TestStatus.stopped)
                    startTest();
            }
        } else if (view.getId() == R.id.angleAddBT) {
            val = Integer.parseInt(angleValTV.getText().toString());
            if (val < MAX_ANGLE) {
                angleValTV.setText(String.valueOf(++val));
                if (status != TestStatus.stopped)
                    startTest();
            }
        } else if (view.getId() == R.id.angleSubBT) {
            val = Integer.parseInt(angleValTV.getText().toString());
            if (val > -MAX_ANGLE) {
                angleValTV.setText(String.valueOf(--val));
                if (status != TestStatus.stopped)
                    startTest();
            }
        }
    }

    private void startTest() {
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error));
            return;
        }
        currentDC  = Integer.parseInt(dcValET.getText().toString());
        currentAngle = (byte)(Integer.parseInt(angleValTV.getText().toString()) & 0xff);
        avg = new RollingAverage(20);
        service.writeCommand(new byte[] {CMD_MOTOR_TEST,
                TEST_START,
                (byte)(currentDC & 0xff),
                currentAngle});
    }

    private void stopTest() {
        resetTimer();
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error));
            return;
        }
        service.writeCommand(new byte[] {CMD_MOTOR_TEST, TEST_STOP});
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

    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TSDZBTService.CONNECTION_LOST_BROADCAST.equals(intent.getAction())
                    || TSDZBTService.SERVICE_STOPPED_BROADCAST.equals(intent.getAction())
                    || TSDZBTService.CONNECTION_FAILURE_BROADCAST.equals(intent.getAction())) {
                service = TSDZBTService.getBluetoothService();
                status = TestStatus.stopped;
                resetTimer();
                startBT.setEnabled(true);
                exitBT.setEnabled(true);
                dcAddBT.setEnabled(true);
                dcSubBT.setEnabled(true);
                angleAddBT.setEnabled(true);
                angleSubBT.setEnabled(true);
                stopBT.setEnabled(false);
                Toast.makeText(getApplicationContext(), "BT Connection Lost.", Toast.LENGTH_LONG).show();
            } else if (TSDZBTService.TSDZ_COMMAND_BROADCAST.equals(intent.getAction())) {
                byte[] data = intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA);
                if (data[0] == CMD_MOTOR_TEST) {
                    switch(data[1]) {
                        case TEST_START:
                            if (data[2] != (byte)0x0) {
                                showDialog(getString(R.string.error), getString(R.string.motorTestStartError));
                            } else {
                                // start calibration procedure 5sec after motor startup
                                status = TestStatus.starting;
                                scheduleTimer();
                                startBT.setEnabled(false);
                                exitBT.setEnabled(false);
                                stopBT.setEnabled(true);
                            }
                            break;
                        case TEST_STOP:
                            if (data[2] != (byte)0x0) {
                                showDialog(getString(R.string.error), getString(R.string.motorTestStopError));
                            } else {
                                status = TestStatus.stopped;
                                resetTimer();
                                startBT.setEnabled(true);
                                exitBT.setEnabled(true);
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
                    }
                }
            }
        }
    };

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
        timer.schedule(timerExpired, 1000);
    }

    private void resetTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}