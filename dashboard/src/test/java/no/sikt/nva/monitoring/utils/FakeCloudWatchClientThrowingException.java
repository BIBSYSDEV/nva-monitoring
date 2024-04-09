package no.sikt.nva.monitoring.utils;

import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.PutDashboardRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutDashboardResponse;

public class FakeCloudWatchClientThrowingException implements CloudWatchClient {

    @Override
    public PutDashboardResponse putDashboard(PutDashboardRequest putDashboardRequest) {
        throw new UnsupportedOperationException("Not supported yet.");
    }


    @Override
    public String serviceName() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void close() {

    }
}
