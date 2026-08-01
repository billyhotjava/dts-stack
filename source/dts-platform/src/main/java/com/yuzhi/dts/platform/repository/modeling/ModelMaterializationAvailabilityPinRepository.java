package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persists the immutable availability generation snapshot owned by one runtime lease. */
@Repository
public class ModelMaterializationAvailabilityPinRepository {

    private final JdbcTemplate jdbcTemplate;

    public ModelMaterializationAvailabilityPinRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Transactional
    public PinnedSnapshot persistSnapshot(
        UUID dispatchId,
        List<AvailabilityPin> requestedPins,
        Instant pinnedAt
    ) {
        if (dispatchId == null || requestedPins == null || pinnedAt == null) {
            throw new IllegalArgumentException("dispatchId, requestedPins and pinnedAt are required");
        }
        List<AvailabilityPin> pins = normalized(requestedPins);
        for (AvailabilityPin pin : pins) {
            jdbcTemplate.update(
                """
                insert into modeling_materialization_source_pin (
                    dispatch_id, source_binding_id, asset_type, asset_key,
                    availability_status, availability_epoch, source_sequence,
                    availability_event_id, resolved_version, pinned_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (dispatch_id, source_binding_id) do nothing
                """,
                dispatchId,
                pin.sourceBindingId(),
                pin.assetType().name(),
                pin.assetKey(),
                pin.status(),
                pin.epoch(),
                pin.sourceSequence(),
                pin.eventId(),
                pin.resolvedVersion(),
                Timestamp.from(pinnedAt)
            );
        }
        int marked = jdbcTemplate.update(
            """
            update modeling_materialization_dispatch
               set availability_pinned_at = ?, availability_pin_count = ?,
                   last_modified_at = ?
             where id = ?
               and profile_lease_id is not null
               and availability_pinned_at is null
               and availability_pin_count is null
            """,
            Timestamp.from(pinnedAt),
            pins.size(),
            Timestamp.from(pinnedAt),
            dispatchId
        );
        if (marked != 0 && marked != 1) {
            throw new IllegalStateException("availability snapshot marker is not unique");
        }
        PinnedSnapshot persisted = loadSnapshot(dispatchId, false);
        if (!persisted.complete() || persisted.pinCount() != pins.size() || !persisted.pins().equals(pins)) {
            throw new IllegalStateException("runtime availability snapshot does not match the durable pin set");
        }
        return persisted;
    }

    @Transactional(readOnly = true)
    public PinnedSnapshot findSnapshot(UUID dispatchId) {
        return loadSnapshot(dispatchId, false);
    }

    @Transactional
    public PinnedSnapshot lockSnapshot(UUID dispatchId) {
        return loadSnapshot(dispatchId, true);
    }

    private PinnedSnapshot loadSnapshot(UUID dispatchId, boolean lock) {
        if (dispatchId == null) {
            throw new IllegalArgumentException("dispatchId is required");
        }
        List<SnapshotMarker> markers = jdbcTemplate.query(
            "select availability_pinned_at, availability_pin_count " +
            "from modeling_materialization_dispatch where id = ?" +
            (lock ? " for update" : ""),
            (row, rowNumber) ->
                new SnapshotMarker(
                    instant(row.getTimestamp("availability_pinned_at")),
                    row.getObject("availability_pin_count", Integer.class)
                ),
            dispatchId
        );
        if (markers.size() != 1) {
            throw new IllegalStateException("materialization dispatch does not exist");
        }
        SnapshotMarker marker = markers.getFirst();
        List<AvailabilityPin> pins = jdbcTemplate.query(
            """
            select source_binding_id, asset_type, asset_key, availability_status,
                   availability_epoch, source_sequence, availability_event_id,
                   resolved_version
              from modeling_materialization_source_pin
             where dispatch_id = ?
             order by source_binding_id
            """,
            (row, rowNumber) ->
                new AvailabilityPin(
                    row.getObject("source_binding_id", UUID.class),
                    CatalogAssetType.valueOf(row.getString("asset_type")),
                    row.getString("asset_key"),
                    row.getString("availability_status"),
                    row.getLong("availability_epoch"),
                    row.getLong("source_sequence"),
                    row.getString("availability_event_id"),
                    row.getString("resolved_version")
                ),
            dispatchId
        );
        boolean complete = marker.pinnedAt() != null && marker.pinCount() != null;
        if (complete && marker.pinCount() != pins.size()) {
            throw new IllegalStateException("materialization availability pin count is inconsistent");
        }
        if (!complete && (!pins.isEmpty() || marker.pinnedAt() != null || marker.pinCount() != null)) {
            throw new IllegalStateException("materialization availability snapshot is partial");
        }
        return new PinnedSnapshot(
            dispatchId,
            complete,
            marker.pinnedAt(),
            marker.pinCount() == null ? 0 : marker.pinCount(),
            List.copyOf(pins)
        );
    }

    private static List<AvailabilityPin> normalized(List<AvailabilityPin> pins) {
        Set<UUID> identities = new HashSet<>();
        List<AvailabilityPin> ordered = pins
            .stream()
            .map(pin -> Objects.requireNonNull(pin, "availability pin is required"))
            .sorted(Comparator.comparing(pin -> pin.sourceBindingId().toString()))
            .toList();
        for (AvailabilityPin pin : ordered) {
            if (
                pin.sourceBindingId() == null ||
                pin.assetType() == null ||
                pin.assetKey() == null ||
                pin.assetKey().isBlank() ||
                !"AVAILABLE".equals(pin.status()) ||
                pin.epoch() < 0L ||
                pin.sourceSequence() < 0L ||
                pin.eventId() == null ||
                pin.eventId().isBlank() ||
                pin.resolvedVersion() == null ||
                pin.resolvedVersion().isBlank()
            ) {
                throw new IllegalArgumentException("availability pin is incomplete");
            }
            if (!identities.add(pin.sourceBindingId())) {
                throw new IllegalArgumentException("source binding availability pin is duplicated");
            }
        }
        return List.copyOf(ordered);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record AvailabilityPin(
        UUID sourceBindingId,
        CatalogAssetType assetType,
        String assetKey,
        String status,
        long epoch,
        long sourceSequence,
        String eventId,
        String resolvedVersion
    ) {}

    public record PinnedSnapshot(
        UUID dispatchId,
        boolean complete,
        Instant pinnedAt,
        int pinCount,
        List<AvailabilityPin> pins
    ) {}

    private record SnapshotMarker(Instant pinnedAt, Integer pinCount) {}
}
