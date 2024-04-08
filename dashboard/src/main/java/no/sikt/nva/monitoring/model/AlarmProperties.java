package no.sikt.nva.monitoring.model;

import java.util.List;
import no.unit.nva.commons.json.JsonSerializable;

public record AlarmProperties(String title, List<String> alarms, String sortBy) implements WidgetProperties,
                                                                                           JsonSerializable {

}
