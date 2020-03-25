package spider65.ebike.tsdz2_esp32.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.jetbrains.annotations.NotNull;

import androidx.fragment.app.Fragment;
import spider65.ebike.tsdz2_esp32.R;


public class FragmentChartSetup extends Fragment implements MyFragmentListener {

    private static final String TAG = "FragmentChartSetup";

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment FragmentStatus.
     */
    public static FragmentChartSetup newInstance() {
        return new FragmentChartSetup();
    }

    private FragmentChartSetup() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_chart_setup, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void refreshView() {
    }
}
