package no.sikt.generator.handlers;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import no.sikt.generator.ApplicationConstants;
import no.sikt.generator.GithubApiResponse;
import no.sikt.generator.Utils;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstallSwaggerUiHandlerTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private InstallSwaggerUiHandler handler;
    private S3Driver s3Driver;

    @BeforeEach
    public void setup() throws IOException, InterruptedException {
        var fakeS3Client = new FakeS3Client();
        this.s3Driver = new S3Driver(fakeS3Client, ApplicationConstants.OUTPUT_BUCKET_NAME);

        handler = new InstallSwaggerUiHandler(httpClient, fakeS3Client);

        HttpResponse<Object> gitHubResponse = mock(HttpResponse.class);
        HttpResponse<Object> downloadResponse = mock(HttpResponse.class);

        when(gitHubResponse.body()).thenReturn(new GithubApiResponse("http://example.org").toString());
        when(downloadResponse.body()).thenReturn(Utils.readResourceAsStream("zippedfile.zip"));

        when(httpClient.send(any(), any()))
            .thenReturn(gitHubResponse);

        when(httpClient.sendAsync(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(downloadResponse));
    }

    @Test
    public void shouldHaveConstrcutorWithNoArgument() {
        Executable action = () -> new InstallSwaggerUiHandler();
    }

    @Test
    public void shouldFetchAndUnpackZipCorrectly() {
        handler.handleRequest(null, null, null);

        assertThat(s3Driver.getFile(UnixPath.of("file1.txt")), notNullValue());
        assertThat(s3Driver.getFile(UnixPath.of("file2.txt")), notNullValue());
        assertThat(s3Driver.getFiles(UnixPath.of("/")), hasSize(2));
    }

}