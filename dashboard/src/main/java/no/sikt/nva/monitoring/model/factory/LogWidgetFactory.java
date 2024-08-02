package no.sikt.nva.monitoring.model.factory;

import java.util.Collection;
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
    public static final String TABLE_VIEW = "table";
    public static final String LIMIT_100 = "limit 100";
    public static final String SORT_TIMESTAMP_DESC = "sort @timestamp desc";
    public static final String DEFAULT_FIELDS = "fields @timestamp, @message, @logStream, @log";
    public static final String API_ACCESS_LOG_GROUP = "ApiAccessLogGroup";
    private final CloudWatchLogsClient cloudWatchLogsClient;
    private final LambdaClient lambdaClient;

    public LogWidgetFactory(CloudWatchLogsClient cloudWatchLogsClient, LambdaClient lambdaClient) {
        this.cloudWatchLogsClient = cloudWatchLogsClient;
        this.lambdaClient = lambdaClient;
    }

    public CloudWatchWidget<LogProperties> createLogWidget(String title, String filter) {
        var logGroups = fetchNewestLambdaLogGroups();
        var query = constructQueryForLogGroupsWithFilter(logGroups, filter);
        return new CloudWatchWidget<>(LOG, constructLogProperties(title, query), 6, 24, 12, 24);
    }

    private static LogProperties constructLogProperties(String title, String query) {
        return LogProperties.builder()
                   .withRegion(Region.EU_WEST_1.toString())
                   .withTitle(title)
                   .withView(TABLE_VIEW)
                   .withQuery(query)
                   .build();
    }

    private static String constructQueryForLogGroupsWithFilter(List<String> logGroups, String filter) {
        return LogQuery.builder()
                   .withLogGroups(logGroups)
                   .withFilter(filter)
                   .withFields(DEFAULT_FIELDS)
                   .withSort(SORT_TIMESTAMP_DESC)
                   .withLimit(LIMIT_100)
                   .build()
                   .constructQuery();
    }

    private  List<String> fetchNewestLambdaLogGroups() {
        return fetchLamdaFunctionNames().stream()
                   .map(this::fetchApiGatewayLogGroupsForFunction)
                   .flatMap(Collection::stream)
                   .max(Comparator.comparing(LogGroup::creationTime))
                   .map(LogGroup::logGroupName)
                   .stream().toList();
    }

    private List<LogGroup> fetchApiGatewayLogGroupsForFunction(String functionName) {
        var request = DescribeLogGroupsRequest.builder().build();
        return cloudWatchLogsClient.describeLogGroups(request).logGroups().stream()
                   .filter(logGroup -> logGroup.logGroupName().contains(API_ACCESS_LOG_GROUP))
                   .toList();
    }

    private List<String> fetchLamdaFunctionNames() {
        return lambdaClient.listFunctions(ListFunctionsRequest.builder().build())
                   .functions()
                   .stream()
                   .map(FunctionConfiguration::functionName)
                   .toList();
    }
}
