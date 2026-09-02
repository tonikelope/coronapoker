package com.tonikelope.coronapoker.swing;

import com.tonikelope.coronapoker.Helpers;
import com.tonikelope.coronapoker.core.FrontendRuntimeService;

/** Transitional owner of the classic per-table executors. */
final class SwingRuntimeBackend implements FrontendRuntimeService.Backend {

    @Override
    public void start() {
        // Class initialization creates the characterized classic worker/log pools.
        if (Helpers.THREAD_POOL == null) {
            Helpers.CREATE_THREAD_POOL();
        }
    }

    @Override
    public void close() {
        if (Helpers.THREAD_POOL != null && !Helpers.THREAD_POOL.isTerminated()) {
            Helpers.SHUTDOWN_THREAD_POOL();
        } else if (Helpers.LOG_POOL != null && !Helpers.LOG_POOL.isTerminated()) {
            Helpers.SHUTDOWN_THREAD_POOL();
        }
    }
}
