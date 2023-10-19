package spider65.ebike.tsdz2_esp32.activities;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
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

import spider65.ebike.tsdz2_esp32.MyApp;
import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.data.LogManager;


public class ChartActivity extends AppCompatActivity implements LogManager.LogResultListener {

    private static final String TAG = "ChartActivity";

    // seekbar x axis shift step in minutes
    private static final long TIME_STEP=20;
    // x axis graph width in minutes
    private static final long TIME_WINDOW=60;

    private LineChart chart;
    private Spinner spinner;
    private SeekBar seekbar;
    private TextView fromToTV;

    protected Typeface tfRegular;
    protected Typeface tfLight;

    private long tzOffset; // Timezone offset in minutes to GMT
    private LogManager logManager;

    private List<LogManager.TimeInterval> intervals = null;
    private int currentInterval;

    private List<LogManager.LogStatusEntry> statusData = null;
    private long startTime = 0;

    private AlertDialog dataDialog = null;
    private DataItemAdapter mAdapter;
    private static final Set<DataType> selectedItemsList = EnumSet.of(DataType.speed, DataType.cadence);
    private static final DataItem[] dialogItemList = new DataItem[] {
            new DataItem(DataType.level, false, true),
            new DataItem(DataType.speed, false, true),
            new DataItem(DataType.cadence, false, true),
            new DataItem(DataType.pPower, false, true),
            new DataItem(DataType.pTorque, false, true),
            new DataItem(DataType.mPower, false, true),
            new DataItem(DataType.current, false, true),
            new DataItem(DataType.volt, false, true),
            new DataItem(DataType.energy, false, true),
            new DataItem(DataType.mTemp, false, true),
            new DataItem(DataType.cTemp, false, true),
            new DataItem(DataType.dCycle, false, true),
            new DataItem(DataType.erps, false, true),
            new DataItem(DataType.focAngle,  false, true),
            new DataItem(DataType.fwHallOffset, false, true),
            new DataItem(DataType.tSmoothPct, false, true),
            new DataItem(DataType.torqueMin, false, true),
            new DataItem(DataType.torqueMax, false, true),
            new DataItem(DataType.torqueAvg, false, true),
            new DataItem(DataType.esp32FromControllerErr, false, true),
            new DataItem(DataType.esp32FromLCDErr, false, true),
            new DataItem(DataType.controllerFromESP32Err, false, true)
    };

    private enum DataType {
        level, speed, cadence, pPower, mPower, current,
        volt, energy, mTemp, cTemp, dCycle, erps,
        focAngle, pTorque, fwHallOffset, tSmoothPct, torqueMin, torqueMax, torqueAvg,
        esp32FromControllerErr, esp32FromLCDErr, controllerFromESP32Err;

        public String getName() {
            switch (this) {
                case level:
                    return MyApp.getInstance().getString(R.string.assist_levels);
                case speed:
                    return MyApp.getInstance().getString(R.string.speed);
                case cadence:
                    return MyApp.getInstance().getString(R.string.cadence);
                case pPower:
                    return MyApp.getInstance().getString(R.string.pedal_power);
                case mPower:
                    return MyApp.getInstance().getString(R.string.motor_power);
                case current:
                    return MyApp.getInstance().getString(R.string.battery_current);
                case volt:
                    return MyApp.getInstance().getString(R.string.voltage);
                case energy:
                    return MyApp.getInstance().getString(R.string.energy_used);
                case mTemp:
                    return MyApp.getInstance().getString(R.string.motor_temp);
                case cTemp:
                    return MyApp.getInstance().getString(R.string.controller_temp);
                case dCycle:
                    return MyApp.getInstance().getString(R.string.duty_cycle);
                case erps:
                    return MyApp.getInstance().getString(R.string.motor_erps);
                case focAngle:
                    return MyApp.getInstance().getString(R.string.foc_angle);
                case pTorque:
                    return MyApp.getInstance().getString(R.string.pedal_torque);
                case fwHallOffset:
                    return MyApp.getInstance().getString(R.string.fw_hall_offset);
                case tSmoothPct:
                    return MyApp.getInstance().getString(R.string.torque_smoot_pct);
                case torqueMin:
                    return MyApp.getInstance().getString(R.string.torque_min);
                case torqueMax:
                    return MyApp.getInstance().getString(R.string.torque_max);
                case torqueAvg:
                    return MyApp.getInstance().getString(R.string.torque_avg);
                case esp32FromControllerErr:
                    return MyApp.getInstance().getString(R.string.esp32_from_ct_err);
                case esp32FromLCDErr:
                    return MyApp.getInstance().getString(R.string.esp32_from_lcd_err);
                case controllerFromESP32Err:
                    return MyApp.getInstance().getString(R.string.ct_from_esp32_err);

            }
            return "";
        }

