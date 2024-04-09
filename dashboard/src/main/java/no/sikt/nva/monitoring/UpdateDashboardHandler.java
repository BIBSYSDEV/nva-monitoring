package no.sikt.nva.monitoring;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CloudFormationCustomResourceEvent;
import java.util.List;
import no.sikt.nva.monitoring.model.CloudWatchWidget;
import no.sikt.nva.monitoring.model.DashboardBody;
import no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory;
import no.sikt.nva.monitoring.model.factory.ApiGatewayWidgetFactory;
import nva.commons.core.Environment;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.cloudwatch.model.PutDashboardRequest;

public class UpdateDashboardHandler implements RequestHandler<CloudFormationCustomResourceEvent, Void> {


    private final CloudWatchClient cloudWatchClient;
    private final String dashboardName;
    private final ApiGatewayClient apiGatewayClient;

    @JacocoGenerated
    public UpdateDashboardHandler() {
        this(CloudWatchClient.builder()
                 .region(Region.EU_WEST_1)
                 .build(),
             ApiGatewayClient.create());
    }

    public UpdateDashboardHandler(CloudWatchClient cloudWatchClient, ApiGatewayClient apiGatewayClient) {
        this.cloudWatchClient = cloudWatchClient;
        this.dashboardName = new Environment().readEnv("DASHBOARD_NAME");
        this.apiGatewayClient = apiGatewayClient;
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
        var apigatewayFactory = new ApiGatewayWidgetFactory(apiGatewayClient);
        var apiGateway5xxWidget = apigatewayFactory.creatCloudWatchWidget(0, "5XXError");
        var apiGateway4xxWidget = apigatewayFactory.creatCloudWatchWidget(1, "4XXError");
        var apiGatewayCountWidget = apigatewayFactory.creatCloudWatchWidget(2, "Count");
        return List.of(alarmWidget, apiGateway5xxWidget, apiGateway4xxWidget, apiGatewayCountWidget);
    }
}
