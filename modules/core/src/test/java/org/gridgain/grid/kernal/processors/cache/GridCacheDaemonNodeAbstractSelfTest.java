/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.spi.discovery.tcp.*;
import org.gridgain.grid.spi.discovery.tcp.ipfinder.*;
import org.gridgain.grid.spi.discovery.tcp.ipfinder.vm.*;
import org.gridgain.testframework.junits.common.*;

import java.util.*;

import static org.gridgain.grid.cache.GridCacheAtomicityMode.*;
import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.GridCacheDistributionMode.*;
import static org.gridgain.grid.cache.GridCacheTxConcurrency.*;
import static org.gridgain.grid.cache.GridCacheTxIsolation.*;

/**
 * Test cache operations with daemon node.
 */
public abstract class GridCacheDaemonNodeAbstractSelfTest extends GridCommonAbstractTest {
    /** */
    private static GridTcpDiscoveryIpFinder ipFinder = new GridTcpDiscoveryVmIpFinder(true);

    /** Daemon flag. */
    protected boolean daemon;

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        daemon = false;
    }

    /** {@inheritDoc} */
    @Override protected GridConfiguration getConfiguration(String gridName) throws Exception {
        GridConfiguration c = super.getConfiguration(gridName);

        c.setDaemon(daemon);

        GridTcpDiscoverySpi disco = new GridTcpDiscoverySpi();

        disco.setIpFinder(ipFinder);

        c.setDiscoverySpi(disco);

        c.setRestEnabled(false);

        GridCacheConfiguration cc = defaultCacheConfiguration();

        cc.setCacheMode(cacheMode());
        cc.setAtomicityMode(TRANSACTIONAL);
        cc.setDistributionMode(NEAR_PARTITIONED);

        c.setCacheConfiguration(cc);

        return c;
    }

    /**
     * Returns cache mode specific for test.
     *
     * @return Cache configuration.
     */
    protected abstract GridCacheMode cacheMode();

    /**
     * @throws Exception If failed.
     */
    public void testImplicit() throws Exception {
        try {
            startGridsMultiThreaded(3);

            daemon = true;

            startGrid(4);

            GridCache<Integer, Integer> cache = grid(0).cache(null);

            for (int i = 0; i < 30; i++)
                cache.put(i, i);

            Map<Integer, Integer> batch = new HashMap<>();

            for (int i = 30; i < 60; i++)
                batch.put(i, i);

            cache.putAll(batch);

            for (int i = 0; i < 60; i++)
                assertEquals(i, (int)cache.get(i));
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testExplicit() throws Exception {
        try {
            startGridsMultiThreaded(3);

            daemon = true;

            startGrid(4);

            GridCache<Integer, Integer> cache = grid(0).cache(null);

            for (int i = 0; i < 30; i++) {
                GridCacheTx tx = cache.txStart(PESSIMISTIC, REPEATABLE_READ);

                try {
                    cache.put(i, i);

                    tx.commit();
                }
                finally {
                    tx.close();
                }
            }

            Map<Integer, Integer> batch = new HashMap<>();

            for (int i = 30; i < 60; i++)
                batch.put(i, i);

            GridCacheTx tx = cache.txStart(PESSIMISTIC, REPEATABLE_READ);

            try {
                cache.putAll(batch);
                tx.commit();
            }
            finally {
                tx.close();
            }

            for (int i = 0; i < 60; i++)
                assertEquals(i, (int)cache.get(i));
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     * Test mapKeyToNode() method for normal and daemon nodes.
     *
     * @throws Exception If failed.
     */
    public void testMapKeyToNode() throws Exception {
        try {
            // Start normal nodes.
            Grid g1 = startGridsMultiThreaded(3);

            // Start daemon node.
            daemon = true;

            Grid g2 = startGrid(4);

            for (long i = 0; i < Integer.MAX_VALUE; i = (i << 1) + 1) {
                GridNode n;

                // Call mapKeyToNode for normal node.
                assertNotNull(n = g1.mapKeyToNode(null, i));

                // Call mapKeyToNode for daemon node.
                if (cacheMode() == PARTITIONED)
                    assertEquals(n, g2.mapKeyToNode(null, i));
                else
                    assertNotNull(g2.mapKeyToNode(null, i));
            }
        }
        finally {
            stopAllGrids();
        }
    }
}
