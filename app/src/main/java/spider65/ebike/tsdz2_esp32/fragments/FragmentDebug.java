package spider65.ebike.tsdz2_esp32.fragments;

import android.content.IntentFilter;
import android.os.Bundle;

import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.databinding.FragmentDebugBinding;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Debug;

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

    private IntentFilter mIntentFilter = new IntentFilter();

    private TSDZ_Debug debug;

    private FragmentDebugBinding binding;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @return A new instance of fragment FragmentDebug.
     */
    public static FragmentDebug newInstance(TSDZ_Debug debug) {
        return new FragmentDebug(debug);
    }

    private FragmentDebug(TSDZ_Debug debug) {
        this.debug = debug;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        mIntentFilter.addAction(TSDZBTService.TSDZ_DEBUG_BROADCAST);
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        // Inflate the layout for this fragment
        binding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_debug, container, false);
        binding.setDebug(debug);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Data could be changed when fragment was not visible. Refresh the view
        binding.invalidateAll();
    }

    @Override
    public void refreshView() {
        binding.invalidateAll();
    }
}
