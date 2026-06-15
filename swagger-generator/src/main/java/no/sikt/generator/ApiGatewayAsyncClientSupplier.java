package no.sikt.generator;

import java.util.function.Supplier;
import nva.commons.core.Environment;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public final class ApiGatewayAsyncClientSupplier {

    public static final String AWS_REGION_ENV = "AWS_REGION";

    private ApiGatewayAsyncClientSupplier() {
    }

    public static Supplier<ApiGatewayAsyncClient> getSupplier() {
        var clientOverrideConfiguration = ClientOverrideConfiguration.builder()
                                              .build();

        return () -> ApiGatewayAsyncClient.builder()
                         .overrideConfiguration(clientOverrideConfiguration)
                         .region(Region.of(new Environment().readEnv(AWS_REGION_ENV)))
                         .credentialsProvider(DefaultCredentialsProvider.create())
                         .build();
    }
}