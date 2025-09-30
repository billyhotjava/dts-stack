package com.yuzhi.dts.common.service;

import java.time.Instant;

public class AuditEventCriteria {
    private String actor;
    private String action;
    private String targetKind;
    private String targetRefPrefix;
    private Instant from;
    private Instant to;

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetKind() {
        return targetKind;
    }

    public void setTargetKind(String targetKind) {
        this.targetKind = targetKind;
    }

    public String getTargetRefPrefix() {
        return targetRefPrefix;
    }

    public void setTargetRefPrefix(String targetRefPrefix) {
        this.targetRefPrefix = targetRefPrefix;
    }

    public Instant getFrom() {
        return from;
    }

    public void setFrom(Instant from) {
        this.from = from;
    }

    public Instant getTo() {
        return to;
    }

    public void setTo(Instant to) {
        this.to = to;
    }
}

