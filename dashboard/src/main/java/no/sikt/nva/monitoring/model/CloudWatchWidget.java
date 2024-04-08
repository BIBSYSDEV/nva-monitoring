package no.sikt.nva.monitoring.model;

import no.unit.nva.commons.json.JsonSerializable;

public record CloudWatchWidget<I> (String type, I properties, int height, int width, int x, int y) implements JsonSerializable {

}
