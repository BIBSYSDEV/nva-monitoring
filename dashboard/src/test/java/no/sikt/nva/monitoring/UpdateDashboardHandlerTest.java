package no.sikt.nva.monitoring;

import static no.sikt.nva.monitoring.LambdaWidget.ASYNC_EVENT_AGE;
import static no.sikt.nva.monitoring.LambdaWidget.ERRORS;
import static no.sikt.nva.monitoring.LambdaWidget.FUNCTION_NAME_LABEL;
import static no.sikt.nva.monitoring.LambdaWidget.INVOCATIONS;
import static no.sikt.nva.monitoring.LambdaWidget.MAXIMUM_STAT;
import static no.sikt.nva.monitoring.LambdaWidget.METRIC;
import static no.sikt.nva.monitoring.LambdaWidget.PERIOD_5_MINUTES;
import static no.sikt.nva.monitoring.LambdaWidget.SEARCH_EXPRESSION_TEMPLATE;
import static no.sikt.nva.monitoring.LambdaWidget.SUM_STAT;
import static no.sikt.nva.monitoring.LambdaWidget.THROTTLES;
import static no.sikt.nva.monitoring.LambdaWidget.TIME_SERIES;
import static no.sikt.nva.monitoring.UpdateDashboardHandler.API_ERRORS_4XX_WIDGET_NAME;
import static no.sikt.nva.monitoring.UpdateDashboardHandler.API_ERRORS_5XX_WIDGET_NAME;
import static no.sikt.nva.monitoring.UpdateDashboardHandler.API_REQUEST_COUNT_WIDGET_NAME;
import static no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory.ALARM;
import static no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory.ALARMS;
import static no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory.ALARM_STATE;
import static no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory.STATE_UPDATED_TIMESTAMP;
import static no.sikt.nva.monitoring.model.factory.ApiGatewayWidgetFactory.API_NAME;
import static no.sikt.nva.monitoring.model.factory.ApiGatewayWidgetFactory.AWS_API_GATEWAY;
import static no.sikt.nva.monitoring.model.factory.ApiGatewayWidgetFactory.NOT_STACKED;
import static no.sikt.nva.monitoring.model.factory.ApiGatewayWidgetFactory.REGION;
import static no.sikt.nva.monitoring.model.factory.ApiGatewayWidgetFactory.TYPE;
import static no.sikt.nva.monitoring.model.factory.ApiGatewayWidgetFactory.VIEW;
import static no.sikt.nva.monitoring.model.factory.DocumentationLinksWidget.MARKDOWN;
import static no.sikt.nva.monitoring.model.factory.DocumentationLinksWidget.TEXT;
import static no.sikt.nva.monitoring.utils.FakeApiGatewayClient.API_1;
import static no.sikt.nva.monitoring.utils.FakeApiGatewayClient.API_2;
import static no.sikt.nva.monitoring.utils.FakeApiGatewayClient.METRIC_WIDGET_OBJECT;
import static no.sikt.nva.monitoring.utils.FakeCloudWatchClient.ALARM_ARN_1;
import static no.sikt.nva.monitoring.utils.FakeCloudWatchClient.ALARM_ARN_2;
import static no.sikt.nva.monitoring.utils.FakeCloudWatchClient.ALARM_ARN_3;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.CloudFormationCustomResourceEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Path;
import java.util.List;
import no.sikt.nva.monitoring.model.AlarmProperties;
import no.sikt.nva.monitoring.model.CloudWatchWidget;
import no.sikt.nva.monitoring.model.DashboardBody;
import no.sikt.nva.monitoring.model.LambdaGraphProperties;
import no.sikt.nva.monitoring.model.LogProperties;
import no.sikt.nva.monitoring.model.MetricProperties;
import no.sikt.nva.monitoring.model.MetricSearchExpression;
import no.sikt.nva.monitoring.model.TextProperties;
import no.sikt.nva.monitoring.utils.FakeApiGatewayClient;
import no.sikt.nva.monitoring.utils.FakeCloudWatchClient;
import no.sikt.nva.monitoring.utils.FakeCloudWatchClientThrowingException;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;

