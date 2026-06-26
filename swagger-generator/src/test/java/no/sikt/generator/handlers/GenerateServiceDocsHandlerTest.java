package no.sikt.generator.handlers;

import static no.sikt.generator.ApplicationConstants.readInternalBucketName;
import static no.sikt.generator.ApplicationConstants.readOpenApiBucketName;
import static no.sikt.generator.Utils.readResource;
import static no.sikt.generator.handlers.GenerateServiceDocsHandler.API_PAGE_KEY;
import static no.sikt.generator.handlers.GenerateServiceDocsHandler.INDEX_KEY;
import static no.sikt.generator.handlers.GenerateServiceDocsHandler.INITIALIZER_KEY;
import static no.sikt.generator.handlers.GenerateServiceDocsHandler.MANIFEST_KEY;
import static nva.commons.core.attempt.Try.attempt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;
import no.sikt.generator.CloudFrontHighLevelClient;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationResponse;

class GenerateServiceDocsHandlerTest {

  private static final String SERVICE_A_KEY = "service-a/openapi.yaml";
  private static final String API_A_RESOURCE = "openapi_docs/api-a.yaml";
  private static final String API_B_RESOURCE = "openapi_docs/api-b.yaml";

  private GenerateServiceDocsHandler handler;
  private S3Driver inputS3Driver;
  private S3Driver outputS3Driver;
  private CloudFrontClient cloudFrontClient;

  @SuppressWarnings({"PMD.CloseResource"})
  @BeforeEach
  void setup() {
    var inputS3Client = new FakeS3Client();
    var outputS3Client = new FakeS3Client();
    inputS3Driver = new S3Driver(inputS3Client, readOpenApiBucketName());
    outputS3Driver = new S3Driver(outputS3Client, readInternalBucketName());

    var cloudFrontHighLevelClient = setupMockedCloudFrontClient();

    handler =
        new GenerateServiceDocsHandler(inputS3Client, outputS3Client, cloudFrontHighLevelClient);
  }

  @SuppressWarnings({"unchecked"})
  private CloudFrontHighLevelClient setupMockedCloudFrontClient() {
    var mockCloudFrontSupplier = mock(Supplier.class);
    cloudFrontClient = mock(CloudFrontClient.class);
    when(mockCloudFrontSupplier.get()).thenReturn(cloudFrontClient);
    when(cloudFrontClient.createInvalidation(any(CreateInvalidationRequest.class)))
        .thenReturn(CreateInvalidationResponse.builder().build());
    return new CloudFrontHighLevelClient(mockCloudFrontSupplier);
  }

  private void uploadContentToS3(String key, String content) {
    attempt(() -> inputS3Driver.insertFile(UnixPath.of(key), content)).orElseThrow();
  }

  private void uploadResourceToS3(String key, String resource) {
    uploadContentToS3(key, readResource(resource));
  }

  private void invokeHandler() {
    handler.handleRequest(null, null, null);
  }

  @Test
  void shouldHaveConstructorWithNoArguments() {
    assertThatNoException().isThrownBy(GenerateServiceDocsHandler::new);
  }

  @Test
  void shouldWriteEachSourceSpecVerbatim() {
    uploadResourceToS3(SERVICE_A_KEY, API_A_RESOURCE);
    uploadResourceToS3("service-b/openapi.yaml", API_B_RESOURCE);

    invokeHandler();

    var specA = outputS3Driver.getFile(UnixPath.of("services/specs/service-a/openapi.yaml"));
    var specB = outputS3Driver.getFile(UnixPath.of("services/specs/service-b/openapi.yaml"));
    assertThat(specA).isEqualTo(readResource(API_A_RESOURCE));
    assertThat(specB).isEqualTo(readResource(API_B_RESOURCE));
  }

  @Test
  void shouldWriteManifestListingApiTitlesAndUrls() {
    uploadResourceToS3(SERVICE_A_KEY, API_A_RESOURCE);
    uploadResourceToS3("service-b/openapi.yaml", API_B_RESOURCE);

    invokeHandler();

    var manifest = outputS3Driver.getFile(UnixPath.of(MANIFEST_KEY));
    assertSoftly(
        softly -> {
          softly.assertThat(manifest).contains("Api A");
          softly.assertThat(manifest).contains("Api B");
          softly.assertThat(manifest).contains("specs/service-a/openapi.yaml");
          softly.assertThat(manifest).contains("specs/service-b/openapi.yaml");
        });
  }

