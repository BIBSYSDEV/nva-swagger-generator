package no.sikt.generator;

import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.InvalidationBatch;
import software.amazon.awssdk.services.cloudfront.model.Paths;

public class CloudFrontHighLevelClient {

    public static final String ALL_FILES = "/*";
    private final CloudFrontClient cloudFrontClient;

    public CloudFrontHighLevelClient(CloudFrontClient cloudFrontClient) {
        this.cloudFrontClient = cloudFrontClient;
    }

    public void invalidateAll(String distributionId) {
        var batch = InvalidationBatch.builder()
                        .paths(Paths.builder().items(ALL_FILES).quantity(1).build())
                        .callerReference(getCallerReference())
                        .build();
        var request = CreateInvalidationRequest.builder()
                          .distributionId(distributionId)
                          .invalidationBatch(batch)
                          .build();
        this.cloudFrontClient.createInvalidation(request);
    }

    @NotNull
    private static String getCallerReference() {
        return "swagger-generator-" + System.currentTimeMillis();
    }
}




