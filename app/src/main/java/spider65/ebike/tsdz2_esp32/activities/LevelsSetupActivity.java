package spider65.ebike.tsdz2_esp32.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Config;
import spider65.ebike.tsdz2_esp32.databinding.ActivityLevelsSetupBinding;

public class LevelsSetupActivity extends AppCompatActivity {

    // EMTB assist min max level values: level 1 - 20
    private static final int EMTB_MIN_LEVEL = 1;
    private static final int EMTB_MAX_LEVEL = 20;
    // Power assist min max level values: % of human Power
    private static final int POWER_MIN_LEVEL = 10;
    private static final int POWER_MAX_LEVEL = 500;
    // Cadence assist min max level values: Power in Watts
    private static final int CADENCE_MIN_LEVEL = 20;
    private static final int CADENCE_MAX_LEVEL = 400;
    // Torque assist min max level values: torque factor 1 - 200
    private static final int TORQUE_MIN_LEVEL = 1;
    private static final int TORQUE_MAX_LEVEL = 200;
    // Walk assist min max level values: Duty Cycle
    private static final int WALK_MIN_LEVEL = 10;
    private static final int WALK_MAX_LEVEL = 90;

    private static final String TAG = "LevelsSetupActivity";
    private final TSDZ_Config cfg = new TSDZ_Config();
    private ActivityLevelsSetupBinding binding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this,R.layout.activity_levels_setup);
        binding.setClickHandler(this);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


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

    public void onOkCancelClick(View view) {
        if (view.getId() == R.id.okButton)
            saveCfg();
        else if (view.getId() == R.id.exitButton)
            finish();
    }

    private void saveCfg() {
        Integer val1,val2,val3,val4;

        if ((val1 = checkRange(binding.eMTBAssist1ET, EMTB_MIN_LEVEL, EMTB_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.emtb_assit_level_1), getString(R.string.range_error, EMTB_MIN_LEVEL, EMTB_MAX_LEVEL));
            return;
        }
        if ((val2 = checkRange(binding.eMTBAssist2ET, EMTB_MIN_LEVEL, EMTB_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.emtb_assit_level_2), getString(R.string.range_error, EMTB_MIN_LEVEL, EMTB_MAX_LEVEL));
            return;
        }
        if ((val3 = checkRange(binding.eMTBAssist3ET, EMTB_MIN_LEVEL, EMTB_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.emtb_assit_level_3), getString(R.string.range_error, EMTB_MIN_LEVEL, EMTB_MAX_LEVEL));
            return;
        }
        if ((val4 = checkRange(binding.eMTBAssist4ET, EMTB_MIN_LEVEL, EMTB_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.emtb_assit_level_4), getString(R.string.range_error, EMTB_MIN_LEVEL, EMTB_MAX_LEVEL));
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

        if ((val1 = checkRange(binding.powerAssist1ET, POWER_MIN_LEVEL, POWER_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.power_assist_level_1), getString(R.string.range_error, POWER_MIN_LEVEL, POWER_MAX_LEVEL));
            return;
        }
        if ((val2 = checkRange(binding.powerAssist2ET, POWER_MIN_LEVEL, POWER_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.power_assist_level_2), getString(R.string.range_error, POWER_MIN_LEVEL, POWER_MAX_LEVEL));
            return;
        }
        if ((val3 = checkRange(binding.powerAssist3ET, POWER_MIN_LEVEL, POWER_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.power_assist_level_3), getString(R.string.range_error, POWER_MIN_LEVEL, POWER_MAX_LEVEL));
            return;
        }
        if ((val4 = checkRange(binding.powerAssist4ET, POWER_MIN_LEVEL, POWER_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.power_assist_level_4), getString(R.string.range_error, POWER_MIN_LEVEL, POWER_MAX_LEVEL));
            return;
        }
        if (val2>val1 && val3>val2 && val4>val3) {
            cfg.ui8_power_assist_level[0] = val1/2;
            cfg.ui8_power_assist_level[1] = val2/2;
            cfg.ui8_power_assist_level[2] = val3/2;
            cfg.ui8_power_assist_level[3] = val4/2;
        } else {
            showDialog(getString(R.string.power_mode), getString(R.string.level_error));
            return;
        }

        if ((val1 = checkRange(binding.torqueAssist1ET, TORQUE_MIN_LEVEL, TORQUE_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.torque_assist_level_1), getString(R.string.range_error, TORQUE_MIN_LEVEL, TORQUE_MAX_LEVEL));
            return;
        }
        if ((val2 = checkRange(binding.torqueAssist2ET, TORQUE_MIN_LEVEL, TORQUE_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.torque_assist_level_2), getString(R.string.range_error, TORQUE_MIN_LEVEL, TORQUE_MAX_LEVEL));
            return;
        }
        if ((val3 = checkRange(binding.torqueAssist3ET, TORQUE_MIN_LEVEL, TORQUE_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.torque_assist_level_3), getString(R.string.range_error, TORQUE_MIN_LEVEL, TORQUE_MAX_LEVEL));
            return;
        }
        if ((val4 = checkRange(binding.torqueAssist4ET, TORQUE_MIN_LEVEL, TORQUE_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.torque_assist_level_4), getString(R.string.range_error, TORQUE_MIN_LEVEL, TORQUE_MAX_LEVEL));
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

        if ((val1 = checkRange(binding.cadenceAssist1ET, CADENCE_MIN_LEVEL, CADENCE_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.cadence_assist_level_1), getString(R.string.range_error, CADENCE_MIN_LEVEL, CADENCE_MAX_LEVEL));
            return;
        }
        if ((val2 = checkRange(binding.cadenceAssist2ET, CADENCE_MIN_LEVEL, CADENCE_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.cadence_assist_level_2), getString(R.string.range_error, CADENCE_MIN_LEVEL, CADENCE_MAX_LEVEL));
            return;
        }
        if ((val3 = checkRange(binding.cadenceAssist3ET, CADENCE_MIN_LEVEL, CADENCE_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.cadence_assist_level_3), getString(R.string.range_error, CADENCE_MIN_LEVEL, CADENCE_MAX_LEVEL));
            return;
        }
        if ((val4 = checkRange(binding.cadenceAssist4ET, CADENCE_MIN_LEVEL, CADENCE_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.cadence_assist_level_4), getString(R.string.range_error, CADENCE_MIN_LEVEL, CADENCE_MAX_LEVEL));
            return;
        }
        if (val2>val1 && val3>val2 && val4>val3) {
            cfg.ui8_cadence_assist_level[0] = val1/2;
            cfg.ui8_cadence_assist_level[1] = val2/2;
            cfg.ui8_cadence_assist_level[2] = val3/2;
            cfg.ui8_cadence_assist_level[3] = val4/2;
        } else {
            showDialog(getString(R.string.cadence_mode), getString(R.string.level_error));
            return;
        }

        if ((val1 = checkRange(binding.walkAssist1ET, WALK_MIN_LEVEL, WALK_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.walk_assist_level_1), getString(R.string.range_error, WALK_MIN_LEVEL, WALK_MAX_LEVEL));
            return;
        }
        if ((val2 = checkRange(binding.walkAssist2ET, WALK_MIN_LEVEL, WALK_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.walk_assist_level_2), getString(R.string.range_error, WALK_MIN_LEVEL, WALK_MAX_LEVEL));
            return;
        }
        if ((val3 = checkRange(binding.walkAssist3ET, WALK_MIN_LEVEL, WALK_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.walk_assist_level_3), getString(R.string.range_error, WALK_MIN_LEVEL, WALK_MAX_LEVEL));
            return;
        }
        if ((val4 = checkRange(binding.walkAssist4ET, WALK_MIN_LEVEL, WALK_MAX_LEVEL)) == null) {
            showDialog(getString(R.string.walk_assist_level_4), getString(R.string.range_error, WALK_MIN_LEVEL, WALK_MAX_LEVEL));
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

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onMessageEvent(TSDZBTService.BTServiceEvent event) {
        Log.d(TAG, "onReceive " + event.eventType);
        switch (event.eventType) {
            case TSDZ_CFG_READ:
                if (cfg.setData(event.data))
                    binding.setCfg(cfg);
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
