package spider65.ebike.tsdz2_esp32.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Config;
import spider65.ebike.tsdz2_esp32.databinding.ActivityLevelsSetupBinding;

import static spider65.ebike.tsdz2_esp32.TSDZConst.PWM_DUTY_CYCLE_MAX;
import static spider65.ebike.tsdz2_esp32.TSDZConst.WALK_ASSIST_DUTY_CYCLE_MAX;

public class LevelsSetupActivity extends AppCompatActivity {

    private static final String TAG = "LevelsSetupActivity";
    private TSDZ_Config cfg = new TSDZ_Config();
    private IntentFilter mIntentFilter = new IntentFilter();
    private ActivityLevelsSetupBinding binding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this,R.layout.activity_levels_setup);
        binding.setHandler(this);
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
            case R.id.cancelButton:
                finish();
                break;
        }
    }

    private void saveCfg() {
        Integer val1,val2,val3,val4;

        if ((val1 = checkRange(binding.eMTBAssist1ET, 1, 20)) == null) {
            showDialog(getString(R.string.emtb_assit_level_1), getString(R.string.range_error, 1, 20));
            return;
        }
        if ((val2 = checkRange(binding.eMTBAssist2ET, 1, 20)) == null) {
            showDialog(getString(R.string.emtb_assit_level_2), getString(R.string.range_error, 1, 20));
            return;
        }
        if ((val3 = checkRange(binding.eMTBAssist3ET, 1, 20)) == null) {
            showDialog(getString(R.string.emtb_assit_level_3), getString(R.string.range_error, 1, 20));
            return;
        }
        if ((val4 = checkRange(binding.eMTBAssist4ET, 1, 20)) == null) {
            showDialog(getString(R.string.emtb_assit_level_4), getString(R.string.range_error, 1, 20));
            return;
        }
        if (val2>val1 && val3>val2 && val4>val3) {
            cfg.ui8_eMTB_assist_level[0] = val1;
            cfg.ui8_eMTB_assist_level[1] = val2;
            cfg.ui8_eMTB_assist_level[2] = val3;
            cfg.ui8_eMTB_assist_level[3] = val4;
        } else {
            showDialog(getString(R.string.eMTB_mode), getString(R.string.level_error));
            return;
        }

        if ((val1 = checkRange(binding.powerAssist1ET, 1, 100)) == null) {
            showDialog(getString(R.string.power_assist_level_1), getString(R.string.range_error, 1, 100));
            return;
        }
        if ((val2 = checkRange(binding.powerAssist2ET, 1, 100)) == null) {
            showDialog(getString(R.string.power_assist_level_2), getString(R.string.range_error, 1, 100));
            return;
        }
        if ((val3 = checkRange(binding.powerAssist3ET, 1, 100)) == null) {
            showDialog(getString(R.string.power_assist_level_3), getString(R.string.range_error, 1, 100));
            return;
        }
        if ((val4 = checkRange(binding.powerAssist4ET, 1, 100)) == null) {
            showDialog(getString(R.string.power_assist_level_4), getString(R.string.range_error, 1, 100));
            return;
        }
        if (val2>val1 && val3>val2 && val4>val3) {
            cfg.ui8_power_assist_level[0] = val1;
            cfg.ui8_power_assist_level[1] = val2;
            cfg.ui8_power_assist_level[2] = val3;
            cfg.ui8_power_assist_level[3] = val4;
        } else {
            showDialog(getString(R.string.power_mode), getString(R.string.level_error));
            return;
        }

        if ((val1 = checkRange(binding.torqueAssist1ET, 1, 100)) == null) {
            showDialog(getString(R.string.torque_assist_level_1), getString(R.string.range_error, 1, 100));
            return;
        }
        if ((val2 = checkRange(binding.torqueAssist2ET, 1, 100)) == null) {
            showDialog(getString(R.string.torque_assist_level_2), getString(R.string.range_error, 1, 100));
            return;
        }
        if ((val3 = checkRange(binding.torqueAssist3ET, 1, 100)) == null) {
            showDialog(getString(R.string.torque_assist_level_3), getString(R.string.range_error, 1, 100));
            return;
        }
        if ((val4 = checkRange(binding.torqueAssist4ET, 1, 100)) == null) {
            showDialog(getString(R.string.torque_assist_level_4), getString(R.string.range_error, 1, 100));
            return;
        }
        if (val2>val1 && val3>val2 && val4>val3) {
            cfg.ui8_torque_assist_level[0] = val1;
            cfg.ui8_torque_assist_level[1] = val2;
            cfg.ui8_torque_assist_level[2] = val3;
            cfg.ui8_torque_assist_level[3] = val4;
        } else {
            showDialog(getString(R.string.torque_mode), getString(R.string.level_error));
            return;
        }

        if ((val1 = checkRange(binding.cadenceAssist1ET, 1, PWM_DUTY_CYCLE_MAX)) == null) {
            showDialog(getString(R.string.cadence_assist_level_1), getString(R.string.range_error, 1, PWM_DUTY_CYCLE_MAX));
            return;
        }
        if ((val2 = checkRange(binding.cadenceAssist2ET, 1, PWM_DUTY_CYCLE_MAX)) == null) {
            showDialog(getString(R.string.cadence_assist_level_2), getString(R.string.range_error, 1, PWM_DUTY_CYCLE_MAX));
            return;
        }
        if ((val3 = checkRange(binding.cadenceAssist3ET, 1, PWM_DUTY_CYCLE_MAX)) == null) {
            showDialog(getString(R.string.cadence_assist_level_3), getString(R.string.range_error, 1, PWM_DUTY_CYCLE_MAX));
            return;
        }
        if ((val4 = checkRange(binding.cadenceAssist4ET, 1, PWM_DUTY_CYCLE_MAX)) == null) {
            showDialog(getString(R.string.cadence_assist_level_4), getString(R.string.range_error, 1, PWM_DUTY_CYCLE_MAX));
            return;
        }
        if (val2>val1 && val3>val2 && val4>val3) {
            cfg.ui8_cadence_assist_level[0] = val1;
            cfg.ui8_cadence_assist_level[1] = val2;
            cfg.ui8_cadence_assist_level[2] = val3;
            cfg.ui8_cadence_assist_level[3] = val4;
        } else {
            showDialog(getString(R.string.cadence_mode), getString(R.string.level_error));
            return;
        }

        if ((val1 = checkRange(binding.walkAssist1ET, 1, WALK_ASSIST_DUTY_CYCLE_MAX)) == null) {
            showDialog(getString(R.string.walk_assist_level_1), getString(R.string.range_error, 1, WALK_ASSIST_DUTY_CYCLE_MAX));
            return;
        }
        if ((val2 = checkRange(binding.walkAssist2ET, 1, WALK_ASSIST_DUTY_CYCLE_MAX)) == null) {
            showDialog(getString(R.string.walk_assist_level_2), getString(R.string.range_error, 1, WALK_ASSIST_DUTY_CYCLE_MAX));
            return;
        }
        if ((val3 = checkRange(binding.walkAssist3ET, 1, WALK_ASSIST_DUTY_CYCLE_MAX)) == null) {
            showDialog(getString(R.string.walk_assist_level_3), getString(R.string.range_error, 1, WALK_ASSIST_DUTY_CYCLE_MAX));
            return;
        }
        if ((val4 = checkRange(binding.walkAssist4ET, 1, WALK_ASSIST_DUTY_CYCLE_MAX)) == null) {
            showDialog(getString(R.string.walk_assist_level_4), getString(R.string.range_error, 1, WALK_ASSIST_DUTY_CYCLE_MAX));
            return;
        }
        if (val2>val1 && val3>val2 && val4>val3) {
            cfg.ui8_walk_assist_level[0] = val1;
            cfg.ui8_walk_assist_level[1] = val2;
            cfg.ui8_walk_assist_level[2] = val3;
            cfg.ui8_walk_assist_level[3] = val4;
        } else {
            showDialog(getString(R.string.walk_mode), getString(R.string.level_error));
            return;
        }

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
                if (cfg.setData(intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA)))
                    binding.setCfg(cfg);
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
