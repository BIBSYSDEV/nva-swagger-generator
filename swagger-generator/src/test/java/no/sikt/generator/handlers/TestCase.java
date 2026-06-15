package no.sikt.generator.handlers;

import static no.sikt.generator.Utils.readResource;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.List;
import java.util.Optional;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;

public class TestCase {

    private String id;
    private String name;
    private String contentApiGateway;
    private Optional<String> contentGithub;

    public TestCase(String id, String name, String contentApiGateway, Optional<String> contentGithub) {
        this.id = id;
        this.name = name;
        this.contentApiGateway = contentApiGateway;
        this.contentGithub = contentGithub;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getContentApiGateway() {
        return contentApiGateway;
    }

    public Optional<String> getContentGithub() {
        return contentGithub;
    }
}
