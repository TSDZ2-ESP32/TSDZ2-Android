package spider65.ebike.tsdz2_esp32.activities;

import android.os.Bundle;

import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Config;


public class MotorSetupActivity extends AppCompatActivity {

    private static final int MAX_ANGLE = 10;
    private static final int MAX_OFFSET = 10;

    private TextView angleValTV, offsetValTV;
    private final TSDZ_Config cfg = new TSDZ_Config();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motor_setup);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        angleValTV = findViewById(R.id.angleValTV);
        offsetValTV = findViewById(R.id.offsetValTV);

        if (TSDZBTService.getBluetoothService() == null
                || TSDZBTService.getBluetoothService().getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED)
            showDialog("", getString(R.string.connection_error), true);
        else
            TSDZBTService.getBluetoothService().readCfg();
        showDialog(getString(R.string.warning), getString(R.string.warning_cfg), false);
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

            int offsetAdj = Integer.parseInt(offsetValTV.getText().toString());
            if (offsetAdj < 0) offsetAdj = (256 + offsetAdj);
            if (offsetAdj != cfg.ui8_hall_offset_adj) {
                cfg.ui8_hall_offset_adj = offsetAdj;
                updated = true;
            }

            if (updated)
                TSDZBTService.getBluetoothService().writeCfg(cfg);
            else
                showDialog("", getString(R.string.valuesNotChanged), false);
        } else if (view.getId() == R.id.angleAddBT) {
            val = Integer.parseInt(angleValTV.getText().toString());
            if (val < MAX_ANGLE)
                angleValTV.setText(String.valueOf(val + 1));
        } else if (view.getId() == R.id.angleSubBT) {
            val = Integer.parseInt(angleValTV.getText().toString());
            if (val > -MAX_ANGLE)
                angleValTV.setText(String.valueOf(val - 1));
        } else if (view.getId() == R.id.offsetAddBT) {
            val = Integer.parseInt(offsetValTV.getText().toString());
            if (val < MAX_OFFSET)
                offsetValTV.setText(String.valueOf(val + 1));
        }  else if (view.getId() == R.id.offsetSubBT) {
            val = Integer.parseInt(offsetValTV.getText().toString());
            if (val > -MAX_OFFSET)
                offsetValTV.setText(String.valueOf(val - 1));
        } else if (view.getId() == R.id.resetBT) {
            angleValTV.setText("0");
            offsetValTV.setText("0");
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

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onMessageEvent(TSDZBTService.BTServiceEvent event) {

        switch (event.eventType) {
            case TSDZ_CFG_READ:
                if (!cfg.setData(event.data)) {
                    showDialog(getString(R.string.error), getString(R.string.cfgReadError), false);
                    break;
                }
                int v = cfg.ui8_phase_angle_adj;
                if (v >= 128) v-=256;
                angleValTV.setText(String.valueOf(v));
                v = cfg.ui8_hall_offset_adj;
                if (v >= 128) v-=256;
                offsetValTV.setText(String.valueOf(v));
                findViewById(R.id.saveButton).setEnabled(true);
                break;
            case TSDZ_CFG_WRITE_OK:
                showDialog("", getString(R.string.cfgSaved), true);
                break;
            case TSDZ_CFG_WRITE_KO:
                showDialog(getString(R.string.error), getString(R.string.cfgSaveError), false);
                break;
        }
    }
}