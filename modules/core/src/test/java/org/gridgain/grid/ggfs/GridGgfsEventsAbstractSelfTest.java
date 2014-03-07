/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.ggfs;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.affinity.consistenthash.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.processors.ggfs.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.discovery.tcp.*;
import org.gridgain.grid.spi.discovery.tcp.ipfinder.vm.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.testframework.*;
import org.gridgain.testframework.junits.common.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.events.GridEventType.*;
import static org.gridgain.grid.cache.GridCacheAtomicityMode.*;
import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.GridCacheDistributionMode.*;
import static org.gridgain.grid.cache.GridCacheWriteSynchronizationMode.*;
import static org.gridgain.testframework.GridTestUtils.*;

/**
 * Tests events, generated by {@link GridGgfs} implementation.
 */
public abstract class GridGgfsEventsAbstractSelfTest extends GridCommonAbstractTest {
    /** GGFS. */
    private static GridGgfsImpl ggfs;

    /** Event listener. */
    private GridPredicate<GridEvent> lsnr;

    /**
     * Gets cache configuration.
     *
     * @param gridName Grid name.
     * @return Cache configuration.
     */
    @SuppressWarnings("deprecation")
    protected GridCacheConfiguration[] getCacheConfiguration(String gridName) {
        GridCacheConfiguration cacheCfg = defaultCacheConfiguration();

        cacheCfg.setName("dataCache");
        cacheCfg.setCacheMode(PARTITIONED);
        cacheCfg.setDistributionMode(PARTITIONED_ONLY);
        cacheCfg.setWriteSynchronizationMode(FULL_SYNC);
        cacheCfg.setEvictionPolicy(null);
        cacheCfg.setAffinityMapper(new GridGgfsGroupDataBlocksKeyMapper(128));
        cacheCfg.setBackups(0);
        cacheCfg.setQueryIndexEnabled(false);
        cacheCfg.setAtomicityMode(TRANSACTIONAL);

        GridCacheConfiguration metaCacheCfg = defaultCacheConfiguration();

        metaCacheCfg.setName("metaCache");
        metaCacheCfg.setCacheMode(REPLICATED);
        metaCacheCfg.setWriteSynchronizationMode(FULL_SYNC);
        metaCacheCfg.setEvictionPolicy(null);
        metaCacheCfg.setQueryIndexEnabled(false);
        metaCacheCfg.setAtomicityMode(TRANSACTIONAL);

        return new GridCacheConfiguration[] {cacheCfg, metaCacheCfg};
    }

    /**
     * @return GGFS configuration for this test.
     */
    protected GridGgfsConfiguration getGgfsConfiguration() {
        GridGgfsConfiguration ggfsCfg = new GridGgfsConfiguration();

        ggfsCfg.setDataCacheName("dataCache");
        ggfsCfg.setMetaCacheName("metaCache");
        ggfsCfg.setName("ggfs");

        ggfsCfg.setBlockSize(512 * 1024); // Together with group blocks mapper will yield 64M per node groups.

        return ggfsCfg;
    }

    /** {@inheritDoc} */
    @Override protected GridConfiguration getConfiguration(String gridName) throws Exception {
        GridConfiguration cfg = G.loadConfiguration("config/ggfs/default-ggfs-data.xml").get1();

        assert cfg != null;

        cfg.setGridName(gridName);

        cfg.setIncludeEventTypes(concat(EVTS_GGFS, EVT_TASK_FAILED, EVT_TASK_FINISHED, EVT_JOB_MAPPED));

        cfg.setGgfsConfiguration(getGgfsConfiguration());

        cfg.setCacheConfiguration(getCacheConfiguration(gridName));

        GridTcpDiscoverySpi discoSpi = new GridTcpDiscoverySpi();

        discoSpi.setIpFinder(new GridTcpDiscoveryVmIpFinder(true));

        cfg.setDiscoverySpi(discoSpi);

        return cfg;
    }

