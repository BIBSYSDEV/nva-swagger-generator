package no.sikt.generator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.when;
import java.util.concurrent.CompletableFuture;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;
import software.amazon.awssdk.services.apigateway.ApiGatewayAsyncClient;
import software.amazon.awssdk.services.apigateway.model.GetExportRequest;
import software.amazon.awssdk.services.apigateway.model.GetExportResponse;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;

class GenerateDocsHandlerTest {

    private final ApiGatewayAsyncClient client = Mockito.mock(ApiGatewayAsyncClient.class);

    private final GenerateDocsHandler handler = new GenerateDocsHandler(client);

    @BeforeEach
    public void setup() {
        var restApi1 = RestApi.builder().name("First API").build();
        var restApi2 = RestApi.builder().name("Second API").build();

        var getRestApisResponse = GetRestApisResponse.builder().items(
            restApi1, restApi2
        ).build();

        var getExportResponse = GetExportResponse.builder().build();

        when(client.getRestApis()).thenReturn(CompletableFuture.completedFuture(getRestApisResponse));
        when(client.getExport(any(GetExportRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(getExportResponse));
    }

    @Test
    public void shouldHaveConstrcutorWithNoArgument() {
        Executable action = () -> new GenerateDocsHandler();
    }

    @Test
    public void shouldLogAPIsWhenInvoked() {
        TestAppender logger = LogUtils.getTestingAppenderForRootLogger();
        handler.handleRequest(null, null, null);
        assertThat(logger.getMessages(), containsString("First API"));
    }

}