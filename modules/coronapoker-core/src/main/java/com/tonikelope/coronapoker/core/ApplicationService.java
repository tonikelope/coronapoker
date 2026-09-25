package com.tonikelope.coronapoker.core;

/**
 * A process-level service owned by {@link CoronaPokerApplication}.
 *
 * Services start in declaration order and close in reverse order. A frontend
 * may use a service but must not own its lifecycle.
 */
public interface ApplicationService extends AutoCloseable {

    /** Starts this service. */
    void start() throws Exception;

    /** Stops this service and releases all process-level resources. */
    @Override
    void close() throws Exception;
}
