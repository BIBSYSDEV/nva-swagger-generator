package no.sikt.generator;

import nva.commons.core.Environment;

public final class ApplicationConstants {
    
    public static final Environment ENVIRONMENT = new Environment();
    public static final String OUTPUT_BUCKET_NAME = readOutputBucketName();


    private ApplicationConstants() {
    
    }
    
    private static String readOutputBucketName() {
        return ENVIRONMENT.readEnv("OUTPUT_BUCKET_NAME");
    }
}
