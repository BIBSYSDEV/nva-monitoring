package no.sikt.nva.monitoring.model;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.core.JacocoGenerated;

@Builder(
    builderClassName = "CloudWatchWidgetBuilder",
    toBuilder = true,
    setterPrefix = "with"
)
@Getter
@Setter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class CloudWatchWidget<I> implements JsonSerializable {

    private String type;
    private I properties;
    private int height;
    private int width;
    private int x;
    private int y;


    @JacocoGenerated
    public CloudWatchWidget() {
    }

    @JacocoGenerated
    @Override
    public int hashCode() {
        return Objects.hash(type, properties);
    }

    @JacocoGenerated
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var that = (CloudWatchWidget) o;
        return Objects.equals(type, that.type) && Objects.equals(properties, that.properties);
    }

    @JacocoGenerated
    @Override
    public String toString() {
        return toJsonString();
    }
}
