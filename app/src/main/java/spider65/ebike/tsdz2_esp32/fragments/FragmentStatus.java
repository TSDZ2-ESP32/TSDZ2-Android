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


public class FragmentStatus extends Fragment implements View.OnLongClickListener, MyFragmentListener {

    private static final String TAG = "FragmentStatus";

    //private IntentFilter mIntentFilter = new IntentFilter();

    private TSDZ_Status tsdz_status;

    private FragmentStatusBinding binding;

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
        this.tsdz_status = tsdz_status;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        //mIntentFilter.addAction(TSDZBTService.TSDZ_STATUS_BROADCAST);
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        // Inflate the layout for this fragment
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_status, container, false);
        binding.setTsdzStatus(tsdz_status);
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
    @Override
    public boolean onLongClick(View v) {
        switch (v.getId()) {
            case R.id.speedValueTV:
            case R.id.cadenceValueTV:
                break;
        }
        return false;
    }

    @Override
    public void refreshView() {
        binding.invalidateAll();
    }
}
