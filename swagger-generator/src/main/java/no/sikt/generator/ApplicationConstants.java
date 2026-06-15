package no.sikt.generator;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import nva.commons.core.Environment;

public final class ApplicationConstants {

    public static final String EXPORT_STAGE_PROD = "Prod";
    public static final String EXPORT_TYPE_OA_3 = "oas30";
    public static final String APPLICATION_YAML = "application/yaml";
    public static final String VERSION_NAME = "swagger-generator";
    public static final Environment ENVIRONMENT = new Environment();
    public static final String EXTERNAL_BUCKET_NAME = readExternalBucketName();
    public static final String INTERNAL_BUCKET_NAME = readInternalBucketName();
    public static final String EXTERNAL_CLOUD_FRONT_DISTRIBUTION = readExternalCloudFrontDistributionId();
    public static final String INTERNAL_CLOUD_FRONT_DISTRIBUTION = readInternalCloudFrontDistributionId();
    public static final String DOMAIN = readDomain();

    public static final List<String> EXCLUDED_APIS = readExcludedApis();


    private ApplicationConstants() {
    
    }
    
    private static String readExternalBucketName() {
        return ENVIRONMENT.readEnv("EXTERNAL_BUCKET_NAME");
    }

    private static String readInternalBucketName()  {
        return ENVIRONMENT.readEnv("INTERNAL_BUCKET_NAME");
    }

    public static String readOpenApiBucketName() {
        return ENVIRONMENT.readEnv("OPEN_API_DOCS_BUCKET_NAME");
    }

    private static String readExternalCloudFrontDistributionId() {
        return ENVIRONMENT.readEnv("EXTERNAL_CLOUD_FRONT_DISTRIBUTION");
    }

    private static String readInternalCloudFrontDistributionId() {
        return ENVIRONMENT.readEnv("INTERNAL_CLOUD_FRONT_DISTRIBUTION");
    }

    private static String readDomain() {
        return ENVIRONMENT.readEnv("DOMAIN");
    }

    private static List<String> readExcludedApis() {
        return Arrays.stream(ENVIRONMENT.readEnv("EXCLUDED_APIS").split(",")).collect(Collectors.toList());
    }
}
