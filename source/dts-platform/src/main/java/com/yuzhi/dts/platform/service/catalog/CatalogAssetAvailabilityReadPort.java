package com.yuzhi.dts.platform.service.catalog;

/** Fail-closed read seam for the current availability generation of a catalog asset. */
public interface CatalogAssetAvailabilityReadPort {

    Availability read(CatalogAssetType assetType, String assetKey);

    record Availability(String status, long epoch, long sourceSequence, String eventId) {
        public static final String AVAILABLE = "AVAILABLE";

        public boolean isAvailable() {
            return AVAILABLE.equals(status);
        }

        public static Availability legacyAvailable() {
            return new Availability(AVAILABLE, 0L, 0L, "legacy-default");
        }
    }
}
