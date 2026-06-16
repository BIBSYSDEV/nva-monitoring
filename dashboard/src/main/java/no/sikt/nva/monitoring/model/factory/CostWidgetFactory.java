package no.sikt.nva.monitoring.model.factory;

import java.util.List;
import no.sikt.nva.monitoring.LambdaProperties;
import no.sikt.nva.monitoring.model.CloudWatchWidget;
import no.sikt.nva.monitoring.model.MetricWidgetObject;

public final class CostWidgetFactory {

    public static final String METRIC = "metric";
    public static final String AWS_BILLING = "AWS/Billing";
    public static final String ESTIMATED_CHARGES = "EstimatedCharges";
    public static final String CURRENCY_DIMENSION = "Currency";
    public static final String USD = "USD";
    public static final String BILLING_REGION = "us-east-1";
    public static final String MAXIMUM_STAT = "Maximum";
    public static final String SINGLE_VALUE = "singleValue";
    public static final String TITLE = "Estimated charges (month-to-date, USD)";
    public static final int PERIOD_6_HOURS = 21600;
    public static final int HEIGHT = 3;
    public static final int WIDTH = 6;
    public static final int X_COORDINATE = 0;
    public static final int Y_COORDINATE = 26;

    private CostWidgetFactory() {
    }

    public static CloudWatchWidget<LambdaProperties> create() {
        return new CloudWatchWidget<>(METRIC, createProperties(), HEIGHT, WIDTH, X_COORDINATE, Y_COORDINATE);
    }

    private static LambdaProperties createProperties() {
        return LambdaProperties.builder()
                   .withMetrics(createMetrics())
                   .withTitle(TITLE)
                   .withView(SINGLE_VALUE)
                   .withRegion(BILLING_REGION)
                   .withStat(MAXIMUM_STAT)
                   .withPeriod(PERIOD_6_HOURS)
                   .build();
    }

    private static List<List<Object>> createMetrics() {
        return List.of(List.of(AWS_BILLING, ESTIMATED_CHARGES, CURRENCY_DIMENSION, USD,
                               new MetricWidgetObject(BILLING_REGION, MAXIMUM_STAT)));
    }
}
