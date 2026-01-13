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
    void testToIdeaLanguageLevelWithNull() {
        assertThat(VersionHelper.toIdeaLanguageLevel(null)).isEqualTo("JDK_21");
        assertThat(VersionHelper.toIdeaLanguageLevel("")).isEqualTo("JDK_21");
    }

    @Test
    void testParseOsgiVersion() {
        VersionHelper.Version v = VersionHelper.parseOsgiVersion("1.2.3.qualifier");

        assertThat(v.major()).isEqualTo(1);
        assertThat(v.minor()).isEqualTo(2);
        assertThat(v.micro()).isEqualTo(3);
        assertThat(v.qualifier()).isEqualTo("qualifier");
    }

    @Test
    void testParseOsgiVersionWithoutQualifier() {
        VersionHelper.Version v = VersionHelper.parseOsgiVersion("1.2.3");

        assertThat(v.major()).isEqualTo(1);
        assertThat(v.minor()).isEqualTo(2);
        assertThat(v.micro()).isEqualTo(3);
        assertThat(v.qualifier()).isNull();
    }

    @Test
    void testCompareVersions() {
        assertThat(VersionHelper.compareVersions("1.0.0", "2.0.0")).isLessThan(0);
        assertThat(VersionHelper.compareVersions("2.0.0", "1.0.0")).isGreaterThan(0);
        assertThat(VersionHelper.compareVersions("1.0.0", "1.0.0")).isEqualTo(0);
        assertThat(VersionHelper.compareVersions("1.1.0", "1.0.0")).isGreaterThan(0);
        assertThat(VersionHelper.compareVersions("1.0.1", "1.0.0")).isGreaterThan(0);
    }

    @Test
    void testMatchesRangeInclusive() {
        // [1.0.0,2.0.0) - inclusive min, exclusive max
        assertThat(VersionHelper.matchesRange("1.5.0", "[1.0.0,2.0.0)")).isTrue();
        assertThat(VersionHelper.matchesRange("1.0.0", "[1.0.0,2.0.0)")).isTrue();
        assertThat(VersionHelper.matchesRange("2.0.0", "[1.0.0,2.0.0)")).isFalse();
        assertThat(VersionHelper.matchesRange("0.9.0", "[1.0.0,2.0.0)")).isFalse();
    }

    @Test
    void testMatchesRangeExclusive() {
        // (1.0.0,2.0.0) - exclusive both
        assertThat(VersionHelper.matchesRange("1.0.0", "(1.0.0,2.0.0)")).isFalse();
        assertThat(VersionHelper.matchesRange("1.0.1", "(1.0.0,2.0.0)")).isTrue();
    }

    @Test
    void testMatchesRangeSimple() {
        // Simple version (no brackets) means >= version
        assertThat(VersionHelper.matchesRange("1.5.0", "1.0.0")).isTrue();
        assertThat(VersionHelper.matchesRange("0.5.0", "1.0.0")).isFalse();
    }

    @Test
    void testVersionToString() {
        VersionHelper.Version v1 = new VersionHelper.Version(1, 2, 3, "qualifier");
        assertThat(v1.toString()).isEqualTo("1.2.3.qualifier");

        VersionHelper.Version v2 = new VersionHelper.Version(1, 2, 3, null);
        assertThat(v2.toString()).isEqualTo("1.2.3");
    }
}
