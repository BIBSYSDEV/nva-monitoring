package no.sikt.nva.monitoring.model;

import java.util.List;
import no.unit.nva.commons.json.JsonSerializable;

public record DashboardBody(List<CloudWatchWidget> widgets) implements JsonSerializable {

    //For documentation of widgets and dashboardbody:
    // https://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/CloudWatch-Dashboard-Body-Structure.html
}
