package no.sikt.nva.monitoring.model;

import no.unit.nva.commons.json.JsonSerializable;

public record TextProperties(String markdown) implements WidgetProperties, JsonSerializable {

}
