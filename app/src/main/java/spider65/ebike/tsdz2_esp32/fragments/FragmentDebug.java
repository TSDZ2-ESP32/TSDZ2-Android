package spider65.ebike.tsdz2_esp32.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import spider65.ebike.tsdz2_esp32.R;
import spider65.ebike.tsdz2_esp32.TSDZBTService;
import spider65.ebike.tsdz2_esp32.databinding.FragmentDebugBinding;
import spider65.ebike.tsdz2_esp32.utils.Utils;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Debug;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Arrays;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * to handle interaction events.
 * Use the {@link FragmentDebug#newInstance} factory method to
 * create an instance of this fragment.
 */
public class FragmentDebug extends Fragment implements MainFragment {

    private static final String TAG = "FragmentDebug";

    private OnFragmentInteractionListener mListener;

    IntentFilter mIntentFilter = new IntentFilter();

    private TSDZ_Debug debugData = new TSDZ_Debug();

    private FragmentDebugBinding binding;

    public FragmentDebug() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @return A new instance of fragment FragmentDebug.
     */
    public static FragmentDebug newInstance() {
        return new FragmentDebug();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        mIntentFilter.addAction(TSDZBTService.TSDZ_DEBUG_BROADCAST);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mMessageReceiver);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_debug, container, false);
        binding.setDebug(debugData);
        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
        mListener.onFragmentInteraction(2);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
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
                    //refresh();
                }
            }
        }
    };

    private void refresh() {

    }

    @Override
    public void selected(boolean visibile) {
        // Log.d(TAG, "selected = " + visibile);
        if (visibile)
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mMessageReceiver, mIntentFilter);
        else
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mMessageReceiver);
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    /*
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }
     */
}