  @Test
  void shouldDisambiguateDuplicateTitlesAndFilenames() {
    uploadResourceToS3("a-service/openapi.yaml", API_A_RESOURCE);
    uploadResourceToS3("b-service/openapi.yaml", API_A_RESOURCE);

    invokeHandler();

    var manifest = outputS3Driver.getFile(UnixPath.of(MANIFEST_KEY));
    assertSoftly(
        softly -> {
          softly.assertThat(manifest).contains("Api A (a-service/openapi)");
          softly.assertThat(manifest).contains("Api A (b-service/openapi)");
        });
  }

  @Test
  void shouldDisambiguateDuplicateTitlesFromSameService() {
    uploadResourceToS3("a-service/foo-openapi.yaml", API_A_RESOURCE);
    uploadResourceToS3("a-service/bar-openapi.yaml", API_A_RESOURCE);

    invokeHandler();

    var manifest = outputS3Driver.getFile(UnixPath.of(MANIFEST_KEY));
    assertSoftly(
        softly -> {
          softly.assertThat(manifest).contains("Api A (a-service/foo-openapi)");
          softly.assertThat(manifest).contains("Api A (a-service/bar-openapi)");
        });
  }

  @Test
  void shouldPreserveSourceKeyPathSoSpecsNeverCollide() {
    uploadResourceToS3("a-b/openapi.yaml", API_A_RESOURCE);
    uploadResourceToS3("a/b/openapi.yaml", API_B_RESOURCE);

    invokeHandler();

    var firstSpec = outputS3Driver.getFile(UnixPath.of("services/specs/a-b/openapi.yaml"));
    var secondSpec = outputS3Driver.getFile(UnixPath.of("services/specs/a/b/openapi.yaml"));
    assertSoftly(
        softly -> {
          softly.assertThat(firstSpec).isEqualTo(readResource(API_A_RESOURCE));
          softly.assertThat(secondSpec).isEqualTo(readResource(API_B_RESOURCE));
        });
  }

  @Test
  void shouldFallBackToKeyPathAsNameWhenTitleIsMissing() {
    uploadResourceToS3("misc/not-openapi.yaml", "openapi_docs/not-openapi.yaml");

    invokeHandler();

    var manifest = outputS3Driver.getFile(UnixPath.of(MANIFEST_KEY));
    assertThat(manifest).contains("misc/not-openapi");
  }

  @Test
  void shouldWriteLandingPageApiPageAndInitializer() {
    uploadResourceToS3(SERVICE_A_KEY, API_A_RESOURCE);

    invokeHandler();

    var landingPage = outputS3Driver.getFile(UnixPath.of(INDEX_KEY));
    var apiPage = outputS3Driver.getFile(UnixPath.of(API_PAGE_KEY));
    var initializer = outputS3Driver.getFile(UnixPath.of(INITIALIZER_KEY));
    assertSoftly(
        softly -> {
          softly.assertThat(landingPage).contains("api.html?api=");
          softly.assertThat(apiPage).contains("swagger-ui");
          softly.assertThat(initializer).contains("apis.json");
        });
  }

  @Test
  void shouldNotWriteSpecForNonYamlFiles() {
    uploadContentToS3("service-a/readme.txt", "not a spec");
    uploadResourceToS3(SERVICE_A_KEY, API_A_RESOURCE);

    invokeHandler();

    var publishedSpecs =
        outputS3Driver.listAllFiles(UnixPath.of("services/specs")).stream()
            .map(UnixPath::toString)
            .toList();
    assertThat(publishedSpecs)
        .anyMatch(path -> path.contains("service-a/openapi.yaml"))
        .noneMatch(path -> path.contains("readme"));
  }

  @Test
  void shouldInvalidateCloudFront() {
    uploadResourceToS3(SERVICE_A_KEY, API_A_RESOURCE);

    invokeHandler();

    verify(cloudFrontClient).createInvalidation(any(CreateInvalidationRequest.class));
  }
}
