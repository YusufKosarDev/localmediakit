package com.localmediakit.mediakit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugServiceTest {

    private final SlugService slugService = new SlugService();

    @Test
    void slugifyLowercasesAndHyphenates() {
        assertThat(slugService.slugify("Merhaba Dunya")).isEqualTo("merhaba-dunya");
    }

    @Test
    void slugifyHandlesTurkishCharacters() {
        assertThat(slugService.slugify("Cigdem Sofor")).isEqualTo("cigdem-sofor");
        assertThat(slugService.slugify("ĞÜŞİÖÇ")).isEqualTo("gusioc");
        assertThat(slugService.slugify("Yusuf'un Cilgin Kanali!!!")).isEqualTo("yusufun-cilgin-kanali");
    }

    @Test
    void slugifyTrimsAndCollapsesSeparators() {
        assertThat(slugService.slugify("  --Hello--  ")).isEqualTo("hello");
        assertThat(slugService.slugify("a   b___c")).isEqualTo("a-b-c");
    }

    @Test
    void slugifyReturnsEmptyForNullOrSymbolsOnly() {
        assertThat(slugService.slugify(null)).isEmpty();
        assertThat(slugService.slugify("!!!")).isEmpty();
    }

    @Test
    void reservedWordsAreDetected() {
        assertThat(slugService.isReserved("admin")).isTrue();
        assertThat(slugService.isReserved("dashboard")).isTrue();
        // Frontend route segments must never be claimable as kit slugs.
        assertThat(slugService.isReserved("preview")).isTrue();
        assertThat(slugService.isReserved("alice")).isFalse();
    }

    @Test
    void makeUniqueReturnsBaseWhenFree() {
        assertThat(slugService.makeUnique("alice", s -> false)).isEqualTo("alice");
    }

    @Test
    void makeUniqueAppendsSuffixOnCollision() {
        assertThat(slugService.makeUnique("alice", s -> s.equals("alice"))).isEqualTo("alice-2");
        // "alice" and "alice-2" taken -> "alice-3"
        assertThat(slugService.makeUnique("alice", s -> s.equals("alice") || s.equals("alice-2")))
                .isEqualTo("alice-3");
    }

    @Test
    void makeUniqueSkipsReservedBase() {
        // "demo" is reserved even though the predicate says it is free
        assertThat(slugService.makeUnique("demo", s -> false)).isEqualTo("demo-2");
    }

    @Test
    void makeUniqueFallsBackWhenBaseEmpty() {
        assertThat(slugService.makeUnique("", s -> false)).isEqualTo("kit");
    }
}
