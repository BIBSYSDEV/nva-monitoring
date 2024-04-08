package no.sikt.nva.monitoring.model.factory;

import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.monitoring.model.AlarmProperties;
import no.sikt.nva.monitoring.model.CloudWatchWidget;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.CompositeAlarm;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse;
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm;

public class AlarmWidgetFactory {

    public static final String METRIC = "metric";
    public static final String ALARMS = "alarms";
    public static final int WIDTH = 24;
    public static final int HEIGHT = 6;
    public static final int X_COORDINATE = 0;
    public static final int Y_COORDINATE = 0;
    public static final String STATE_UPDATED_TIMESTAMP = "stateUpdatedTimestamp";
    public static final String ALARM = "alarm";
    private final CloudWatchClient cloudWatchClient;

    public AlarmWidgetFactory(CloudWatchClient cloudWatchClient) {
        this.cloudWatchClient = cloudWatchClient;
    }

    public CloudWatchWidget creatCloudWatchWidget() {
        return CloudWatchWidget.builder()
                   .withType(ALARM)
                   .withWidth(WIDTH)
                   .withHeight(HEIGHT)
                   .withX(X_COORDINATE)
                   .withY(Y_COORDINATE)
                   .withProperties(createAlarmProperties())
                   .build();
    }

    private AlarmProperties createAlarmProperties() {
        return AlarmProperties.builder()
                   .withTitle(ALARMS)
                   .withSortBy(STATE_UPDATED_TIMESTAMP)
                   .withAlarms(retrieveExistingAlarms())
                   .build();
    }

    private List<String> retrieveExistingAlarms() {
        var alarmsResponse = cloudWatchClient.describeAlarms();
        return extractAlarmArns(alarmsResponse);
    }

    private List<String> extractAlarmArns(DescribeAlarmsResponse alarmsResponse) {
        var alarmsArns = new ArrayList<String>();
        var metricAlarmsArn = alarmsResponse.metricAlarms().stream().map(MetricAlarm::alarmArn).toList();
        if (!metricAlarmsArn.isEmpty()) {
            alarmsArns.addAll(metricAlarmsArn);
        }
        var compositeAlarms = alarmsResponse.compositeAlarms().stream().map(CompositeAlarm::alarmArn).toList();
        if (!compositeAlarms.isEmpty()) {
            alarmsArns.addAll(compositeAlarms);
        }
        return alarmsArns;
    }
}