        public boolean compatible(Set<DataType> selectedItems) {

            if (selectedItems.isEmpty())
                return true;

            switch (this) {
                case pPower:
                case mPower:
                    return (selectedItems.contains(mPower) || selectedItems.contains(pPower));
                case mTemp:
                case cTemp:
                    return (selectedItems.contains(mTemp) || selectedItems.contains(cTemp));
                case torqueMin:
                case torqueMax:
                case torqueAvg:
                    for (DataType item : selectedItems)
                        if ((item == torqueMin) || (item == torqueMax) || (item == torqueAvg))
                            return true;
                case esp32FromControllerErr:
                case esp32FromLCDErr:
                case controllerFromESP32Err:
                    for (DataType item : selectedItems)
                        if ((item == esp32FromControllerErr) || (item == esp32FromLCDErr) || (item == controllerFromESP32Err))
                            return true;
            }

            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayShowTitleEnabled(false);
        chart = findViewById(R.id.chart1);
        spinner = findViewById(R.id.spinner);
        seekbar = findViewById(R.id.seekBar);
        fromToTV = findViewById(R.id.fromToTV);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                if (intervals == null)
                    return;
                currentInterval = position;
                long end,start;
                end = intervals.get(currentInterval).endTime;
                start = end - (1000L * 60L * TIME_WINDOW);
                if (start < intervals.get(position).startTime)
                    start = intervals.get(position).startTime;
                startTime = start;

                // calculate the length in minutes of the interval
                long length = intervals.get(position).endTime - intervals.get(position).startTime;
                length = length / 1000 / 60;
                // if TIME_WINDOW cover all the interval disable the seek bar
                if (length <= TIME_WINDOW)
                    seekbar.setEnabled(false);
                else {
                    // the seek bar moves the TIME_WINDOW by TIME_STEP minutes
                    // set the seekbar max value according of the calculated length
                    length -= TIME_WINDOW;
                    seekbar.setEnabled(true);
                    seekbar.setMax((int)((length/TIME_STEP)+(length%TIME_STEP>0?1:0)));
                }
                seekbar.setProgress(0);
                updateSeekbarLabel(0);
                startQuery((int)(start/1000L/60L), (int)(end/1000L/60L));
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        seekbar.setProgress(0);
        seekbar.setEnabled(false);
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
           @Override
           public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
               updateSeekbarLabel(progress);
           }

           @Override
           public void onStartTrackingTouch(SeekBar seekBar) {
           }

           @Override
           public void onStopTrackingTouch(SeekBar seekBar) {
               Log.d(TAG,"onStopTrackingTouch");
               // TIME_WINDOW moved: start an new query
               SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault());
               long progress = seekBar.getProgress();

