package no.sikt.nva.monitoring.model;

import no.unit.nva.commons.json.JsonSerializable;

public record MetricWidgetObject(String region, String stat) implements JsonSerializable {

}
