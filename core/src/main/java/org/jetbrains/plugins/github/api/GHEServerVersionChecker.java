// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api;

public final class GHEServerVersionChecker {
    private static final int REQUIRED_VERSION_MAJOR = 3;
    private static final int REQUIRED_VERSION_MINOR = 2;

    public static final String ENTERPRISE_VERSION_HEADER = "x-github-enterprise-version";

    private GHEServerVersionChecker() {
    }

    public static void checkVersionSupported(String versionValue) {
        int majorVersion;
        int minorVersion;
        try {
            String[] versionSplit = versionValue.split("\\.");
            majorVersion = Integer.parseInt(versionSplit[0]);
            minorVersion = Integer.parseInt(versionSplit[1]);
        }
        catch (Throwable e) {
            throw new IllegalStateException("Could not determine GitHub Enterprise server version", e);
        }

        if (majorVersion > REQUIRED_VERSION_MAJOR) {
            return;
        }
        if (majorVersion < REQUIRED_VERSION_MAJOR) {
            throwUnsupportedVersion(majorVersion, minorVersion);
        }
        else if (minorVersion < REQUIRED_VERSION_MINOR) {
            throwUnsupportedVersion(majorVersion, minorVersion);
        }
    }

    private static void throwUnsupportedVersion(int currentMajor, int currentMinor) {
        throw new IllegalStateException("Unsupported GitHub Enterprise server version " + currentMajor + "." + currentMinor +
            ". Earliest supported version is " + REQUIRED_VERSION_MAJOR + "." + REQUIRED_VERSION_MINOR);
    }
}
