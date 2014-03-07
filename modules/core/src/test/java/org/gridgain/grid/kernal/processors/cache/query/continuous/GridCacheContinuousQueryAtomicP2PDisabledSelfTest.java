/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.query.continuous;

/**
 * Continuous queries tests for atomic cache with disabled P2P class loading.
 */
public class GridCacheContinuousQueryAtomicP2PDisabledSelfTest
    extends GridCacheContinuousQueryAtomicSelfTest {
    /** {@inheritDoc} */
    @Override protected boolean peerClassLoadingEnabled() {
        return false;
    }
}
