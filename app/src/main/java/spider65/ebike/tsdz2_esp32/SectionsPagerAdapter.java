package spider65.ebike.tsdz2_esp32;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import spider65.ebike.tsdz2_esp32.fragments.FragmentDebug;
import spider65.ebike.tsdz2_esp32.fragments.FragmentStatus;
import spider65.ebike.tsdz2_esp32.fragments.MainFragment;

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
public class SectionsPagerAdapter extends FragmentPagerAdapter {

    private static final Fragment[] TAB_FRAGMENTS = new Fragment[]{FragmentStatus.newInstance(), FragmentDebug.newInstance()};
    private final Context mContext;

    public SectionsPagerAdapter(Context context, FragmentManager fm) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        mContext = context;
    }

    @Override
    public Fragment getItem(int position) {
        // getItem is called to instantiate the fragment for the given page.
        return TAB_FRAGMENTS[position];
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return null;
    }

    @Override
    public int getCount() {
        return TAB_FRAGMENTS.length;
    }

    void selected(int position) {
        for (int i=0; i<TAB_FRAGMENTS.length; i++) {
            if (i == position)
                ((MainFragment)TAB_FRAGMENTS[i]).selected(true);
            else
                ((MainFragment)TAB_FRAGMENTS[i]).selected(false);
        }
    }
}