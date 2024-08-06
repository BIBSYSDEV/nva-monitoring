package no.sikt.nva.monitoring;

import java.util.List;
import no.sikt.nva.monitoring.model.CloudWatchWidget;
import software.amazon.awssdk.regions.Region;

public final class LambdaWidget {

    public static final String AWS_LAMBDA = "AWS/Lambda";

    private LambdaWidget() {
    }

    public static CloudWatchWidget<LambdaProperties> create() {
        var lambdaProperties = LambdaProperties.builder()
                                   .withMetrics(List.of(List.of(AWS_LAMBDA, "Errors", lambdaMetricWithRegion()),
                                                        List.of(AWS_LAMBDA, "Throttles", lambdaMetricWithRegion()),
                                                        List.of(AWS_LAMBDA, "Invocations", lambdaMetricWithRegion()),
                                                        List.of(AWS_LAMBDA, "ConcurrentExecutions",
                                                                lambdaMetricWithRegionAndMaxStat()),
                                                        List.of(AWS_LAMBDA, "ClaimedAccountConcurrency",
                                                                lambdaMetricWithRegionAndMaxStat()),
                                                        List.of(AWS_LAMBDA, "AsyncEventAge",
                                                                LambdaMetric.builder().withStat("Maximum").build())))
                                   .withTitle("Lambda metrics - last 5 minutes")
                                   .withView("singleValue")
                                   .withRegion(Region.EU_WEST_1.toString())
                                   .withStat("Sum")
                                   .withPeriod(300)
                                   .withStacked(true)
                                   .withSparkline(true)
                                   .withTrend(true)
                                   .withLiveData(true)
                                   .withSingleValueFullPrecision(true)
                                   .build();
        return new CloudWatchWidget<LambdaProperties>("metric", lambdaProperties, 4, 24, 0, 29);
    }

    private static LambdaMetric lambdaMetricWithRegion() {
        return LambdaMetric.builder().withRegion(Region.EU_WEST_1.toString()).build();
    }

    private static LambdaMetric lambdaMetricWithRegionAndMaxStat() {
        return LambdaMetric.builder().withRegion(Region.EU_WEST_1.toString()).withStat("Maximum").build();
    }
}
