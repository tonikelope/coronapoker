package com.tonikelope.coronapoker;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RabbitRequesterAuthorizationBindingTest {

    @Test
    void hostCannotForgeARequestThatChargesAnotherPlayer() throws Exception {
        Path root = locateRoot();
        String crupier = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        String identity = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/IdentityManager.java"));
        String ledger = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/RabbitFeeLedger.java"));

        assertTrue(identity.contains("signRabbitRequest")
                        && identity.contains("verifyRabbitRequest"),
                "Rabbit requests must have a dedicated Ed25519 domain");
        assertTrue(ledger.contains("requesterSignature"),
                "the signed requester proof must survive inside every authorization wire");
        assertTrue(count(crupier, "verifyRabbitRequest(") >= 2,
                "both host admission and every peer's fee application must verify requester authorship");
    }

    @Test
    void requesterSignatureSurvivesAuthorizationAndBindsEveryField() {
        IdentityManager requester = IdentityManager.initializeForNick(
                "__qa_rabbit_requester_" + System.nanoTime());
        byte[] hand = RabbitClientChosenCounterRejectedTest.hand(7);
        byte[] nonce = RabbitClientChosenCounterRejectedTest.nonce(9);
        byte[] signature = requester.signRabbitRequest(hand, "alice", nonce);
        RabbitFeeLedger.Request request = new RabbitFeeLedger.Request(
                hand, "alice", nonce, signature);
        RabbitFeeLedger ledger = new RabbitFeeLedger(hand, 3, 10, 20);
        RabbitFeeLedger.Authorization authorization = ledger.authorize(request).value();
        RabbitFeeLedger.Result<RabbitFeeLedger.Authorization> decoded
                = RabbitFeeLedger.Authorization.decode(authorization.encode());

        assertTrue(decoded.isOk());
        RabbitFeeLedger.Request relayed = decoded.value().request();
        assertTrue(IdentityManager.verifyRabbitRequest(requester.getPublicKey(),
                relayed.handId(), relayed.playerId(), relayed.nonce(),
                relayed.requesterSignature()));
        assertFalse(IdentityManager.verifyRabbitRequest(requester.getPublicKey(),
                relayed.handId(), "mallory", relayed.nonce(),
                relayed.requesterSignature()));
        byte[] changedNonce = relayed.nonce();
        changedNonce[0] ^= 1;
        assertFalse(IdentityManager.verifyRabbitRequest(requester.getPublicKey(),
                relayed.handId(), relayed.playerId(), changedNonce,
                relayed.requesterSignature()));
    }

    private static int count(String text, String token) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }

    private static Path locateRoot() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null) {
            if (Files.isRegularFile(path.resolve("pom.xml"))
                    && Files.isDirectory(path.resolve("src/main/java"))) return path;
            path = path.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
