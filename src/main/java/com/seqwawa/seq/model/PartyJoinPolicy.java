package com.seqwawa.seq.model;

public enum PartyJoinPolicy {
    OPEN,
    INVITE_ONLY;

    public static final PartyJoinPolicy DEFAULT_CREATE_POLICY = OPEN;
}
