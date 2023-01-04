package no.sikt.generator.handlers;

import static no.sikt.generator.Utils.readResource;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.List;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;

public class TestCase {

    private String id;
    private String name;

    public TestCase(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
