package no.sikt.generator;

import static software.amazon.awssdk.regions.Region.AWS_GLOBAL;
import java.util.function.Supplier;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;

@JacocoGenerated
public final class CloudFrontClientSupplier {
    private CloudFrontClientSupplier() {
    }

    public static Supplier<CloudFrontClient> getSupplier() {
        return () -> CloudFrontClient.builder()
            .httpClient(UrlConnectionHttpClient.builder().build())
            .region(AWS_GLOBAL)
            .build();
    }
}
