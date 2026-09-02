package com.tonikelope.coronapoker.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import com.tonikelope.coronapoker.core.NewGameRequest;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NetworkLobbyGatewayTest {
    @TempDir Path temporary;

    @Test void twoNativeSessionsHandshakeChatManageBotsAndLeave() throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) { port = reservation.getLocalPort(); }
        try (NetworkLobbyGateway hostGateway = new NetworkLobbyGateway(temporary.resolve("host"));
             NetworkLobbyGateway clientGateway = new NetworkLobbyGateway(temporary.resolve("client"));
             NetworkLobbyGateway lateGateway = new NetworkLobbyGateway(temporary.resolve("late"))) {
            LobbySession host = hostGateway.open(request(false, "Anfitrion", port))
                    .get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(request(true, "Invitado", port))
                    .get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2);
                assertEquals(2, client.snapshot().participants().size());
                assertEquals(host.snapshot().tableSettings(), client.snapshot().tableSettings());

                LobbySession late = lateGateway.open(request(true, "Ultimo", port))
                        .get(5, TimeUnit.SECONDS);
                try {
                    await(() -> late.snapshot().participants().size() == 3);
                    assertTrue(late.snapshot().participants().stream()
                            .anyMatch(participant -> participant.nickname().equals("Invitado")
                                    && participant.secure()),
                            "USERSLIST must preserve the existing peer's verified identity");
                } finally {
                    late.close();
                }
                await(() -> host.snapshot().participants().size() == 2);

                client.submit(new LobbyCommand.SendText("hola mesa")).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                await(() -> host.snapshot().chat().stream()
                        .anyMatch(item -> item.nickname().equals("Invitado")
                                && item.content().equals("hola mesa")));

                host.submit(new LobbyCommand.AddBot()).toCompletableFuture().get(2, TimeUnit.SECONDS);
                await(() -> client.snapshot().participants().stream().anyMatch(p -> p.bot()));
                assertTrue(host.snapshot().participants().stream().anyMatch(p -> p.bot()));

                client.submit(new LobbyCommand.Leave()).toCompletableFuture().get(2, TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().size() == 2);
            } finally {
                client.close();
                host.close();
            }
        }
    }

    private static NewGameRequest request(boolean joining, String nickname, int port) {
        NewGameConnectionDraft.Submission connection = new NewGameConnectionDraft.Submission(
                joining ? NewGameConnectionDraft.Mode.JOIN : NewGameConnectionDraft.Mode.CREATE,
                nickname, "", "127.0.0.1", Integer.toString(port), null,
                false, false, null);
        return new NewGameRequest(connection,
                joining ? null : new NewGameTableDraft().snapshot());
    }

    private static void await(BooleanSupplier condition) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "condition did not become true before timeout");
    }
}
