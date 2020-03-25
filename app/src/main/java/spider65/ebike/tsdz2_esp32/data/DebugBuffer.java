package spider65.ebike.tsdz2_esp32.data;

import androidx.annotation.NonNull;

import static spider65.ebike.tsdz2_esp32.TSDZConst.DEBUG_ADV_SIZE;

public class DebugBuffer {

    private DebugBuffer next;
    public byte[] data = new byte[DEBUG_ADV_SIZE];

    private final static Object mLock = new Object();
    private static DebugBuffer mPool;

    public static DebugBuffer obtain() {
        synchronized (mLock) {
            if (mPool != null) {
                DebugBuffer res = mPool;
                mPool = res.next;
                return res;
            }
            return new DebugBuffer();
        }
    }

    public static void recycle(@NonNull DebugBuffer buffer) {
        synchronized (mLock) {
            buffer.next = mPool;
            mPool = buffer;
        }
    }
}
