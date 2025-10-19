package com.yuzhi.dts.admin.web.rest.dto;

public class PkiChallengeView {

    private final String challengeId;
    private final String nonce;
    private final String aud;
    private final long ts;
    private final long exp;

    public PkiChallengeView(String challengeId, String nonce, String aud, long ts, long exp) {
        this.challengeId = challengeId;
        this.nonce = nonce;
        this.aud = aud;
        this.ts = ts;
        this.exp = exp;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public String getNonce() {
        return nonce;
    }

    public String getAud() {
        return aud;
    }

    public long getTs() {
        return ts;
    }

    public long getExp() {
        return exp;
    }
}