               Log.d(TAG,"New offset="+progress*TIME_STEP+" minutes");
               Log.d(TAG,"Current startTime="+sdf.format(new Date(startTime)));
               long end,start;
               if (progress == seekBar.getMax()) {
                   start = intervals.get(currentInterval).startTime;
                   end = start + (TIME_WINDOW*60L*1000L);
               } else if (progress == 0){
                   end = intervals.get(currentInterval).endTime;
                   start = end - (TIME_WINDOW*60L*1000L);
               } else {
                   end = intervals.get(currentInterval).endTime - (progress * 1000 * 60 * TIME_STEP);
                   start = end - (1000L * 60L * TIME_WINDOW);
               }
               startTime = start;
               Log.d(TAG,"New startTime="+sdf.format(new Date(startTime)));
               startQuery((int)(start/1000L/60L), (int)(end/1000L/60L));
           }
        });

        tfRegular = Typeface.createFromAsset(getAssets(), "OpenSans-Regular.ttf");
        tfLight = Typeface.createFromAsset(getAssets(), "OpenSans-Light.ttf");

        // Init timezone offset in minutes
        TimeZone tz = Calendar.getInstance().getTimeZone();
        tzOffset = tz.getOffset(System.currentTimeMillis())/1000/60;

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

        // create empty Data
        LineData data = new LineData();
        data.setHighlightEnabled(false);
        chart.setData(data);

        // get the legend (only possible after setting data)
        Legend l = chart.getLegend();
        l.setForm(Legend.LegendForm.LINE);
        l.setTypeface(tfLight);
        l.setTextSize(14f);
        l.setTextColor(Color.BLACK);
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setTypeface(tfLight);
        xAxis.setTextSize(14f);
        xAxis.setTextColor(Color.BLACK);
        xAxis.setDrawGridLines(true);
        xAxis.setDrawAxisLine(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawLabels(true);
        xAxis.setGranularity(.25f);

        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf(value);
            }
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                // X Axis: minutes
                // startTime:  milliseconds from January 1, 1970 UTC
                // tzOffset:  Time Zone offset in minutes
                long l = (long)(value)+(startTime/1000L/60L)+tzOffset;
                int minutes = (int) (l % 60L);
                int hours   = (int) ((l / 60L) % 24L);
                if ((chart.getXAxis().mAxisMaximum / chart.getViewPortHandler().getScaleX()) < 1.3333) {
                    int seconds = (int)(value * 60F) % 60;
                    return String.format(Locale.ITALY,"%02d:%02d:%02d",hours,minutes,seconds);
                }
                return String.format(Locale.ITALY,"%02d:%02d",hours,minutes);
            }
        });

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTypeface(tfLight);
        leftAxis.setTextColor(Color.BLUE);
        leftAxis.setTextSize(14f);
        leftAxis.setAxisMaximum(100f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(false);
        leftAxis.setDrawZeroLine(false);
        leftAxis.setGranularityEnabled(false);

        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setTypeface(tfLight);
        rightAxis.setTextColor(Color.RED);
        rightAxis.setTextSize(14f);
        rightAxis.setAxisMaximum(100f);
        rightAxis.setAxisMinimum(0f);
        rightAxis.setDrawGridLines(false);
        rightAxis.setDrawZeroLine(false);
        rightAxis.setGranularityEnabled(false);
        rightAxis.setEnabled(false);

        chart.setScaleMinima(1f,1f);

        logManager.queryLogIntervals();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MyApp.getLogManager().setListener(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chart, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_edit) {
            showDataSelectionDialog();
            return true;
        }
        return true;
    }

    private void updateSeekbarLabel(int progress) {
        if (!seekbar.isEnabled()) {
            fromToTV.setText("");
            return;
        }

        long end,start;
        if (progress == seekbar.getMax()) {
            start = intervals.get(currentInterval).startTime/1000L/60L + tzOffset;
            end = start + TIME_WINDOW;
        } else if (progress == 0){
            end = intervals.get(currentInterval).endTime/1000L/60L + tzOffset;
            start = end - TIME_WINDOW;
        } else {
            end = intervals.get(currentInterval).endTime/1000L/60L + tzOffset - progress*TIME_STEP;
            start = end - TIME_WINDOW;
        }
        int minutesStart = (int) (start % 60L);
        int hoursStart   = (int) ((start / 60L) % 24L);
        int minutesEnd = (int) (end % 60L);
        int hoursEnd   = (int) ((end / 60L) % 24L);
        fromToTV.setText(String.format(Locale.ITALY,"%02d:%02d  --  %02d:%02d",
                hoursStart, minutesStart, hoursEnd, minutesEnd));
    }

    // Avoid to have multiple query running on the same time.
    // when a query is started, the interval spinner and the seekbar must be disabled
    private boolean seekbarEnabled;
    void startQuery(int start, int end) {
        seekbarEnabled = seekbar.isEnabled();
        seekbar.setEnabled(false);
        spinner.setEnabled(false);
        logManager.queryLogData(start, end);
    }

    // Re-enable interval spinner and seekbar at the end of the query
    void endQuery() {
        runOnUiThread(() -> {
            seekbar.setEnabled(seekbarEnabled);
            spinner.setEnabled(true);
        });
    }

    // create and initialize a new LineDataSet
    private LineDataSet newLineDataSet(List<Entry> values, String label, int color) {
        LineDataSet set = new LineDataSet(values, label);
        set.setColor(color);
        set.setLineWidth(2f);
        set.setFillAlpha(65);
        set.setFillColor(color);
        set.setDrawValues(false);
        set.setDrawCircles(false);
        set.setDrawFilled(false);
        set.setHighlightEnabled(false);
        set.setDrawHighlightIndicators(false);
        set.setValues(values);
        return set;
    }

    // Fill the data of a LineDataSet and update the relative YAxis maximum value
    private ArrayList<Entry> fillData(DataType dataType, YAxis yAxis, float maxY) {
        ArrayList<Entry> values = new ArrayList<>();
        float prevx = -1;
        for (int i = 0; i < statusData.size(); i++) {
            float x = (float) ((statusData.get(i).time - startTime) / 1000) / 60f;
            if ((x - prevx) > 0.2f) {
                values.add(new Entry(prevx + 1 / 60f, 0));
                values.add(new Entry(x - 1 / 60f, 0));
            }

            prevx = x;
            float y = 0;
            switch (dataType) {
                case level:
                    y = statusData.get(i).status.assistLevel;
                    break;
                case speed:
                    y = statusData.get(i).status.speed;
                    break;
                case cadence:
                    y = statusData.get(i).status.cadence;
                    break;
                case mTemp:
                    y = statusData.get(i).status.motorTemperature;
                    break;
                case pPower:
                    y = statusData.get(i).status.pPower;
                    break;
                case mPower:
                    y = statusData.get(i).status.volts * statusData.get(i).status.amperes;
                    break;
                case volt:
                    y = statusData.get(i).status.volts;
                    break;
                case current:
                    y = statusData.get(i).status.amperes;
                    break;
                case energy:
                    y = statusData.get(i).status.wattHour;
                    break;
                case dCycle:
                    y = statusData.get(i).status.dutyCycle;
                    break;
                case erps:
                    y = statusData.get(i).status.motorERPS;
                    break;
                case focAngle:
                    y = statusData.get(i).status.focAngle;
                    break;
                case pTorque:
                    y = statusData.get(i).status.pTorque;
                    break;
                case cTemp:
                    y = statusData.get(i).status.pcbTemperature;
                    break;
                case fwHallOffset:
                    y = statusData.get(i).status.fwOffset;
                    break;
                case tSmoothPct:
                    y = statusData.get(i).status.torqueSmoothPct;
                    break;
                case torqueAvg:
                    y = statusData.get(i).status.torqueSmoothAvg;
                    break;
                case torqueMin:
                    y = statusData.get(i).status.torqueSmoothMin;
                    break;
                case torqueMax:
                    y = statusData.get(i).status.torqueSmoothMax;
                    break;
                case esp32FromControllerErr:
                    y = statusData.get(i).status.esp32FromControllerReceiveError? 1:0;
                    break;
                case esp32FromLCDErr:
                    y = statusData.get(i).status.esp32FromLDCReceiveError? 1:0;
                    break;
                case controllerFromESP32Err:
                    y = statusData.get(i).status.controllerFromESP32ReceiveError? 1:0;
                    break;
            }
            //float y = (float) (40 + 10 * Math.sin(x));
            if (y > maxY)
                maxY = y;
            values.add(new Entry(x, y));
        }

        if ((maxY * 0.1f) < 1f)
            maxY += 1f;
        else
            maxY *= 1.1f;
        yAxis.setAxisMaximum(maxY);

        return values;
    }

    // update a line dataset
    private float updateDataSet(DataType dataType, int index, YAxis.AxisDependency axisDependency, int color, boolean resetYAxis) {
        YAxis yAxis;
        LineDataSet set;

        if (axisDependency == YAxis.AxisDependency.LEFT)
            yAxis = chart.getAxisLeft();
        else
            yAxis = chart.getAxisRight();
        yAxis.setEnabled(true);

        float prevMax = resetYAxis? 0: yAxis.getAxisMaximum();
        ArrayList<Entry> values = fillData(dataType, yAxis, prevMax);
        if (index >= chart.getData().getDataSetCount()) {
            LineData ld = chart.getData();
            set = newLineDataSet( values, dataType.getName(), color);
            set.setAxisDependency(axisDependency);
            ld.addDataSet(set);
        } else {
            set = (LineDataSet) chart.getData().getDataSetByIndex(0);
            set.setValues(values);
        }
        return values.get(values.size()-1).getX();
    }

    // Redraw the graphs.
    // selected data types are in selectedItemsList
    // Must be synchronized because could be called from the Runnable started by the Data selection Dialog or
    // by the LogManager Handler Thread (Query result)
    private synchronized void drawData() {
        Log.d(TAG, "X Scale = " + chart.getViewPortHandler().getScaleX());
        Log.d(TAG, "Y Scale = " + chart.getViewPortHandler().getScaleY());
        //Log.d(TAG, "X mAxisMinimum = " + chart.getXAxis().mAxisMinimum);
        //Log.d(TAG, "X mAxisMaximum = " + chart.getXAxis().mAxisMaximum);
        //Log.d(TAG, "X mAxisRange = " + chart.getXAxis().mAxisRange);
        final Set<DataType> leftAxis = EnumSet.noneOf(DataType.class);
        final Set<DataType> rightAxis = EnumSet.noneOf(DataType.class);

        chart.getData().clearValues();
        chart.getAxisRight().setEnabled(false);

        float maxX = 0;
        float val;

        int i = 0;
        for (DataType data : selectedItemsList) {
            if (data.compatible(leftAxis)) {
                int color = 0xFF0000FF; // Color.Blue
                color |= ((0x50*leftAxis.size()) & 0xff) << 8;
                val = updateDataSet(data, i, YAxis.AxisDependency.LEFT, color, leftAxis.isEmpty());
                leftAxis.add(data);
            } else {
                int color = 0xFFFF0000; // Color.Red
                color |= ((0x50*rightAxis.size()) & 0xff) << 8;
                val = updateDataSet(data, i, YAxis.AxisDependency.RIGHT, color, rightAxis.isEmpty());
                rightAxis.add(data);
            }
            maxX = Math.max(maxX, val);
            i++;
        }

        if (maxX < 2)
            maxX = 2f;
        chart.getXAxis().setAxisMinimum(0f);
        chart.getXAxis().setAxisMaximum(maxX);
        chart.getViewPortHandler().setMaximumScaleY(4f);
        chart.getViewPortHandler().setMaximumScaleX(maxX*2);
        if (selectedItemsList.size() > 3)
            chart.getLegend().setWordWrapEnabled(true);
        chart.getData().notifyDataChanged();
        chart.notifyDataSetChanged();
        chart.postInvalidate();
    }

    // Callabck called by the LogManager Class at the end of the queryLogIntervals
    // The callback is called by the LogManager Handler Thread (not the Main Thread!)
    @Override
    public void logIntervalsResult(List<LogManager.TimeInterval> intervals) {
        if (intervals.size() == 0) {
            Log.d(TAG, "logIntervalsResult - no intervals found.");
            return;
        }
        this.intervals = intervals;

        SimpleDateFormat sdf2 = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        List<String> spinnerArray =  new ArrayList<>();
        for (int i = 0; i < intervals.size(); i++) {
            String s = String.format(Locale.ITALY,"%s -- %s",
                    sdf2.format(new Date(intervals.get(i).startTime)),
                    sdf2.format(new Date(intervals.get(i).endTime)));
            spinnerArray.add(s);
            Log.d(TAG, s);
        }
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, spinnerArray);

        runOnUiThread(() -> {
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
                spinner.setSelection(0);
            });
    }

    // Callabck called by the LogManager Class at the end of the queryLogData
    // The callback is called by the LogManager Handler Thread (not the Main Thread!)
    @Override
    public void logDataResult(List<LogManager.LogStatusEntry> statusList) {
        if (statusList.size() == 0) {
            Log.d(TAG, "logDataResult - no data found. Num status records=" + statusList.size());
            return;
        }

        synchronized (this) {
            statusData = statusList;
        }

        /*
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault());
        Log.d(TAG, "logDataResult Status: startTTime=" + sdf.format(new Date(statusData.get(0).time)) +
                " endTime=" + sdf.format(new Date(statusData.get(statusData.size()-1).time)));
        Log.d(TAG, "logDataResult Debug: - startTTime=" + sdf.format(new Date(debugData.get(0).time)) +
                " endTime=" + sdf.format(new Date(debugData.get(debugData.size()-1).time)));
        */
        drawData();
        endQuery();
    }

    public static class DataItem {
        private final DataType type;
        private final String name;
        private Boolean checked;
        private Boolean enabled;

        DataItem(DataType type, Boolean isChecked, Boolean isEnabled) {
            this.type = type;
            this.name = type.getName();
            this.checked = isChecked;
            this.enabled = isEnabled;
        }
    }

    private class DataItemAdapter extends BaseAdapter {
        private final DataItem[] mData;
        private final Context mContext;
        private final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        private final int cbId;

        DataItemAdapter(DataItem[] items, Context context) {
            this.mData = items;
            this.mContext = context;
            // generate CheckBox view id
            cbId = View.generateViewId();
            // sel left margin to 20dp
            params.leftMargin = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, mContext.getResources().getDisplayMetrics());
        }

        @Override
        public int getCount() {
            return mData.length;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = new LinearLayout(mContext);
                convertView.setLayoutParams(params);
                CheckBox cb = new CheckBox(mContext);
                cb.setTextSize(TypedValue.COMPLEX_UNIT_SP,18f);
                cb.setOnClickListener(v -> {
                    mData[(int)(v.getTag())].checked = ((CheckBox)v).isChecked();
                    notifyDataSetChanged();
                });
                cb.setId(cbId);
                ((LinearLayout)convertView).addView(cb, params);
            }
            CheckBox cb = convertView.findViewById(cbId);
            cb.setText(mData[position].name);
            cb.setTag(position);
            cb.setChecked(mData[position].checked);
            cb.setEnabled(mData[position].enabled);
            return convertView;
        }

        @Override
        public void notifyDataSetChanged() {
            Set<DataType> set1 = EnumSet.noneOf(DataType.class);
            Set<DataType> set2 = EnumSet.noneOf(DataType.class);
            int numDataSets = 1;
            for (DataItem item : dialogItemList) {
                if (item.checked) {
                    if (item.type.compatible(set1))
                        set1.add(item.type);
                    else {
                        set2.add(item.type);
                        numDataSets = 2;
                        break;
                    }
                }
            }

            set1.addAll(set2);
            for (DataItem item : dialogItemList) {
                if ((numDataSets < 2) || item.checked) {
                    item.enabled = true;
                } else {
                    item.enabled = item.type.compatible(set1);
                }
            }
            dataDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(!set1.isEmpty() && (statusData != null));
            super.notifyDataSetChanged();
        }
    }

    private void showDataSelectionDialog() {
        if (dataDialog == null) {
            mAdapter = new DataItemAdapter(dialogItemList, this);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(false);
            builder.setTitle(getString(R.string.data_select));
            builder.setAdapter(mAdapter, null);
            builder.setPositiveButton(getString(R.string.ok), (dialogInterface, which) -> new Thread(() -> {
                synchronized (ChartActivity.this) {
                    selectedItemsList.clear();
                    for (DataItem item : dialogItemList)
                        if (item.checked)
                            selectedItemsList.add(item.type);
                    drawData();
                }
            }).start());
            builder.setNegativeButton(getString(R.string.cancel), null);
            dataDialog = builder.create();
            ListView listView = dataDialog.getListView();
            listView.setDivider(new ColorDrawable(Color.GRAY));
            listView.setDividerHeight(2);
        }
        for (DataItem item: dialogItemList)
            item.checked = selectedItemsList.contains(item.type);
        dataDialog.show();
        mAdapter.notifyDataSetChanged();
    }
}