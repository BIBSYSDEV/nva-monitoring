package no.sikt.nva.monitoring;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CloudFormationCustomResourceEvent;
import java.util.List;
import no.sikt.nva.monitoring.model.CloudWatchWidget;
import no.sikt.nva.monitoring.model.DashboardBody;
import no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory;
import nva.commons.core.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.cloudwatch.model.PutDashboardRequest;

public class UpdateDashboardHandler implements RequestHandler<CloudFormationCustomResourceEvent, Void> {

    private static final Logger logger = LoggerFactory.getLogger(UpdateDashboardHandler.class);

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
        logger.info("Trigges ved stack updates");
        //Future plans:
        //List all lambdas in the account
        //Create cloudwatch widgets for lambdas
        cloudWatchClient.putDashboard(PutDashboardRequest.builder()
                                          .dashboardName(dashboardName)
                                          .dashboardBody(createDashBoardBody())
                                          .build());
        //Update sharing policy? and share dashboard
        return null;
    }

    private String createDashBoardBody() {
        var dashboardBody = new DashboardBody();
        dashboardBody.setWidgets(createWidgets());
        return dashboardBody.toJsonString();
    }

    private List<CloudWatchWidget> createWidgets() {
        var alarmWidget = new AlarmWidgetFactory(cloudWatchClient).creatCloudWatchWidget();
        return List.of(alarmWidget);
    }
}
