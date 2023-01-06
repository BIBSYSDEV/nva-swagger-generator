package no.sikt.generator;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import nva.commons.core.Environment;

public final class ApplicationConstants {
    
    public static final Environment ENVIRONMENT = new Environment();
    public static final String OUTPUT_BUCKET_NAME = readOutputBucketName();
    public static final String DOMAIN = readDomain();

    public static final List<String> EXCLUDED_APIS = readExcludedApis();


    private ApplicationConstants() {
    
    }
    
    private static String readOutputBucketName() {
        return ENVIRONMENT.readEnv("OUTPUT_BUCKET_NAME");
    }

    private static String readDomain() {
        return ENVIRONMENT.readEnv("DOMAIN");
    }

    private static List<String> readExcludedApis() {
        return Arrays.stream(ENVIRONMENT.readEnv("EXCLUDED_APIS").split(",")).collect(Collectors.toList());
    }
}
