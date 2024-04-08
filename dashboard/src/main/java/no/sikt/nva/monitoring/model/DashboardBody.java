package no.sikt.nva.monitoring.model;

import static java.util.Objects.nonNull;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.core.JacocoGenerated;

public class DashboardBody implements JsonSerializable {

    //For documentation of widgets and dashboardbody: https://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/CloudWatch-Dashboard-Body-Structure.html

    @JsonProperty("widgets")
    private List<CloudWatchWidget> widgets;

    @JacocoGenerated
    public DashboardBody(){
    }

    public List<CloudWatchWidget> getWidgets() {
        return nonNull(widgets) ? widgets : new ArrayList<>();
    }

    public void setWidgets(List<CloudWatchWidget> widgets) {
        this.widgets = widgets;
    }

    @JacocoGenerated
    @Override
    public String toString() {
        return toJsonString();
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
        DashboardBody that = (DashboardBody) o;
        return Objects.equals(widgets, that.widgets);
    }

    @JacocoGenerated
    @Override
    public int hashCode() {
        return Objects.hashCode(widgets);
    }
}