    /**
     * Concatenates elements to an int array.
     *
     * @param arr Array.
     * @param obj One or more elements to concatenate.
     * @return Concatenated array.
     */
    protected static int[] concat(@Nullable int[] arr, int... obj) {
        int[] newArr;

        if (arr == null || arr.length == 0)
            newArr = obj;
        else {
            newArr = Arrays.copyOf(arr, arr.length + obj.length);

            System.arraycopy(obj, 0, newArr, arr.length, obj.length);
        }

        return newArr;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        Grid grid = startGrid(1);

        ggfs = (GridGgfsImpl)grid.ggfss().iterator().next();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        if (lsnr != null) {
            grid(1).events().stopLocalListen(lsnr, EVTS_GGFS);

            lsnr = null;
        }

        // Clean up file system.
        ggfs.format().get();
    }

    /** {@inheritDoc} */
    @Override protected void afterTestsStopped() throws Exception {
        stopGrid(1);
    }

    /**
     * Checks events on CRUD operations on a single file in nested directories.
     *
     * @throws Exception If failed.
     */
    public void testSingleFileNestedDirs() throws Exception {
        final List<GridEvent> evtList = new ArrayList<>();

        final int evtsCnt = 6 + 1 + 1;

        final CountDownLatch latch = new CountDownLatch(evtsCnt);

        grid(1).events().localListen(lsnr = new GridPredicate<GridEvent>() {
            @Override public boolean apply(GridEvent evt) {
                log.info("Received event [evt=" + evt + ']');

                evtList.add(evt);

                latch.countDown();

                return true;
            }
        }, EVTS_GGFS);

        GridGgfsPath dir = new GridGgfsPath("/dir1/dir2/dir3");

        GridGgfsPath file = new GridGgfsPath(dir, "file1");

        // Will generate 3 EVT_GGFS_DIR_CREATED + EVT_GGFS_FILE_CREATED + EVT_GGFS_FILE_OPENED_WRITE +
        // EVT_GGFS_FILE_CLOSED and a number of EVT_GGFS_META_UPDATED.
        ggfs.create(file, true).close();

        GridGgfsPath mvFile = new GridGgfsPath(dir, "mvFile1");

        ggfs.rename(file, mvFile); // Will generate EVT_GGFS_FILE_RENAMED.

        // Will generate EVT_GGFS_DIR_DELETED event.
        assertTrue(ggfs.delete(dir.parent(), true));

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertEquals(evtsCnt, evtList.size());

        GridGgfsEvent evt = (GridGgfsEvent)evtList.get(0);
        assertEquals(EVT_GGFS_DIR_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1"), evt.path());
        assertTrue(evt.isDirectory());

        evt = (GridGgfsEvent)evtList.get(1);
        assertEquals(EVT_GGFS_DIR_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1/dir2"), evt.path());

        evt = (GridGgfsEvent)evtList.get(2);
        assertEquals(EVT_GGFS_DIR_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1/dir2/dir3"), evt.path());

        evt = (GridGgfsEvent)evtList.get(3);
        assertEquals(EVT_GGFS_FILE_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1/dir2/dir3/file1"), evt.path());
        assertFalse(evt.isDirectory());

        evt = (GridGgfsEvent)evtList.get(4);
        assertEquals(EVT_GGFS_FILE_OPENED_WRITE, evt.type());
        assertEquals(new GridGgfsPath("/dir1/dir2/dir3/file1"), evt.path());

        evt = (GridGgfsEvent)evtList.get(5);
        assertEquals(EVT_GGFS_FILE_CLOSED_WRITE, evt.type());
        assertEquals(new GridGgfsPath("/dir1/dir2/dir3/file1"), evt.path());
        assertEquals(0, evt.dataSize());

        evt = (GridGgfsEvent)evtList.get(6);
        assertEquals(EVT_GGFS_FILE_RENAMED, evt.type());
        assertEquals(new GridGgfsPath("/dir1/dir2/dir3/file1"), evt.path());
        assertEquals(new GridGgfsPath("/dir1/dir2/dir3/mvFile1"), evt.newPath());

        evt = (GridGgfsEvent)evtList.get(7);
        assertEquals(EVT_GGFS_DIR_DELETED, evt.type());
        assertEquals(new GridGgfsPath("/dir1/dir2"), evt.path());
    }

    /**
     * Checks events on CRUD operations on a single directory
     * with some files.
     *
     * @throws Exception If failed.
     */
    public void testDirWithFiles() throws Exception {
        final List<GridEvent> evtList = new ArrayList<>();

        final int evtsCnt = 4 + 3 + 1;

        final CountDownLatch latch = new CountDownLatch(evtsCnt);

        grid(1).events().localListen(lsnr = new GridPredicate<GridEvent>() {
            @Override public boolean apply(GridEvent evt) {
                log.info("Received event [evt=" + evt + ']');

                evtList.add(evt);

                latch.countDown();

                return true;
            }
        }, EVTS_GGFS);

        GridGgfsPath dir = new GridGgfsPath("/dir1");

        GridGgfsPath file1 = new GridGgfsPath(dir, "file1");
        GridGgfsPath file2 = new GridGgfsPath(dir, "file2");

        // Will generate EVT_GGFS_DIR_CREATED + EVT_GGFS_FILE_CREATED + EVT_GGFS_FILE_OPENED_WRITE +
        // EVT_GGFS_FILE_CLOSED_WRITE.
        ggfs.create(file1, true).close();

        // Will generate EVT_GGFS_FILE_CREATED + EVT_GGFS_FILE_OPENED_WRITE +
        // EVT_GGFS_FILE_CLOSED.
        ggfs.create(file2, true).close();

        // Will generate EVT_GGFS_DIR_DELETED event.
        assertTrue(ggfs.delete(dir, true));

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertEquals(evtsCnt, evtList.size());

        GridGgfsEvent evt = (GridGgfsEvent)evtList.get(0);
        assertEquals(EVT_GGFS_DIR_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1"), evt.path());
        assertTrue(evt.isDirectory());

        evt = (GridGgfsEvent)evtList.get(1);
        assertEquals(EVT_GGFS_FILE_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1/file1"), evt.path());
        assertFalse(evt.isDirectory());

        evt = (GridGgfsEvent)evtList.get(2);
        assertEquals(EVT_GGFS_FILE_OPENED_WRITE, evt.type());
        assertEquals(new GridGgfsPath("/dir1/file1"), evt.path());

        evt = (GridGgfsEvent)evtList.get(3);
        assertEquals(EVT_GGFS_FILE_CLOSED_WRITE, evt.type());
        assertEquals(new GridGgfsPath("/dir1/file1"), evt.path());

        evt = (GridGgfsEvent)evtList.get(4);
        assertEquals(EVT_GGFS_FILE_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1/file2"), evt.path());
        assertFalse(evt.isDirectory());

        evt = (GridGgfsEvent)evtList.get(5);
        assertEquals(EVT_GGFS_FILE_OPENED_WRITE, evt.type());
        assertEquals(new GridGgfsPath("/dir1/file2"), evt.path());

        evt = (GridGgfsEvent)evtList.get(6);
        assertEquals(EVT_GGFS_FILE_CLOSED_WRITE, evt.type());
        assertEquals(new GridGgfsPath("/dir1/file2"), evt.path());

        evt = (GridGgfsEvent)evtList.get(7);
        assertEquals(EVT_GGFS_DIR_DELETED, evt.type());
        assertEquals(new GridGgfsPath("/dir1"), evt.path());
    }

    /**
     * Checks events on CRUD operations on a single empty
     * directory.
     *
     * @throws Exception If failed.
     */
    public void testSingleEmptyDir() throws Exception {
        final List<GridEvent> evtList = new ArrayList<>();

        final int evtsCnt = 1 + 1 + 0 + 1;

        final CountDownLatch latch = new CountDownLatch(evtsCnt);

        grid(1).events().localListen(lsnr = new GridPredicate<GridEvent>() {
            @Override public boolean apply(GridEvent evt) {
                log.info("Received event [evt=" + evt + ']');

                evtList.add(evt);

                latch.countDown();

                return true;
            }
        }, EVTS_GGFS);

        GridGgfsPath dir = new GridGgfsPath("/dir1");

        ggfs.mkdirs(dir); // Will generate EVT_GGFS_DIR_CREATED.

        GridGgfsPath mvDir = new GridGgfsPath("/mvDir1");

        ggfs.rename(dir, mvDir); // Will generate EVT_GGFS_DIR_RENAMED.

        assertFalse(ggfs.delete(dir, true)); // Will generate no event.

        assertTrue(ggfs.delete(mvDir, true)); // Will generate EVT_GGFS_DIR_DELETED events.

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertEquals(evtsCnt, evtList.size());

        GridGgfsEvent evt = (GridGgfsEvent)evtList.get(0);
        assertEquals(EVT_GGFS_DIR_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1"), evt.path());
        assertTrue(evt.isDirectory());

        evt = (GridGgfsEvent)evtList.get(1);
        assertEquals(EVT_GGFS_DIR_RENAMED, evt.type());
        assertEquals(new GridGgfsPath("/dir1"), evt.path());
        assertEquals(new GridGgfsPath("/mvDir1"), evt.newPath());
        assertTrue(evt.isDirectory());

        evt = (GridGgfsEvent)evtList.get(2);
        assertEquals(EVT_GGFS_DIR_DELETED, evt.type());
        assertEquals(new GridGgfsPath("/mvDir1"), evt.path());
        assertTrue(evt.isDirectory());
    }

    /**
     * Checks events on CRUD operations on 2 files.
     *
     * @throws Exception If failed.
     */
    public void testTwoFiles() throws Exception {
        final List<GridEvent> evtList = new ArrayList<>();

        final int evtsCnt = 4 + 3 + 2 + 2;

        final CountDownLatch latch = new CountDownLatch(evtsCnt);

        grid(1).events().localListen(lsnr = new GridPredicate<GridEvent>() {
            @Override public boolean apply(GridEvent evt) {
                log.info("Received event [evt=" + evt + ']');

                evtList.add(evt);

                latch.countDown();

                return true;
            }
        }, EVTS_GGFS);

        GridGgfsPath dir = new GridGgfsPath("/dir1");

        GridGgfsPath file1 = new GridGgfsPath(dir, "file1");

        // Will generate EVT_GGFS_FILE_CREATED event + EVT_GGFS_DIR_CREATED event + OPEN + CLOSE.
        ggfs.create(file1, true).close();

        GridGgfsPath file2 = new GridGgfsPath(dir, "file2");

        ggfs.create(file2, true).close(); // Will generate 1 EVT_GGFS_FILE_CREATED event + OPEN + CLOSE.

        assertTrue(ggfs.exists(dir));
        assertTrue(ggfs.exists(file1));
        assertTrue(ggfs.exists(file2));

        assertTrue(ggfs.delete(file1, false)); // Will generate 1 EVT_GGFS_FILE_DELETED and 1 EVT_GGFS_FILE_PURGED.
        assertTrue(ggfs.delete(file2, false)); // Same.

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertEquals(evtsCnt, evtList.size());

        GridGgfsEvent evt = (GridGgfsEvent)evtList.get(0);
        assertEquals(EVT_GGFS_DIR_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1"), evt.path());
        assertTrue(evt.isDirectory());

        evt = (GridGgfsEvent)evtList.get(1);
        assertEquals(EVT_GGFS_FILE_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1/file1"), evt.path());

        evt = (GridGgfsEvent)evtList.get(2);
        assertEquals(EVT_GGFS_FILE_OPENED_WRITE, evt.type());
        assertEquals(new GridGgfsPath("/dir1/file1"), evt.path());

        evt = (GridGgfsEvent)evtList.get(3);
        assertEquals(EVT_GGFS_FILE_CLOSED_WRITE, evt.type());
        assertEquals(new GridGgfsPath("/dir1/file1"), evt.path());
        assertEquals(0, evt.dataSize());

        evt = (GridGgfsEvent)evtList.get(4);
        assertEquals(EVT_GGFS_FILE_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1/file2"), evt.path());

        evt = (GridGgfsEvent)evtList.get(5);
        assertEquals(EVT_GGFS_FILE_OPENED_WRITE, evt.type());
        assertEquals(new GridGgfsPath("/dir1/file2"), evt.path());

        evt = (GridGgfsEvent)evtList.get(6);
        assertEquals(EVT_GGFS_FILE_CLOSED_WRITE, evt.type());
        assertEquals(new GridGgfsPath("/dir1/file2"), evt.path());
        assertEquals(0, evt.dataSize());

        assertOneToOne(
            evtList.subList(7, 11),
            new EventPredicate(EVT_GGFS_FILE_DELETED, new GridGgfsPath("/dir1/file1")),
            new EventPredicate(EVT_GGFS_FILE_PURGED, new GridGgfsPath("/dir1/file1")),
            new EventPredicate(EVT_GGFS_FILE_DELETED, new GridGgfsPath("/dir1/file2")),
            new EventPredicate(EVT_GGFS_FILE_PURGED, new GridGgfsPath("/dir1/file2"))
        );
    }

    /**
     * Checks events on CRUD operations with non-recursive
     * directory deletion.
     *
     * @throws Exception If failed.
     */
    public void testDeleteNonRecursive() throws Exception {
        final List<GridEvent> evtList = new ArrayList<>();

        final int evtsCnt = 2 + 0 + 1;

        final CountDownLatch latch = new CountDownLatch(evtsCnt);

        grid(1).events().localListen(lsnr = new GridPredicate<GridEvent>() {
            @Override public boolean apply(GridEvent evt) {
                log.info("Received event [evt=" + evt + ']');

                evtList.add(evt);

                latch.countDown();

                return true;
            }
        }, EVTS_GGFS);

        GridGgfsPath dir = new GridGgfsPath("/dir1/dir2");

        ggfs.mkdirs(dir); // Will generate 2 EVT_GGFS_DIR_CREATED events.

        try {
            ggfs.delete(dir.parent(), false); // Will generate no events.
        }
        catch (GridException ignore) {
            // No-op.
        }

        assertTrue(ggfs.delete(dir, false)); // Will generate 1 EVT_GGFS_DIR_DELETED event.

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertEquals(evtsCnt, evtList.size());

        GridGgfsEvent evt = (GridGgfsEvent)evtList.get(0);
        assertEquals(EVT_GGFS_DIR_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1"), evt.path());

        evt = (GridGgfsEvent)evtList.get(1);
        assertEquals(EVT_GGFS_DIR_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1/dir2"), evt.path());

        GridGgfsEvent evt3 = (GridGgfsEvent)evtList.get(2);
        assertEquals(EVT_GGFS_DIR_DELETED, evt3.type());
        assertEquals(new GridGgfsPath("/dir1/dir2"), evt3.path());
    }

    /**
     * Checks events on CRUD operations on file move.
     *
     * @throws Exception If failed.
     */
    public void testMoveFile() throws Exception {
        final List<GridEvent> evtList = new ArrayList<>();

        final int evtsCnt = 5 + 1;

        final CountDownLatch latch = new CountDownLatch(evtsCnt);

        grid(1).events().localListen(lsnr = new GridPredicate<GridEvent>() {
            @Override public boolean apply(GridEvent evt) {
                log.info("Received event [evt=" + evt + ']');

                evtList.add(evt);

                latch.countDown();

                return true;
            }
        }, EVTS_GGFS);

        GridGgfsPath dir = new GridGgfsPath("/dir1/dir2");

        GridGgfsPath file = new GridGgfsPath(dir, "file1");

        // Will generate 2 EVT_GGFS_DIR_CREATED events + EVT_GGFS_FILE_CREATED_EVENT + OPEN + CLOSE.
        ggfs.create(file, true).close();

        ggfs.rename(file, dir.parent()); // Will generate 1 EVT_GGFS_FILE_RENAMED.

        assertTrue(ggfs.exists(new GridGgfsPath(dir.parent(), file.name())));

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertEquals(evtsCnt, evtList.size());

        GridGgfsEvent evt = (GridGgfsEvent)evtList.get(0);
        assertEquals(EVT_GGFS_DIR_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1"), evt.path());

        evt = (GridGgfsEvent)evtList.get(1);
        assertEquals(EVT_GGFS_DIR_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1/dir2"), evt.path());

        evt = (GridGgfsEvent)evtList.get(2);
        assertEquals(EVT_GGFS_FILE_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1/dir2/file1"), evt.path());

        evt = (GridGgfsEvent)evtList.get(3);
        assertEquals(EVT_GGFS_FILE_OPENED_WRITE, evt.type());
        assertEquals(new GridGgfsPath("/dir1/dir2/file1"), evt.path());

        evt = (GridGgfsEvent)evtList.get(4);
        assertEquals(EVT_GGFS_FILE_CLOSED_WRITE, evt.type());
        assertEquals(new GridGgfsPath("/dir1/dir2/file1"), evt.path());
        assertEquals(0, evt.dataSize());

        GridGgfsEvent evt4 = (GridGgfsEvent)evtList.get(5);
        assertEquals(EVT_GGFS_FILE_RENAMED, evt4.type());
        assertEquals(new GridGgfsPath("/dir1/dir2/file1"), evt4.path());
        assertEquals(new GridGgfsPath("/dir1/file1"), evt4.newPath());
    }

    /**
     * Checks events on CRUD operations with multiple
     * empty directories.
     *
     * @throws Exception If failed.
     */
    public void testNestedEmptyDirs() throws Exception {
        final List<GridEvent> evtList = new ArrayList<>();

        final int evtsCnt = 2 + 1;

        final CountDownLatch latch = new CountDownLatch(evtsCnt);

        grid(1).events().localListen(lsnr = new GridPredicate<GridEvent>() {
            @Override public boolean apply(GridEvent evt) {
                log.info("Received event [evt=" + evt + ']');

                evtList.add(evt);

                latch.countDown();

                return true;
            }
        }, EVTS_GGFS);

        GridGgfsPath dir = new GridGgfsPath("/dir1/dir2");

        assertFalse(ggfs.exists(dir.parent()));

        ggfs.mkdirs(dir); // Will generate 2 EVT_GGFS_DIR_RENAMED events.

        assertTrue(ggfs.delete(dir.parent(), true)); // Will generate EVT_GGFS_DIR_DELETED event.

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertEquals(evtsCnt, evtList.size());

        GridGgfsEvent evt = (GridGgfsEvent)evtList.get(0);
        assertEquals(EVT_GGFS_DIR_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1"), evt.path());

        evt = (GridGgfsEvent)evtList.get(1);
        assertEquals(EVT_GGFS_DIR_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/dir1/dir2"), evt.path());

        evt = (GridGgfsEvent)evtList.get(2);
        assertEquals(EVT_GGFS_DIR_DELETED, evt.type());
        assertEquals(new GridGgfsPath("/dir1"), evt.path());
    }

    /**
     * Checks events on CRUD operations with single
     * file overwrite.
     *
     * @throws Exception If failed.
     */
    public void testSingleFileOverwrite() throws Exception {
        final List<GridEvent> evtList = new ArrayList<>();

        final int evtsCnt = 3 + 4 + 1;

        final CountDownLatch latch = new CountDownLatch(evtsCnt);

        grid(1).events().localListen(lsnr = new GridPredicate<GridEvent>() {
            @Override public boolean apply(GridEvent evt) {
                log.info("Received event [evt=" + evt + ']');

                evtList.add(evt);

                latch.countDown();

                return true;
            }
        }, EVTS_GGFS);

        final GridGgfsPath file = new GridGgfsPath("/file1");

        ggfs.create(file, false).close(); // Will generate create, open and close events.

        ggfs.create(file, true).close(); // Will generate same event set + delete and purge events.

        GridTestUtils.assertThrowsWithCause(new Callable<Object>() {
            @Override public Object call() throws Exception {
                ggfs.create(file, false).close(); // Won't generate any event.

                return true;
            }
        }, GridGgfsPathAlreadyExistsException.class);

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertEquals(evtsCnt, evtList.size());

        final GridGgfsPath file1 = new GridGgfsPath("/file1");

        GridGgfsEvent evt = (GridGgfsEvent)evtList.get(0);
        assertEquals(EVT_GGFS_FILE_CREATED, evt.type());
        assertEquals(file1, evt.path());

        evt = (GridGgfsEvent)evtList.get(1);
        assertEquals(EVT_GGFS_FILE_OPENED_WRITE, evt.type());
        assertEquals(file1, evt.path());

        evt = (GridGgfsEvent)evtList.get(2);
        assertEquals(EVT_GGFS_FILE_CLOSED_WRITE, evt.type());
        assertEquals(file1, evt.path());
        assertEquals(0, evt.dataSize());

        assertOneToOne(
            evtList.subList(3, 8),
            new P1<GridEvent>() {
                @Override public boolean apply(GridEvent e) {
                    GridGgfsEvent e0 = (GridGgfsEvent)e;

                    return e0.type() == EVT_GGFS_FILE_DELETED && e0.path().equals(file1);
                }
            },
            new P1<GridEvent>() {
                @Override public boolean apply(GridEvent e) {
                    GridGgfsEvent e0 = (GridGgfsEvent)e;

                    return e0.type() == EVT_GGFS_FILE_PURGED && e0.path().equals(file1);
                }
            },
            new P1<GridEvent>() {
                @Override public boolean apply(GridEvent e) {
                    GridGgfsEvent e0 = (GridGgfsEvent)e;

                    return e0.type() == EVT_GGFS_FILE_CREATED && e0.path().equals(file1);
                }
            },
            new P1<GridEvent>() {
                @Override public boolean apply(GridEvent e) {
                    GridGgfsEvent e0 = (GridGgfsEvent)e;

                    return e0.type() == EVT_GGFS_FILE_OPENED_WRITE && e0.path().equals(file1);
                }
            },
            new P1<GridEvent>() {
                @Override public boolean apply(GridEvent e) {
                    GridGgfsEvent e0 = (GridGgfsEvent)e;

                    return e0.type() == EVT_GGFS_FILE_CLOSED_WRITE && e0.path().equals(file1);
                }
            }
        );
    }

    /**
     * Checks events on file data transfer operations.
     *
     * @throws Exception If failed.
     */
    public void testFileDataEvents() throws Exception {
        final List<GridEvent> evtList = new ArrayList<>();

        final int evtsCnt = 5;

        final CountDownLatch latch = new CountDownLatch(evtsCnt);

        grid(1).events().localListen(lsnr = new GridPredicate<GridEvent>() {
            @Override public boolean apply(GridEvent evt) {
                log.info("Received event [evt=" + evt + ']');

                evtList.add(evt);

                latch.countDown();

                return true;
            }
        }, EVTS_GGFS);

        final GridGgfsPath file = new GridGgfsPath("/file1");

        final int dataSize = 1024;

        byte[] buf = new byte[dataSize];

        // Will generate GGFS_FILE_CREATED, GGFS_FILE_OPENED_WRITE, GGFS_FILE_CLOSED_WRITE.
        try (GridGgfsOutputStream os = ggfs.create(file, false)) {
            os.write(buf); // Will generate no events.
        }

        // Will generate EVT_GGFS_FILE_OPENED_READ, GGFS_FILE_CLOSED_READ.
        try (GridGgfsInputStream is = ggfs.open(file, 256)) {
            is.readFully(0, buf); // Will generate no events.
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertEquals(evtsCnt, evtList.size());

        GridGgfsEvent evt = (GridGgfsEvent)evtList.get(0);
        assertEquals(EVT_GGFS_FILE_CREATED, evt.type());
        assertEquals(new GridGgfsPath("/file1"), evt.path());

        evt = (GridGgfsEvent)evtList.get(1);
        assertEquals(EVT_GGFS_FILE_OPENED_WRITE, evt.type());
        assertEquals(new GridGgfsPath("/file1"), evt.path());

        evt = (GridGgfsEvent)evtList.get(2);
        assertEquals(EVT_GGFS_FILE_CLOSED_WRITE, evt.type());
        assertEquals(new GridGgfsPath("/file1"), evt.path());
        assertEquals((long)dataSize, evt.dataSize());

        evt = (GridGgfsEvent)evtList.get(3);
        assertEquals(EVT_GGFS_FILE_OPENED_READ, evt.type());
        assertEquals(new GridGgfsPath("/file1"), evt.path());

        evt = (GridGgfsEvent)evtList.get(4);
        assertEquals(EVT_GGFS_FILE_CLOSED_READ, evt.type());
        assertEquals(new GridGgfsPath("/file1"), evt.path());
        assertEquals((long)dataSize, evt.dataSize());
    }

    /**
     * Predicate for matching {@link GridGgfsEvent}.
     */
    private static class EventPredicate implements GridPredicate<GridEvent> {
        /** */
        private final int evt;

        /** */
        private final GridGgfsPath path;

        /**
         * @param evt Event type.
         * @param path GGFS path.
         */
        EventPredicate(int evt, GridGgfsPath path) {

            this.evt = evt;
            this.path = path;
        }

        /** {@inheritDoc} */
        @Override public boolean apply(GridEvent e) {
            GridGgfsEvent e0 = (GridGgfsEvent)e;

            return e0.type() == evt && e0.path().equals(path);
        }
    }
}
