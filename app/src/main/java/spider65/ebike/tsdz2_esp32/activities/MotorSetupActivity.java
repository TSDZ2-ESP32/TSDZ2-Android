package spider65.ebike.tsdz2_esp32.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import android.view.View;
import android.widget.TextView;

import java.util.Locale;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Config;


public class MotorSetupActivity extends AppCompatActivity {

    private static final int MAX_ANGLE = 10;
    private static final int MIN_OFFSET = 7;
    private static final int MAX_OFFSET = 40;
    private static final int MIN_DIFF = 0;
    private static final int MAX_DIFF = 30;

    private static final int DEFAULT_PHASE_SHIFT = 0;
    private static final int DEFAULT_DN_HALL_OFFSET = 8;
    private static final int DEFAULT_UP_DN_HALL_DIFF = 20;


    private final IntentFilter mIntentFilter = new IntentFilter();
    private TextView angleValTV, offsetValTV, diffValTV;
    private final TSDZ_Config cfg = new TSDZ_Config();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motor_setup);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        offsetValTV = findViewById(R.id.offsetValTV);
        angleValTV = findViewById(R.id.angleValTV);
        diffValTV = findViewById(R.id.diffValTV);

        mIntentFilter.addAction(TSDZBTService.TSDZ_CFG_READ_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.TSDZ_CFG_WRITE_BROADCAST);

        if (TSDZBTService.getBluetoothService() == null
                || TSDZBTService.getBluetoothService().getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED)
            showDialog("", getString(R.string.connection_error), true);
        else
            TSDZBTService.getBluetoothService().readCfg();
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
        int val;
        if (view.getId() == R.id.exitButton)
            finish();
        else if (view.getId() == R.id.saveButton) {
            boolean updated = false;

            int angleAdj = Integer.parseInt(angleValTV.getText().toString());
            if (angleAdj < 0) angleAdj = (256 + angleAdj);
            if (angleAdj != cfg.ui8_phase_angle_adj) {
                cfg.ui8_phase_angle_adj = angleAdj;
                updated = true;
            }

            int hallOffset = Integer.parseInt(offsetValTV.getText().toString());
            if (hallOffset != cfg.ui8_hall_counter_offset_down) {
                cfg.ui8_hall_counter_offset_down = hallOffset;
                updated = true;
            }

            int hallDiff = Integer.parseInt(diffValTV.getText().toString());
            if (hallDiff != (cfg.ui8_hall_counter_offset_up - cfg.ui8_hall_counter_offset_down)) {
                cfg.ui8_hall_counter_offset_up = hallOffset + hallDiff;
                updated = true;
            }

            if (updated)
                TSDZBTService.getBluetoothService().writeCfg(cfg);
            else
                showDialog("", getString(R.string.valuesNotChanged), false);
        } else if (view.getId() == R.id.angleAddBT) {
            val = Integer.parseInt(angleValTV.getText().toString());
            if (val < MAX_ANGLE)
                angleValTV.setText(String.valueOf(++val));
        } else if (view.getId() == R.id.angleSubBT) {
            val = Integer.parseInt(angleValTV.getText().toString());
            if (val > -MAX_ANGLE)
                angleValTV.setText(String.valueOf(--val));
        } else if (view.getId() == R.id.offsetAddBT) {
            val = Integer.parseInt(offsetValTV.getText().toString());
            if (val < MAX_OFFSET)
                offsetValTV.setText(String.valueOf(++val));
        }  else if (view.getId() == R.id.offsetSubBT) {
            val = Integer.parseInt(offsetValTV.getText().toString());
            if (val > MIN_OFFSET)
                offsetValTV.setText(String.valueOf(--val));
        } else if (view.getId() == R.id.diffAddBT) {
            val = Integer.parseInt(diffValTV.getText().toString());
            if (val < MAX_DIFF)
                diffValTV.setText(String.valueOf(++val));
        }  else if (view.getId() == R.id.diffSubBT) {
            val = Integer.parseInt(diffValTV.getText().toString());
            if (val > MIN_DIFF)
                diffValTV.setText(String.valueOf(--val));
        } else if (view.getId() == R.id.resetButton) {
            angleValTV.setText(String.format(Locale.getDefault(),"%d", DEFAULT_PHASE_SHIFT));
            offsetValTV.setText(String.format(Locale.getDefault(), "%d", DEFAULT_DN_HALL_OFFSET));
            diffValTV.setText(String.format(Locale.getDefault(), "%d", DEFAULT_UP_DN_HALL_DIFF));
            showDialog("", getString(R.string.defaultLoaded), false);
        }
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

    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null)
                return;
            switch (intent.getAction()) {
                case TSDZBTService.TSDZ_CFG_READ_BROADCAST:
                    if (!cfg.setData(intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA))) {
                        showDialog(getString(R.string.error), getString(R.string.cfgReadError), false);
                        break;
                    }
                    int v = cfg.ui8_phase_angle_adj;
                    if (v >= 128) v-=256;
                    angleValTV.setText(String.format(Locale.getDefault(),"%d", v));
                    offsetValTV.setText(String.format(Locale.getDefault(), "%d", cfg.ui8_hall_counter_offset_down));
                    diffValTV.setText(String.format(Locale.getDefault(), "%d",
                            cfg.ui8_hall_counter_offset_up - cfg.ui8_hall_counter_offset_down));
                    findViewById(R.id.saveButton).setEnabled(true);
                    break;
                case TSDZBTService.TSDZ_CFG_WRITE_BROADCAST:
                    if (intent.getBooleanExtra(TSDZBTService.VALUE_EXTRA,false))
                        showDialog("", getString(R.string.cfgSaved), true);
                    else
                        showDialog(getString(R.string.error), getString(R.string.cfgSaveError), false);
                    break;
            }
        }
    };
}