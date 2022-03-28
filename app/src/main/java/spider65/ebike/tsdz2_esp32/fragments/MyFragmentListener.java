package spider65.ebike.tsdz2_esp32.fragments;

import spider65.ebike.tsdz2_esp32.data.TSDZ_Status;

public interface MyFragmentListener {
    void refreshView(TSDZ_Status newData);
}
