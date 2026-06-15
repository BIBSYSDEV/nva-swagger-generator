package no.sikt.generator.handlers;

import static java.net.http.HttpClient.Redirect.ALWAYS;
import static java.util.Objects.nonNull;
import static no.sikt.generator.ApplicationConstants.EXTERNAL_BUCKET_NAME;
import static no.sikt.generator.ApplicationConstants.INTERNAL_BUCKET_NAME;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;
import no.sikt.generator.GithubApiResponse;
import no.sikt.generator.Utils;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@JacocoGenerated
public class InstallSwaggerUiHandler implements RequestStreamHandler {

    private static final Logger logger = LoggerFactory.getLogger(InstallSwaggerUiHandler.class);
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String SWAGGER_INITIALIZER_JS = "swagger-initializer.js";
    private final S3Client s3Client;
    ObjectMapper mapper = new ObjectMapper();
    HttpClient httpClient;

    public InstallSwaggerUiHandler() {
        this.httpClient = HttpClient.newBuilder().followRedirects(ALWAYS).build();
        this.s3Client = S3Driver.defaultS3Client().build();
    }

    public InstallSwaggerUiHandler(HttpClient httpClient, S3Client s3Client) {
        this.httpClient = httpClient;
        this.s3Client = s3Client;
    }

    private RequestBody createRequestBody(InputStream input) throws IOException {
        var bytes = IoUtils.inputStreamToBytes(input);
        return RequestBody.fromBytes(bytes);
    }

    private void writeToS3(String bucket, String filename, String content) {
        var fullPath = UnixPath.of(filename);
        var putObjectRequest = PutObjectRequest.builder()
                                   .bucket(bucket)
                                   .key(fullPath.toString())
                                   .contentType(getContentTypeFromFilename(filename))
                                   .build();
        attempt(() -> {
            var inputStream = IoUtils.stringToStream(content);
            return s3Client.putObject(putObjectRequest, createRequestBody(inputStream));
        }).orElseThrow();
    }

    private static String getContentTypeFromFilename(String filename) {
        if (filename.endsWith(".html")) {
            return "text/html";
        }
        if (filename.endsWith(".css")) {
            return "text/css";
        }
        if (filename.endsWith(".png")) {
            return "image/png";
        }
        if (filename.endsWith(".js")) {
            return "application/javascript";
        }
        return null;
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) {
        var downloadUri = fetchDownloadUri();
        logger.info("Downloading from {}", downloadUri);

        var downloadRequest = HttpRequest.newBuilder().uri(downloadUri).GET().build();

        httpClient.sendAsync(downloadRequest, BodyHandlers.ofInputStream())
                                 .thenApply(HttpResponse::body)
                                 .thenApply(ZipInputStream::new)
                                 .thenAccept(this::writeZipFileToBuckets)
                                 .join();

        writeToS3(EXTERNAL_BUCKET_NAME, SWAGGER_INITIALIZER_JS, Utils.readResource(SWAGGER_INITIALIZER_JS));
        writeToS3(INTERNAL_BUCKET_NAME, SWAGGER_INITIALIZER_JS, Utils.readResource(SWAGGER_INITIALIZER_JS));
    }

    private void writeZipFileToBuckets(ZipInputStream zipStream) {
        var buckets = List.of(
            EXTERNAL_BUCKET_NAME,
            INTERNAL_BUCKET_NAME
        );
        writeZipFilesToS3(buckets, zipStream);
    }

    private void writeZipFilesToS3(List<String> buckets, ZipInputStream zipStream) {
        try {
            for (var zip = zipStream.getNextEntry(); nonNull(zip); zip = zipStream.getNextEntry()) {

                var filePath = zip.getName();
                if (filePath.contains("/dist/")
                    && !zip.isDirectory()
                    && !filePath.endsWith(SWAGGER_INITIALIZER_JS)
                ) {
                    logger.info("Copying file {}", filePath);

                    var content = readFromZipInputStream(zipStream);
                    var fileName =  filePath.substring(filePath.lastIndexOf('/') + 1);
                    buckets.forEach(b -> writeToS3(b, fileName, content));
                }
            }

            zipStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String readFromZipInputStream(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream fos = new ByteArrayOutputStream();
        IOUtils.copy(zis, fos);
        fos.close();
        return fos.toString(StandardCharsets.UTF_8);
    }

    private URI fetchDownloadUri() {
        return attempt(() -> {
            var listRequest = HttpRequest.newBuilder().uri(buildUri()).GET().build();
            var response = httpClient.send(listRequest, BodyHandlers.ofString()).body();
            var githubResponse = mapper.readValue(response, GithubApiResponse.class);
            return URI.create(githubResponse.zipUrl);
        }).orElseThrow();
    }

    private URI buildUri() {
        return URI.create(String.format("https://api.github.com/repos/%s/%s/releases/latest",
                                        "swagger-api", "swagger-ui"));
    }
}
