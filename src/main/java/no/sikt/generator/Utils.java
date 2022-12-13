package no.sikt.generator;

import static nva.commons.core.attempt.Try.attempt;
import com.google.common.io.Resources;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import nva.commons.core.ioutils.IoUtils;

public final class Utils {

    private Utils() {
    }

    public static String readResource(String filename) {
        URL url = Resources.getResource(filename);
        return attempt(() -> Resources.toString(url, StandardCharsets.UTF_8)).orElseThrow();
    }

    public static InputStream readResourceAsStream(String filename) {
        return IoUtils.inputStreamFromResources(filename);
    }

}
