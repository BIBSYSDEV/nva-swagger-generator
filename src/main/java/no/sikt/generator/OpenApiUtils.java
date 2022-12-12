package no.sikt.generator;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class OpenApiUtils {

    private OpenApiUtils() {

    }

    static List<Operation> getAllOperationsFromPathItem(PathItem pathItem) {
        return Stream.of(
            pathItem.getGet(),
            pathItem.getDelete(),
            pathItem.getPut(),
            pathItem.getPost(),
            pathItem.getOptions(),
            pathItem.getHead(),
            pathItem.getTrace(),
            pathItem.getPatch()
        )
           .filter(Objects::nonNull)
           .collect(Collectors.toList());
    }
}
