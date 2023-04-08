package spider65.ebike.tsdz2_esp32.activities;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Config;
import spider65.ebike.tsdz2_esp32.databinding.ActivityTemperatureSetupBinding;

public class TemperatureSetupActivity extends AppCompatActivity {

    private static final String TAG = "TempSetupActivity";
    private final TSDZ_Config cfg = new TSDZ_Config();
    private ActivityTemperatureSetupBinding binding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this,R.layout.activity_temperature_setup);
        binding.setClickHandler(this);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        binding.controlTypeSP.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                if (position == 0 || position == 2) { // none
                    binding.throttleCB.setChecked(cfg.throttleEnabled);
                    binding.throttleCB.setEnabled(true);
                } else {
                    binding.throttleCB.setEnabled(false);
                    binding.throttleCB.setChecked(false);
                }
                binding.warningTempET.setEnabled(position != 0);
                binding.stopTempET.setEnabled(position != 0);
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        TSDZBTService service = TSDZBTService.getBluetoothService();
        if (service != null && service.getConnectionStatus() == TSDZBTService.ConnectionState.CONNECTED)
            service.readCfg();
        else {
            showDialog(getString(R.string.error), getString(R.string.connection_error));
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

    @SuppressLint("NonConstantResourceId")
    public void onOkCancelClick(View view) {
        switch (view.getId()) {
            case R.id.okButton:
                saveCfg();
                break;
            case R.id.exitButton:
                finish();
                break;
        }
    }

    private void saveCfg() {
        Integer val1,val2;
        int selected = binding.controlTypeSP.getSelectedItemPosition();
        cfg.temperature_control = TSDZ_Config.TempControl.fromValue(selected);

        if (cfg.temperature_control == TSDZ_Config.TempControl.tempADC ||
                cfg.temperature_control == TSDZ_Config.TempControl.tempESP) {
            if ((val1 = checkRange(binding.warningTempET, 0, 124)) == null) {
                showDialog(getString(R.string.warning_temp), getString(R.string.range_error, 0, 124));
                return;
            }

            if ((val2 = checkRange(binding.stopTempET, val1+1, 125)) == null) {
                showDialog(getString(R.string.stop_temp), getString(R.string.range_error, val1+1, 125));
                return;
            }

            cfg.ui8_motor_temperature_min_value_to_limit = val1;
            cfg.ui8_motor_temperature_max_value_to_limit = val2;
        }

        cfg.throttleEnabled = binding.throttleCB.isChecked();

        TSDZBTService service = TSDZBTService.getBluetoothService();
        if (service != null && service.getConnectionStatus() == TSDZBTService.ConnectionState.CONNECTED)
            service.writeCfg(cfg);
        else {
            showDialog(getString(R.string.error), getString(R.string.connection_error));
        }
    }

    Integer checkRange(EditText et, int min, int max) {
        int val = Integer.parseInt(et.getText().toString());
        if (val < min || val > max) {
            et.setError(getString(R.string.range_error, min, max));
            return null;
        }
        return val;
    }

    private void showDialog (String title, String message) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (title != null)
            builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onMessageEvent(TSDZBTService.BTServiceEvent event) {
        Log.d(TAG, "onReceive " + event.eventType);
        switch (event.eventType) {
            case TSDZ_CFG_READ:
                if (cfg.setData(event.data)) {
                    binding.setCfg(cfg);
                    binding.throttleCB.setChecked(cfg.throttleEnabled);
                    binding.controlTypeSP.setSelection(cfg.temperature_control.getValue());
                }
                break;
            case TSDZ_CFG_WRITE_OK:
                finish();
                break;
            case TSDZ_CFG_WRITE_KO:
                showDialog(getString(R.string.error), getString(R.string.write_cfg_error));
                break;
        }
    }
}
