package no.sikt.nva.monitoring;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CloudFormationCustomResourceEvent;
import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.monitoring.model.CloudWatchWidget;
import no.sikt.nva.monitoring.model.DashboardBody;
import no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory;
import no.sikt.nva.monitoring.model.factory.ApiGatewayWidgetFactory;
import no.sikt.nva.monitoring.model.factory.DocumentationLinksWidget;
import no.sikt.nva.monitoring.model.factory.LogWidgetFactory;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.PutDashboardRequest;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;

public class UpdateDashboardHandler
    implements RequestHandler<CloudFormationCustomResourceEvent, Void> {

  public static final String FILTER_FOR_5XX_ERRORS =
      "filter @message like /\"status\"\\s*:\\s*\"5\\d{2}\"/";
  public static final String FILTER_FOR_4XX_ERRORS =
      "filter @message like /\"status\"\\s*:\\s*\"4\\d{2}\"/";
  public static final String API_GATEWAY_5XX_ERROR_LOG = "5XX API Gateway Error logs";
  public static final String API_GATEWAY_4XX_ERROR_LOG = "4XX API Gateway Error logs";
  public static final String API_REQUEST_COUNT_WIDGET_NAME = "Count";
  public static final String API_ERRORS_4XX_WIDGET_NAME = "4XXError";
  public static final String API_ERRORS_5XX_WIDGET_NAME = "5XXError";
  public static final int LOG_5XX_X_COORDINATE = 0;
  public static final int LOG_4XX_X_COORDINATE = 12;
  private final CloudWatchClient cloudWatchClient;
  private final CloudWatchLogsClient cloudWatchLogsClient;
  private final String dashboardName;
  private final ApiGatewayClient apiGatewayClient;

  @JacocoGenerated
  public UpdateDashboardHandler() {
    this(CloudWatchClient.create(), ApiGatewayClient.create(), CloudWatchLogsClient.create());
  }

  public UpdateDashboardHandler(
      CloudWatchClient cloudWatchClient,
      ApiGatewayClient apiGatewayClient,
      CloudWatchLogsClient cloudWatchLogsClient) {
    this.cloudWatchClient = cloudWatchClient;
    this.dashboardName = new Environment().readEnv("DASHBOARD_NAME");
    this.apiGatewayClient = apiGatewayClient;
    this.cloudWatchLogsClient = cloudWatchLogsClient;
  }

  @Override
  public Void handleRequest(
      CloudFormationCustomResourceEvent cloudFormationCustomResourceEvent, Context context) {
    // Future plans:
    // List all lambdas in the account
    // Create cloudwatch widgets for lambdas
    cloudWatchClient.putDashboard(
        PutDashboardRequest.builder()
            .dashboardName(dashboardName)
            .dashboardBody(createDashBoardBody())
            .build());
    return null;
  }

  private String createDashBoardBody() {
    return new DashboardBody(createWidgets()).toJsonString();
  }

  private List<CloudWatchWidget> createWidgets() {
    var documentationLinksWidget = DocumentationLinksWidget.create();
    var alarmWidget = new AlarmWidgetFactory(cloudWatchClient).creatCloudWatchWidget();
    var apigatewayFactory = new ApiGatewayWidgetFactory(apiGatewayClient);
    var apiGateway5xxWidget =
        apigatewayFactory.creatCloudWatchWidget(0, API_ERRORS_5XX_WIDGET_NAME);
    var apiGateway4xxWidget =
        apigatewayFactory.creatCloudWatchWidget(1, API_ERRORS_4XX_WIDGET_NAME);
    var apiGatewayCountWidget =
        apigatewayFactory.creatCloudWatchWidget(2, API_REQUEST_COUNT_WIDGET_NAME);
    var logWidgetFactory = new LogWidgetFactory(cloudWatchLogsClient);
    var log5xxWidget =
        logWidgetFactory.createLogWidgetForApiGatewayLogs(
            API_GATEWAY_5XX_ERROR_LOG, FILTER_FOR_5XX_ERRORS, LOG_5XX_X_COORDINATE);
    var log4xxWidget =
        logWidgetFactory.createLogWidgetForApiGatewayLogs(
            API_GATEWAY_4XX_ERROR_LOG, FILTER_FOR_4XX_ERRORS, LOG_4XX_X_COORDINATE);
    var lambdaConcurrencyWidget = LambdaWidget.createConcurrencyWidget();
    var staticWidgets =
        Stream.<CloudWatchWidget>of(
            documentationLinksWidget,
            alarmWidget,
            lambdaConcurrencyWidget,
            apiGateway5xxWidget,
            apiGateway4xxWidget,
            apiGatewayCountWidget,
            log5xxWidget,
            log4xxWidget);
    return Stream.concat(staticWidgets, LambdaWidget.createPerFunctionGraphs().stream()).toList();
  }
}
