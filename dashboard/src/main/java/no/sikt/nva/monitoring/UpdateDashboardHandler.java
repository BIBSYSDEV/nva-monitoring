package no.sikt.nva.monitoring;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CloudFormationCustomResourceEvent;
import java.util.List;
import no.sikt.nva.monitoring.model.CloudWatchWidget;
import no.sikt.nva.monitoring.model.DashboardBody;
import no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory;
import nva.commons.core.Environment;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.cloudwatch.model.PutDashboardRequest;

public class UpdateDashboardHandler implements RequestHandler<CloudFormationCustomResourceEvent, Void> {


    private final CloudWatchClient cloudWatchClient;
    private final String dashboardName;

    @JacocoGenerated
    public UpdateDashboardHandler() {
        this(CloudWatchClient.builder()
                 .region(Region.EU_WEST_1)
                 .build());
    }

    public UpdateDashboardHandler(CloudWatchClient cloudWatchClient) {
        this.cloudWatchClient = cloudWatchClient;
        this.dashboardName = new Environment().readEnv("DASHBOARD_NAME");
    }

    @Override
    public Void handleRequest(CloudFormationCustomResourceEvent cloudFormationCustomResourceEvent, Context context) {
        //Future plans:
        //List all lambdas in the account
        //Create cloudwatch widgets for lambdas
        cloudWatchClient.putDashboard(PutDashboardRequest.builder()
                                          .dashboardName(dashboardName)
                                          .dashboardBody(createDashBoardBody())
                                          .build());
        return null;
    }

    private String createDashBoardBody() {
        return new DashboardBody(createWidgets()).toJsonString();
    }

    private List<CloudWatchWidget> createWidgets() {
        var alarmWidget = new AlarmWidgetFactory(cloudWatchClient).creatCloudWatchWidget();
        return List.of(alarmWidget);
    }
}
