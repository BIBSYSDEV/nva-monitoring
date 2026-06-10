package no.sikt.nva.monitoring.model;

import no.unit.nva.commons.json.JsonSerializable;

public record MetricSearchExpression(String expression, String label) implements JsonSerializable {}
