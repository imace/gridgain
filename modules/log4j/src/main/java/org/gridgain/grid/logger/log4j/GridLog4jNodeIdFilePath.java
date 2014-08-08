/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.logger.log4j;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.grid.util.typedef.internal.*;

import java.io.*;
import java.util.*;

/**
 * Closure that generates file path adding node id to filename as a suffix.
 */
class GridLog4jNodeIdFilePath implements GridClosure<String, String> {
    /** Node id. */
    private final UUID nodeId;

    /**
     * Creates new instance.
     *
     * @param id Node id.
     */
    GridLog4jNodeIdFilePath(UUID id) {
        nodeId = id;
    }

    /** {@inheritDoc} */
    @Override public String apply(String oldPath) {
        if (oldPath != null) // fileName could be null if GRIDGAIN_HOME is not defined.
            return U.nodeIdLogFileName(nodeId, oldPath);

        String tmpDir = X.getSystemOrEnv("java.io.tmpdir");

        if (tmpDir != null)
            return U.nodeIdLogFileName(nodeId, new File(tmpDir, "gridgain.log").getAbsolutePath());

        System.err.println("Failed to get tmp directory for log file.");
        return null;
    }
}