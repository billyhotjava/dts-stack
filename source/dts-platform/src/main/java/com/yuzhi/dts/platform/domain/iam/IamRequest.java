package com.yuzhi.dts.platform.domain.iam;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "iam_request")
public class IamRequest extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "requester", length = 64)
    private String requester;

    @Column(name = "resource", length = 128)
    private String resource;

    @Column(name = "action", length = 32)
    private String action;

    @Column(name = "reason", length = 1024)
    private String reason;

    @Column(name = "status", length = 32)
    private String status;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRequester() {
        return requester;
    }

    public void setRequester(String requester) {
        this.requester = requester;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

