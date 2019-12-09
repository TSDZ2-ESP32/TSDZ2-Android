package spider65.ebike.tsdz2_esp32.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import spider65.ebike.tsdz2_esp32.MyApp;
import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.databinding.FragmentDebugBinding;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Debug;
import spider65.ebike.tsdz2_esp32.utils.Utils;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * to handle interaction events.
 * Use the {@link FragmentDebug#newInstance} factory method to
 * create an instance of this fragment.
 */
public class FragmentDebug extends Fragment {

    private static final String TAG = "FragmentDebug";

    private IntentFilter mIntentFilter = new IntentFilter();

    private TSDZ_Debug debugData = new TSDZ_Debug();

    private FragmentDebugBinding binding;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @return A new instance of fragment FragmentDebug.
     */
    public static FragmentDebug newInstance() {
        return new FragmentDebug();
    }


    public FragmentDebug() {
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
        binding.setDebug(debugData);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        LocalBroadcastManager.getInstance(MyApp.getInstance()).registerReceiver(mMessageReceiver, mIntentFilter);
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        LocalBroadcastManager.getInstance(MyApp.getInstance()).unregisterReceiver(mMessageReceiver);
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //Log.d(TAG, "onReceive " + intent.getAction());
            if (TSDZBTService.TSDZ_DEBUG_BROADCAST.equals(intent.getAction())) {
                byte[] debugVal = intent.getByteArrayExtra(TSDZBTService.VALUE_EXTRA);
                //Log.d(TAG, "value = " + Utils.bytesToHex(debugVal));
                if (debugData.data == null || !Arrays.equals(debugData.data, debugVal)) {
                    if (debugData.setData(debugVal))
                        binding.invalidateAll();
                }
            }
        }
    };
}
