package no.sikt.generator;

import java.util.function.Supplier;
import javax.validation.constraints.NotNull;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;

public class CloudFrontHighLevelClient {

    public static final String ALL_FILES = "/*";
    private final Supplier<CloudFrontClient> cloudFrontClientSupplier;

    public CloudFrontHighLevelClient(Supplier<CloudFrontClient> cloudFrontClientSupplier) {
        this.cloudFrontClientSupplier = cloudFrontClientSupplier;
    }

    public void invalidateAll(String distributionId) {
        try (var cloudFrontClient = cloudFrontClientSupplier.get()) {
            var request = CreateInvalidationRequest.builder()
                              .distributionId(distributionId)
                              .invalidationBatch(b -> b
                                                          .paths(p -> p.items(ALL_FILES).quantity(1))
                                                          .callerReference(getCallerReference())
                              )
                              .build();
            cloudFrontClient.createInvalidation(request);
        }
    }

    @NotNull
    private static String getCallerReference() {
        return "swagger-generator-" + System.currentTimeMillis();
    }
}