package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable Candidate command keys for the local publication commit and publication retry receipt. */
public final class CandidatePublicationKeys {

    private CandidatePublicationKeys() {}

    public static String finalCommit(CandidateView candidate, String publishRequestKey) {
        if (candidate == null) throw new IllegalArgumentException("candidate is required");
        if (candidate.status() == DeliveryStatus.PUBLISHING) {
            return "candidate-publication-commit:" + candidate.id() + ":v" + candidate.version();
        }
        if (candidate.status() == DeliveryStatus.PARTIAL) {
            return publicationRetry(publishRequestKey);
        }
        throw new IllegalArgumentException("Candidate must be PUBLISHING or PARTIAL");
    }

    public static String publicationRetry(String publishRequestKey) {
        if (publishRequestKey == null || publishRequestKey.isBlank()) {
            throw new IllegalArgumentException("publishRequestKey is required");
        }
        return "candidate-publication-retry:" + sha256(publishRequestKey.trim());
    }

    private static String sha256(String value) {
        try {
            return HexFormat
                .of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
