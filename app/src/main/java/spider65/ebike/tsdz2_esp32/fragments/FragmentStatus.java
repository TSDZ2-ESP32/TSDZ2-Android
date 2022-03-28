package spider65.ebike.tsdz2_esp32.fragments;

import android.os.Bundle;

import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Status;
import spider65.ebike.tsdz2_esp32.databinding.FragmentStatusBinding;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.jetbrains.annotations.NotNull;


//public class FragmentStatus extends Fragment implements View.OnLongClickListener, MyFragmentListener {
public class FragmentStatus extends Fragment implements MyFragmentListener {

    private static final String TAG = "FragmentStatus";

    public static class FragmentData {
        public float speed;
        public short cadence;
        public int pPower;
        public float volts;
        public float amperes;
        public float motorTemperature;
        public int wattHour;

        private boolean update(TSDZ_Status newStatus) {
            boolean changed = false;
            if (newStatus.speed != speed) {
                speed = newStatus.speed;
                changed = true;
            }
            if (newStatus.cadence != cadence) {
                cadence = newStatus.cadence;
                changed = true;
            }
            if (newStatus.pPower != pPower) {
                pPower = newStatus.pPower;
                changed = true;
            }
            if (newStatus.volts != volts) {
                volts = newStatus.volts;
                changed = true;
            }
            if (newStatus.amperes != amperes) {
                amperes = newStatus.amperes;
                changed = true;
            }
            if (newStatus.motorTemperature != motorTemperature) {
                motorTemperature = newStatus.motorTemperature;
                changed = true;
            }
            if (newStatus.wattHour != wattHour) {
                wattHour = newStatus.wattHour;
                changed = true;
            }
            return changed;
        }
    }

    private FragmentStatusBinding binding;
    private final FragmentData viewData = new FragmentData();

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment FragmentStatus.
     */
    public static FragmentStatus newInstance(TSDZ_Status status) {
        return new FragmentStatus(status);
    }

    private FragmentStatus(TSDZ_Status tsdz_status) {
        viewData.update(tsdz_status);
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
        Log.d(TAG, "onCreateView");
        // Inflate the layout for this fragment
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_status, container, false);
        binding.setTsdzStatus(viewData);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Data could be changed when fragment was not visible. Refresh the view
        binding.invalidateAll();
    }

    // TODO
    // Visualizzazione grafici
    /*
    @Override
    public boolean onLongClick(View v) {
        switch (v.getId()) {
            case R.id.speedValueTV:
            case R.id.cadenceValueTV:
                break;
        }
        return false;
    }
    */

    @Override
    public void refreshView(TSDZ_Status newStatus) {
        if (viewData.update(newStatus) && isVisible())
            binding.invalidateAll();
    }
}
