package spider65.ebike.tsdz2_esp32.activities;


import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.TSDZConst;
import spider65.ebike.tsdz2_esp32.ota.Esp32_Ota;
import spider65.ebike.tsdz2_esp32.ota.Stm8_Ota;
import spider65.ebike.tsdz2_esp32.utils.OnSwipeListener;


public class ChartActivity extends AppCompatActivity {

    private static final String TAG = "ChartActivity";

    private LineChart chart;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_chart);

        chart = findViewById(R.id.chart1);

        chart.getDescription().setEnabled(false);

        // enable touch gestures
        chart.setTouchEnabled(true);

        chart.setDragDecelerationFrictionCoef(0.9f);

        // enable scaling and dragging
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setDrawGridBackground(false);
        chart.setHighlightPerDragEnabled(true);

        // if disabled, scaling can be done on x- and y-axis separately
        chart.setPinchZoom(true);

        // set an alternative background color
        chart.setBackgroundColor(Color.LTGRAY);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        TSDZBTService service = TSDZBTService.getBluetoothService();
        if (service != null && service.getConnectionStatus() == TSDZBTService.ConnectionState.CONNECTED) {
            menu.findItem(R.id.bikeOTA).setEnabled(true);
            menu.findItem(R.id.espOTA).setEnabled(true);
            menu.findItem(R.id.showVersion).setEnabled(true);
            menu.findItem(R.id.config).setEnabled(true);
            menu.findItem(R.id.esp32Config).setEnabled(true);
        } else {
            menu.findItem(R.id.bikeOTA).setEnabled(false);
            menu.findItem(R.id.espOTA).setEnabled(false);
            menu.findItem(R.id.showVersion).setEnabled(false);
            menu.findItem(R.id.config).setEnabled(false);
            menu.findItem(R.id.esp32Config).setEnabled(false);
        }
        return true;
    }
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        int id = item.getItemId();
        switch (id) {
            case R.id.espOTA:
                intent = new Intent(this, Esp32_Ota.class);
                startActivity(intent);
                return true;
            case R.id.bikeOTA:
                intent = new Intent(this, Stm8_Ota.class);
                startActivity(intent);
                return true;
            case R.id.config:
                intent = new Intent(this, TSDZCfgActivity.class);
                startActivity(intent);
                return true;
            case R.id.btSetup:
                intent = new Intent(this, BluetoothSetupActivity.class);
                startActivity(intent);
                return true;
            case R.id.showVersion:
                TSDZBTService.getBluetoothService().writeCommand(new byte[] {TSDZConst.CMD_GET_APP_VERSION});
                return true;
            case R.id.esp32Config:
                intent = new Intent(this, ESP32ConfigActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}