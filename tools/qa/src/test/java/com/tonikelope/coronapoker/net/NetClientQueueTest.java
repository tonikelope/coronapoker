/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.net;

import com.tonikelope.coronapoker.NetClient;
import com.tonikelope.coronapoker.WaitingRoomFrame;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client's socket reader queue used to be unbounded, so a hostile host could OOM its
 * clients. It now carries the same cap as the host (Participant.SOCKET_READER_QUEUE_CAPACITY)
 * plus the twin enqueue helpers. This pins the property that matters most and that a naive
 * cap would break: the close signal (POISON_PILL) still reaches the consumer even when the
 * queue is full, so bounding it does not reintroduce the "reader blocks on a full queue and
 * the table waits forever" bug that a plain put() would cause.
 *
 * encolarSenalCierre does not touch the WaitingRoomFrame, so a null one is fine here.
 */
class NetClientQueueTest {

    @Test
    @DisplayName("the client reader queue is now bounded")
    void clientQueueIsBounded() {
        NetClient nc = new NetClient(null);
        LinkedBlockingQueue<String> q = nc.getLocal_client_socket_reader_queue();

        for (int i = 0; i < NetClient.SOCKET_READER_QUEUE_CAPACITY; i++) {
            assertTrue(q.offer("m" + i), "offer within the cap must succeed");
        }
        assertEquals(0, q.remainingCapacity(), "the queue must be bounded and now full");
        assertFalse(q.offer("overflow"), "a bounded full queue must reject a plain offer");
    }

    @Test
    @DisplayName("encolarSenalCierre reaches a full queue by making room for the close signal")
    void closeSignalEntersFullQueue() {
        NetClient nc = new NetClient(null);
        LinkedBlockingQueue<String> q = nc.getLocal_client_socket_reader_queue();

        for (int i = 0; i < NetClient.SOCKET_READER_QUEUE_CAPACITY; i++) {
            q.offer("m" + i);
        }
        assertEquals(0, q.remainingCapacity(), "precondition: queue full");
        assertFalse(q.contains(WaitingRoomFrame.POISON_PILL));

        nc.encolarSenalCierre();

        assertTrue(q.contains(WaitingRoomFrame.POISON_PILL),
                "the close signal must enter even a full queue (room made by dropping the oldest)");
        assertTrue(q.size() <= NetClient.SOCKET_READER_QUEUE_CAPACITY,
                "making room must not push the queue past its cap");
    }
}
