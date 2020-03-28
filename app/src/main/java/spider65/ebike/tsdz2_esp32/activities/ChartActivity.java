package spider65.ebike.tsdz2_esp32.activities;


import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import spider65.ebike.tsdz2_esp32.MyApp;
import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.data.LogManager;


public class ChartActivity extends AppCompatActivity implements LogManager.LogResultListener {

    private static final String TAG = "ChartActivity";

    private LineChart chart;

    protected Typeface tfRegular;
    protected Typeface tfLight;

    private long tzOffset; // Timezone offset in seconds to GMT
    private LogManager logManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_chart);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayShowTitleEnabled(false);
        chart = findViewById(R.id.chart1);

        tfRegular = Typeface.createFromAsset(getAssets(), "OpenSans-Regular.ttf");
        tfLight = Typeface.createFromAsset(getAssets(), "OpenSans-Light.ttf");

        TimeZone tz = Calendar.getInstance().getTimeZone();
        tzOffset = tz.getOffset(System.currentTimeMillis())/1000;

        logManager = MyApp.getLogManager();
        logManager.setListener(this);

        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragDecelerationFrictionCoef(0.9f);
        // enable scaling and dragging
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setDrawGridBackground(false);
        chart.setHighlightPerDragEnabled(true);
        // if disabled, scaling can be done on x- and y-axis separately
        chart.setPinchZoom(false);
        // set an alternative background color
        chart.setBackgroundColor(Color.LTGRAY);

        setData();

        // get the legend (only possible after setting data)
        Legend l = chart.getLegend();
        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTypeface(tfLight);
        l.setTextSize(12f);
        l.setTextColor(Color.WHITE);
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);
//        l.setYOffset(11f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setTypeface(tfLight);
        xAxis.setTextSize(12f);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(true);
        xAxis.setDrawAxisLine(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawLabels(true);


        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                /*
                ViewPortHandler viewPortHandler = myChart.getViewPortHandler();
                float scaleX = viewPortHandler.getScaleX();
                */
                return String.valueOf(value);
            }
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                //return String.valueOf(value);

                long l = (long)(value*60)+statusStartTime+tzOffset;
                int minutes = (int) ((l / (60L)) % 60L);
                int hours   = (int) ((l / (60L*60L)) % 24L);
                return String.format(Locale.ITALY,"%02d:%02d",hours,minutes);
            }
        });

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTypeface(tfLight);
        leftAxis.setTextColor(ColorTemplate.getHoloBlue());
        //leftAxis.setAxisMaximum(200f);
        //leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGranularityEnabled(true);

        chart.getAxisRight().setEnabled(false);

        chart.getViewPortHandler().setMaximumScaleY(1f);
        chart.getViewPortHandler().setMaximumScaleX(6f);


        /*
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);
        rightAxis.setTypeface(tfLight);
        rightAxis.setTextColor(Color.RED);
        rightAxis.setAxisMaximum(900);
        rightAxis.setAxisMinimum(-200);
        rightAxis.setDrawGridLines(false);
        rightAxis.setDrawZeroLine(false);
        rightAxis.setGranularityEnabled(false);
        */
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        MyApp.getLogManager().setListener(null);
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
        getMenuInflater().inflate(R.menu.menu_chart, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.actionToggleValues: {
                List<ILineDataSet> sets = chart.getData()
                        .getDataSets();

                for (ILineDataSet iSet : sets) {

                    LineDataSet set = (LineDataSet) iSet;
                    set.setDrawValues(!set.isDrawValuesEnabled());
                }

                chart.invalidate();
                break;
            }
            case R.id.actionToggleHighlight: {
                if (chart.getData() != null) {
                    chart.getData().setHighlightEnabled(!chart.getData().isHighlightEnabled());
                    chart.invalidate();
                }
                break;
            }
            case R.id.actionToggleFilled: {

                List<ILineDataSet> sets = chart.getData()
                        .getDataSets();

                for (ILineDataSet iSet : sets) {

                    LineDataSet set = (LineDataSet) iSet;
                    if (set.isDrawFilledEnabled())
                        set.setDrawFilled(false);
                    else
                        set.setDrawFilled(true);
                }
                chart.invalidate();
                break;
            }
            case R.id.actionToggleCircles: {
                List<ILineDataSet> sets = chart.getData()
                        .getDataSets();

                for (ILineDataSet iSet : sets) {

                    LineDataSet set = (LineDataSet) iSet;
                    if (set.isDrawCirclesEnabled())
                        set.setDrawCircles(false);
                    else
                        set.setDrawCircles(true);
                }
                chart.invalidate();
                break;
            }
            case R.id.actionToggleCubic: {
                List<ILineDataSet> sets = chart.getData()
                        .getDataSets();

                for (ILineDataSet iSet : sets) {

                    LineDataSet set = (LineDataSet) iSet;
                    set.setMode(set.getMode() == LineDataSet.Mode.CUBIC_BEZIER
                            ? LineDataSet.Mode.LINEAR
                            : LineDataSet.Mode.CUBIC_BEZIER);
                }
                chart.invalidate();
                break;
            }
            case R.id.actionToggleStepped: {
                List<ILineDataSet> sets = chart.getData()
                        .getDataSets();

                for (ILineDataSet iSet : sets) {

                    LineDataSet set = (LineDataSet) iSet;
                    set.setMode(set.getMode() == LineDataSet.Mode.STEPPED
                            ? LineDataSet.Mode.LINEAR
                            : LineDataSet.Mode.STEPPED);
                }
                chart.invalidate();
                break;
            }
            case R.id.actionToggleHorizontalCubic: {
                List<ILineDataSet> sets = chart.getData()
                        .getDataSets();

                for (ILineDataSet iSet : sets) {

                    LineDataSet set = (LineDataSet) iSet;
                    set.setMode(set.getMode() == LineDataSet.Mode.HORIZONTAL_BEZIER
                            ? LineDataSet.Mode.LINEAR
                            : LineDataSet.Mode.HORIZONTAL_BEZIER);
                }
                chart.invalidate();
                break;
            }
            case R.id.actionTogglePinch: {
                if (chart.isPinchZoomEnabled())
                    chart.setPinchZoom(false);
                else
                    chart.setPinchZoom(true);

                chart.invalidate();
                break;
            }
            case R.id.actionToggleAutoScaleMinMax: {
                chart.setAutoScaleMinMaxEnabled(!chart.isAutoScaleMinMaxEnabled());
                chart.notifyDataSetChanged();
                break;
            }
            case R.id.animateX: {
                chart.animateX(2000);
                break;
            }
            case R.id.animateY: {
                chart.animateY(2000);
                break;
            }
            case R.id.animateXY: {
                chart.animateXY(2000, 2000);
                break;
            }
        }
        return true;
    }


    void setData() {
        long start,end;
        ArrayList<Entry> values = new ArrayList<>();

        /*
        end = System.currentTimeMillis();
        end = end/1000/60*60;
        start = end - (60 * 30); // 30 min

        ArrayList<Entry> values = new ArrayList<>();
        float val, min = 1000000000, max = 0;

        // impostare l'asse x in modo che l'unità sia 1 minuto e garanularity = 1
        // con secondi e granularity = 60 non funziona.
        for (long i=start; i<end; i++) {
            val = (float)(16 * Math.sin((double)i/57));
            values.add(new Entry((float)(i-start)/60, val));
        }
        max = 16f;
        min = -16f;

        chart.getXAxis().setAxisMinimum(0f);
        chart.getXAxis().setAxisMaximum(30f);
        chart.getXAxis().setGranularity(1f);
        */

        end = System.currentTimeMillis()/1000L/60L;
        start = end - 30;
        LineDataSet set = new LineDataSet(values, "Speed");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(3f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setDrawCircleHole(false);
        set.setDrawValues(false);
        set.setDrawCircles(false);
        set.setDrawFilled(false);
        LineData data = new LineData(set);
        chart.getAxisLeft().setAxisMinimum(0);
        chart.getAxisLeft().setAxisMaximum(100);
        chart.getXAxis().setGranularity(1f);
        chart.setData(data);
        logManager.queryStatusData((int)start, (int)end);
        //logManager.queryDebugData((int)start, (int)end);
    }

    private List<LogManager.LogStatusEntry> statusData = null;
    private List<LogManager.LogDebugEntry>  debugData  = null;
    private long statusStartTime = 0;
    private long debugStartTime = 0;

    @Override
    public void logStatusResult(List<LogManager.LogStatusEntry> result) {
        statusData = result;
        statusStartTime = statusData.get(0).time/1000;

        LineDataSet set = (LineDataSet) chart.getData().getDataSetByIndex(0);

        ArrayList<Entry> values = new ArrayList<>();
        for (int i=0; i<statusData.size(); i++) {
            float y = statusData.get(i).status.speed;
            float x = (float)((statusData.get(i).time - statusStartTime) / 1000) / 60f;
            values.add(new Entry(x, y));
        }
        set.setValues(values);
        runOnUiThread( () -> {
                chart.getData().notifyDataChanged();
                chart.notifyDataSetChanged();
            });
    }

    @Override
    public void logDebugResult(List<LogManager.LogDebugEntry>  result) {
        debugData = result;
        debugStartTime = debugData.get(0).time;

        LineDataSet set = (LineDataSet) chart.getData().getDataSetByIndex(0);

        ArrayList<Entry> values = new ArrayList<>();
        for (int i=0; i<debugData.size(); i++) {
            float y = debugData.get(i).debug.dutyCycle;
            float x = (float)((debugData.get(i).time - debugStartTime) / 1000) / 60f;
            values.add(new Entry(x, y));
        }
        set.setValues(values);
        runOnUiThread( () -> {
            chart.getData().notifyDataChanged();
            chart.notifyDataSetChanged();
        });
    }
}