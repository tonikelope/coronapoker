package com.tonikelope.coronapoker.core;

import com.tonikelope.coronapoker.core.game.CoreGameDatabase;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RecoverableGameRepositoryTest {

    private static final String DEFAULT_SETTINGS = "IWTSTH=1#RABBIT=2"
            + "#DIFFICULTY=HARD#BLIND_CAP=2.0#REBUY_LIMIT=3"
            + "#BOT_REBUY=1#BOTBAL=1#RUNITWICE=1#VOICEMSG=1#TTS=0"
            + "#FIXED_BUYIN=1#BLINDS=#BMINBB=10#BMAXBB=100#RBCAP=1"
            + "#ANTE=1#STRADDLE=1#MANOS=50#THINKT=45#THINKON=1"
            + "#SHOWDOWN=12";

    @TempDir
    Path temporary;

    private DatabaseService database;

    @AfterEach
    void closeDatabase() throws Exception {
        if (database != null) database.close();
    }

    @Test
    void decodesTheCompleteCurrentSwingRecoverySchema() {
        NewGameTableDraft.Settings settings = RecoverableGameRepository.decodeSettings(
                DEFAULT_SETTINGS, 10, 0.5, 60, 1, true);

        assertEquals(new NewGameTableDraft.BlindLevel(0.5, 1.0),
                settings.selectedBlindLevel());
        assertTrue(settings.increaseBlinds());
        assertEquals(NewGameTableDraft.BlindIncreaseType.MINUTES,
                settings.blindIncreaseType());
        assertEquals(60, settings.blindInterval());
        assertTrue(settings.blindCap());
        assertEquals(1, settings.blindCapRaises());
        assertTrue(settings.fixedBuyin());
        assertEquals(10, settings.buyin());
        assertTrue(settings.rebuy());
        assertTrue(settings.rebuyLimit());
        assertEquals(3, settings.rebuyLimitCount());
        assertTrue(settings.botRebuy());
        assertTrue(settings.botBalanceToHumans());
        assertEquals(NewGameTableDraft.RebuyCapPolicy.HIGHEST_STACK,
                settings.rebuyCapPolicy());
        assertTrue(settings.handLimit());
        assertEquals(50, settings.handLimitCount());
        assertTrue(settings.thinkTime());
        assertEquals(45, settings.thinkSeconds());
        assertEquals(12, settings.showdownSeconds());
        assertTrue(settings.ante());
        assertTrue(settings.straddle());
        assertTrue(settings.iwtsth());
        assertTrue(settings.runItTwice());
        assertEquals(NewGameTableDraft.RabbitHunting.FREE_SMALL_BLIND,
                settings.rabbitHunting());
        assertEquals(NewGameTableDraft.BotDifficulty.HARD,
                settings.botDifficulty());
    }

    @Test
    void decodesCustomBlindStructureAndHandsBasedIncrease() {
        String settings = DEFAULT_SETTINGS
                .replace("BLIND_CAP=2.0", "BLIND_CAP=1.0")
                .replace("BLINDS=", "BLINDS=0.1/0.25,0.2/0.5,0.5/1.0")
                .replace("MANOS=50", "MANOS=-1");

        NewGameTableDraft.Settings decoded = RecoverableGameRepository.decodeSettings(
                settings, 10, 0.2, 8, 2, false);

        assertEquals("Recuperada", decoded.structureName());
        assertEquals(List.of(
                new NewGameTableDraft.BlindLevel(0.1, 0.25),
                new NewGameTableDraft.BlindLevel(0.2, 0.5),
                new NewGameTableDraft.BlindLevel(0.5, 1.0)),
                decoded.blindLevels());
        assertEquals(1, decoded.blindLevelIndex());
        assertEquals(NewGameTableDraft.BlindIncreaseType.HANDS,
                decoded.blindIncreaseType());
        assertEquals(8, decoded.blindInterval());
        assertFalse(decoded.rebuy());
        assertFalse(decoded.handLimit());
    }

    @Test
    void rejectsIncompleteMalformedAndEconomicallyInconsistentRows() {
        assertThrows(IllegalArgumentException.class,
                () -> RecoverableGameRepository.decodeSettings(
                        DEFAULT_SETTINGS.replace("#SHOWDOWN=12", ""),
                        10, 0.5, 60, 1, true));
        assertThrows(IllegalArgumentException.class,
                () -> RecoverableGameRepository.decodeSettings(
                        DEFAULT_SETTINGS + "#TTS=1", 10, 0.5, 60, 1, true));
        assertThrows(IllegalArgumentException.class,
                () -> RecoverableGameRepository.decodeSettings(
                        DEFAULT_SETTINGS, 10, 0.4, 60, 1, true));
    }

    @Test
    void immutableSettingsRoundTripThroughAnEditableDraft() {
        NewGameTableDraft original = new NewGameTableDraft();
        original.setBlindStructure("Turbo", List.of(
                new NewGameTableDraft.BlindLevel(0.1, 0.25),
                new NewGameTableDraft.BlindLevel(0.2, 0.5),
                new NewGameTableDraft.BlindLevel(0.5, 1.0)), 1);
        original.setIncreaseBlinds(true);
        original.setBlindIncreaseType(NewGameTableDraft.BlindIncreaseType.HANDS);
        original.setBlindInterval(7);
        original.setBlindCap(true);
        original.setBlindCapRaises(1);
        original.setMaxBuyinBb(200);
        original.setMinBuyinBb(20);
        original.setFixedBuyin(false);
        original.setBuyin(50);
        original.setRebuy(false);
        original.setRebuyLimit(true);
        original.setRebuyLimitCount(9);
        original.setBotRebuy(false);
        original.setBotBalanceToHumans(true);
        original.setRebuyCapPolicy(NewGameTableDraft.RebuyCapPolicy.HIGHEST_STACK);
        original.setHandLimit(true);
        original.setHandLimitCount(123);
        original.setThinkTime(false);
        original.setThinkSeconds(70);
        original.setShowdownSeconds(22);
        original.setAnte(true);
        original.setStraddle(true);
        original.setIwtsth(true);
        original.setRunItTwice(true);
        original.setRabbitHunting(
                NewGameTableDraft.RabbitHunting.FREE_SMALL_AND_BIG_BLIND);
        original.setBotDifficulty(NewGameTableDraft.BotDifficulty.EASY);

        assertEquals(original.snapshot(),
                NewGameTableDraft.from(original.snapshot()).snapshot());
    }

    @Test
    void neutralConfigurationSerializesToTheExactSwingRecoverySchema() {
        NewGameTableDraft draft = new NewGameTableDraft();
        draft.setBlindStructure("Turbo", List.of(
                new NewGameTableDraft.BlindLevel(0.1, 0.25),
                new NewGameTableDraft.BlindLevel(0.2, 0.5),
                new NewGameTableDraft.BlindLevel(0.5, 1.0)), 1);
        draft.setIncreaseBlinds(true);
        draft.setBlindIncreaseType(NewGameTableDraft.BlindIncreaseType.HANDS);
        draft.setBlindInterval(7);
        draft.setBlindCap(true);
        draft.setBlindCapRaises(1);
        draft.setHandLimit(true);
        draft.setHandLimitCount(123);
        draft.setAnte(true);
        draft.setStraddle(true);
        draft.setIwtsth(true);
        draft.setRunItTwice(true);
        draft.setBotDifficulty(NewGameTableDraft.BotDifficulty.EASY);
        NewGameTableDraft.Settings source = draft.snapshot();
        GameConfigCodecV1.Configuration configuration
                = GameConfigCodecV1.fromSettings(source, false, "session");

        String encoded = RecoverableGameRepository.encodeSettings(configuration,
                source.botDifficulty(), false, true);
        NewGameTableDraft.Settings recovered
                = RecoverableGameRepository.decodeSettings(encoded,
                        configuration.buyin(), configuration.smallBlind(),
                        configuration.blindsDouble(),
                        configuration.blindsDoubleType(), configuration.rebuy());

        assertEquals(source.blindLevels(), recovered.blindLevels());
        assertEquals(source.blindLevelIndex(), recovered.blindLevelIndex());
        assertEquals(source.handLimitCount(), recovered.handLimitCount());
        assertEquals(source.botDifficulty(), recovered.botDifficulty());
        assertEquals(source.ante(), recovered.ante());
        assertEquals(source.straddle(), recovered.straddle());
        assertTrue(encoded.contains("#VOICEMSG=0#TTS=1#"));
    }

    @Test
    void coreDatabaseActuallyPersistsRecoverySettingsForGdxRows()
            throws Exception {
        database = new DatabaseService(
                temporary.resolve("core-recovery-write.db").toString());
        database.start();
        CoreGameDatabase games = new CoreGameDatabase(database, -1,
                () -> DEFAULT_SETTINGS);
        try (PreparedStatement insert = database.connection().prepareStatement(
                "INSERT INTO game(id,start,server,buyin,sb,blinds_time,"
                + "blinds_time_type,rebuy,ugi,local) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            insert.setInt(1, 9);
            insert.setLong(2, 500L);
            insert.setString(3, "host");
            insert.setInt(4, 10);
            insert.setDouble(5, 0.5);
            insert.setInt(6, 60);
            insert.setInt(7, 1);
            insert.setBoolean(8, true);
            insert.setString(9, "ugi");
            insert.setBoolean(10, true);
            insert.executeUpdate();
        }

        games.persistRecoverySettings(9);

        try (Statement statement = database.connection().createStatement();
                ResultSet row = statement.executeQuery(
                        "SELECT recover_settings FROM game WHERE id=9")) {
            assertTrue(row.next());
            assertEquals(DEFAULT_SETTINGS, row.getString(1));
        }
    }

    @Test
    void latestLocalReadsOnlyTheNewestRecoverableLocalGame() throws Exception {
        database = new DatabaseService(
                temporary.resolve("recovery-test.db").toString());
        database.start();
        try (Statement statement = database.connection().createStatement()) {
            statement.execute("CREATE TABLE game(id INTEGER PRIMARY KEY, start INTEGER,"
                    + " server TEXT, buyin INTEGER, sb REAL, blinds_time INTEGER,"
                    + " blinds_time_type INTEGER, rebuy INTEGER, ugi TEXT, local INTEGER,"
                    + " recover_settings TEXT)");
        }
        insertGame(1, 100L, "old", true, true, DEFAULT_SETTINGS);
        insertGame(2, 300L, "remote", false, true, DEFAULT_SETTINGS);
        insertGame(3, 400L, "unfinished", true, false, DEFAULT_SETTINGS);
        insertGame(4, 200L, " localhost:7234 ", true, true, DEFAULT_SETTINGS);

        RecoverableGameRepository.RecoverableGame recovered
                = new RecoverableGameRepository(database).latestLocal().orElseThrow();

        assertEquals(4, recovered.id());
        assertEquals(200L, recovered.startedAtMillis());
        assertEquals("localhost:7234", recovered.server());
        assertEquals(10, recovered.settings().buyin());
        assertTrue(recovered.voiceMessages());
        assertFalse(recovered.textToSpeech());
    }

    private void insertGame(int id, long start, String server, boolean local,
            boolean recoverable, String settings) throws Exception {
        try (PreparedStatement insert = database.connection().prepareStatement(
                "INSERT INTO game(id,start,server,buyin,sb,blinds_time,"
                + "blinds_time_type,rebuy,ugi,local,recover_settings)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
            insert.setInt(1, id);
            insert.setLong(2, start);
            insert.setString(3, server);
            insert.setInt(4, 10);
            insert.setDouble(5, 0.5);
            insert.setInt(6, 60);
            insert.setInt(7, 1);
            insert.setBoolean(8, true);
            insert.setString(9, recoverable ? "ugi" : null);
            insert.setBoolean(10, local);
            insert.setString(11, settings);
            insert.executeUpdate();
        }
    }
}
