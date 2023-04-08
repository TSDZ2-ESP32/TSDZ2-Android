package spider65.ebike.tsdz2_esp32;

import androidx.annotation.NonNull;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import androidx.viewpager2.adapter.FragmentStateAdapter;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Status;
import spider65.ebike.tsdz2_esp32.fragments.FragmentDebug;
import spider65.ebike.tsdz2_esp32.fragments.FragmentStatus;
import spider65.ebike.tsdz2_esp32.fragments.MyFragmentListener;


public class MainPagerAdapter extends FragmentStateAdapter {
    private final MyFragmentListener[] fragments = new MyFragmentListener[2];
    private final TSDZ_Status mStatus;

    MainPagerAdapter(FragmentActivity fragmentActivity, TSDZ_Status status) {
        super(fragmentActivity);
        mStatus = status;
    }

    public MyFragmentListener getItem(int position) {
        // getItem is called to instantiate the fragment for the given page.
        return fragments[position];
    }

    @Override
    public int getItemCount() {
        return 2;
    }


    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Fragment f;
        if (position == 0)
            f = FragmentStatus.newInstance(mStatus);
        else
            f = FragmentDebug.newInstance(mStatus);
        fragments[position] = (MyFragmentListener)f;
        return f;
    }
}