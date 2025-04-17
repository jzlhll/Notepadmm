package com.allan.baseparty.handler;

import java.util.Arrays;
/**
* A histogram for positive integers where each bucket is twice the size of the previous one.
*/
public final class ExponentiallyBucketedHistogram {
    private final int[] mData;

    /**
     * Create a new histogram.
     *
     * @param numBuckets The number of buckets. The highest bucket is for all value >=
     *                   2<sup>numBuckets - 1</sup>
     */
    public ExponentiallyBucketedHistogram(int numBuckets) {
        if (numBuckets < 1 || numBuckets > 31) {
            throw new RuntimeException("ExponentiallyBucketedHistogram init error!");
        }
        mData = new int[numBuckets];
    }

    /**
     * Add a new value to the histogram.
     *
     * All values <= 0 are in the first bucket. The last bucket contains all values >=
     * 2<sup>numBuckets - 1</sup>
     *
     * @param value The value to add
     */
    public void add(int value) {
        if (value <= 0) {
            mData[0]++;
        } else {
            mData[Math.min(mData.length - 1, 32 - Integer.numberOfLeadingZeros(value))]++;
        }
    }

    /**
     * Clear all data from the histogram
     */
    public void reset() {
        Arrays.fill(mData, 0);
    }

    /**
     * Write the histogram to the log.
     *
     * @param tag    The tag to use when logging
     * @param prefix A custom prefix that is printed in front of the histogram
     */
    public void log(String tag, CharSequence prefix) {
        StringBuilder builder = new StringBuilder(prefix);
        builder.append('[');

        for (int i = 0; i < mData.length; i++) {
            if (i != 0) {
                builder.append(", ");
            }

            if (i < mData.length - 1) {
                builder.append("<");
                builder.append(1 << i);
            } else {
                builder.append(">=");
                builder.append(1 << (i - 1));
            }

            builder.append(": ");
            builder.append(mData[i]);
        }
        builder.append("]");

        System.out.println(tag + " " + builder);
    }
}
