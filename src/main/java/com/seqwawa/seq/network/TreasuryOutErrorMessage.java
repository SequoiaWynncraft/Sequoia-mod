package com.seqwawa.seq.network;

/** Correlated backend error for a treasury submission. */
public record TreasuryOutErrorMessage(String requestId, String code, String message) {}
