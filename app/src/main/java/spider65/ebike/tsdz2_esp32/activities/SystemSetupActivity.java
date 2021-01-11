package spider65.ebike.tsdz2_esp32.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.View;
import android.widget.EditText;

import java.util.Locale;

import androidx.databinding.DataBindingUtil;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Config;
import spider65.ebike.tsdz2_esp32.databinding.ActivitySystemSetupBinding;

public class SystemSetupActivity extends AppCompatActivity {

    private static final String TAG = "MotorSetupActivity";
    private TSDZ_Config cfg = new TSDZ_Config();
    private IntentFilter mIntentFilter = new IntentFilter();
    private ActivitySystemSetupBinding binding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this,R.layout.activity_system_setup);
        binding.setClickHandler(this);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mIntentFilter.addAction(TSDZBTService.TSDZ_CFG_READ_BROADCAST);
        mIntentFilter.addAction(TSDZBTService.TSDZ_CFG_WRITE_BROADCAST);
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
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, mIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    }

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

    public void onClickInductance(View view) {
        switch (view.getId()) {
            case R.id.inductance36BT:
                binding.inductanceET.setText("80");
                break;
            case R.id.inductance48BT:
                binding.inductanceET.setText("142");
                break;
        }
    }

    public void onClickResetHall(View view) {
        binding.hallCalibDataTV.setText("(0,0,0,0,0,0) - (0,0)");
        for (int i=0; i<6; i++) {
            cfg.ui8_hall_ref_angles[i] = 0;
        }
        cfg.ui8_hall_counter_offset_up = 0;
        cfg.ui8_hall_counter_offset_down = 0;
    }

    //  invalidate all to hide/show the checkbox dependant fields
    public void onCheckedChanged(View view, boolean checked) {
        switch (view.getId()) {
            case R.id.assistCB:
                binding.assistWPRET.setEnabled(checked);
                break;
            case R.id.streetPowerCB:
                binding.streetPowerET.setEnabled(checked);
                break;
            case R.id.torqueFixCB:
                binding.torqueADCOffsetET.setEnabled(checked);
                break;
        }
    }

    private void saveCfg() {
        Integer val;
        boolean checked;
        if ((val = checkRange(binding.inductanceET, 0, 150)) == null) {
            showDialog(getString(R.string.motor_inductance), getString(R.string.range_error, 0, 150));
            return;
        }
        cfg.ui8_motor_inductance_x1048576 = val;

        if ((val = checkRange(binding.accelerationET, 0, 100)) == null) {
            showDialog(getString(R.string.acceleration), getString(R.string.range_error, 0, 100));
            return;
        }
        cfg.ui8_motor_acceleration = val;

        if ((val = checkRange(binding.maxCurrentET, 1, 18)) == null) {
            showDialog(getString(R.string.max_current), getString(R.string.range_error, 1, 18));
            return;
        }
        cfg.ui8_battery_max_current = val;

        if ((val = checkRange(binding.maxPowerET, 50, 1000)) == null) {
            showDialog(getString(R.string.max_power), getString(R.string.range_error, 50, 1000));
            return;
        }
        cfg.ui8_target_max_battery_power_div25 = val;

        checked = binding.assistCB.isChecked();
        if (checked) {
            if ((val = checkRange(binding.assistWPRET, 0, 100)) == null) {
                showDialog(getString(R.string.assit_wpr), getString(R.string.range_error, 0, 100));
                return;
            }
            cfg.ui8_assist_without_pedal_rotation_threshold = val;
        }
        cfg.assist_without_pedal_rotation = checked;

        if ((val = checkRange(binding.torqueADCET, 0, 255)) == null) {
            showDialog(getString(R.string.torque_adc_step), getString(R.string.range_error, 0, 255));
            return;
        }
        cfg.ui8_pedal_torque_per_10_bit_ADC_step_x100 = val;

        checked = binding.torqueFixCB.isChecked();
        if (checked) {
            if ((val = checkRange(binding.torqueADCOffsetET, 40, 300)) == null) {
                showDialog(getString(R.string.torque_adc_offset), getString(R.string.range_error, 40, 300));
                return;
            }
            cfg.ui16_torque_offset_ADC = val;
        }
        cfg.torque_offset_fix = checked;

        if ((val = checkRange(binding.wheelPerimeterET, 1000, 2500)) == null) {
            showDialog(getString(R.string.wheel_perimeter), getString(R.string.range_error, 1000, 2500));
            return;
        }
        cfg.ui16_wheel_perimeter = val;

        if ((val = checkRange(binding.maxSpeedET, 10, 60)) == null) {
            showDialog(getString(R.string.max_speed), getString(R.string.range_error, 10, 60));
            return;
        }
        cfg.ui8_max_speed = val;

        checked = binding.cruiseModeCB.isChecked();
        cfg.ui8_cruise_enabled = checked;

        if ((val = checkRange(binding.maxStreetSpeedET, 10, 45)) == null) {
            showDialog(getString(R.string.max_speed), getString(R.string.range_error, 10, 45));
            return;
        }
        cfg.ui8_street_max_speed = val;

        checked = binding.streetPowerCB.isChecked();
        if (checked) {
            if ((val = checkRange(binding.streetPowerET, 50, 1000)) == null) {
                showDialog(getString(R.string.max_power), getString(R.string.range_error, 50, 1000));
                return;
            }
            cfg.ui8_street_mode_power_limit_div25 = val;
        }
        cfg.ui8_street_mode_power_limit_enabled = checked;

        checked = binding.streetThrottleCB.isChecked();
        cfg.ui8_street_mode_throttle_enabled = checked;

        cfg.ui8_lights_configuration = binding.lightConfigSP.getSelectedItemPosition();

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

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive " + intent.getAction());
        if (intent.getAction() == null)
            return;
        switch (intent.getAction()) {
            case TSDZBTService.TSDZ_CFG_READ_BROADCAST:
                if (cfg.setData(intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA))) {
                    binding.setCfg(cfg);
                    binding.lightConfigSP.setSelection(cfg.ui8_lights_configuration);
                    String s = String.format(Locale.getDefault(), "(%d,%d,%d,%d,%d,%d) - (%d,%d)",
                            cfg.ui8_hall_ref_angles[0] & 0xff,
                            cfg.ui8_hall_ref_angles[1] & 0xff,
                            cfg.ui8_hall_ref_angles[2] & 0xff,
                            cfg.ui8_hall_ref_angles[3] & 0xff,
                            cfg.ui8_hall_ref_angles[4] & 0xff,
                            cfg.ui8_hall_ref_angles[5] & 0xff,
                            cfg.ui8_hall_counter_offset_up & 0xff,
                            cfg.ui8_hall_counter_offset_down & 0xff
                            );
                    binding.hallCalibDataTV.setText(s);
                }
                break;
            case TSDZBTService.TSDZ_CFG_WRITE_BROADCAST:
                if (intent.getBooleanExtra(TSDZBTService.VALUE_EXTRA,false))
                    finish();
                else
                    showDialog(getString(R.string.error), getString(R.string.write_cfg_error));
                break;
         }
        }
    };
}
