package no.sikt.nva.monitoring.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;

public record LogProperties(String region, String title, String query, String view) {

    public static final String LOG = "log";

    public CloudWatchWidget<LogProperties> toCloudWatchWidget(LambdaClient lambdaClient,
                                                              CloudWatchLogsClient cloudWatchLogsClient) {
        var logGroups = fetchNewestLambdaLogGroups(lambdaClient, cloudWatchLogsClient);
        var query = constructQueryForLogGroups(logGroups);
        return new CloudWatchWidget<>(LOG, this, 6, 12, 12, 24);
    }

    private static String constructQueryForLogGroups(ArrayList<String> logGroups) {
        return LogQuery.builder()
                        .withLogGroups(logGroups)
                        .withFilter("filter @message like /5\\d{2}/")
                        .withFields("fields @timestamp, @message, @logStream, @log")
                        .withSort("sort @timestamp desc")
                        .withLimit("limit 10000")
                        .build().constructQuery();
    }

    private ArrayList<String> fetchNewestLambdaLogGroups(LambdaClient lambdaClient, CloudWatchLogsClient cloudWatchLogsClient) {
        var functions = fetchLamdaFunctions(lambdaClient);
        var logGroups = new ArrayList<String>();
        for (FunctionConfiguration function : functions) {

            var logGroupsRequest = DescribeLogGroupsRequest.builder()
                                       .logGroupNamePrefix("/aws/lambda/" + function.functionName())
                                       .build();

            cloudWatchLogsClient.describeLogGroups(logGroupsRequest).logGroups().stream()
                .max(Comparator.comparing(LogGroup::creationTime))
                .map(LogGroup::logGroupName)
                .map(logGroups::add);
        }
        return logGroups;
    }

    private List<FunctionConfiguration> fetchLamdaFunctions(LambdaClient lambdaClient) {
        return lambdaClient.listFunctions(ListFunctionsRequest.builder().build()).functions();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String region;
        private String title;
        private String query;
        private String view;

        private Builder() {
        }

        public Builder withRegion(String region) {
            this.region = region;
            return this;
        }

        public Builder withTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder withQuery(String query) {
            this.query = query;
            return this;
        }

        public Builder withView(String view) {
            this.view = view;
            return this;
        }

        public LogProperties build() {
            return new LogProperties(region, title, query, view);
        }
    }
}