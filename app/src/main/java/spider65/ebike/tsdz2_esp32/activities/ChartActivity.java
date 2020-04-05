package spider65.ebike.tsdz2_esp32.activities;

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
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import spider65.ebike.tsdz2_esp32.MyApp;
import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.data.LogManager;


public class ChartActivity extends AppCompatActivity implements LogManager.LogResultListener {

    private static final String TAG = "ChartActivity";

    // x axis shift step in minutes
    private static final long TIME_STEP=15;
    // x axis graph width in minutes
    private static final long TIME_WINDOW=45;

    private LineChart chart;
    private Spinner spinner;
    private SeekBar seekbar;
    private TextView fromToTV;

    protected Typeface tfRegular;
    protected Typeface tfLight;

    private long tzOffset; // Timezone offset in minutes to GMT
    private LogManager logManager;
    private boolean queryRunning = false; // avoid to start a new query if the previous is not ended

    private List<LogManager.TimeInterval> intervals = null;
    private int currentInterval;

    private List<LogManager.LogStatusEntry> statusData = null;
    private List<LogManager.LogDebugEntry>  debugData  = null;
    private long startTime = 0;

    private AlertDialog dataDialog = null;
    ArrayList<DataItem> selectedItemsList = new ArrayList<>();

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
        spinner = findViewById(R.id.spinner);
        seekbar = findViewById(R.id.seekBar);
        fromToTV = findViewById(R.id.fromToTV);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                if (intervals == null)
                    return;
                Log.d(TAG,"onItemSelected: position=" + position);
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
                    seekbar.setProgress(0);
                }

                logManager.queryLogData((int)(start/1000L/60L), (int)(end/1000L/60L));
                queryRunning = true;
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        seekbar.setProgress(0);
        seekbar.setEnabled(false);
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
           @Override
           public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
               // updates the From - To label in the view
               long end,start;
               if (progress == seekBar.getMax()) {
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

           @Override
           public void onStartTrackingTouch(SeekBar seekBar) {
           }

           @Override
           public void onStopTrackingTouch(SeekBar seekBar) {
               Log.d(TAG,"onStopTrackingTouch");
               // TIME_WINDOW moved: start an new query
               if (!queryRunning) {
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
                   logManager.queryLogData((int)(start/1000L/60L), (int)(end/1000L/60L));
                   queryRunning = true;
               }
           }
       });

        tfRegular = Typeface.createFromAsset(getAssets(), "OpenSans-Regular.ttf");
        tfLight = Typeface.createFromAsset(getAssets(), "OpenSans-Light.ttf");

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

        setData();

        // get the legend (only possible after setting data)
        Legend l = chart.getLegend();
        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTypeface(tfLight);
        l.setTextSize(14f);
        l.setTextColor(Color.WHITE);
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);
//        l.setYOffset(11f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setTypeface(tfLight);
        xAxis.setTextSize(14f);
        xAxis.setTextColor(Color.WHITE);
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
        leftAxis.setTextColor(ColorTemplate.getHoloBlue());
        leftAxis.setTextSize(14f);
        leftAxis.setAxisMaximum(100f);
        leftAxis.setAxisMinimum(0f);
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
        return true;
    }

    void setData() {
        ArrayList<Entry> values = new ArrayList<>();
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
        data.setHighlightEnabled(false);
        chart.setData(data);

        logManager.queryLogIntervals();
    }

    private static final float MAXSTEP = 0.1f;
    private void drawData() {
        ArrayList<Entry> values = new ArrayList<>();
        float prevx = -1;

        int n = 0;
        for (DataItem dataItem:  selectedItemsList) {
            n++;
            for (int i = 0; i < statusData.size(); i++) {
                float x = (float) ((statusData.get(i).time - startTime) / 1000) / 60f;
                if ((x - prevx) > 0.1f) {
                    values.add(new Entry(prevx + 1 / 60f, 0));
                    values.add(new Entry(x - 1 / 60f, 0));
                }
                prevx = x;
                float y = (float) (30 + n*10 + 10 * Math.sin(x));
                //float y = statusData.get(i).status.speed;
                values.add(new Entry(x, y));
            }
            if (chart.getData().getDataSetCount() >= (n)) {
                LineDataSet set = (LineDataSet) chart.getData().getDataSetByIndex(n-1);
                set.setValues(values);
            } else {
                LineData ld = chart.getData();
                LineDataSet set = new LineDataSet(values, dataItem.name);
                set.setAxisDependency(YAxis.AxisDependency.RIGHT);
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
                set.setValues(values);
                ld.addDataSet(set);
            }
        }

        float max = (float) ((statusData.get(statusData.size() - 1).time - startTime) / 1000) / 60f;
        if (max < 2)
            max = 2f;
        chart.getXAxis().setAxisMaximum(max);
        chart.getViewPortHandler().setMaximumScaleY(1f);
        chart.getViewPortHandler().setMaximumScaleX(max / 2f);

        synchronized (this) {
            chart.getData().getDataSetCount();
            LineDataSet set = (LineDataSet) chart.getData().getDataSetByIndex(0);
            set.setValues(values);
            chart.getData().notifyDataChanged();
            chart.notifyDataSetChanged();
            chart.postInvalidate();
        }
    }

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

    @Override
    public void logDataResult(List<LogManager.LogStatusEntry> statusList, List<LogManager.LogDebugEntry>  debugList) {
        queryRunning = false;
        if (statusList.size() == 0 || debugList.size() == 0) {
            Log.d(TAG, "logQueryResult - no data found. Num status records=" + statusList.size()
                    + " Num debug records=" + debugList.size());
            return;
        }
        statusData = statusList;
        debugData = debugList;

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault());
        Log.d(TAG, "logQueryResult Status: startTTime=" + sdf.format(new Date(statusData.get(0).time)) +
                " endTime=" + sdf.format(new Date(statusData.get(statusData.size()-1).time)));
        Log.d(TAG, "logQueryResult Debug: - startTTime=" + sdf.format(new Date(debugData.get(0).time)) +
                " endTime=" + sdf.format(new Date(debugData.get(debugData.size()-1).time)));
        drawData();
    }


    private enum DataType {
        level(13), speed(0),cadence(1),pPower(2),mPower(3),current(4),
        volt(5),energy(6),mTemp(7),cTemp(8),dCycle(9),erps(10),
        foc(11),pTorque(12);

        private final int value;
        DataType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public interface ChekedItemInterface {
        void onItemChecked(int position, boolean checked, int numChecked);
    }

    public static class DataItem {
        private final DataType type;
        private final String name;
        private Boolean checked;

        DataItem(DataType type, String name, Boolean isChecked) {
            this.type = type;
            this.name = name;
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
            for (DataItem i: items)
                if (i.checked)
                    numChecked++;
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
                Log.d(TAG,"toggle: " + " numChecked=" + numChecked + " powerCheched="+powerCheched+" temperatureChecked="+temperatureChecked);
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
                    new DataItem(DataType.level, getString(R.string.assistLevel), false),
                    new DataItem(DataType.speed, getString(R.string.speed), true),
                    new DataItem(DataType.cadence, getString(R.string.cadence), false),
                    new DataItem(DataType.pPower, getString(R.string.pedal_power), false),
                    new DataItem(DataType.mPower, getString(R.string.motor_power), false),
                    new DataItem(DataType.current, getString(R.string.motor_current), false),
                    new DataItem(DataType.volt, getString(R.string.voltage), false),
                    new DataItem(DataType.energy, getString(R.string.energy_used), false),
                    new DataItem(DataType.mTemp, getString(R.string.motor_temp), false),
                    new DataItem(DataType.cTemp, getString(R.string.controller_temp), false),
                    new DataItem(DataType.dCycle, getString(R.string.duty_cycle), false),
                    new DataItem(DataType.erps, getString(R.string.motor_erps), false),
                    new DataItem(DataType.foc, getString(R.string.foc_angle), false),
                    new DataItem(DataType.pTorque, getString(R.string.pedal_torque), false)
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
            builder.setPositiveButton(getString(R.string.ok), (dialogInterface, which) -> {
                selectedItemsList.clear();
                for (DataItem i:dialogItemList) {
                    if (i.checked)
                        selectedItemsList.add(i);
                }
            });
            builder.setNegativeButton(getString(R.string.cancel), null);
            dataDialog = builder.create();
            ListView listView = dataDialog.getListView();
            listView.setDivider(new ColorDrawable(Color.GRAY));
            listView.setDividerHeight(2);
        }
        dataDialog.show();
    }
}