public class UpdateDashboardHandlerTest {

  public static final MetricProperties METRIC_PROPERTIES_5XX =
      new MetricProperties(
          VIEW,
          NOT_STACKED,
          REGION,
          (List.of(
              List.of(
                  AWS_API_GATEWAY,
                  API_ERRORS_5XX_WIDGET_NAME,
                  API_NAME,
                  API_1,
                  METRIC_WIDGET_OBJECT),
              List.of(
                  AWS_API_GATEWAY,
                  API_ERRORS_5XX_WIDGET_NAME,
                  API_NAME,
                  API_2,
                  METRIC_WIDGET_OBJECT))));
  public static final MetricProperties METRIC_PROPERTIES_4XX =
      new MetricProperties(
          VIEW,
          NOT_STACKED,
          REGION,
          (List.of(
              List.of(
                  AWS_API_GATEWAY,
                  API_ERRORS_4XX_WIDGET_NAME,
                  API_NAME,
                  API_1,
                  METRIC_WIDGET_OBJECT),
              List.of(
                  AWS_API_GATEWAY,
                  API_ERRORS_4XX_WIDGET_NAME,
                  API_NAME,
                  API_2,
                  METRIC_WIDGET_OBJECT))));
  public static final MetricProperties METRIC_PROPERTIES_COUNT =
      new MetricProperties(
          VIEW,
          NOT_STACKED,
          REGION,
          (List.of(
              List.of(
                  AWS_API_GATEWAY,
                  API_REQUEST_COUNT_WIDGET_NAME,
                  API_NAME,
                  API_1,
                  METRIC_WIDGET_OBJECT),
              List.of(
                  AWS_API_GATEWAY,
                  API_REQUEST_COUNT_WIDGET_NAME,
                  API_NAME,
                  API_2,
                  METRIC_WIDGET_OBJECT))));
  private static final int IGNORED = 0;
  private static final Context mockContext = mock(Context.class);
  private static final CloudFormationCustomResourceEvent EVENT =
      CloudFormationCustomResourceEvent.builder().build();
  private static final String EXPECTED_ALARM_WIDGET =
      new CloudWatchWidget<>(
              ALARM,
              new AlarmProperties(
                  ALARMS, List.of(ALARM_ARN_1, ALARM_ARN_2),
                  STATE_UPDATED_TIMESTAMP, List.of(ALARM_STATE)),
              IGNORED,
              IGNORED,
              IGNORED,
              IGNORED)
          .toJsonString();
  private static final String EXPECTED_DOCUMENTATION_LINKS_WIDGET =
      new CloudWatchWidget<>(TEXT, new TextProperties(MARKDOWN), IGNORED, IGNORED, IGNORED, IGNORED)
          .toJsonString();
  private static final String EXPECTED_5XX_WIDGET =
      new CloudWatchWidget<>(TYPE, METRIC_PROPERTIES_5XX, IGNORED, IGNORED, IGNORED, IGNORED)
          .toJsonString();
  private static final String EXPECTED_4XX_WIDGET =
      new CloudWatchWidget<>(TYPE, METRIC_PROPERTIES_4XX, IGNORED, IGNORED, IGNORED, IGNORED)
          .toJsonString();
  private static final String EXPECTED_API_GATEWAY_COUNT_WIDGET =
      new CloudWatchWidget<>(TYPE, METRIC_PROPERTIES_COUNT, IGNORED, IGNORED, IGNORED, IGNORED)
          .toJsonString();
  public static final String EXPECTED_LOG_QUERY =
      "SOURCE 'master-pipelines-testLogGroup-ApiAccessLogGroup' "
          + "| filter @message like /\"status\"\\s*:\\s*\"5\\d{2}\"/ "
          + "| fields httpMethod, path, status "
          + "| stats count() as ErrorCount by httpMethod, path, status "
          + "| sort ErrorCount desc "
          + "| limit 100";
  public static final String LAMBDA_WIDGET_JSON = "lambda_widget.json";
  public static final String EXPECTED_LAMBDA_WIDGET =
      IoUtils.stringFromResources(Path.of(LAMBDA_WIDGET_JSON));
  private UpdateDashboardHandler handler;
  private FakeCloudWatchClient cloudWatchClient;
  private FakeApiGatewayClient apiGatewayClient;
  private CloudWatchLogsClient cloudWatchLogsClient;

