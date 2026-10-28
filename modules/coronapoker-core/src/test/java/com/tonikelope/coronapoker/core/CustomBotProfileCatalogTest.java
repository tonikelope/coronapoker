package com.tonikelope.coronapoker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import java.util.Properties;
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
}
