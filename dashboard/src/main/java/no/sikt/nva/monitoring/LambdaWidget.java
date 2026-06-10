package no.sikt.nva.monitoring;

import java.util.List;
import no.sikt.nva.monitoring.model.CloudWatchWidget;
import no.sikt.nva.monitoring.model.LambdaGraphProperties;
import no.sikt.nva.monitoring.model.MetricSearchExpression;
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
  public static final String TIME_SERIES = "timeSeries";
  public static final String SUM_STAT = "Sum";
  public static final String MAXIMUM_STAT = "Maximum";
  public static final int PERIOD_5_MINUTES = 300;
  public static final String METRIC = "metric";
  public static final String SEARCH_EXPRESSION_TEMPLATE =
      "SEARCH('{AWS/Lambda,FunctionName} MetricName=\"%s\"', '%s', %d)";
  public static final String FUNCTION_NAME_LABEL = "${PROP('Dim.FunctionName')}";
  private static final int CONCURRENCY_WIDGET_HEIGHT = 3;
  private static final int CONCURRENCY_WIDGET_WIDTH = 12;
  private static final int CONCURRENCY_WIDGET_X = 6;
  private static final int CONCURRENCY_WIDGET_Y = 0;
  private static final int GRAPH_HEIGHT = 6;
  private static final int GRAPH_WIDTH = 6;
  private static final int GRAPH_ROW_Y = 3;

  private LambdaWidget() {}

  public static CloudWatchWidget<LambdaProperties> createConcurrencyWidget() {
    var lambdaProperties =
        LambdaProperties.builder()
            .withMetrics(getConcurrencyMetricEntries())
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
    return new CloudWatchWidget<>(
        METRIC,
        lambdaProperties,
        CONCURRENCY_WIDGET_HEIGHT,
        CONCURRENCY_WIDGET_WIDTH,
        CONCURRENCY_WIDGET_X,
        CONCURRENCY_WIDGET_Y);
  }

  public static List<CloudWatchWidget<LambdaGraphProperties>> createPerFunctionGraphs() {
    return List.of(
        createPerFunctionGraph(ERRORS, SUM_STAT, 0),
        createPerFunctionGraph(THROTTLES, SUM_STAT, 1),
        createPerFunctionGraph(INVOCATIONS, SUM_STAT, 2),
        createPerFunctionGraph(ASYNC_EVENT_AGE, MAXIMUM_STAT, 3));
  }

  private static CloudWatchWidget<LambdaGraphProperties> createPerFunctionGraph(
      String metricName, String stat, int columnIndex) {
    var properties =
        new LambdaGraphProperties(
            searchExpressionMetrics(metricName, stat),
            metricName,
            TIME_SERIES,
            Region.EU_WEST_1.toString(),
            stat,
            PERIOD_5_MINUTES,
            true);
    return new CloudWatchWidget<>(
        METRIC, properties, GRAPH_HEIGHT, GRAPH_WIDTH, columnIndex * GRAPH_WIDTH, GRAPH_ROW_Y);
  }

  private static List<List<Object>> searchExpressionMetrics(String metricName, String stat) {
    var expression = SEARCH_EXPRESSION_TEMPLATE.formatted(metricName, stat, PERIOD_5_MINUTES);
    return List.of(List.of(new MetricSearchExpression(expression, FUNCTION_NAME_LABEL)));
  }

  private static List<List<Object>> getConcurrencyMetricEntries() {
    return List.of(
        createAwsLambdaMetricEntry(ERRORS, hiddenMetric()),
        createAwsLambdaMetricEntry(THROTTLES, hiddenMetric()),
        createAwsLambdaMetricEntry(INVOCATIONS, hiddenMetric()),
        createAwsLambdaMetricEntry(CONCURRENT_EXECUTIONS, visibleMaxStatMetric()),
        createAwsLambdaMetricEntry(CLAIMED_ACCOUNT_CONCURRENCY, visibleMaxStatMetric()),
        createAwsLambdaMetricEntry(ASYNC_EVENT_AGE, hiddenMaxStatMetric()));
  }

  private static List<Object> createAwsLambdaMetricEntry(String type, LambdaMetric lambdaMetric) {
    return List.of(AWS_LAMBDA, type, lambdaMetric);
  }

  private static LambdaMetric hiddenMetric() {
    return LambdaMetric.builder()
        .withRegion(Region.EU_WEST_1.toString())
        .withVisible(false)
        .build();
  }

  private static LambdaMetric hiddenMaxStatMetric() {
    return LambdaMetric.builder()
        .withRegion(Region.EU_WEST_1.toString())
        .withStat(MAXIMUM_STAT)
        .withVisible(false)
        .build();
  }

  private static LambdaMetric visibleMaxStatMetric() {
    return LambdaMetric.builder()
        .withRegion(Region.EU_WEST_1.toString())
        .withStat(MAXIMUM_STAT)
        .build();
  }
}
