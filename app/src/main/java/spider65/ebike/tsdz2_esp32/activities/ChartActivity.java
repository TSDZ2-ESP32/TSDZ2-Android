package spider65.ebike.tsdz2_esp32.activities;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import android.content.Context;
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
    private List<LogManager.LogDebugEntry>  debugData  = null;
    private long startTime = 0;

    private AlertDialog dataDialog = null;
    ArrayList<DataType> selectedItemsList = new ArrayList<>();


    private enum DataType {
        level, speed,cadence,pPower,mPower,current,
        volt,energy,mTemp,cTemp,dCycle,erps,
        foc,pTorque;

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
                    return MyApp.getInstance().getString(R.string.motor_current);
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
                case foc:
                    return MyApp.getInstance().getString(R.string.foc_angle);
                case pTorque:
                    return MyApp.getInstance().getString(R.string.pedal_torque);
            }
            return "";
        }
    }
    private static final Set<DataType> STATUS_DATA_TYPES = new HashSet<>(Arrays.asList(
            DataType.level, DataType.speed, DataType.cadence, DataType.pPower, DataType.mPower,
            DataType.mTemp, DataType.volt, DataType.current, DataType.energy));
    private static final Set<DataType> DEBUG_DATA_TYPES = new HashSet<>(Arrays.asList(
            DataType.dCycle, DataType.erps, DataType.foc, DataType.pTorque, DataType.cTemp));
    private static final Set<DataType> POWER_DATA_TYPES = new HashSet<>(Arrays.asList(
            DataType.mPower, DataType.pPower));
    private static final Set<DataType> TEMPERATURE_DATA_TYPES = new HashSet<>(Arrays.asList(
            DataType.mTemp, DataType.cTemp));


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
                Log.d(TAG,"onItemSelected: position=" + position);
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
//        l.setYOffset(11f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setTypeface(tfLight);
        xAxis.setTextSize(14f);
        xAxis.setTextColor(Color.BLACK);
        xAxis.setDrawGridLines(true);
        xAxis.setDrawAxisLine(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawLabels(true);
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(30f);
        xAxis.setGranularity(1f);

        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf(value);
            }
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                //return String.format(Locale.ITALY,"%.1f",value);

                long l = (long)(value)+(startTime/1000L/60L)+tzOffset;
                int minutes = (int) (l % 60L);
                int hours   = (int) ((l / 60L) % 24L);
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
        rightAxis.setAxisMaximum(100);
        rightAxis.setAxisMinimum(0);
        rightAxis.setDrawGridLines(false);
        rightAxis.setDrawZeroLine(false);
        rightAxis.setGranularityEnabled(false);
        rightAxis.setEnabled(false);

        chart.getViewPortHandler().setMaximumScaleY(1f);
        chart.getViewPortHandler().setMaximumScaleX(6f);

        selectedItemsList.add(DataType.speed);
        selectedItemsList.add(DataType.pPower);
        logManager.queryLogIntervals();
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
        if (item.getItemId() == R.id.menu_edit) {
            showDataSelectionDialog();
            return true;
        }
        /*
        switch (item.getItemId()) {
            case R.id.menu_edit:
                showDataSelectionDialog();
                break;
            case R.id.actionToggleHighlight: {
                if (chart.getData() != null) {
                    chart.getData().setHighlightEnabled(!chart.getData().isHighlightEnabled());
                    chart.invalidate();
                }
                break;
            }
            case R.id.actionToggleFilled: {
                List<ILineDataSet> sets = chart.getData().getDataSets();
                for (ILineDataSet iSet : sets) {
                    LineDataSet set = (LineDataSet) iSet;
                    set.setDrawFilled(!set.isDrawFilledEnabled());
                }
                chart.invalidate();
                break;
            }
            case R.id.actionTogglePinch: {
                chart.setPinchZoom(!chart.isPinchZoomEnabled());
                chart.invalidate();
                break;
            }
            case R.id.actionToggleAutoScaleMinMax: {
                chart.setAutoScaleMinMaxEnabled(!chart.isAutoScaleMinMaxEnabled());
                chart.notifyDataSetChanged();
                break;
            }
        }
        */
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
        if (STATUS_DATA_TYPES.contains(dataType)) {
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
                }
                //float y = (float) (40 + 10 * Math.sin(x));
                if (y > maxY)
                    maxY = y;
                values.add(new Entry(x, y));
            }
        } else if (DEBUG_DATA_TYPES.contains(dataType)) {
            for (int i = 0; i < debugData.size(); i++) {
                float x = (float) ((debugData.get(i).time - startTime) / 1000) / 60f;
                if ((x - prevx) > 0.1f) {
                    values.add(new Entry(prevx + 1 / 60f, 0));
                    values.add(new Entry(x - 1 / 60f, 0));
                }
                prevx = x;
                float y = 0;
                switch (dataType) {
                    case dCycle:
                        y = debugData.get(i).debug.dutyCycle;
                        break;
                    case erps:
                        y = debugData.get(i).debug.motorERPS;
                        break;
                    case foc:
                        y = debugData.get(i).debug.focAngle;
                        break;
                    case pTorque:
                        y = debugData.get(i).debug.pTorque;
                        break;
                    case cTemp:
                        y = debugData.get(i).debug.pcbTemperature;
                        break;
                }
                if (y > maxY)
                    maxY = y;
                values.add(new Entry(x, y));
            }
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
        chart.getData().clearValues();
        chart.getAxisRight().setEnabled(false);

        float maxX, val = 0;
        maxX = updateDataSet(selectedItemsList.get(0), 0, YAxis.AxisDependency.LEFT, Color.BLUE, true);

        if (selectedItemsList.size() > 1) {
            if ((TEMPERATURE_DATA_TYPES.contains(selectedItemsList.get(0)) && TEMPERATURE_DATA_TYPES.contains(selectedItemsList.get(1))) ||
                (POWER_DATA_TYPES.contains(selectedItemsList.get(0)) && POWER_DATA_TYPES.contains(selectedItemsList.get(1)))) {
                val = updateDataSet(selectedItemsList.get(1), 1, YAxis.AxisDependency.LEFT, 0xFF0090FF, false);
            } else {
                val = updateDataSet(selectedItemsList.get(1), 1, YAxis.AxisDependency.RIGHT, Color.RED, true);
            }
        }
        maxX = Math.max(maxX, val);

        if (selectedItemsList.size() > 2) {
            if ((TEMPERATURE_DATA_TYPES.contains(selectedItemsList.get(1)) && TEMPERATURE_DATA_TYPES.contains(selectedItemsList.get(2))) ||
                (POWER_DATA_TYPES.contains(selectedItemsList.get(1)) && POWER_DATA_TYPES.contains(selectedItemsList.get(2)))) {
                val = updateDataSet(selectedItemsList.get(2), 2, YAxis.AxisDependency.RIGHT, 0xFFB84000, false);
            } else {
                val = updateDataSet(selectedItemsList.get(2), 2, YAxis.AxisDependency.RIGHT, Color.RED, true);
            }
        }
        maxX = Math.max(maxX, val);
        if (maxX < 2)
            maxX = 2f;
        chart.getXAxis().setAxisMaximum(maxX);
        chart.getViewPortHandler().setMaximumScaleY(2f);
        chart.getViewPortHandler().setMaximumScaleX(maxX / 2f);
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
    public void logDataResult(List<LogManager.LogStatusEntry> statusList, List<LogManager.LogDebugEntry>  debugList) {
        if (statusList.size() == 0 || debugList.size() == 0) {
            Log.d(TAG, "logDataResult - no data found. Num status records=" + statusList.size()
                    + " Num debug records=" + debugList.size());
            return;
        }

        synchronized (this) {
            statusData = statusList;
            debugData = debugList;
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

    // follows Classes and interfaces used by the Data Type selection dialog
    public interface ChekedItemInterface {
        void onItemChecked(int position, boolean checked, int numChecked);
    }

    public static class DataItem {
        private final DataType type;
        private final String name;
        private Boolean checked;

        DataItem(DataType type, Boolean isChecked) {
            this.type = type;
            this.name = type.getName();
            this.checked = isChecked;
        }
    }

    private static class DataItemAdapter extends BaseAdapter {
        private final DataItem[] mData;
        private Context mContext;
        private ChekedItemInterface listener;
        private int numChecked = 0;
        private int powerCheched = 0;
        private int temperatureChecked = 0;
        private static final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        private final int cbId;

        DataItemAdapter(DataItem[] items, Context context, ChekedItemInterface listener) {
            for (DataItem i: items) {
                if (i.checked) {
                    numChecked++;
                    if (i.type==DataType.mPower || i.type==DataType.pPower)
                        powerCheched++;
                    if (i.type==DataType.mTemp || i.type==DataType.cTemp)
                        temperatureChecked++;
                }
            }
            this.mData = items;
            this.mContext = context;
            this.listener = listener;
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
                cb.setId(cbId);
                ((LinearLayout)convertView).addView(cb, params);
            }
            CheckBox cb = convertView.findViewById(cbId);
            cb.setChecked(mData[position].checked);
            cb.setText(mData[position].name);
            cb.setOnClickListener(v -> {
                if (((CheckBox)v).isChecked()) {
                    numChecked++;
                    if (mData[position].type == DataType.mTemp || mData[position].type == DataType.cTemp)
                        temperatureChecked++;
                    if (mData[position].type == DataType.mPower || mData[position].type == DataType.pPower)
                        powerCheched++;
                } else {
                    numChecked--;
                    if (mData[position].type == DataType.mTemp || mData[position].type == DataType.cTemp)
                        temperatureChecked--;
                    if (mData[position].type == DataType.mPower || mData[position].type == DataType.pPower)
                        powerCheched--;
                }
                super.notifyDataSetChanged();
                //Log.d(TAG,"toggle: " + " numChecked=" + numChecked + " powerCheched="+powerCheched+" temperatureChecked="+temperatureChecked);
                this.listener.onItemChecked(position, ((CheckBox)v).isChecked(), numChecked);
            });

            if ((numChecked < 2) || cb.isChecked() || (numChecked==2 && (powerCheched==2 || temperatureChecked==2))) {
                cb.setEnabled(true);
            } else if (numChecked > 2) {
                cb.setEnabled(false);
            } else if ((powerCheched>0 && (
                            mData[position].type==DataType.mPower ||
                            mData[position].type==DataType.pPower)) ||
                       (temperatureChecked>0 && (
                            mData[position].type==DataType.mTemp ||
                            mData[position].type==DataType.cTemp))) {
                cb.setEnabled(true);
            } else {
                cb.setEnabled(false);
            }
            return convertView;
        }
    }

    private void showDataSelectionDialog() {
        if (dataDialog == null) {
            DataItem[] dialogItemList = new DataItem[] {
                    new DataItem(DataType.level, false),
                    new DataItem(DataType.speed, true),
                    new DataItem(DataType.cadence, false),
                    new DataItem(DataType.pPower, true),
                    new DataItem(DataType.mPower, false),
                    new DataItem(DataType.current, false),
                    new DataItem(DataType.volt, false),
                    new DataItem(DataType.energy, false),
                    new DataItem(DataType.mTemp, false),
                    new DataItem(DataType.cTemp, false),
                    new DataItem(DataType.dCycle, false),
                    new DataItem(DataType.erps, false),
                    new DataItem(DataType.foc,  false),
                    new DataItem(DataType.pTorque, false)
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(false);
            builder.setTitle(getString(R.string.data_select));

            DataItemAdapter mAdapter = new DataItemAdapter(dialogItemList, this, (position, checked, numChecked) -> {
                    dialogItemList[position].checked = (checked);
                    if (numChecked > 0)
                        dataDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    else
                        dataDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

            });
            builder.setAdapter(mAdapter, null);
            builder.setPositiveButton(getString(R.string.ok), (dialogInterface, which) -> new Thread(() -> {
                synchronized (ChartActivity.this) {
                    selectedItemsList.clear();
                    for (DataItem i:dialogItemList) {
                        if (i.checked)
                            selectedItemsList.add(i.type);
                    }
                    drawData();
                }
            }).start());
            builder.setNegativeButton(getString(R.string.cancel), null);
            dataDialog = builder.create();
            ListView listView = dataDialog.getListView();
            listView.setDivider(new ColorDrawable(Color.GRAY));
            listView.setDividerHeight(2);
        }
        dataDialog.show();
    }
}