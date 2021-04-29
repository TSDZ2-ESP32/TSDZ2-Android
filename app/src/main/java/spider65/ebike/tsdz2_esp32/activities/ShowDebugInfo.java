package spider65.ebike.tsdz2_esp32.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Arrays;
import java.util.Locale;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Debug;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Status;


public class ShowDebugInfo extends AppCompatActivity {

    private IntentFilter mIntentFilter = new IntentFilter();

    private LinearLayout mainTimeLL, pwmTimeLL, hallErrLL;
    private TextView mainLoopTV, pwmTV, hallErrTV;
    private TextView rxcTV, rxlTV, ebikeTimeTV, motorTimeTV, pwmDownTV, pwmUpTV, hallStateErrTV, hallSeqErrTV;
    private View div1, div2, div3;
    private final TSDZ_Status status = new TSDZ_Status();
    private final TSDZ_Debug debug = new TSDZ_Debug();

    private boolean pwmDebug = false;
    private boolean mainDebug = false;
    private boolean hallDebug = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_debug);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        rxcTV  = findViewById(R.id.rxcErrorsTV);
        rxlTV  = findViewById(R.id.rxlErrorsTV);

        mainTimeLL = findViewById(R.id.mainTimeLinearLayout);
        mainLoopTV = findViewById(R.id.mainTimeTV);
        ebikeTimeTV = findViewById(R.id.mainLoopTimeTV);
        motorTimeTV = findViewById(R.id.motorLoopTimeTV);
        div1 = findViewById(R.id.dbg_infoDV1);
        mainTimeLL.setVisibility(View.GONE);
        mainLoopTV.setVisibility(View.GONE);
        div1.setVisibility(View.GONE);

        pwmTimeLL = findViewById(R.id.pwmTimeLinearLayout);
        pwmTV = findViewById(R.id.pwmTimeTV);
        pwmDownTV = findViewById(R.id.pwmDownTimeTV);
        pwmUpTV = findViewById(R.id.pwmUpTimeTV);
        div2 = findViewById(R.id.dbg_infoDV2);
        pwmTimeLL.setVisibility(View.GONE);
        pwmTV.setVisibility(View.GONE);
        div2.setVisibility(View.GONE);

        hallErrLL = findViewById(R.id.hallErrLinearLayout);
        hallErrTV = findViewById(R.id.hallErrTV);
        hallStateErrTV = findViewById(R.id.hallStateErrTV);
        hallSeqErrTV = findViewById(R.id.hallSeqErrTV);
        div3 = findViewById(R.id.dbg_infoDV3);
        hallErrLL.setVisibility(View.GONE);
        hallErrTV.setVisibility(View.GONE);
        div3.setVisibility(View.GONE);

        mIntentFilter.addAction(TSDZBTService.TSDZ_STATUS_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.TSDZ_DEBUG_BROADCAST);

        if (TSDZBTService.getBluetoothService() == null || TSDZBTService.getBluetoothService().getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.connection_error);
            builder.setOnCancelListener((dialog) -> finish());
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> finish());
            builder.show();
        }
    }

    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    }

    @Override
    public void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, mIntentFilter);
    }

    public void onButtonClick(View view) {
        if (view.getId() == R.id.exitButton)
            finish();
    }

    private void refreshDebug() {
        if (!mainDebug && (debug.debugFlags & 0x40) != 0) {
            mainDebug = true;
            mainTimeLL.setVisibility(View.VISIBLE);
            mainLoopTV.setVisibility(View.VISIBLE);
            div1.setVisibility(View.VISIBLE);
        }
        if (!pwmDebug && (debug.debugFlags & 0x80) != 0) {
            pwmDebug = true;
            pwmTimeLL.setVisibility(View.VISIBLE);
            pwmTV.setVisibility(View.VISIBLE);
            div2.setVisibility(View.VISIBLE);
        }
        if (!hallDebug && (debug.debugFlags & 0x20) != 0) {
            hallDebug = true;
            hallErrLL.setVisibility(View.VISIBLE);
            hallErrTV.setVisibility(View.VISIBLE);
            div3.setVisibility(View.VISIBLE);
        }

        if (pwmDebug) {
            int upIRQ = ((debug.debug4 & 255) << 8) + (debug.debug3 & 255);
            int dnIRQ = ((debug.debug6 & 255) << 8) + (debug.debug5 & 255);
            pwmUpTV.setText(String.format(Locale.getDefault(),"%d", upIRQ));
            pwmDownTV.setText(String.format(Locale.getDefault(),"%d", dnIRQ));
        }

        if (mainDebug) {
            ebikeTimeTV.setText(String.format(Locale.getDefault(),"%d", debug.debug2));
            motorTimeTV.setText(String.format(Locale.getDefault(),"%d", debug.debug1));
        }

        if (hallDebug) {
            hallStateErrTV.setText(String.format(Locale.getDefault(),"%d", debug.debug5));
            hallSeqErrTV.setText(String.format(Locale.getDefault(),"%d", debug.debug6));
        }
    }

    private void refreshStatus() {
        rxcTV.setText(String.format(Locale.getDefault(),"%d", status.rxcErrors));
        rxlTV.setText(String.format(Locale.getDefault(),"%d", status.rxlErrors));
    }


    private byte[] lastStatusData, lastDebugData;
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) return;
            byte[] data;
            switch (intent.getAction()) {
                case TSDZBTService.TSDZ_STATUS_BROADCAST:
                    data = intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA);
                    if (!Arrays.equals(lastStatusData, data)) {
                        if (status.setData(data)) {
                            lastStatusData = data;
                            runOnUiThread(() -> refreshStatus());
                        }
                    }
                    break;
                case TSDZBTService.TSDZ_DEBUG_BROADCAST:
                    data = intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA);
                    if (!Arrays.equals(lastDebugData, data)) {
                        // refresh Debug Fragment if visibile
                        if (debug.setData(data)) {
                            lastDebugData = data;
                            runOnUiThread(() -> refreshDebug());
                        }
                        break;
                    }
            }
        }
    };
}
