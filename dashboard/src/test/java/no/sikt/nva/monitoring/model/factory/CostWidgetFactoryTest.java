package no.sikt.nva.monitoring.model.factory;

import static no.sikt.nva.monitoring.model.factory.CostWidgetFactory.AWS_BILLING;
import static no.sikt.nva.monitoring.model.factory.CostWidgetFactory.BILLING_REGION;
import static no.sikt.nva.monitoring.model.factory.CostWidgetFactory.CURRENCY_DIMENSION;
import static no.sikt.nva.monitoring.model.factory.CostWidgetFactory.ESTIMATED_CHARGES;
import static no.sikt.nva.monitoring.model.factory.CostWidgetFactory.HEIGHT;
import static no.sikt.nva.monitoring.model.factory.CostWidgetFactory.MAXIMUM_STAT;
import static no.sikt.nva.monitoring.model.factory.CostWidgetFactory.METRIC;
import static no.sikt.nva.monitoring.model.factory.CostWidgetFactory.PERIOD_6_HOURS;
import static no.sikt.nva.monitoring.model.factory.CostWidgetFactory.SINGLE_VALUE;
import static no.sikt.nva.monitoring.model.factory.CostWidgetFactory.TITLE;
import static no.sikt.nva.monitoring.model.factory.CostWidgetFactory.USD;
import static no.sikt.nva.monitoring.model.factory.CostWidgetFactory.WIDTH;
import static no.sikt.nva.monitoring.model.factory.CostWidgetFactory.X_COORDINATE;
import static no.sikt.nva.monitoring.model.factory.CostWidgetFactory.Y_COORDINATE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;
import no.sikt.nva.monitoring.LambdaProperties;
import no.sikt.nva.monitoring.model.MetricWidgetObject;
import org.junit.jupiter.api.Test;

class CostWidgetFactoryTest {

  @Test
  void shouldCreateSingleValueWidgetForEstimatedChargesBillingMetric() {
    var widget = CostWidgetFactory.create();

    assertThat(widget.type(), is(METRIC));
    assertThat(widget.properties().view(), is(SINGLE_VALUE));
    assertThat(widget.properties().title(), is(TITLE));
    assertThat(widget.properties().region(), is(BILLING_REGION));
    assertThat(widget.properties().stat(), is(MAXIMUM_STAT));
    assertThat(widget.properties().period(), is(PERIOD_6_HOURS));
  }

  @Test
  void shouldReferenceEstimatedChargesMetricInUsEast1() {
    var expectedMetrics =
        List.of(
            List.of(
                AWS_BILLING,
                ESTIMATED_CHARGES,
                CURRENCY_DIMENSION,
                USD,
                new MetricWidgetObject(BILLING_REGION, MAXIMUM_STAT)));

    var metrics = CostWidgetFactory.create().properties().metrics();

    assertThat(metrics, is(expectedMetrics));
  }

  @Test
  void shouldPlaceWidgetAtConfiguredGridPosition() {
    var widget = CostWidgetFactory.create();

    assertThat(widget.x(), is(X_COORDINATE));
    assertThat(widget.y(), is(Y_COORDINATE));
    assertThat(widget.width(), is(WIDTH));
    assertThat(widget.height(), is(HEIGHT));
  }

  @Test
  void shouldBuildPropertiesWithExpectedMetricList() {
    var properties = CostWidgetFactory.create().properties();

    assertThat(properties, is(instanceOfLambdaProperties()));
  }

  private static LambdaProperties instanceOfLambdaProperties() {
    return LambdaProperties.builder()
        .withMetrics(
            List.of(
                List.of(
                    AWS_BILLING,
                    ESTIMATED_CHARGES,
                    CURRENCY_DIMENSION,
                    USD,
                    new MetricWidgetObject(BILLING_REGION, MAXIMUM_STAT))))
        .withTitle(TITLE)
        .withView(SINGLE_VALUE)
        .withRegion(BILLING_REGION)
        .withStat(MAXIMUM_STAT)
        .withPeriod(PERIOD_6_HOURS)
        .build();
  }
}
