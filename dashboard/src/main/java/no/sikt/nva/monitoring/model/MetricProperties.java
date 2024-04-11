package no.sikt.nva.monitoring.model;

import java.util.List;

public record MetricProperties(String view, boolean stacked, String region, List<List<String>> metrics)
    implements WidgetProperties {

}
