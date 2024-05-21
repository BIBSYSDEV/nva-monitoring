package no.sikt.nva.monitoring.model.factory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import no.sikt.nva.monitoring.model.AlarmProperties;
import no.sikt.nva.monitoring.model.CloudWatchWidget;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.CompositeAlarm;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse;
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm;

public class AlarmWidgetFactory {

    public static final String ALARMS = "alarms";
    public static final int WIDTH = 24;
    public static final int HEIGHT = 8;
    public static final int X_COORDINATE = 0;
    public static final int Y_COORDINATE = 0;
    public static final String STATE_UPDATED_TIMESTAMP = "stateUpdatedTimestamp";
    public static final String ALARM = "alarm";
    private final CloudWatchClient cloudWatchClient;

    public AlarmWidgetFactory(CloudWatchClient cloudWatchClient) {
        this.cloudWatchClient = cloudWatchClient;
    }

    public CloudWatchWidget<AlarmProperties> creatCloudWatchWidget() {
        return new CloudWatchWidget<>(ALARM, createAlarmProperties(), HEIGHT, WIDTH, X_COORDINATE, Y_COORDINATE);
    }

    private AlarmProperties createAlarmProperties() {
        return new AlarmProperties(ALARMS, retrieveExistingAlarms(), STATE_UPDATED_TIMESTAMP);
    }

    private List<String> retrieveExistingAlarms() {
        var alarmsResponse = cloudWatchClient.describeAlarms();
        return extractAlarmArns(alarmsResponse);
    }

    private List<String> extractAlarmArns(DescribeAlarmsResponse alarmsResponse) {
        var alarmsArns = new ArrayList<String>();
        var metricAlarmsArn = alarmsResponse.metricAlarms().stream().map(MetricAlarm::alarmArn).toList();
        if (!metricAlarmsArn.isEmpty()) {
            alarmsArns.addAll(metricAlarmsArn.stream().filter(alarmFilter()).toList());
        }
        var compositeAlarms = alarmsResponse.compositeAlarms().stream().map(CompositeAlarm::alarmArn).toList();
        if (!compositeAlarms.isEmpty()) {
            alarmsArns.addAll(compositeAlarms);
        }
        return alarmsArns;
    }

    private static Predicate<String> alarmFilter() {
        return arn -> !arn.contains(":alarm:TargetTracking-function:");
    }
}
