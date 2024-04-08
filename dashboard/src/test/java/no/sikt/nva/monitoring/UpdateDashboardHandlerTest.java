package no.sikt.nva.monitoring;

import static no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory.ALARMS;
import static no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory.HEIGHT;
import static no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory.METRIC;
import static no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory.WIDTH;
import static no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory.X_COORDINATE;
import static no.sikt.nva.monitoring.model.factory.AlarmWidgetFactory.Y_COORDINATE;
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
import no.sikt.nva.monitoring.utils.FakeCloudWatchClient;
import no.sikt.nva.monitoring.utils.FakeCloudWatchClientThrowingException;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UpdateDashboardHandlerTest {

    private static final Context mockContext = mock(Context.class);
    private static final CloudFormationCustomResourceEvent EVENT = CloudFormationCustomResourceEvent.builder().build();
    private static final String EXPECTED_ALARM_WIDGET = CloudWatchWidget.builder()
                                                            .withType(METRIC)
                                                            .withWidth(WIDTH)
                                                            .withHeight(HEIGHT)
                                                            .withX(X_COORDINATE)
                                                            .withY(Y_COORDINATE)
                                                            .withProperties(AlarmProperties.builder()
                                                                                .withAlarms(
                                                                                    List.of(ALARM_ARN_1,
                                                                                            ALARM_ARN_2))
                                                                                .withTitle(ALARMS).build())
                                                            .build().toJsonString();
    private FakeCloudWatchClient cloudWatchClient;
    private UpdateDashboardHandler handler;

    /*
 Hva vil jeg asserte:
 Jeg vil asserte at det finnes et dashboard med mine widgets.
 Jeg kan gjøre det ved å enten lytte på kallene eller lage noe fake cloudwatchClient implementasjon.
  */

    @BeforeEach
    void init() {
        cloudWatchClient = new FakeCloudWatchClient();
        handler = new UpdateDashboardHandler(cloudWatchClient);
    }

    @Test
    void shouldThrowExceptionWhenCloudWatchClientThrowsException() {
        handler = new UpdateDashboardHandler(new FakeCloudWatchClientThrowingException());
        assertThrows(Exception.class, () -> handler.handleRequest(EVENT,
                                                                  mockContext));
    }

    @Test
    void shouldUpdateDashboardWithAlarmsInWidgetList() throws JsonProcessingException {
        handler.handleRequest(EVENT, mockContext);
        var dashboardBodyString = cloudWatchClient.getPutDashboardRequest().dashboardBody();
        var dashboardBody = JsonUtils.dtoObjectMapper.readValue(dashboardBodyString, DashboardBody.class);
        var expectedAlarmWidget = JsonUtils.dtoObjectMapper.readValue(EXPECTED_ALARM_WIDGET, CloudWatchWidget.class);
        assertThat(dashboardBody.getWidgets(), hasItem(expectedAlarmWidget));
    }
}
