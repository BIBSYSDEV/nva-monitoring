package no.sikt.nva.monitoring.utils;

import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.CompositeAlarm;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse;
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm;
import software.amazon.awssdk.services.cloudwatch.model.PutDashboardRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutDashboardResponse;

public class FakeCloudWatchClient implements CloudWatchClient {

    public static final String ALARM_ARN_1 = "arn:aws:cloudwatch:eu-west-1:some-account:alarm:some-alarm-1";
    public static final String ALARM_ARN_2 = "arn:aws:cloudwatch:eu-west-1:some-account:alarm:some-alarm-2";
    public static final String ALARM_ARN_3 = "arn:aws:cloudwatch:eu-west-1:some-account:alarm:TargetTracking-function"
                                             + ":some-alarm-3";

    private PutDashboardRequest putDashboardRequest;

    @Override
    public DescribeAlarmsResponse describeAlarms() {
        return DescribeAlarmsResponse
                   .builder()
                   .metricAlarms(MetricAlarm.builder().alarmArn(ALARM_ARN_1).build(),
                                 MetricAlarm.builder().alarmArn(ALARM_ARN_3).build())
                   .compositeAlarms(CompositeAlarm.builder().alarmArn(ALARM_ARN_2).build())
                   .build();
    }

    @Override
    public PutDashboardResponse putDashboard(PutDashboardRequest putDashboardRequest) {
        this.putDashboardRequest = putDashboardRequest;
        return PutDashboardResponse.builder().build();
    }

    public PutDashboardRequest getPutDashboardRequest() {
        return putDashboardRequest;
    }

    @Override
    public String serviceName() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void close() {

    }
}
