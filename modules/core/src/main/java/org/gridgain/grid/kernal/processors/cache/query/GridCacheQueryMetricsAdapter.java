/* 
 Copyright (C) GridGain Systems. All Rights Reserved.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.query;

import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.util.typedef.internal.*;

import java.io.*;

/**
 * Adapter for {@link GridCacheQueryMetrics}.
 */
public class GridCacheQueryMetricsAdapter implements GridCacheQueryMetrics, Externalizable {
    /** */
    private static final long serialVersionUID = 0L;

    /** Minimum time of execution. */
    private volatile long minTime;

    /** Maximum time of execution. */
    private volatile long maxTime;

    /** Average time of execution. */
    private volatile double avgTime;

    /** Number of hits. */
    private volatile int execs;

    /** Number of fails. */
    private volatile int fails;

    /** Whether query was executed at least once. */
    private boolean executed;

    /** Mutex. */
    private final Object mux = new Object();

    /** {@inheritDoc} */
    @Override public long minimumTime() {
        return minTime;
    }

    /** {@inheritDoc} */
    @Override public long maximumTime() {
        return maxTime;
    }

    /** {@inheritDoc} */
    @Override public double averageTime() {
        return avgTime;
    }

    /** {@inheritDoc} */
    @Override public int executions() {
        return execs;
    }

    /** {@inheritDoc} */
    @Override public int fails() {
        return fails;
    }

    /**
     * Callback for query execution.
     *
     * @param duration Duration of queue execution.
     * @param fail {@code True} query executed unsuccessfully {@code false} otherwise.
     */
    public void onQueryExecute(long duration, boolean fail) {
        synchronized (mux) {
            if (!executed) {
                minTime = duration;
                maxTime = duration;

                executed = true;
            }
            else {
                if (minTime > duration)
                    minTime = duration;

                if (maxTime < duration)
                    maxTime = duration;
            }

            execs++;

            if (fail)
                fails++;

            avgTime = (avgTime * (execs - 1) + duration) / execs;
        }
    }

    /**
     * Merge with given metrics.
     *
     * @return Copy.
     */
    public GridCacheQueryMetricsAdapter copy() {
        GridCacheQueryMetricsAdapter m = new GridCacheQueryMetricsAdapter();

        synchronized (mux) {
            m.fails = fails;
            m.minTime = minTime;
            m.maxTime = maxTime;
            m.execs = execs;
            m.avgTime = avgTime;
        }

        return m;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(minTime);
        out.writeLong(maxTime);
        out.writeDouble(avgTime);
        out.writeInt(execs);
        out.writeInt(fails);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        minTime = in.readLong();
        maxTime = in.readLong();
        avgTime = in.readDouble();
        execs = in.readInt();
        fails = in.readInt();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheQueryMetricsAdapter.class, this);
    }
}
