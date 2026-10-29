package com.tonikelope.coronapoker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CustomBotProfileCatalogTest {

    @Test
    void preservesOrderDifficultyAndPersonality() {
        List<CustomBotProfile> profiles = List.of(
                CustomBotProfile.create("Ada", null,
                        NewGameTableDraft.BotDifficulty.MEDIUM,
                        CustomBotProfile.Personality.TAG),
                CustomBotProfile.create("Turing", null,
                        NewGameTableDraft.BotDifficulty.HARD,
                        CustomBotProfile.Personality.NIT));
        Properties properties = new Properties();

        CustomBotProfileCatalog.writeTo(properties, profiles);

        assertEquals(profiles,
                CustomBotProfileCatalog.readFrom(properties));
    }

    @Test
    void missingOptionalValuesUseAaaDefaults() {
        Properties properties = new Properties();
        properties.setProperty(CustomBotProfileCatalog.PROP_COUNT, "1");
        properties.setProperty(CustomBotProfileCatalog.PROP_PREFIX
                + "0.nickname", "Ada$");

        CustomBotProfile profile = CustomBotProfileCatalog
                .readFrom(properties).get(0);

        assertEquals(NewGameTableDraft.BotDifficulty.MEDIUM,
                profile.difficulty());
        assertEquals(CustomBotProfile.Personality.RANDOM,
                profile.personality());
    }

    @Test
    void enabledPreferencePersistsIndependentlyFromTheCatalogue() {
        Properties properties = new Properties();
        assertFalse(CustomBotProfileCatalog.enabled(properties));

        CustomBotProfileCatalog.writeEnabled(properties, true);
        CustomBotProfileCatalog.writeTo(properties, List.of(
                CustomBotProfile.create("Ada", null,
                        NewGameTableDraft.BotDifficulty.MEDIUM,
                        CustomBotProfile.Personality.RANDOM)));

        assertTrue(CustomBotProfileCatalog.enabled(properties));
        assertEquals("true", properties.getProperty(
                CustomBotProfileCatalog.PROP_ENABLED));
        assertEquals(1, CustomBotProfileCatalog.readFrom(properties).size());
    }

    @Test
    void randomSelectionPersistsAndShufflesOneSessionWithoutMutation() {
        Properties properties = new Properties();
        List<CustomBotProfile> profiles = List.of(
                profile("Ada"), profile("Turing"), profile("Grace"),
                profile("Edsger"));

        assertFalse(CustomBotProfileCatalog.randomSelection(properties));
        CustomBotProfileCatalog.writeRandomSelection(properties, true);
        List<CustomBotProfile> first = CustomBotProfileCatalog
                .profilesForSession(profiles, true, new Random(42L));
        List<CustomBotProfile> repeated = CustomBotProfileCatalog
                .profilesForSession(profiles, true, new Random(42L));

        assertTrue(CustomBotProfileCatalog.randomSelection(properties));
        assertEquals(first, repeated);
        assertFalse(first.equals(profiles));
        assertEquals(profiles.stream().map(CustomBotProfile::nickname)
                        .sorted().toList(),
                first.stream().map(CustomBotProfile::nickname)
                        .sorted().toList());
        assertEquals(List.of("Ada$", "Turing$", "Grace$", "Edsger$"),
                profiles.stream().map(CustomBotProfile::nickname).toList());
    }

    private static CustomBotProfile profile(String name) {
        return CustomBotProfile.create(name, null,
                NewGameTableDraft.BotDifficulty.MEDIUM,
                CustomBotProfile.Personality.RANDOM);
    }
}
