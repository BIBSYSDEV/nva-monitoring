package no.sikt.nva.monitoring;

import static no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory.ALARM;
import static no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory.ALARMS;
import static no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory.STATE_UPDATED_TIMESTAMP;
import static no.sikt.nva.monitoring.model.factory.ApiGatewayWidgetFactory.API_NAME;
import static no.sikt.nva.monitoring.model.factory.ApiGatewayWidgetFactory.AWS_API_GATEWAY;
import static no.sikt.nva.monitoring.model.factory.ApiGatewayWidgetFactory.REGION;
import static no.sikt.nva.monitoring.model.factory.ApiGatewayWidgetFactory.NOT_STACKED;
import static no.sikt.nva.monitoring.model.factory.ApiGatewayWidgetFactory.TYPE;
import static no.sikt.nva.monitoring.model.factory.ApiGatewayWidgetFactory.VIEW;
import static no.sikt.nva.monitoring.utils.FakeApiGatewayClient.API_1;
import static no.sikt.nva.monitoring.utils.FakeApiGatewayClient.API_2;
import static no.sikt.nva.monitoring.utils.FakeCloudWatchClient.ALARM_ARN_1;
import static no.sikt.nva.monitoring.utils.FakeCloudWatchClient.ALARM_ARN_2;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.CloudFormationCustomResourceEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import no.sikt.nva.monitoring.model.AlarmProperties;
import no.sikt.nva.monitoring.model.CloudWatchWidget;
import no.sikt.nva.monitoring.model.DashboardBody;
import no.sikt.nva.monitoring.model.MetricProperties;
import no.sikt.nva.monitoring.utils.FakeApiGatewayClient;
import no.sikt.nva.monitoring.utils.FakeCloudWatchClient;
import no.sikt.nva.monitoring.utils.FakeCloudWatchClientThrowingException;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UpdateDashboardHandlerTest {

    public static final MetricProperties METRIC_PROPERTIES_5XX =
        new MetricProperties(VIEW, NOT_STACKED, REGION,
                             (List.of(List.of(AWS_API_GATEWAY,
                                              "5XXError",
                                              API_NAME,
                                              API_1),
                                      List.of(AWS_API_GATEWAY,
                                              "5XXError",
                                              API_NAME,
                                              API_2))));
    public static final MetricProperties METRIC_PROPERTIES_4XX =
        new MetricProperties(VIEW, NOT_STACKED, REGION,
                             (List.of(List.of(AWS_API_GATEWAY,
                                              "4XXError",
                                              API_NAME,
                                              API_1),
                                      List.of(AWS_API_GATEWAY,
                                              "4XXError",
                                              API_NAME,
                                              API_2))));
    public static final MetricProperties METRIC_PROPERTIES_COUNT = new MetricProperties(VIEW, NOT_STACKED, REGION,
                                                                                        (List.of(
                                                                                            List.of(AWS_API_GATEWAY,
                                                                                                    "Count",
                                                                                                    API_NAME,
                                                                                                    API_1),
                                                                                            List.of(AWS_API_GATEWAY,
                                                                                                    "Count",
                                                                                                    API_NAME, API_2))));
    private static final int IGNORED = 0;
    private static final Context mockContext = mock(Context.class);
    private static final CloudFormationCustomResourceEvent EVENT = CloudFormationCustomResourceEvent.builder().build();
    private static final String EXPECTED_ALARM_WIDGET =
        new CloudWatchWidget<>(ALARM, new AlarmProperties(ALARMS, List.of(ALARM_ARN_1, ALARM_ARN_2),
                                                          STATE_UPDATED_TIMESTAMP), IGNORED, IGNORED,
                               IGNORED,
                               IGNORED).toJsonString();
    private static final String EXPECTED_5XX_WIDGET =
        new CloudWatchWidget<>(TYPE, METRIC_PROPERTIES_5XX, IGNORED, IGNORED, IGNORED, IGNORED).toJsonString();
    private static final String EXPECTED_4XX_WIDGET =
        new CloudWatchWidget<>(TYPE, METRIC_PROPERTIES_4XX, IGNORED, IGNORED, IGNORED, IGNORED).toJsonString();
    private static final String EXPECTED_API_GATEWAY_COUNT_WIDGET = new CloudWatchWidget<>(
        TYPE, METRIC_PROPERTIES_COUNT, IGNORED, IGNORED, IGNORED, IGNORED).toJsonString();
    private FakeCloudWatchClient cloudWatchClient;
    private UpdateDashboardHandler handler;
    private FakeApiGatewayClient apiGatewayClient;

    @BeforeEach
    void init() {
        cloudWatchClient = new FakeCloudWatchClient();
        apiGatewayClient = new FakeApiGatewayClient();
        handler = new UpdateDashboardHandler(cloudWatchClient, apiGatewayClient);
    }

    @Test
    void shouldThrowExceptionWhenCloudWatchClientThrowsException() {
        handler = new UpdateDashboardHandler(new FakeCloudWatchClientThrowingException(), apiGatewayClient);
        assertThrows(Exception.class, () -> handler.handleRequest(EVENT,
                                                                  mockContext));
    }

    @Test
    void shouldUpdateDashboardWithAlarmsInWidgetList() throws JsonProcessingException {
        handler.handleRequest(EVENT, mockContext);
        var dashboardBodyString = cloudWatchClient.getPutDashboardRequest().dashboardBody();
        var dashboardBody = JsonUtils.dtoObjectMapper.readValue(dashboardBodyString, DashboardBody.class);
        var expectedAlarmWidget = JsonUtils.dtoObjectMapper.readValue(EXPECTED_ALARM_WIDGET, CloudWatchWidget.class);
        assertThat(dashboardBody.widgets(), hasItem(expectedAlarmWidget));
    }

    @Test
    void shouldUpdateDashboardWith5xxErrorsForApis() throws JsonProcessingException {
        handler.handleRequest(EVENT, mockContext);
        var dashboardBodyString = cloudWatchClient.getPutDashboardRequest().dashboardBody();
        var dashboardBody = JsonUtils.dtoObjectMapper.readValue(dashboardBodyString, DashboardBody.class);
        var expectedApigateway5xxErrors = JsonUtils.dtoObjectMapper.readValue(EXPECTED_5XX_WIDGET,
                                                                              CloudWatchWidget.class);
        assertThat(dashboardBody.widgets(), hasItem(expectedApigateway5xxErrors));
    }

    @Test
    void shouldUpdateDashboardWith4xxErrorsForApis() throws JsonProcessingException {
        handler.handleRequest(EVENT, mockContext);
        var dashboardBodyString = cloudWatchClient.getPutDashboardRequest().dashboardBody();
        var dashboardBody = JsonUtils.dtoObjectMapper.readValue(dashboardBodyString, DashboardBody.class);
        var expectedApigateway4xxErrors = JsonUtils.dtoObjectMapper.readValue(EXPECTED_4XX_WIDGET,
                                                                              CloudWatchWidget.class);
        assertThat(dashboardBody.widgets(), hasItem(expectedApigateway4xxErrors));
    }

    @Test
    void shouldUpdateDashboardWithCountApiGetewayApis() throws JsonProcessingException {
        handler.handleRequest(EVENT, mockContext);
        var dashboardBodyString = cloudWatchClient.getPutDashboardRequest().dashboardBody();
        var dashboardBody = JsonUtils.dtoObjectMapper.readValue(dashboardBodyString, DashboardBody.class);
        var expectedApigatewayCountWidget = JsonUtils.dtoObjectMapper.readValue(EXPECTED_API_GATEWAY_COUNT_WIDGET,
                                                                                CloudWatchWidget.class);
        assertThat(dashboardBody.widgets(), hasItem(expectedApigatewayCountWidget));
    }
}
