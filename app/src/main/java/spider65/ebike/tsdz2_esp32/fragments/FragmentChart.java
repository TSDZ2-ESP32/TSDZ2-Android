package spider65.ebike.tsdz2_esp32.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.mikephil.charting.charts.LineChart;

import org.jetbrains.annotations.NotNull;

import androidx.fragment.app.Fragment;
import spider65.ebike.tsdz2_esp32.R;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * to handle interaction events.
 * Use the {@link FragmentChart#newInstance} factory method to
 * create an instance of this fragment.
 */
public class FragmentChart extends Fragment implements MyFragmentListener {

    private static final String TAG = "FragmentChart";

    private View view;
    private LineChart chart;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @return A new instance of fragment FragmentDebug.
     */
    public static FragmentChart newInstance() {
        return new FragmentChart();
    }

    private FragmentChart() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.activity_chart, container, false);
        if (view != null) {
            chart = view.findViewById(R.id.chart1);
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
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void refreshView() {
    }
}
