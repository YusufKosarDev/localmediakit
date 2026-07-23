package com.localmediakit.mediakit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmediakit.user.PlanPolicy;
import com.localmediakit.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * "What changed between v{a} and v{b}?" — the second dividend of the
 * append-only version table: both sides are immutable snapshots that already
 * exist, so the diff is a pure function over stored JSON, no new state.
 *
 * Plan rule matches the history view: FREE may only diff versions inside its
 * visible window, PRO any two. Snapshots from older schema generations diff
 * cleanly because every list accessor normalizes null to empty.
 */
@Service
public class VersionDiffService {

    private final MediaKitAccess access;
    private final MediaKitVersionRepository versionRepository;
    private final PlanPolicy planPolicy;
    private final ObjectMapper objectMapper;

    public VersionDiffService(MediaKitAccess access,
                              MediaKitVersionRepository versionRepository,
                              PlanPolicy planPolicy,
                              ObjectMapper objectMapper) {
        this.access = access;
        this.versionRepository = versionRepository;
        this.planPolicy = planPolicy;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public VersionDiffResponse diff(String userEmail, Long kitId, int fromVersion, int toVersion) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        User owner = access.requireUser(userEmail);
        MediaKitSnapshot from = visibleSnapshot(kit, owner, fromVersion);
        MediaKitSnapshot to = visibleSnapshot(kit, owner, toVersion);
        return compute(fromVersion, toVersion, from, to);
    }

