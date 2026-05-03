package com.yuzhi.dts.platform.service.policy;

import org.springframework.stereotype.Service;

@Service
public class NoopPolicyDecisionService implements PolicyDecisionService {

    @Override
    public PolicyDecision decide(PolicyDecisionContext context) {
        return PolicyDecision.allow("policy-not-configured");
    }
}
