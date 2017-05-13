package monitoring.seminar.fer.monitoring;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by roko on 4/2/17.
 */

public class Serialization {
    private static final String DELIMITER = ",";

    public static String serializeStrings(Set<String> strings) {
        if (strings == null || strings.isEmpty()) return "";

        return TextUtils.join(DELIMITER, strings);
    }

    public static Set<String> deserializeString(@NonNull String string) {
        return new HashSet<>(Arrays.asList(string.split(DELIMITER)));
    }
}
