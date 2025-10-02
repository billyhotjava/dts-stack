package com.yuzhi.dts.admin.service.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditService {

    public static final class AuditEvent {
        public long id;
        public String timestamp;
        public String actor;
        public String action;
        public String resource;
        public String outcome;
        public String detailJson;
        public String targetType;
        public String targetUri;
    }

    private final List<AuditEvent> store = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong seq = new AtomicLong(1);
    private final DtsCommonAuditClient client;

    public AdminAuditService(DtsCommonAuditClient client) {
        this.client = client;
    }

    public AuditEvent record(String actor, String action, String targetKind, String targetRef, String outcome, String detailJson) {
        AuditEvent e = new AuditEvent();
        e.id = seq.getAndIncrement();
        e.timestamp = Instant.now().toString();
        e.actor = actor;
        e.action = action;
        e.targetType = targetKind;
        e.targetUri = targetRef;
        e.resource = targetKind + ":" + Objects.toString(targetRef, "");
        e.outcome = outcome;
        e.detailJson = detailJson;
        store.add(e);
        client.trySend(actor, action, targetKind, targetRef, e.timestamp);
        return e;
    }

    public List<AuditEvent> list(String actor, String action, String resource, String outcome, String targetType, String targetUri) {
        return store
            .stream()
            .filter(x -> actor == null || (x.actor != null && x.actor.contains(actor)))
            .filter(x -> action == null || (x.action != null && x.action.contains(action)))
            .filter(x -> resource == null || (x.resource != null && x.resource.contains(resource)))
            .filter(x -> outcome == null || (x.outcome != null && x.outcome.equals(outcome)))
            .filter(x -> targetType == null || (x.targetType != null && x.targetType.contains(targetType)))
            .filter(x -> targetUri == null || (x.targetUri != null && x.targetUri.contains(targetUri)))
            .toList();
    }
}
