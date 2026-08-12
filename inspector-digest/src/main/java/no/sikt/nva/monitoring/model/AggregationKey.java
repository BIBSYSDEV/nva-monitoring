package no.sikt.nva.monitoring.model;

public record AggregationKey(String vulnerabilityId, String packageName, String packageVersion) {}
