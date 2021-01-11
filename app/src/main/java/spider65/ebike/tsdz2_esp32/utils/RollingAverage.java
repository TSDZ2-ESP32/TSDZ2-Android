package spider65.ebike.tsdz2_esp32.utils;

public class RollingAverage {

    private final int size;
    private long total = 0;
    private int index = 0;
    private final int[] samples;
    private boolean rollover = false;

    public RollingAverage(int size) {
        this.size = size;
        samples = new int[size];
        for (int i = 0; i < size; i++) samples[i] = 0;
    }

    public void add(int x) {
        total -= samples[index];
        samples[index] = x;
        total += x;
        index++;
        if (index == size) {
            index = 0; // cheaper than modulus
            rollover = true;
        }
    }

    public double getAverage() {
        if (rollover)
            return (double)total/size;
        else
            return (double)total/index;
    }

    public int getIndex() {
        return index;
    }

    public void reset() {
        total = 0;
        index = 0;
        rollover = false;
        for (int i = 0; i < size; i++) samples[i] = 0;
    }
}
