package no.sikt.nva.monitoring.model;

import java.util.Objects;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.core.JacocoGenerated;

public record CloudWatchWidget<I>(String type, I properties, int height, int width, int x, int y)
    implements JsonSerializable {

    @JacocoGenerated
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CloudWatchWidget<?> that = (CloudWatchWidget<?>) o;
        return Objects.equals(type, that.type) && Objects.equals(properties, that.properties);
    }

    @JacocoGenerated
    @Override
    public int hashCode() {
        return Objects.hash(type, properties);
    }
}
