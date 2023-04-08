package spider65.ebike.tsdz2_esp32.activities;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.TSDZConst;
import spider65.ebike.tsdz2_esp32.utils.Utils;


public class ESP32ConfigActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "ESP32ConfigActivity";

    private SwitchCompat lockBikeSwitch;
    private Spinner  btDelaySpinner;
    private EditText ds18b20PinET;
    private Spinner  dbgLevelSpinner;
    private Button   okButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp32_config);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        lockBikeSwitch = findViewById(R.id.lockBikeSW);
        btDelaySpinner = findViewById(R.id.btDelaySP);
        ds18b20PinET = findViewById(R.id.ds18b20ET);
        dbgLevelSpinner = findViewById(R.id.logLevelSP);
        okButton = findViewById(R.id.okButton);
        Button cancelButton = findViewById(R.id.exitButton);
        cancelButton.setOnClickListener(this);
        okButton.setOnClickListener(this);

        TSDZBTService service = TSDZBTService.getBluetoothService();
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error), true);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        // get current ESP32 Configuration
        TSDZBTService.getBluetoothService().writeCommand(new byte[] {TSDZConst.CMD_ESP32_CONFIG, TSDZConst.CONFIG_GET});
    }

    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.okButton:
                saveCfg();
                break;
            case R.id.exitButton:
                finish();
                break;
        }
    }

    private void saveCfg() {
        byte[] msg = new byte[] {TSDZConst.CMD_ESP32_CONFIG, TSDZConst.CONFIG_SET,0,0,0,0};

        int idx = btDelaySpinner.getSelectedItemPosition();
        int[] values = getResources().getIntArray(R.array.delay_values);
        msg[2] = (byte)(values[idx] & 0xff);
        Integer val;
        if ((val = Utils.checkRange(ds18b20PinET, TSDZConst.MIN_DS18B20_PIN, TSDZConst.MAX_DS18B20_PIN)) == null) {
            showDialog(getString(R.string.ds18b2_pin_nr),
                    getString(R.string.range_error, TSDZConst.MIN_DS18B20_PIN, TSDZConst.MAX_DS18B20_PIN),
                    false);
            return;
        }
        msg[3] = (byte)(val & 0xff);
        idx = dbgLevelSpinner.getSelectedItemPosition();
        msg[4] = (byte)(idx & 0xff);
        msg[5] = (byte)(lockBikeSwitch.isChecked()?1:0);

        TSDZBTService service = TSDZBTService.getBluetoothService();
        if (service != null && service.getConnectionStatus() == TSDZBTService.ConnectionState.CONNECTED)
            TSDZBTService.getBluetoothService().writeCommand(msg);
        else
            showDialog(getString(R.string.error), getString(R.string.connection_error), false);
    }

    private void showDialog (String title, String message, boolean exit) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (title != null)
            builder.setTitle(title);
        builder.setMessage(message);
        if (exit) {
            builder.setOnCancelListener((dialog) -> ESP32ConfigActivity.this.finish());
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> ESP32ConfigActivity.this.finish());
        } else
            builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onMessageEvent(TSDZBTService.BTServiceEvent event) {
        if (event.eventType != TSDZBTService.BTEventType.TSDZ_COMMAND)
            return;
        Log.d(TAG, "onReceive ");

        byte[] data = event.data;
        Log.d(TAG, "TSDZ_COMMAND Data: " + Utils.bytesToHex(data));
        if (data[0] == TSDZConst.CMD_ESP32_CONFIG && data[1] == TSDZConst.CONFIG_GET) {
            if (data.length != 7) {
                showDialog(getString(R.string.error), getString(R.string.esp32_cfg_error), true);
            }
            if (data[2] != 0) {
                showDialog(getString(R.string.error), getString(R.string.read_cfg_error), true);
            } else {
                // current BT delay value is data[3]
                int[] values = getResources().getIntArray(R.array.delay_values);
                int i;
                for (i = 0; i < values.length; i++) {
                    if (data[3] == values[i])
                        break;
                }
                if (i < values.length)
                    btDelaySpinner.setSelection(i);
                else
                    btDelaySpinner.setSelection(values.length - 1);
                ds18b20PinET.setText(String.valueOf(data[4]));
                dbgLevelSpinner.setSelection(data[5]);
                lockBikeSwitch.setChecked(data[6] != 0);

                okButton.setEnabled(true);
            }
        } else if (data[0] == TSDZConst.CMD_ESP32_CONFIG && data[1] == TSDZConst.CONFIG_SET) {
            if (data[2] != 0) {
                showDialog(getString(R.string.error), getString(R.string.write_cfg_error), false);
            } else {
                finish();
            }
        }
    }
}