  @BeforeEach
  void init() {
    cloudWatchClient = new FakeCloudWatchClient();
    apiGatewayClient = new FakeApiGatewayClient();
    cloudWatchLogsClient = mockedCloudWatchLogsClient();
    handler = new UpdateDashboardHandler(cloudWatchClient, apiGatewayClient, cloudWatchLogsClient);
  }

  private CloudWatchLogsClient mockedCloudWatchLogsClient() {
    cloudWatchLogsClient = mock(CloudWatchLogsClient.class);
    when(cloudWatchLogsClient.describeLogGroups((DescribeLogGroupsRequest) any()))
        .thenReturn(
            DescribeLogGroupsResponse.builder()
                .nextToken(null)
                .logGroups(
                    List.of(
                        LogGroup.builder()
                            .creationTime(10L)
                            .logGroupName("master-pipelines-testLogGroup-ApiAccessLogGroup")
                            .build()))
                .build());
    return cloudWatchLogsClient;
  }

  @Test
  void shouldThrowExceptionWhenCloudWatchClientThrowsException() {
    handler =
        new UpdateDashboardHandler(
            new FakeCloudWatchClientThrowingException(), apiGatewayClient, cloudWatchLogsClient);
    assertThrows(Exception.class, () -> handler.handleRequest(EVENT, mockContext));
  }

  @Test
  void shouldUpdateDashboardWithAlarmsInWidgetList() throws JsonProcessingException {
    handler.handleRequest(EVENT, mockContext);
    var dashboardBody = getDashboardBody();
    var expectedAlarmWidget =
        JsonUtils.dtoObjectMapper.readValue(EXPECTED_ALARM_WIDGET, CloudWatchWidget.class);
    assertThat(dashboardBody.widgets(), hasItem(expectedAlarmWidget));
  }

  @Test
  void shouldUpdateDashboardAlarmsWithoutTargetTracking() throws JsonProcessingException {
    handler.handleRequest(EVENT, mockContext);
    var dashboardBody = getDashboardBody();
    var alarms = getAlarmArns(dashboardBody);
    assertThat(alarms, not(hasItem(ALARM_ARN_3)));
  }

  @Test
  void shouldUpdateDashboardWith5xxErrorsForApis() throws JsonProcessingException {
    handler.handleRequest(EVENT, mockContext);
    var dashboardBody = getDashboardBody();
    var expectedApigateway5xxErrors =
        JsonUtils.dtoObjectMapper.readValue(EXPECTED_5XX_WIDGET, CloudWatchWidget.class);
    assertThat(dashboardBody.widgets(), hasItem(expectedApigateway5xxErrors));
  }

  @Test
  void shouldUpdateDashboardWith4xxErrorsForApis() throws JsonProcessingException {
    handler.handleRequest(EVENT, mockContext);
    var dashboardBody = getDashboardBody();
    var expectedApigateway4xxErrors =
        JsonUtils.dtoObjectMapper.readValue(EXPECTED_4XX_WIDGET, CloudWatchWidget.class);
    assertThat(dashboardBody.widgets(), hasItem(expectedApigateway4xxErrors));
  }

  @Test
  void shouldUpdateDashboardWithCountApiGetewayApis() throws JsonProcessingException {
    handler.handleRequest(EVENT, mockContext);
    var dashboardBody = getDashboardBody();
    var expectedApigatewayCountWidget =
        JsonUtils.dtoObjectMapper.readValue(
            EXPECTED_API_GATEWAY_COUNT_WIDGET, CloudWatchWidget.class);
    assertThat(dashboardBody.widgets(), hasItem(expectedApigatewayCountWidget));
  }

