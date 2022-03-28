package spider65.ebike.tsdz2_esp32.fragments;

import android.os.Bundle;

import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Status;
import spider65.ebike.tsdz2_esp32.databinding.FragmentDebugBinding;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.jetbrains.annotations.NotNull;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * to handle interaction events.
 * Use the {@link FragmentDebug#newInstance} factory method to
 * create an instance of this fragment.
 */
public class FragmentDebug extends Fragment implements MyFragmentListener {

    private static final String TAG = "FragmentDebug";

    public static class FragmentData {
        public short dutyCycle;
        public int motorERPS;
        public short focAngle;
        public int torqueADCValue;
        public float pTorque;
        public short fwOffset;
        public short torqueSmoothPct;
        public float pcbTemperature;

        private boolean update(TSDZ_Status newStatus) {
            boolean changed = false;
            if (newStatus.dutyCycle != dutyCycle) {
                dutyCycle = newStatus.dutyCycle;
                changed = true;
            }
            if (newStatus.motorERPS != motorERPS) {
                motorERPS = newStatus.motorERPS;
                changed = true;
            }
            if (newStatus.focAngle != focAngle) {
                focAngle = newStatus.focAngle;
                changed = true;
            }
            if (newStatus.torqueADCValue != torqueADCValue) {
                torqueADCValue = newStatus.torqueADCValue;
                changed = true;
            }
            if (newStatus.pTorque != pTorque) {
                pTorque = newStatus.pTorque;
                changed = true;
            }
            if (newStatus.fwOffset != fwOffset) {
                fwOffset = newStatus.fwOffset;
                changed = true;
            }
            if (newStatus.torqueSmoothPct != torqueSmoothPct) {
                torqueSmoothPct = newStatus.torqueSmoothPct;
                changed = true;
            }
            if (newStatus.pcbTemperature != pcbTemperature) {
                pcbTemperature = newStatus.pcbTemperature;
                changed = true;
            }
            return changed;
        }
    }

    private FragmentDebugBinding binding;
    private final FragmentDebug.FragmentData viewData = new FragmentDebug.FragmentData();

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @return A new instance of fragment FragmentDebug.
     */
    public static FragmentDebug newInstance(TSDZ_Status status) {
        return new FragmentDebug(status);
    }

    private FragmentDebug(TSDZ_Status tsdz_status) {
        viewData.update(tsdz_status);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        //mIntentFilter.addAction(TSDZBTService.TSDZ_DEBUG_BROADCAST);
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        // Inflate the layout for this fragment
        binding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_debug, container, false);
        binding.setTsdzDebug(viewData);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Data could be changed when fragment was not visible. Refresh the view
        binding.invalidateAll();
    }

    @Override
    public void refreshView(TSDZ_Status newStatus) {
        if (viewData.update(newStatus) && isVisible())
            binding.invalidateAll();
    }
}
