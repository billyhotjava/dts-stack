package com.yuzhi.dts.platform.domain.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_asset_resolution_failure")
public class CatalogAssetResolutionFailure implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "ref", nullable = false, columnDefinition = "text")
    private String ref;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "caller", length = 128)
    private String caller;

    @Column(name = "type_hint_guess", length = 64)
    private String typeHintGuess;

    @Column(name = "reason", length = 64, nullable = false)
    private String reason;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getCaller() {
        return caller;
    }

    public void setCaller(String caller) {
        this.caller = caller;
    }

    public String getTypeHintGuess() {
        return typeHintGuess;
    }

    public void setTypeHintGuess(String typeHintGuess) {
        this.typeHintGuess = typeHintGuess;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
