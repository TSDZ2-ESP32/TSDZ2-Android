package spider65.ebike.tsdz2_esp32.activities;

import androidx.appcompat.app.AppCompatActivity;

import spider65.ebike.tsdz2_esp32.R;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class TSDZCfgActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tsdzcfg);

        Button b;
        b = findViewById(R.id.motor_button);
        b.setOnClickListener((View) -> setupMotor());
        b = findViewById(R.id.battery_button);
        b.setOnClickListener((View) -> setupBattery());
        b = findViewById(R.id.levels_button);
        b.setOnClickListener((View) -> setupLevels());
        b = findViewById(R.id.temperature_control);
        b.setOnClickListener((View) -> setupTemperature());
        b = findViewById(R.id.street_mode);
        b.setOnClickListener((View) -> setupStreetMode());
        b = findViewById(R.id.cadence_sensor);
        b.setOnClickListener((View) -> cadenceSensorCalib());
    }

    private void setupMotor() {
        Intent intent = new Intent(this, SystemSetupActivity.class);
        startActivity(intent);
    }

    private void setupBattery() {
        Intent intent = new Intent(this, BatterySetupActivity.class);
        startActivity(intent);
    }

    private void setupLevels() {
        Intent intent = new Intent(this, LevelsSetupActivity.class);
        startActivity(intent);    }

    private void setupTemperature() {
        Intent intent = new Intent(this, TemperatureSetupActivity.class);
        startActivity(intent);    }

    private void setupStreetMode() {
        Intent intent = new Intent(this, StreetModeSetupActivity.class);
        startActivity(intent);    }

    private void cadenceSensorCalib() {
        Intent intent = new Intent(this, CadenceSensorCalibrationActivity.class);
        startActivity(intent);    }
}
