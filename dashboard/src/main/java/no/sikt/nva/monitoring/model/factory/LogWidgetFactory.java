package no.sikt.nva.monitoring.model.factory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import no.sikt.nva.monitoring.model.CloudWatchWidget;
import no.sikt.nva.monitoring.model.LogProperties;
import no.sikt.nva.monitoring.model.LogQuery;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;

public class LogWidgetFactory {

    public static final String LOG = "log";
    public static final String AWS_LAMBDA_LOG_GROUP_PREFIX = "/aws/lambda/";
    private final CloudWatchLogsClient cloudWatchLogsClient;
    private final LambdaClient lambdaClient;

    public LogWidgetFactory(CloudWatchLogsClient cloudWatchLogsClient, LambdaClient lambdaClient) {
        this.cloudWatchLogsClient = cloudWatchLogsClient;
        this.lambdaClient = lambdaClient;

    }

    public CloudWatchWidget<LogProperties> createLogWidget(String title, String filter) {
        var logGroups = fetchNewestLambdaLogGroups(this.lambdaClient, cloudWatchLogsClient);
        var query = constructQueryForLogGroups(logGroups, filter);
        var logProperties = LogProperties.builder()
                                .withRegion(Region.EU_WEST_1.toString())
                                .withTitle(title)
                                .withView("table")
                                .withQuery(query)
                                .build();
        return new CloudWatchWidget<>(LOG, logProperties, 6, 12, 12, 24);
    }

    private static String constructQueryForLogGroups(List<String> logGroups, String filter) {
        return LogQuery.builder()
                   .withLogGroups(logGroups)
                   .withFilter(filter)
                   .withFields("fields @timestamp, @message, @logStream, @log")
                   .withSort("sort @timestamp desc")
                   .withLimit("limit 100")
                   .build().constructQuery();
    }

    private static ArrayList<String> fetchNewestLambdaLogGroups(LambdaClient lambdaClient,
                                                                CloudWatchLogsClient cloudWatchLogsClient) {
        var functions = fetchLamdaFunctions(lambdaClient);
        var logGroups = new ArrayList<String>();
        for (FunctionConfiguration function : functions) {

            var logGroupsRequest = createDescribeLambdaLogGroupsRequest(function);

            cloudWatchLogsClient.describeLogGroups(logGroupsRequest).logGroups().stream()
                .max(Comparator.comparing(LogGroup::creationTime))
                .map(LogGroup::logGroupName)
                .map(logGroups::add);
        }
        return logGroups;
    }

    private static DescribeLogGroupsRequest createDescribeLambdaLogGroupsRequest(FunctionConfiguration function) {
        return DescribeLogGroupsRequest.builder()
                   .logGroupNamePrefix(AWS_LAMBDA_LOG_GROUP_PREFIX + function.functionName())
                   .build();
    }

    private static List<FunctionConfiguration> fetchLamdaFunctions(LambdaClient lambdaClient) {
        return lambdaClient.listFunctions(ListFunctionsRequest.builder().build()).functions();
    }
}
