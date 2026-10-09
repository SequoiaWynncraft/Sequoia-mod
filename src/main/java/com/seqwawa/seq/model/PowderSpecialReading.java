package com.seqwawa.seq.model;

/**
 * One reading of the local player's powder special.
 *
 * @param fire whether the charging special is Fire's — Courage
 * @param charge how far it has charged, as a 0..1 fraction
 */
public record PowderSpecialReading(boolean fire, double charge) {}
