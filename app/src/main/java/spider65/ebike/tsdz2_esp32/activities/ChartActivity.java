package spider65.ebike.tsdz2_esp32.activities;


import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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

    private long tzOffset; // Timezone offset in seconds to GMT
    private LogManager logManager;
    private boolean queryRunning = false;

    private List<LogManager.TimeInterval> intervals = null;
    private int currentInterval;

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

                long length = intervals.get(position).endTime - intervals.get(position).startTime;
                length = length / 1000 / 60;
                if (length <= TIME_WINDOW)
                    seekbar.setEnabled(false);
                else {
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
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
           @Override
           public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
               long end,start;
               end = intervals.get(currentInterval).endTime/1000L/60L - (progress * TIME_STEP) + tzOffset;
               if (progress == seekBar.getMax())
                   start = intervals.get(currentInterval).startTime/1000L/60L + tzOffset;
               else
                   start = end - TIME_WINDOW;

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

    private void showDataSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.data_select));

        String[] animals = {"speed", "cadence", "Voltage", "Amperes", "Motor Power", "Human Power", "torque", "level", "Motor Temp.", "PCB Temp."};
        boolean[] checkedItems = {true, false, false, false, false, false, false, false, false, false};
        builder.setMultiChoiceItems(animals, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                Log.d(TAG, "OnMultiChoiceClickListener: which="+which+" isChecked="+isChecked);
                checkedItems[which] = isChecked;
                if (((AlertDialog)dialog).getListView().getCheckedItemCount() > 2) {
                    ((AlertDialog)dialog).getListView().setItemChecked(which, false);
                    checkedItems[which]=false;
                    Toast.makeText(getApplicationContext(),"You can select max 2 values",Toast.LENGTH_SHORT).show();
                }
            }
        });

        // add OK and Cancel buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "PositiveButton onClick: which="+which);
                SparseBooleanArray sba = ((AlertDialog)dialog).getListView().getCheckedItemPositions();
                for (int i=0;i<checkedItems.length;i++) {
                    Log.d(TAG,animals[i]+"="+sba.get(i));
                }
            }
        });
        builder.setNegativeButton("Cancel", null);

        // create and show the alert dialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
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

    private void drawData() {

        ArrayList<Entry> values = new ArrayList<>();
        for (int i=0; i<statusData.size(); i++) {
            float x = (float)((statusData.get(i).time - startTime) / 1000) / 60f;
            float y = (float)(50 + 10*Math.sin(x));
            //float y = statusData.get(i).status.speed;
            values.add(new Entry(x, y));
        }

        float max = (float)((statusData.get(statusData.size()-1).time - startTime) / 1000) / 60f;
        if (max < 2)
            max = 2f;
        chart.getXAxis().setAxisMaximum(max);
        chart.getViewPortHandler().setMaximumScaleY(1f);
        chart.getViewPortHandler().setMaximumScaleX(max/2f);

        synchronized (this) {
            LineDataSet set = (LineDataSet) chart.getData().getDataSetByIndex(0);
            set.setValues(values);
            chart.getData().notifyDataChanged();
            chart.notifyDataSetChanged();
            chart.postInvalidate();
        }
    }


    private List<LogManager.LogStatusEntry> statusData = null;
    private List<LogManager.LogDebugEntry>  debugData  = null;
    private long startTime = 0;

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

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
                spinner.setSelection(0);
            }
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
}