package spider65.ebike.tsdz2_esp32.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.TSDZConst;
import spider65.ebike.tsdz2_esp32.utils.Utils;


public class ESP32ConfigActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "ESP32ConfigActivity";

    private IntentFilter mIntentFilter = new IntentFilter();

    private Spinner btDelaySpinner;
    private EditText ds18b20PinET;
    private CheckBox lcdAltPinsCB;
    private Button   okButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp32_config);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        btDelaySpinner = findViewById(R.id.btDelaySP);
        ds18b20PinET = findViewById(R.id.ds18b20ET);
        lcdAltPinsCB = findViewById(R.id.lcdCB);
        okButton = findViewById(R.id.okButton);
        Button cancelButton = findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(this);
        okButton.setOnClickListener(this);
        mIntentFilter.addAction(TSDZBTService.TSDZ_COMMAND_BROADCAST);

        TSDZBTService service = TSDZBTService.getBluetoothService();
        if (service == null || service.getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            showDialog(getString(R.string.error), getString(R.string.connection_error), true);
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
        // get current ESP32 Configuration
        TSDZBTService.getBluetoothService().writeCommand(new byte[] {TSDZConst.CMD_ESP32_CONFIG, TSDZConst.CONFIG_GET});
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.okButton:
                saveCfg();
                break;
            case R.id.cancelButton:
                finish();
                break;
        }
    }

    private void saveCfg() {
        byte[] msg = new byte[] {TSDZConst.CMD_ESP32_CONFIG, TSDZConst.CONFIG_SET,0,0,0};

        String sval = btDelaySpinner.getSelectedItem().toString();
        msg[2] = (byte)(Integer.valueOf(sval) & 0xff);
        Integer val;
        if ((val = Utils.checkRange(ds18b20PinET, TSDZConst.MIN_DS18B20_PIN, TSDZConst.MAX_DS18B20_PIN)) == null) {
            showDialog(getString(R.string.ds18b2_pin_nr),
                    getString(R.string.range_error, TSDZConst.MIN_DS18B20_PIN, TSDZConst.MAX_DS18B20_PIN),
                    false);
            return;
        }
        msg[3] = (byte)(val & 0xff);
        msg[4] = (lcdAltPinsCB.isChecked()?(byte)1:(byte)0);

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

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive " + intent.getAction());
            if (intent.getAction() == null || !intent.getAction().equals(TSDZBTService.TSDZ_COMMAND_BROADCAST))
                return;

            byte[] data = intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA);
            Log.d(TAG, "TSDZ_COMMAND_BROADCAST Data: " + Utils.bytesToHex(data));
            if (data[0] == TSDZConst.CMD_ESP32_CONFIG && data[1] == TSDZConst.CONFIG_GET) {
                if (data[2] != 0) {
                    showDialog(getString(R.string.error), getString(R.string.read_cfg_error), true);
                } else {
                    // current BT delay value
                    String val = String.valueOf(data[3]);
                    String[] values = getResources().getStringArray(R.array.delay_values);
                    int i;
                    for (i = 0; i < values.length; i++) {
                        if (val.equals(values[i]))
                            break;
                    }
                    if (i < values.length)
                        btDelaySpinner.setSelection(i);
                    else
                        btDelaySpinner.setSelection(values.length - 1);
                    ds18b20PinET.setText(String.valueOf(data[4]));
                    lcdAltPinsCB.setChecked(data[5] != 0);
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
    };
}
