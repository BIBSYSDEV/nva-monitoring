package no.sikt.nva.monitoring.model.factory;

import java.util.Collection;
import java.util.List;
import no.sikt.nva.monitoring.model.CloudWatchWidget;
import no.sikt.nva.monitoring.model.MetricProperties;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;

public class ApiGatewayWidgetFactory {

    public static final String TYPE = "metric";
    public static final String VIEW = "timeSeries";
    public static final String REGION = "eu-west-1";
    public static final boolean NOT_STACKED = false;
    public static final int HEIGHT = 9;
    public static final int WIDTH = 8;
    public static final int Y_COORDINATE = 8;
    public static final String AWS_API_GATEWAY = "AWS/ApiGateway";
    public static final String API_NAME = "ApiName";
    private final ApiGatewayClient apiGatewayClient;

    public ApiGatewayWidgetFactory(ApiGatewayClient apiGatewayClient) {
        this.apiGatewayClient = apiGatewayClient;
    }

    public CloudWatchWidget<MetricProperties> creatCloudWatchWidget(int widgetIndex, String metricName) {
        return new CloudWatchWidget<>(TYPE,
                                      new MetricProperties(VIEW, NOT_STACKED, REGION, createMetricList(metricName)),
                                      HEIGHT,
                                      WIDTH,
                                      calculateWidgetOffset(widgetIndex),
                                      Y_COORDINATE);
    }

    private static List<String> getApiIds(GetRestApisResponse page) {
        return page.items().stream().map(RestApi::name).toList();
    }

    private List<List<String>> createMetricList(String metricName) {
        var pages = apiGatewayClient.getRestApisPaginator();
        return pages.stream()
                       .map(ApiGatewayWidgetFactory::getApiIds)
                       .flatMap(Collection::stream)
                       .map(api -> createMetric(metricName, api))
                       .toList();
    }

    private List<String> createMetric(String metricName, String apiId) {
        return List.of(AWS_API_GATEWAY, metricName, API_NAME, apiId);
    }



    private int calculateWidgetOffset(int widgetIndex) {
        return widgetIndex * WIDTH;
    }
}