  @Test
  void shouldUpdateDashboardWithCloudWatchApiGatewayMasterPipelineLogs()
      throws JsonProcessingException {
    handler.handleRequest(EVENT, mockContext);
    var dashboardBody = getDashboardBody();
    var expectedCloudWatchLogsWidget =
        new CloudWatchWidget<>(
                "log",
                LogProperties.builder()
                    .withRegion("eu-west-1")
                    .withTitle("5XX API Gateway Error logs")
                    .withView("table")
                    .withQuery(EXPECTED_LOG_QUERY)
                    .build(),
                6,
                12,
                12,
                24)
            .toJsonString();
    var expectedWidget =
        JsonUtils.dtoObjectMapper.readValue(expectedCloudWatchLogsWidget, CloudWatchWidget.class);

    assertThat(dashboardBody.widgets(), hasItem(expectedWidget));
  }

  @Test
  void shouldUpdateDashboardWithLambdaWidget() throws JsonProcessingException {
    handler.handleRequest(EVENT, mockContext);
    var dashboardBody = getDashboardBody();
    var expectedLambdaWidget =
        JsonUtils.dtoObjectMapper.readValue(EXPECTED_LAMBDA_WIDGET, CloudWatchWidget.class);

    assertThat(dashboardBody.widgets(), hasItem(expectedLambdaWidget));
  }

  @Test
  void shouldUpdateDashboardWithDocumentationLinks() throws JsonProcessingException {
    handler.handleRequest(EVENT, mockContext);
    var dashboardBody = getDashboardBody();
    var expectedWidget =
        JsonUtils.dtoObjectMapper.readValue(
            EXPECTED_DOCUMENTATION_LINKS_WIDGET, CloudWatchWidget.class);
    assertThat(dashboardBody.widgets(), hasItem(expectedWidget));
  }

  @Test
  void shouldUpdateDashboardWithPerFunctionLambdaGraphs() throws JsonProcessingException {
    handler.handleRequest(EVENT, mockContext);
    var dashboardBody = getDashboardBody();
    assertThat(dashboardBody.widgets(), hasItem(expectedPerFunctionGraph(ERRORS, SUM_STAT)));
    assertThat(dashboardBody.widgets(), hasItem(expectedPerFunctionGraph(THROTTLES, SUM_STAT)));
    assertThat(dashboardBody.widgets(), hasItem(expectedPerFunctionGraph(INVOCATIONS, SUM_STAT)));
    assertThat(
        dashboardBody.widgets(), hasItem(expectedPerFunctionGraph(ASYNC_EVENT_AGE, MAXIMUM_STAT)));
  }

  private static CloudWatchWidget expectedPerFunctionGraph(String metricName, String stat)
      throws JsonProcessingException {
    var expression = SEARCH_EXPRESSION_TEMPLATE.formatted(metricName, stat, PERIOD_5_MINUTES);
    List<List<Object>> metrics =
        List.of(List.of(new MetricSearchExpression(expression, FUNCTION_NAME_LABEL)));
    var properties =
        new LambdaGraphProperties(
            metrics, metricName, TIME_SERIES, REGION, stat, PERIOD_5_MINUTES, true);
    var widget =
        new CloudWatchWidget<>(METRIC, properties, IGNORED, IGNORED, IGNORED, IGNORED)
            .toJsonString();
    return JsonUtils.dtoObjectMapper.readValue(widget, CloudWatchWidget.class);
  }

  private static List<String> getAlarmArns(DashboardBody dashboardBody) {
    return dashboardBody.widgets().stream()
        .filter(widget -> widget.type().equals(ALARM))
        .map(
            alarmWidget ->
                JsonUtils.dtoObjectMapper.convertValue(
                    alarmWidget.properties(), AlarmProperties.class))
        .map(AlarmProperties::alarms)
        .flatMap(List::stream)
        .toList();
  }

  private DashboardBody getDashboardBody() throws JsonProcessingException {
    var dashboardBodyString = cloudWatchClient.getPutDashboardRequest().dashboardBody();
    return JsonUtils.dtoObjectMapper.readValue(dashboardBodyString, DashboardBody.class);
  }
}
