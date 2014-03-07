/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.testsuites;

import junit.framework.*;
import org.gridgain.grid.spi.eventstorage.memory.*;
import org.gridgain.testframework.*;

/**
 * Event storage test suite.
 */
public class GridSpiEventStorageSelfTestSuite extends TestSuite {
    /**
     * @return Event storage test suite.
     * @throws Exception If failed.
     */
    public static TestSuite suite() throws Exception {
        TestSuite suite = GridTestUtils.createDistributedTestSuite("Gridgain Event Storage Test Suite");

        suite.addTest(new TestSuite(GridMemoryEventStorageSpiSelfTest.class));
        suite.addTest(new TestSuite(GridMemoryEventStorageSpiStartStopSelfTest.class));
        suite.addTest(new TestSuite(GridMemoryEventStorageMultiThreadedSelfTest.class));
        suite.addTest(new TestSuite(GridMemoryEventStorageSpiConfigSelfTest.class));

        return suite;
    }
}
