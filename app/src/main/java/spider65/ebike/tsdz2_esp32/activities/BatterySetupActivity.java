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
import spider65.ebike.tsdz2_esp32.databinding.ActivityBatterySetupBinding;

public class BatterySetupActivity extends AppCompatActivity {

    private static final String TAG = "BatterySetupActivity";
    private TSDZ_Config cfg = new TSDZ_Config();
    private IntentFilter mIntentFilter = new IntentFilter();
    private ActivityBatterySetupBinding binding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this,R.layout.activity_battery_setup);
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

    private void saveCfg() {
        Integer val;

        if ((val = checkRange(binding.cellsNrET, 10, 14)) == null) {
            showDialog(getString(R.string.cells_nr), getString(R.string.range_error, 10, 14));
            return;
        }
        cfg.ui8_battery_cells_number = val;

        if ((val = checkRange(binding.whResetET, 38*cfg.ui8_battery_cells_number, 43*cfg.ui8_battery_cells_number)) == null) {
            showDialog(getString(R.string.wh_reset_volt), getString(R.string.range_error, 38*cfg.ui8_battery_cells_number, 43*cfg.ui8_battery_cells_number));
            return;
        }
        cfg.ui16_battery_voltage_reset_wh_counter_x10 = val;

        if ((val = checkRange(binding.batteryCutOffET, 25*cfg.ui8_battery_cells_number, 33*cfg.ui8_battery_cells_number)) == null) {
            showDialog(getString(R.string.volt_cut_off), getString(R.string.range_error, 25*cfg.ui8_battery_cells_number, 33*cfg.ui8_battery_cells_number));
            return;
        }
        cfg.ui16_battery_low_voltage_cut_off_x10 = val;

        if ((val = checkRange(binding.batteryResET, 10, 1000)) == null) {
            showDialog(getString(R.string.battery_resist), getString(R.string.range_error, 10, 1000));
            return;
        }
        cfg.ui16_battery_pack_resistance_x1000 = val;

        if ((val = checkRange(binding.cellOvervoltET, 300, 440)) == null) {
            showDialog(getString(R.string.cell_overvolt), getString(R.string.range_error, 300, 440));
            return;
        }
        cfg.ui8_li_io_cell_overvolt_x100 = val;

        if ((val = checkRange(binding.cellEmptyET, 250, 330)) == null) {
            showDialog(getString(R.string.cell_empty), getString(R.string.range_error, 250, 330));
            return;
        }
        cfg.ui8_li_io_cell_empty_x100 = val;

        if ((val = checkRange(binding.cellOneBarET, 250, 340)) == null) {
            showDialog(getString(R.string.cell_one_bar), getString(R.string.range_error, 250, 340));
            return;
        }
        cfg.ui8_li_io_cell_one_bar_x100 = val;

        if ((val = checkRange(binding.cellAllBarsET, 370, 430)) == null) {
            showDialog(getString(R.string.cell_all_bars), getString(R.string.range_error, 370, 430));
            return;
        }
        cfg.ui8_li_io_cell_full_bars_x100 = val;

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
