package spider65.ebike.tsdz2_esp32;


import androidx.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import android.content.Context;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import spider65.ebike.tsdz2_esp32.data.TSDZ_Debug;
import spider65.ebike.tsdz2_esp32.data.TSDZ_Status;
import spider65.ebike.tsdz2_esp32.fragments.FragmentDebug;
import spider65.ebike.tsdz2_esp32.fragments.FragmentStatus;
import spider65.ebike.tsdz2_esp32.fragments.MyFragmentListener;

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
public class MainPagerAdapter extends FragmentPagerAdapter {
    private static final Fragment[] TAB_FRAGMENTS = new Fragment[2];
    private final Context mContext;

    MainPagerAdapter(Context context, FragmentManager fm, TSDZ_Status status, TSDZ_Debug debug) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        TAB_FRAGMENTS[0] = FragmentStatus.newInstance(status);
        TAB_FRAGMENTS[1] = FragmentDebug.newInstance(debug);
        mContext = context;
    }

    @NotNull
    @Override
    public Fragment getItem(int position) {
        // getItem is called to instantiate the fragment for the given page.
        return TAB_FRAGMENTS[position];
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        switch (position) {
            case 0:
                return mContext.getResources().getString(R.string.status);
            case 1:
                return mContext.getResources().getString(R.string.debug);
        }
        return null;
    }

    @Override
    public int getCount() {
        return TAB_FRAGMENTS.length;
    }

    MyFragmentListener getMyFragment(int position) {
        return (MyFragmentListener)TAB_FRAGMENTS[position];
    }
}