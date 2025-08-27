package no.sikt.nva.monitoring.model.factory;

import static java.util.Objects.nonNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.monitoring.model.CloudWatchWidget;
import no.sikt.nva.monitoring.model.LogProperties;
import no.sikt.nva.monitoring.model.LogQuery;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;

public class LogWidgetFactory {

    public static final String LOG = "log";
    public static final String TABLE_VIEW = "table";
    public static final String LIMIT_100 = "limit 100";
    public static final String SORT_TIMESTAMP_DESC = "sort ErrorCount desc";
    public static final String DEFAULT_FIELDS = "fields httpMethod, path, status, error.message";
    public static final String DEFAULT_STATS = "stats count() as ErrorCount by httpMethod, path, status, error.message";
    public static final String MASTER_PIPELINES = "master-pipelines";
    public static final String API_ACCESS_LOG_GROUP_NAME_PATTERN = "*ApiAccessLogGroup*";
    public static final int HEIGHT = 6;
    public static final int WIDTH = 24;
    public static final int Y = 24;
    public static final int X = 12;
    private final CloudWatchLogsClient cloudWatchLogsClient;

    public LogWidgetFactory(CloudWatchLogsClient cloudWatchLogsClient) {
        this.cloudWatchLogsClient = cloudWatchLogsClient;
    }

    public CloudWatchWidget<LogProperties> createLogWidgetForApiGatewayLogs(String title, String filter) {
        var logGroups = fetchApiGatewayLogGroups();
        var query = constructQueryForLogGroupsWithFilter(logGroups, filter);
        return new CloudWatchWidget<>(LOG, constructLogProperties(title, query), HEIGHT, WIDTH, X, Y);
    }

    public List<String> fetchApiGatewayLogGroups() {
        String nextToken = null;
        var allLogGroups = new ArrayList<LogGroup>();
        do {
            var request = createListLogGroupRequestWithNamePattern(nextToken);
            var response = cloudWatchLogsClient.describeLogGroups(request);
            allLogGroups.addAll(response.logGroups());
            nextToken = response.nextToken();
        } while (nonNull(nextToken));
        return keepMasterLogGroupsOnly(allLogGroups);
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
                   .withStats(DEFAULT_STATS)
                   .build()
                   .constructQuery();
    }

    private static List<String> keepMasterLogGroupsOnly(Collection<LogGroup> allLogGroups) {
        return allLogGroups.stream()
                   .map(LogGroup::logGroupName)
                   .filter(logGroup -> logGroup.contains(MASTER_PIPELINES))
                   .toList();
    }

    private static DescribeLogGroupsRequest createListLogGroupRequestWithNamePattern(String nextToken) {
        return DescribeLogGroupsRequest.builder()
                   .logGroupNamePattern(API_ACCESS_LOG_GROUP_NAME_PATTERN)
                   .nextToken(nextToken)
                   .build();
    }
}
