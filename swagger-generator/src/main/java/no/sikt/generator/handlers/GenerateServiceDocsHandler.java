package no.sikt.generator.handlers;

import static no.sikt.generator.ApplicationConstants.INTERNAL_BUCKET_NAME;
import static no.sikt.generator.ApplicationConstants.INTERNAL_CLOUD_FRONT_DISTRIBUTION;
import static no.sikt.generator.ApplicationConstants.readOpenApiBucketName;
import static no.sikt.generator.Utils.readResource;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import no.sikt.generator.CloudFrontClientSupplier;
import no.sikt.generator.CloudFrontHighLevelClient;
import no.unit.nva.s3.S3Driver;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Handler that serves each service's source OpenAPI doc verbatim under a {@code services/} prefix
 * on the internal Swagger UI bucket. Unlike the combine pipeline, it does not touch API Gateway and
 * does not merge specs, so all examples and descriptions are preserved.
 */
public class GenerateServiceDocsHandler implements RequestStreamHandler {

  public static final String SERVICES_PREFIX = "services/";
  public static final String SPECS_DIRECTORY = "specs/";
  public static final String SPECS_PREFIX = SERVICES_PREFIX + SPECS_DIRECTORY;
  public static final String MANIFEST_KEY = SERVICES_PREFIX + "apis.json";
  public static final String INDEX_KEY = SERVICES_PREFIX + "index.html";
  public static final String API_PAGE_KEY = SERVICES_PREFIX + "api.html";
  public static final String INITIALIZER_KEY = SERVICES_PREFIX + "swagger-initializer.js";
  public static final String INDEX_RESOURCE = "services-index.html";
  public static final String API_PAGE_RESOURCE = "services-api.html";
  public static final String INITIALIZER_RESOURCE = "services-swagger-initializer.js";
  public static final String YAML_EXTENSION = ".yaml";
  public static final int MAX_KEYS = 1000;
  private static final String URL = "url";
  private static final String NAME = "name";
  private static final String SOURCE = "source";

  private static final Logger LOGGER = LoggerFactory.getLogger(GenerateServiceDocsHandler.class);
  private final S3Client inputS3Client;
  private final S3Client outputS3Client;
  private final CloudFrontHighLevelClient cloudFrontHighLevelClient;
  private final OpenAPIV3Parser openApiParser = new OpenAPIV3Parser();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String openApiBucketName = readOpenApiBucketName();

  public GenerateServiceDocsHandler() {
    this(
        S3Driver.defaultS3Client().build(),
        S3Driver.defaultS3Client().build(),
        new CloudFrontHighLevelClient(CloudFrontClientSupplier.getSupplier()));
  }

  public GenerateServiceDocsHandler(
      S3Client inputS3Client,
      S3Client outputS3Client,
      CloudFrontHighLevelClient cloudFrontHighLevelClient) {
    this.inputS3Client = inputS3Client;
    this.outputS3Client = outputS3Client;
    this.cloudFrontHighLevelClient = cloudFrontHighLevelClient;
  }

  @Override
  public void handleRequest(InputStream input, OutputStream output, Context context) {
    var sourceDocs = listSourceDocs();
    LOGGER.info("Found {} source OpenAPI files", sourceDocs.size());

    var manifestEntries = sourceDocs.stream().map(this::publishSpec).toList();

    writeManifest(manifestEntries);
    writeStaticAsset(INDEX_KEY, INDEX_RESOURCE, "text/html");
    writeStaticAsset(API_PAGE_KEY, API_PAGE_RESOURCE, "text/html");
    writeStaticAsset(INITIALIZER_KEY, INITIALIZER_RESOURCE, "application/javascript");
    cloudFrontHighLevelClient.invalidateAll(INTERNAL_CLOUD_FRONT_DISTRIBUTION);
  }

  private List<S3Object> listSourceDocs() {
    var request = ListObjectsRequest.builder().bucket(openApiBucketName).maxKeys(MAX_KEYS).build();
    return inputS3Client.listObjects(request).contents().stream()
        .filter(s3Object -> s3Object.key().endsWith(YAML_EXTENSION))
        .sorted(Comparator.comparing(S3Object::key))
        .toList();
  }

  private Map<String, String> publishSpec(S3Object s3Object) {
    var content = readSourceDoc(s3Object.key());
    var slug = toSlug(s3Object.key());
    writeToOutput(SPECS_PREFIX + slug + YAML_EXTENSION, content, "application/yaml");
    LOGGER.info("Published service spec for {}", s3Object.key());

    var entry = new LinkedHashMap<String, String>();
    entry.put(URL, SPECS_DIRECTORY + slug + YAML_EXTENSION);
    entry.put(NAME, extractTitle(content, slug));
    entry.put(SOURCE, baseName(s3Object.key()));
    return entry;
  }

  private static String baseName(String key) {
    var fileName = key.substring(key.lastIndexOf('/') + 1);
    return Strings.CI.removeEnd(fileName, YAML_EXTENSION);
  }

  private String readSourceDoc(String key) {
    var request = GetObjectRequest.builder().bucket(openApiBucketName).key(key).build();
    return inputS3Client.getObject(request, ResponseTransformer.toBytes()).asUtf8String();
  }

  private String extractTitle(String content, String fallback) {
    return attempt(() -> openApiParser.readContents(content).getOpenAPI().getInfo().getTitle())
        .toOptional()
        .filter(StringUtils::isNotBlank)
        .orElse(fallback);
  }

  private static String toSlug(String key) {
    var withoutExtension = Strings.CI.removeEnd(key, YAML_EXTENSION);
    return withoutExtension.replaceAll("[^a-zA-Z0-9]+", "-");
  }

  private void writeManifest(List<Map<String, String>> entries) {
    disambiguateDuplicateNames(entries);
    var sorted =
        entries.stream()
            .map(entry -> Map.of(URL, entry.get(URL), NAME, entry.get(NAME)))
            .sorted(Comparator.comparing(entry -> entry.get(NAME)))
            .toList();
    var json = attempt(() -> objectMapper.writeValueAsString(sorted)).orElseThrow();
    writeToOutput(MANIFEST_KEY, json, "application/json");
  }

  private void disambiguateDuplicateNames(List<Map<String, String>> entries) {
    var nameCounts =
        entries.stream()
            .collect(Collectors.groupingBy(entry -> entry.get(NAME), Collectors.counting()));
    entries.stream()
        .filter(entry -> nameCounts.get(entry.get(NAME)) > 1)
        .forEach(entry -> entry.put(NAME, entry.get(NAME) + " (" + entry.get(SOURCE) + ")"));
  }

  private void writeStaticAsset(String key, String resource, String contentType) {
    writeToOutput(key, readResource(resource), contentType);
  }

  private void writeToOutput(String key, String content, String contentType) {
    var request =
        PutObjectRequest.builder()
            .bucket(INTERNAL_BUCKET_NAME)
            .key(key)
            .contentType(contentType)
            .build();
    var body = RequestBody.fromString(content, StandardCharsets.UTF_8);
    attempt(() -> outputS3Client.putObject(request, body)).orElseThrow();
  }
}
