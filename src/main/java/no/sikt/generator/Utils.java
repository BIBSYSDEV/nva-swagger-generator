package no.sikt.generator;

import static nva.commons.core.attempt.Try.attempt;
import com.google.common.io.Resources;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class Utils {

    private Utils() {
    }

    public static String readResource(String filename) {
        URL url = Resources.getResource(filename);
        return attempt(() -> Resources.toString(url, StandardCharsets.UTF_8)).orElseThrow();
    }

}
