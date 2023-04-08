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
import org.greenrobot.eventbus.ThreadMode;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Config;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Status;
import spider65.ebike.tsdz2_esp32.databinding.ActivityTorqueSetupBinding;

public class TorqueSetupActivity extends AppCompatActivity {

    private static final String TAG = "TorqueSetupActivity";

    private final TSDZ_Config cfg = new TSDZ_Config();
    private ActivityTorqueSetupBinding binding;

    private final TSDZ_Status status = new TSDZ_Status();
    private int minADC = 10000;
    private int maxADC = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this,R.layout.activity_torque_setup);
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

    //  invalidate all to hide/show the checkbox dependant fields
    public void onCheckedChanged(View view, boolean checked) {
        if (view.getId() == R.id.torqueFixCB)
            binding.torqueADCOffsetET.setEnabled(checked);
        else if (view.getId() == R.id.torqueSmoothCB) {
            binding.torqueSmotothMin.setEnabled(checked);
            binding.torqueSmotothMax.setEnabled(checked);
        }
    }

    private void saveCfg() {
        Integer val, val2;
        boolean checked;

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

        checked = binding.torqueSmoothCB.isChecked();
        if (checked) {
            if ((val = checkRange(binding.torqueSmotothMin, 4, 30)) == null) {
                showDialog(getString(R.string.torque_smooth_min), getString(R.string.range_error, 4, 30));
                return;
            }
            if ((val2 = checkRange(binding.torqueSmotothMax, 4, 30)) == null) {
                showDialog(getString(R.string.torque_smooth_max), getString(R.string.range_error, 4, 30));
                return;
            }
            cfg.ui8_torque_smooth_min = val;
            cfg.ui8_torque_smooth_max = val2;
        }
        cfg.torqueSmoothEnable = checked;

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
                }
                break;
            case TSDZ_CFG_WRITE_OK:
                finish();
                break;
            case TSDZ_CFG_WRITE_KO:
                showDialog(getString(R.string.error), getString(R.string.write_cfg_error));
                break;
            case TSDZ_STATUS:
                if (status.setData(event.data)) {
                    if (status.torqueADCValue < minADC)
                        minADC = status.torqueADCValue;
                    if (status.torqueADCValue > maxADC)
                        maxADC = status.torqueADCValue;
                    binding.setCurrADC(status.torqueADCValue);
                    binding.setMaxDelta(maxADC - minADC);
                    binding.setMinADC(minADC);
                    binding.setMaxADC(maxADC);
                }
                break;
            }
        }
}
