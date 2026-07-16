package io.github.maidstorageextension.scan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiscSortPlannerTest {
    @Test
    void emptyWhitelistStorageOutranksStorageWhoseFreshCacheContainsTheItem() {
        FakeTarget source = new FakeTarget("misc", 0, 0, 0);
        FakeTarget whitelist = new FakeTarget("empty-whitelist", 20, 0, 0);
        FakeTarget containing = new FakeTarget("contains-oak", 2, 0, 0);
        FakeStack oak = new FakeStack("minecraft:oak_log", "oak#plain");

        var tasks = MiscSortPlanner.plan(List.of(
                storage(source, true, true, List.of(), List.of(stack(oak, 64))),
                storage(whitelist, false, true, List.of(oak.exactKey()), List.of()),
                storage(containing, false, true, List.of(), List.of(stack(oak, 12)))));

        assertEquals(1, tasks.size());
        assertEquals(List.of(whitelist, containing),
                tasks.get(0).destinations().stream().map(MiscSortPlanner.Destination::target).toList());
        assertEquals(MiscSortPlanner.Match.WHITELIST, tasks.get(0).destinations().get(0).match());
    }

    @Test
    void unmatchedOrdinaryStorageIsNeverUsedAsAnEmptyFallback() {
        FakeTarget source = new FakeTarget("misc", 0, 0, 0);
        FakeTarget ordinaryEmpty = new FakeTarget("ordinary-empty", 1, 0, 0);
        FakeStack helmet = new FakeStack("minecraft:golden_helmet", "helmet#plain");

        assertTrue(MiscSortPlanner.plan(List.of(
                storage(source, true, true, List.of(), List.of(stack(helmet, 1))),
                storage(ordinaryEmpty, false, true, List.of(), List.of()))).isEmpty());
    }

    @Test
    void collectOnlyMiscSourceIsLeftUntouchedBecauseItCannotAcceptAReturn() {
        FakeTarget source = new FakeTarget("collect-only-misc", 0, 0, 0);
        FakeTarget destination = new FakeTarget("logs", 2, 0, 0);
        FakeStack logs = new FakeStack("minecraft:oak_log", "oak#plain");

        assertTrue(MiscSortPlanner.planBatch(List.of(
                        storage(source, true, false, List.of(), List.of(stack(logs, 64))),
                        storage(destination, false, true, List.of(), List.of(stack(logs, 1)))),
                MiscSortPlanner.IgnorePolicy.ITEM_ID, List.of()).sourceJobs().isEmpty());
    }

    @Test
    void allExactStacksFromAllMiscStoragesArePlannedInOnePass() {
        FakeTarget firstMisc = new FakeTarget("misc-a", 0, 0, 0);
        FakeTarget secondMisc = new FakeTarget("misc-b", 5, 0, 0);
        FakeTarget logs = new FakeTarget("logs", 2, 0, 0);
        FakeTarget helmets = new FakeTarget("helmets", 3, 0, 0);
        FakeStack oak = new FakeStack("minecraft:oak_log", "oak#plain");
        FakeStack namedOak = new FakeStack("minecraft:oak_log", "oak#named");
        FakeStack helmet = new FakeStack("minecraft:golden_helmet", "helmet#plain");

        var tasks = MiscSortPlanner.plan(List.of(
                storage(firstMisc, true, true, List.of(), List.of(stack(oak, 64), stack(helmet, 1))),
                storage(secondMisc, true, true, List.of(), List.of(stack(namedOak, 7))),
                storage(logs, false, true, List.of(), List.of(stack(oak, 1))),
                storage(helmets, false, true, List.of(helmet.exactKey()), List.of())));

        assertEquals(3, tasks.size());
        assertEquals(List.of(firstMisc, firstMisc, secondMisc),
                tasks.stream().map(MiscSortPlanner.Transfer::source).toList());
        assertEquals(List.of("minecraft:golden_helmet", "minecraft:oak_log", "minecraft:oak_log"),
                tasks.stream().map(task -> task.stack().itemId()).toList());
    }

    @Test
    void batchPlanGroupsEveryClassifiablePayloadFromOneSource() {
        FakeTarget source = new FakeTarget("misc", 0, 0, 0);
        FakeTarget logs = new FakeTarget("logs", 2, 0, 0);
        FakeTarget helmets = new FakeTarget("helmets", 3, 0, 0);
        FakeStack oak = new FakeStack("minecraft:oak_log", "oak#plain");
        FakeStack helmet = new FakeStack("minecraft:golden_helmet", "helmet#plain");

        var batch = MiscSortPlanner.planBatch(List.of(
                        storage(source, true, true, List.of(), List.of(stack(oak, 128), stack(helmet, 1))),
                        storage(logs, false, true, List.of(), List.of(stack(oak, 1))),
                        storage(helmets, false, true, List.of(helmet.exactKey()), List.of())),
                MiscSortPlanner.IgnorePolicy.ITEM_ID, List.of());

        assertEquals(1, batch.sourceJobs().size());
        assertEquals(2, batch.sourceJobs().get(0).payloads().size());
        assertEquals(List.of("item:minecraft:golden_helmet", "item:minecraft:oak_log"),
                batch.sourceJobs().get(0).payloads().stream()
                        .map(MiscSortPlanner.PayloadPlan::ignoreKey).toList());
    }

    @Test
    void itemPolicySuppressesLaterNbtVariantsAfterFirstVariantCannotBeClassified() {
        FakeTarget source = new FakeTarget("misc", 0, 0, 0);
        FakeTarget namedOnly = new FakeTarget("named-only", 2, 0, 0);
        FakeStack plain = new FakeStack("minecraft:dirt", "dirt#a-plain");
        FakeStack named = new FakeStack("minecraft:dirt", "dirt#b-named");
        var storages = List.of(
                storage(source, true, true, List.of(), List.of(stack(plain, 64), stack(named, 64))),
                storage(namedOnly, false, true, List.of(named.exactKey()), List.of()));

        var itemWide = MiscSortPlanner.planBatch(
                storages, MiscSortPlanner.IgnorePolicy.ITEM_ID, List.of());
        var exact = MiscSortPlanner.planBatch(
                storages, MiscSortPlanner.IgnorePolicy.EXACT_STACK, List.of());

        assertTrue(itemWide.sourceJobs().isEmpty());
        assertEquals(List.of("item:minecraft:dirt"), itemWide.ignoredKeys().stream().sorted().toList());
        assertEquals(1, exact.sourceJobs().get(0).payloads().size());
        assertEquals(named, exact.sourceJobs().get(0).payloads().get(0).stack().stack());
    }

    @Test
    void generationWideIgnoreKeepsEarlierWorkButSkipsLaterVariants() {
        FakeTarget source = new FakeTarget("misc", 0, 0, 0);
        FakeTarget acceptedOnly = new FakeTarget("accepted-only", 2, 0, 0);
        FakeStack accepted = new FakeStack("minecraft:dirt", "dirt#a-accepted");
        FakeStack rejected = new FakeStack("minecraft:dirt", "dirt#z-rejected");

        var batch = MiscSortPlanner.planBatch(List.of(
                        storage(source, true, true, List.of(),
                                List.of(stack(accepted, 32), stack(rejected, 32))),
                        storage(acceptedOnly, false, true,
                                List.of(accepted.exactKey()), List.of())),
                MiscSortPlanner.IgnorePolicy.ITEM_ID, List.of());

        assertEquals(1, batch.sourceJobs().size());
        assertEquals(List.of(accepted), batch.sourceJobs().get(0).payloads().stream()
                .map(payload -> payload.stack().stack()).toList());
        assertEquals(List.of("item:minecraft:dirt"), batch.ignoredKeys().stream().toList());
    }

    @Test
    void cachedSameItemMatchHonorsNbtPolicyWhileWhitelistStaysExact() {
        FakeTarget source = new FakeTarget("misc", 0, 0, 0);
        FakeTarget cachedPlain = new FakeTarget("cached-plain", 2, 0, 0);
        FakeStack plain = new FakeStack("minecraft:dirt", "dirt#plain");
        FakeStack named = new FakeStack("minecraft:dirt", "dirt#named");
        var storages = List.of(
                storage(source, true, true, List.of(), List.of(stack(named, 1))),
                storage(cachedPlain, false, true, List.of(), List.of(stack(plain, 1))));

        assertEquals(1, MiscSortPlanner.planBatch(
                storages, MiscSortPlanner.IgnorePolicy.ITEM_ID, List.of()).sourceJobs().size());
        assertTrue(MiscSortPlanner.planBatch(
                storages, MiscSortPlanner.IgnorePolicy.EXACT_STACK, List.of()).sourceJobs().isEmpty());
    }

    @Test
    void whitelistExactNbtRuleIsUnchangedInBothMaidMatchModes() {
        FakeTarget source = new FakeTarget("misc", 0, 0, 0);
        FakeTarget whitelist = new FakeTarget("whitelist", 8, 0, 0);
        FakeTarget cached = new FakeTarget("cached", 2, 0, 0);
        FakeStack named = new FakeStack("minecraft:golden_helmet", "helmet#named");
        FakeStack plain = new FakeStack("minecraft:golden_helmet", "helmet#plain");
        var accepted = List.of(
                storage(source, true, true, List.of(), List.of(stack(named, 1))),
                storage(whitelist, false, true, List.of(named.exactKey()), List.of()),
                storage(cached, false, true, List.of(), List.of(stack(plain, 1))));

        for (MiscSortPlanner.IgnorePolicy policy : MiscSortPlanner.IgnorePolicy.values()) {
            var batch = MiscSortPlanner.planBatch(accepted, policy, List.of());
            assertEquals(whitelist, batch.sourceJobs().get(0).payloads().get(0)
                    .destinations().get(0).target());
        }

        var rejectedWhitelistOnly = List.of(
                storage(source, true, true, List.of(), List.of(stack(named, 1))),
                storage(whitelist, false, true, List.of(plain.exactKey()), List.of()));
        assertTrue(MiscSortPlanner.planBatch(rejectedWhitelistOnly,
                MiscSortPlanner.IgnorePolicy.ITEM_ID, List.of()).sourceJobs().isEmpty());
    }

    @Test
    void logsAndDifferentlyEnchantedGoldenHelmetsMoveTogetherWhileUnmatchedDirtStays() {
        FakeTarget source = new FakeTarget("misc", 0, 0, 0);
        FakeTarget logChest = new FakeTarget("logs", 2, 0, 0);
        FakeTarget helmetChest = new FakeTarget("helmets", 3, 0, 0);
        FakeStack logs = new FakeStack("minecraft:oak_log", "oak#plain");
        FakeStack helmetProtection = new FakeStack(
                "minecraft:golden_helmet", "helmet#protection");
        FakeStack helmetUnbreaking = new FakeStack(
                "minecraft:golden_helmet", "helmet#unbreaking");
        FakeStack cachedHelmet = new FakeStack(
                "minecraft:golden_helmet", "helmet#plain");
        FakeStack dirt = new FakeStack("minecraft:dirt", "dirt#plain");

        var batch = MiscSortPlanner.planBatch(List.of(
                        storage(source, true, true, List.of(), List.of(
                                stack(logs, 64), stack(helmetProtection, 1),
                                stack(helmetUnbreaking, 1), stack(dirt, 64))),
                        storage(logChest, false, true, List.of(), List.of(stack(logs, 8))),
                        storage(helmetChest, false, true, List.of(), List.of(stack(cachedHelmet, 1)))),
                MiscSortPlanner.IgnorePolicy.ITEM_ID, List.of());

        assertEquals(1, batch.sourceJobs().size());
        assertEquals(List.of(
                        "minecraft:golden_helmet",
                        "minecraft:golden_helmet",
                        "minecraft:oak_log"),
                batch.sourceJobs().get(0).payloads().stream()
                        .map(payload -> payload.stack().itemId()).toList());
        assertTrue(batch.ignoredKeys().contains("item:minecraft:dirt"));
        assertTrue(batch.sourceJobs().get(0).payloads().stream()
                .noneMatch(payload -> payload.stack().itemId().equals("minecraft:dirt")));
    }

    private static MiscSortPlanner.StorageView<FakeTarget, FakeStack> storage(
            FakeTarget target, boolean misc, boolean eligible,
            List<String> whitelistAccepted, List<MiscSortPlanner.StackView<FakeStack>> stacks) {
        return new MiscSortPlanner.StorageView<>(
                target, target.name(), target.name(), target.x(), target.y(), target.z(),
                misc, false, misc && eligible, !misc && eligible, whitelistAccepted, stacks);
    }

    private static MiscSortPlanner.StackView<FakeStack> stack(FakeStack stack, long count) {
        return new MiscSortPlanner.StackView<>(stack.itemId(), stack.exactKey(), stack, count);
    }

    private record FakeTarget(String name, double x, double y, double z) {
    }

    private record FakeStack(String itemId, String exactKey) {
    }
}
