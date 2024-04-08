package no.sikt.nva.monitoring.model;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.core.JacocoGenerated;

@Builder(
    builderClassName = "AlarmPropertiesBuilder",
    toBuilder = true,
    setterPrefix = "with"
)
@Getter
@Setter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class AlarmProperties implements WidgetProperties, JsonSerializable {

    private String title;
    private List<String> alarms;

    @JacocoGenerated
    public AlarmProperties() {

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
        if (!super.equals(o)) {
            return false;
        }
        AlarmProperties that = (AlarmProperties) o;
        return Objects.equals(title, that.title) && Objects.equals(alarms, that.alarms);
    }

    @JacocoGenerated
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), title, alarms);
    }

    @JacocoGenerated
    @Override
    public String toString() {
        return toJsonString();
    }
}
