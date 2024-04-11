package no.sikt.nva.monitoring.utils;

import java.util.List;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.GetRestApisRequest;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.apigateway.paginators.GetRestApisIterable;

public class FakeApiGatewayClient implements ApiGatewayClient {

    public static String API_1 = "API 1";
    public static String API_2 = "API 2";

    @Override
    public GetRestApisResponse getRestApis(GetRestApisRequest getRestApisRequest) throws
                                                                                  AwsServiceException,
                                                                                  SdkClientException {
        return GetRestApisResponse.builder()
                   .items(randomApis())
                   .build();
    }

    @Override
    public GetRestApisIterable getRestApisPaginator()
        throws
        AwsServiceException, SdkClientException {
        return new GetRestApisIterable(this, GetRestApisRequest.builder().build());
    }

    @Override
    public String serviceName() {
        return "";
    }

    @Override
    public void close() {

    }

    private List<RestApi> randomApis() {
        return List.of(RestApi.builder()
                           .name(API_1)
                           .build(),
                       RestApi.builder()
                           .name(API_2)
                           .build());
    }
}