    /** Loads one version, enforcing the same plan window as the history list. */
    private MediaKitSnapshot visibleSnapshot(MediaKit kit, User owner, int versionNumber) {
        MediaKitVersion version = versionRepository
                .findByMediaKitIdAndVersionNumber(kit.getId(), versionNumber)
                .orElseThrow(VersionNotFoundException::new);
        long newerCount = versionRepository
                .countByMediaKitIdAndVersionNumberGreaterThan(kit.getId(), versionNumber);
        if (newerCount >= planPolicy.maxVisibleVersions(owner.getPlan())) {
            throw new VersionNotVisibleException();
        }
        try {
            return objectMapper.readValue(version.getContentJson(), MediaKitSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt snapshot content_json", e);
        }
    }

    // --- pure diff (package-private for direct unit testing) ---

    static VersionDiffResponse compute(int fromVersion, int toVersion,
                                       MediaKitSnapshot from, MediaKitSnapshot to) {
        return new VersionDiffResponse(
                fromVersion, toVersion,
                fieldChanges(from, to),
                platformChanges(from, to),
                collaborationChanges(from, to),
                rateCardChanges(from, to),
                demographicChanges(from, to));
    }

    private static List<VersionDiffResponse.FieldChange> fieldChanges(MediaKitSnapshot a, MediaKitSnapshot b) {
        List<VersionDiffResponse.FieldChange> out = new ArrayList<>();
        addFieldIfChanged(out, "slug", a.slug(), b.slug());
        addFieldIfChanged(out, "title", a.title(), b.title());
        addFieldIfChanged(out, "headline", a.headline(), b.headline());
        addFieldIfChanged(out, "avatarUrl", a.avatarUrl(), b.avatarUrl());
        addFieldIfChanged(out, "theme", a.theme(), b.theme());
        addFieldIfChanged(out, "displayName", a.displayName(), b.displayName());
        addFieldIfChanged(out, "showBadge",
                String.valueOf(a.showBadgeOrDefault()), String.valueOf(b.showBadgeOrDefault()));
        addFieldIfChanged(out, "contactEnabled",
                String.valueOf(a.contactEnabledOrDefault()), String.valueOf(b.contactEnabledOrDefault()));
        return out;
    }

    private static void addFieldIfChanged(List<VersionDiffResponse.FieldChange> out,
                                          String field, String from, String to) {
        if (!Objects.equals(from, to)) {
            out.add(new VersionDiffResponse.FieldChange(field, from, to));
        }
    }

    private static List<VersionDiffResponse.PlatformDiff> platformChanges(MediaKitSnapshot a, MediaKitSnapshot b) {
        Map<String, MediaKitSnapshot.PlatformStatSnapshot> before = byKey(
                a.platformsOrEmpty(), MediaKitSnapshot.PlatformStatSnapshot::platform);
        Map<String, MediaKitSnapshot.PlatformStatSnapshot> after = byKey(
                b.platformsOrEmpty(), MediaKitSnapshot.PlatformStatSnapshot::platform);

        List<VersionDiffResponse.PlatformDiff> out = new ArrayList<>();
        for (Map.Entry<String, MediaKitSnapshot.PlatformStatSnapshot> entry : after.entrySet()) {
            MediaKitSnapshot.PlatformStatSnapshot prev = before.get(entry.getKey());
            if (prev == null) {
                out.add(new VersionDiffResponse.PlatformDiff(
                        entry.getKey(), "ADDED", metricChanges(null, entry.getValue())));
            } else {
                List<VersionDiffResponse.MetricChange> changes = metricChanges(prev, entry.getValue());
                if (!changes.isEmpty()) {
                    out.add(new VersionDiffResponse.PlatformDiff(entry.getKey(), "CHANGED", changes));
                }
            }
        }
        for (String platform : before.keySet()) {
            if (!after.containsKey(platform)) {
                out.add(new VersionDiffResponse.PlatformDiff(platform, "REMOVED", List.of()));
            }
        }
        return out;
    }

    private static List<VersionDiffResponse.MetricChange> metricChanges(
            MediaKitSnapshot.PlatformStatSnapshot prev, MediaKitSnapshot.PlatformStatSnapshot next) {
        List<VersionDiffResponse.MetricChange> out = new ArrayList<>();
        addMetricIfChanged(out, "followers",
                prev == null ? null : prev.followers(), next.followers());
        addMetricIfChanged(out, "avgViews", prev == null ? null : prev.avgViews(), next.avgViews());
        addMetricIfChanged(out, "avgLikes", prev == null ? null : prev.avgLikes(), next.avgLikes());
        addMetricIfChanged(out, "avgComments", prev == null ? null : prev.avgComments(), next.avgComments());
        addMetricIfChanged(out, "engagementRate",
                prev == null ? null : prev.engagementRate(), next.engagementRate());
        addMetricIfChanged(out, "followerGrowth30d",
                prev == null ? null : prev.followerGrowth30d(), next.followerGrowth30d());
        return out;
    }

    private static void addMetricIfChanged(List<VersionDiffResponse.MetricChange> out,
                                           String metric, Object from, Object to) {
        // BigDecimal: 8.0 and 8.00 are the same value; compareTo, not equals.
        boolean equal = (from instanceof BigDecimal f && to instanceof BigDecimal t)
                ? f.compareTo(t) == 0
                : Objects.equals(from, to);
        if (!equal) {
            out.add(new VersionDiffResponse.MetricChange(
                    metric, from == null ? null : from.toString(), to == null ? null : to.toString()));
        }
    }

    private static VersionDiffResponse.ListDiff collaborationChanges(MediaKitSnapshot a, MediaKitSnapshot b) {
        Map<String, MediaKitSnapshot.CollaborationSnapshot> before = byKey(
                a.collaborationsOrEmpty(), MediaKitSnapshot.CollaborationSnapshot::brandName);
        Map<String, MediaKitSnapshot.CollaborationSnapshot> after = byKey(
                b.collaborationsOrEmpty(), MediaKitSnapshot.CollaborationSnapshot::brandName);
        // Collaborations change rarely; added/removed by brand is enough signal.
        return new VersionDiffResponse.ListDiff(
                addedKeys(before, after), addedKeys(after, before), List.of());
    }

    private static VersionDiffResponse.ListDiff rateCardChanges(MediaKitSnapshot a, MediaKitSnapshot b) {
        Map<String, MediaKitSnapshot.RateCardSnapshot> before = byKey(
                a.rateCardOrEmpty(), MediaKitSnapshot.RateCardSnapshot::serviceName);
        Map<String, MediaKitSnapshot.RateCardSnapshot> after = byKey(
                b.rateCardOrEmpty(), MediaKitSnapshot.RateCardSnapshot::serviceName);

        List<VersionDiffResponse.MetricChange> changed = new ArrayList<>();
        for (Map.Entry<String, MediaKitSnapshot.RateCardSnapshot> entry : after.entrySet()) {
            MediaKitSnapshot.RateCardSnapshot prev = before.get(entry.getKey());
            if (prev == null) {
                continue;
            }
            String from = price(prev);
            String to = price(entry.getValue());
            if (!from.equals(to)) {
                changed.add(new VersionDiffResponse.MetricChange(entry.getKey(), from, to));
            }
        }
        return new VersionDiffResponse.ListDiff(
                addedKeys(before, after), addedKeys(after, before), changed);
    }

    private static String price(MediaKitSnapshot.RateCardSnapshot item) {
        return item.priceAmount().stripTrailingZeros().toPlainString() + " " + item.currency();
    }

    private static VersionDiffResponse.ListDiff demographicChanges(MediaKitSnapshot a, MediaKitSnapshot b) {
        Function<MediaKitSnapshot.DemographicSnapshot, String> key =
                d -> d.category() + " " + d.label();
        Map<String, MediaKitSnapshot.DemographicSnapshot> before = byKey(a.demographicsOrEmpty(), key);
        Map<String, MediaKitSnapshot.DemographicSnapshot> after = byKey(b.demographicsOrEmpty(), key);

        List<VersionDiffResponse.MetricChange> changed = new ArrayList<>();
        for (Map.Entry<String, MediaKitSnapshot.DemographicSnapshot> entry : after.entrySet()) {
            MediaKitSnapshot.DemographicSnapshot prev = before.get(entry.getKey());
            if (prev != null && prev.percentage().compareTo(entry.getValue().percentage()) != 0) {
                changed.add(new VersionDiffResponse.MetricChange(entry.getKey(),
                        prev.percentage().toPlainString(), entry.getValue().percentage().toPlainString()));
            }
        }
        return new VersionDiffResponse.ListDiff(
                addedKeys(before, after), addedKeys(after, before), changed);
    }

    /** Keys present in {@code in} but absent from {@code notIn} (insertion order). */
    private static <T> List<String> addedKeys(Map<String, T> notIn, Map<String, T> in) {
        return in.keySet().stream().filter(k -> !notIn.containsKey(k)).toList();
    }

    private static <T> Map<String, T> byKey(List<T> items, Function<T, String> key) {
        Map<String, T> map = new LinkedHashMap<>();
        for (T item : items) {
            map.putIfAbsent(key.apply(item), item);
        }
        return map;
    }
}
