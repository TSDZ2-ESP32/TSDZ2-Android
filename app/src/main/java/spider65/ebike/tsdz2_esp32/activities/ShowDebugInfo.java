package spider65.ebike.tsdz2_esp32.activities;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Locale;

import spider65.ebike.tsdz2_esp32.MainActivity;
import spider65.ebike.tsdz2_esp32.MyApp;
import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Status;


public class ShowDebugInfo extends AppCompatActivity {

    private LinearLayout mainTimeLL, pwmTimeLL, hallErrLL;
    private TextView mainLoopTV, pwmTV, hallErrTV;
    private TextView rxcTV, rxlTV, tsActiveTV, tsAvgTV, tsMinTV, tsMaxTV,  ebikeTimeTV, pwmDownTV, pwmUpTV, hallStateErrTV, hallSeqErrTV;
    private View div1, div2, div3;
    private final TSDZ_Status status = new TSDZ_Status();

    private boolean timeDebug = false;
    private boolean hallDebug = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_debug);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        boolean screenOn = MyApp.getPreferences().getBoolean(MainActivity.KEY_SCREEN_ON, false);
        if (screenOn)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        rxcTV  = findViewById(R.id.rxcErrorsTV);
        rxlTV  = findViewById(R.id.rxlErrorsTV);
        tsActiveTV = findViewById(R.id.tsActiveTV);
        tsAvgTV = findViewById(R.id.tsAverageTV);
        tsMinTV = findViewById(R.id.minADCTV);
        tsMaxTV = findViewById(R.id.maxADCTV);

        mainTimeLL = findViewById(R.id.mainTimeLinearLayout);
        mainLoopTV = findViewById(R.id.mainTimeTV);
        ebikeTimeTV = findViewById(R.id.mainLoopTimeTV);
        div1 = findViewById(R.id.dbg_infoDV1);
        mainTimeLL.setVisibility(View.GONE);
        mainLoopTV.setVisibility(View.GONE);
        div1.setVisibility(View.GONE);

        pwmTimeLL = findViewById(R.id.pwmTimeLinearLayout);
        pwmTV = findViewById(R.id.pwmTimeTV);
        pwmDownTV = findViewById(R.id.pwmDownTimeTV);
        pwmUpTV = findViewById(R.id.pwmUpTimeTV);
        div2 = findViewById(R.id.dbg_infoDV2);
        pwmTimeLL.setVisibility(View.GONE);
        pwmTV.setVisibility(View.GONE);
        div2.setVisibility(View.GONE);

        hallErrLL = findViewById(R.id.hallErrLinearLayout);
        hallErrTV = findViewById(R.id.hallErrTV);
        hallStateErrTV = findViewById(R.id.hallStateErrTV);
        hallSeqErrTV = findViewById(R.id.hallSeqErrTV);
        div3 = findViewById(R.id.dbg_infoDV3);
        hallErrLL.setVisibility(View.GONE);
        hallErrTV.setVisibility(View.GONE);
        div3.setVisibility(View.GONE);

        if (TSDZBTService.getBluetoothService() == null || TSDZBTService.getBluetoothService().getConnectionStatus() != TSDZBTService.ConnectionState.CONNECTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.connection_error);
            builder.setOnCancelListener((dialog) -> finish());
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> finish());
            builder.show();
        }
    }

    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    public void onButtonClick(View view) {
        if (view.getId() == R.id.exitButton)
            finish();
    }

    private void refreshStatus() {
        tsActiveTV.setText(String.format(Locale.getDefault(),"%d", status.torqueSmoothPct));
        tsAvgTV.setText(String.format(Locale.getDefault(),"%d", status.torqueSmoothAvg));
        tsMinTV.setText(String.format(Locale.getDefault(),"%d", status.torqueSmoothMin));
        tsMaxTV.setText(String.format(Locale.getDefault(),"%d", status.torqueSmoothMax));

        if (!timeDebug && status.timeDebug) {
            timeDebug = true;
            mainTimeLL.setVisibility(View.VISIBLE);
            mainLoopTV.setVisibility(View.VISIBLE);
            div1.setVisibility(View.VISIBLE);
            pwmTimeLL.setVisibility(View.VISIBLE);
            pwmTV.setVisibility(View.VISIBLE);
            div2.setVisibility(View.VISIBLE);
        }

        if (!hallDebug && status.hallDebug) {
            hallDebug = true;
            hallErrLL.setVisibility(View.VISIBLE);
            hallErrTV.setVisibility(View.VISIBLE);
            div3.setVisibility(View.VISIBLE);
        }

        if (timeDebug) {
            ebikeTimeTV.setText(String.format(Locale.getDefault(),"%d", status.debug1));
            int upIRQ = ((status.debug3 & 255) << 8) + (status.debug2 & 255);
            int dnIRQ = ((status.debug5 & 255) << 8) + (status.debug4 & 255);
            pwmUpTV.setText(String.format(Locale.getDefault(),"%d", upIRQ));
            pwmDownTV.setText(String.format(Locale.getDefault(),"%d", dnIRQ));
        }

        if (hallDebug) {
            hallStateErrTV.setText(String.format(Locale.getDefault(),"%d", status.debug1));
            hallSeqErrTV.setText(String.format(Locale.getDefault(),"%d", status.debug2));
        }

        rxcTV.setText(String.format(Locale.getDefault(),"%d", status.rxcErrors));
        rxlTV.setText(String.format(Locale.getDefault(),"%d", status.rxlErrors));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onMessageEvent(TSDZBTService.BTServiceEvent event) {
        if (event.eventType == TSDZBTService.BTEventType.TSDZ_STATUS) {
            if (status.setData(event.data)) {
                runOnUiThread(this::refreshStatus);
            }
        }
    }
}
