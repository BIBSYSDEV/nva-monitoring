package no.sikt.nva.monitoring;

import java.util.List;
import no.sikt.nva.monitoring.model.CloudWatchWidget;
import software.amazon.awssdk.regions.Region;

public final class LambdaWidget {

    public static final String AWS_LAMBDA = "AWS/Lambda";
    public static final String ERRORS = "Errors";
    public static final String THROTTLES = "Throttles";
    public static final String INVOCATIONS = "Invocations";
    public static final String CONCURRENT_EXECUTIONS = "ConcurrentExecutions";
    public static final String CLAIMED_ACCOUNT_CONCURRENCY = "ClaimedAccountConcurrency";
    public static final String ASYNC_EVENT_AGE = "AsyncEventAge";
    public static final String LAMBDA_METRICS_TITLE = "Lambda metrics - last 5 minutes";
    public static final String SINGLE_VALUE = "singleValue";
    public static final String SUM_STAT = "Sum";
    public static final int PERIOD_5_MINUTES = 300;
    public static final String METRIC = "metric";
    public static final String MAXIMUM_STAT = "Maximum";

    private LambdaWidget() {
    }

    public static CloudWatchWidget<LambdaProperties> create() {
        var lambdaProperties = LambdaProperties.builder()
                                   .withMetrics(getAwsLambdaMetricEntry())
                                   .withTitle(LAMBDA_METRICS_TITLE)
                                   .withView(SINGLE_VALUE)
                                   .withRegion(Region.EU_WEST_1.toString())
                                   .withStat(SUM_STAT)
                                   .withPeriod(PERIOD_5_MINUTES)
                                   .withStacked(true)
                                   .withSparkline(true)
                                   .withTrend(true)
                                   .withLiveData(true)
                                   .withSingleValueFullPrecision(true)
                                   .build();
        return new CloudWatchWidget<LambdaProperties>(METRIC, lambdaProperties, 4, 24, 5, 0);
    }

    private static List<List<Object>> getAwsLambdaMetricEntry() {
        return List.of(
            createAwsLambdaMetricEntry(ERRORS, lambdaMetricWithRegion()),
            createAwsLambdaMetricEntry(THROTTLES, lambdaMetricWithRegion()),
            createAwsLambdaMetricEntry(INVOCATIONS, lambdaMetricWithRegion()),
            createAwsLambdaMetricEntry(CONCURRENT_EXECUTIONS, lambdaMetricWithRegionAndMaxStat()),
            createAwsLambdaMetricEntry(CLAIMED_ACCOUNT_CONCURRENCY, lambdaMetricWithRegionAndMaxStat()),
            createAwsLambdaMetricEntry(ASYNC_EVENT_AGE, lambdaMetricWithRegionAndMaxStat())
        );
    }

    private static List<Object> createAwsLambdaMetricEntry(String type, LambdaMetric lambdaMetric) {
        return List.of(AWS_LAMBDA, type, lambdaMetric);
    }

    private static LambdaMetric lambdaMetricWithRegion() {
        return LambdaMetric.builder().withRegion(Region.EU_WEST_1.toString()).build();
    }

    private static LambdaMetric lambdaMetricWithRegionAndMaxStat() {
        return LambdaMetric.builder().withRegion(Region.EU_WEST_1.toString()).withStat(MAXIMUM_STAT).build();
    }
}
