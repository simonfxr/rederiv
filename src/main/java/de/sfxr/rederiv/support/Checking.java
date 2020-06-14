package de.sfxr.rederiv.support;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import java.util.Arrays;

public final class Checking {

    private Checking() {}

    public static boolean isCheckingEnabled(Class<?> cls) {
        var parts = Arrays.asList(cls.getName().split("\\."));
        for (int i = parts.size() - 1; i >= 0; --i) {
            var propName = Joiner.on('.').join(parts.subList(0, i + 1)) + ".checking";
            var val = System.getProperty(propName, "");
            if (!Strings.isNullOrEmpty(val)) {
                var checking = Boolean.valueOf(val);
                if (checking) System.err.println("Checking enabled for " + cls.getName());
                return checking;
            }
        }
        return false;
    }
}
