package com.yuzhi.dts.platform.service.goldenchain;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class GoldenChainContract {

    private static final List<GoldenChainStage> STAGES = List.copyOf(Arrays.asList(GoldenChainStage.values()));
    private static final List<GoldenChainBlockerCode> BLOCKER_CODES = List.copyOf(Arrays.asList(GoldenChainBlockerCode.values()));

    private GoldenChainContract() {}

    public static List<GoldenChainStage> stages() {
        return STAGES;
    }

    public static List<GoldenChainBlockerCode> blockerCodes() {
        return BLOCKER_CODES;
    }

    public static List<GoldenChainBlockerCode> blockerCodesFor(GoldenChainStage stage) {
        Objects.requireNonNull(stage, "stage must not be null");
        return BLOCKER_CODES.stream().filter(blockerCode -> blockerCode.blockedStage() == stage).toList();
    }

    public static boolean isForwardTransition(GoldenChainStage from, GoldenChainStage to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        return to.sequence() >= from.sequence();
    }
}
