package com.localmediakit.mediakit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reserved-slug list, checked against the routes it exists to protect.
 *
 * <p>A published kit lives at {@code /<slug>}, which is the same URL space the
 * frontend's own pages occupy. {@link SlugService} keeps a list of the words a
 * slug may not take — and that list is a copy of something owned by another
 * project in this repository. Copies drift in one direction here: a route is
 * added under {@code frontend/app}, it ships, and nothing on this side notices
 * until a creator whose kit is called "reset" finds their page answering with
 * somebody else's screen. The failure is silent, it belongs to a user rather
 * than to a deploy, and no existing test could see it, because every test knew
 * only the list.
 *
 * <p>So this one reads the directory instead. Adding a route to the frontend
 * now breaks the backend build until the word is reserved, which is the whole
 * point: the reminder arrives while the route is being written, not after
 * someone reports a broken link.
 *
 * <p>Reserving more than the routes is allowed — {@code favicon.ico} and
 * friends were never route directories, and {@code settings} is reserved
 * against the day it stops being nested under the dashboard. The assertion runs
 * one way: every segment must be covered, extras are a judgement call.
 */
class ReservedSlugRoutesTest {

    /**
     * Relative because the module root is where Maven runs, in CI as well as on
     * a laptop; the checkout always contains both projects.
     */
    private static final Path APP_DIR = Path.of("..", "frontend", "app");

    private final SlugService slugService = new SlugService();

    @Test
    void everyFrontendRouteSegmentIsReserved() {
        List<String> segments = topLevelRouteSegments();

        // Guard the guard. If this ever reads an empty directory it would pass
        // while checking nothing, which is the failure mode that lets a test
        // stay green for years after it stopped being true.
        assertThat(segments)
                .as("no route segments found under %s — this test is not testing anything",
                        APP_DIR.toAbsolutePath().normalize())
                .isNotEmpty();

        assertThat(segments)
                .as("frontend/app has route segments that a kit slug could claim; "
                        + "add them to SlugService.RESERVED")
                .allMatch(slugService::isReserved);
    }

    /**
     * Every top-level directory under {@code app} that occupies a URL segment.
     *
     * <p>Skipped: private folders ({@code _components}, {@code _i18n}) and route
     * groups, neither of which appears in a URL, and {@code [slug]} itself,
     * which is the kit route this list protects rather than something competing
     * with it. Files are skipped because a segment is a directory; the ones with
     * extensions could not be produced by {@link SlugService#slugify} anyway,
     * which turns a dot into a hyphen.
     *
     * <p>A directory counts whether or not it renders a page today. {@code reset}
     * only serves {@code /reset/<token>}, so {@code /reset} is currently free —
     * but "this segment happens to have no page.tsx" is not a property worth
     * betting a creator's URL on, and adding one is a one-line change nobody
     * would think to check this list for.
     */
    private List<String> topLevelRouteSegments() {
        assertThat(Files.isDirectory(APP_DIR))
                .as("expected the frontend app directory at %s", APP_DIR.toAbsolutePath().normalize())
                .isTrue();

        try (Stream<Path> entries = Files.list(APP_DIR)) {
            return entries
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("_"))
                    .filter(name -> !name.startsWith("("))
                    .filter(name -> !name.startsWith("@"))
                    .filter(name -> !name.startsWith("["))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
