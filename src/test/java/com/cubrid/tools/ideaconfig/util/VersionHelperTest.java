package com.cubrid.tools.ideaconfig.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class VersionHelperTest {

    @ParameterizedTest
    @CsvSource({
        "JavaSE-1.8, JDK_1_8",
        "JavaSE-11, JDK_11",
        "JavaSE-17, JDK_17",
        "JavaSE-21, JDK_21",
        "J2SE-1.5, JDK_1_5"
    })
    void testToIdeaLanguageLevel(String eclipseEnv, String expected) {
        assertThat(VersionHelper.toIdeaLanguageLevel(eclipseEnv)).isEqualTo(expected);
    }

    @Test
    void testToIdeaLanguageLevelDefaultsToJdk21() {
        assertThat(VersionHelper.toIdeaLanguageLevel(null)).isEqualTo("JDK_21");
        assertThat(VersionHelper.toIdeaLanguageLevel("")).isEqualTo("JDK_21");
        assertThat(VersionHelper.toIdeaLanguageLevel("Unknown-Env")).isEqualTo("JDK_21");
    }

    @Test
    void testToIdeaLanguageLevelExtractsVersionFromUnknownPattern() {
        // Newer JavaSE-NN values not in the table still resolve via the regex.
        assertThat(VersionHelper.toIdeaLanguageLevel("JavaSE-99")).isEqualTo("JDK_99");
    }
}
