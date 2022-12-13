package no.sikt.generator.handlers;

import static java.net.http.HttpClient.Redirect.ALWAYS;
import static no.sikt.generator.ApplicationConstants.OUTPUT_BUCKET_NAME;
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
import java.util.Map;
import java.util.zip.ZipEntry;
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

    private void writeToS3(String filename, String content) {
        Map<String, String> metadata = Map.of();

        if (filename.endsWith(".html")) {
            metadata = Map.of(
                CONTENT_TYPE, "text/html"
            );
        }
        if (filename.endsWith(".css")) {
            metadata = Map.of(
                CONTENT_TYPE,"text/css"
            );
        }
        if (filename.endsWith(".png")) {
            metadata = Map.of(
                CONTENT_TYPE, "image/png"
            );
        }
        if (filename.endsWith(".js")) {
            metadata = Map.of(
                CONTENT_TYPE,"application/javascript"
            );
        }

        var fullPath = UnixPath.of(filename);
        var putObjectRequest = PutObjectRequest.builder()
                                   .bucket(OUTPUT_BUCKET_NAME)
                                   .key(fullPath.toString())
                                   .metadata(metadata)
                                   .build();
        attempt(() -> {
            var inputStream = IoUtils.stringToStream(content);
            return s3Client.putObject(putObjectRequest, createRequestBody(inputStream));
        }).orElseThrow();
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) {
        attempt(() -> {

            var downloadUri = fetchDownloadUri();
            logger.info("Downloading from {}", downloadUri);

            var downloadRequest = HttpRequest.newBuilder().uri(downloadUri).GET().build();

            InputStream is = httpClient.sendAsync(downloadRequest, BodyHandlers.ofInputStream())
                                 .thenApply(HttpResponse::body).join();

            ZipInputStream zis = new ZipInputStream(is);
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                var fileName = zipEntry.getName();
                if (fileName.contains("/dist/") && !zipEntry.isDirectory() && !fileName.contains("swagger-initializer"
                                                                                                 + ".js")) {
                    logger.info("Copying file {}", fileName);

                    ByteArrayOutputStream fos = new ByteArrayOutputStream();
                    IOUtils.copy(zis, fos);
                    fos.close();
                    var content = fos.toString(StandardCharsets.UTF_8);
                    var cleanFileName =  fileName.substring(fileName.lastIndexOf('/') + 1);

                    writeToS3(cleanFileName, content);
                }
                zipEntry = zis.getNextEntry();
            }

            zis.close();

            return null;
        }).orElseThrow();

        writeToS3("swagger-initializer.js", Utils.readResource("swagger-initializer.js"));
    }

    private URI fetchDownloadUri() throws IOException, InterruptedException {
        var listRequest = HttpRequest.newBuilder().uri(buildUri()).GET().build();
        var response = httpClient.send(listRequest, BodyHandlers.ofString()).body();
        var githubResponse = mapper.readValue(response, GithubApiResponse.class);
        return URI.create(githubResponse.zipUrl);
    }

    private URI buildUri() {
        return URI.create(String.format("https://api.github.com/repos/%s/%s/releases/latest",
                                        "swagger-api", "swagger-ui"));
    }
}
