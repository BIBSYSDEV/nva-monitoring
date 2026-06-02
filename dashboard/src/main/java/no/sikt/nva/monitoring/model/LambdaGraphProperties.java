package no.sikt.nva.monitoring.model;

import java.util.List;

public record LambdaGraphProperties(List<List<Object>> metrics,
                                    String title,
                                    String view,
                                    String region,
                                    String stat,
                                    int period,
                                    boolean liveData) implements WidgetProperties {

}
