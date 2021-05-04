package org.shipkit.auto.version;

import com.github.zafarkhaja.semver.Version;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the next version to use.
 */
class NextVersionPicker {
    private final ProcessRunner runner;
    private final Logger log;

    NextVersionPicker(ProcessRunner runner, Logger log) {
        this.runner = runner;
        this.log = log;
    }

    /**
     * Picks the next version to use based on the input parameters
     */
    String pickNextVersion(Optional<Version> previousVersion, VersionConfig config, String projectVersion) {
        if (!Project.DEFAULT_VERSION.equals(projectVersion)) {
            explainVersion(log, projectVersion, "uses version already specified in the Gradle project");
            return projectVersion;
        }

        if (!config.getRequestedVersion().isPresent()) {
            String tag = runner.run("git", "describe", "--tags");
            Pattern pattern = Pattern.compile(config.getTagPrefix() + "\\d+\\.\\d+\\.\\d+");
            Matcher matcher = pattern.matcher(tag);
            String result;
            if (matcher.find()) {
                result = matcher.group().substring(config.getTagPrefix().length());
                explainVersion(log, result, "deducted version based on tag: '" + config.getTagPrefix() + result + "'");
            } else {
                result = "0.0.1";
                explainVersion(log, result, "found no version property and the code is not checked out on a valid tag");
            }

            return result;
        }

        if (!config.isWildcard()) {
            //if there is no wildcard we will use the version 'as is'
            explainVersion(log, config.getRequestedVersion().get(), "uses verbatim version from version file");
            return config.getRequestedVersion().get();
        }

        if (previousVersion.isPresent() && previousVersion.get().satisfies(config.getRequestedVersion().get())) {
            Version prev = previousVersion.get();
            String gitOutput = runner.run(
                    "git", "log", "--pretty=oneline", TagConvention.tagFor(prev.toString(), config.getTagPrefix()) + "..HEAD");
            int commitCount = new CommitCounter().countCommitDelta(gitOutput);
            String result = Version
                    .forIntegers(
                            prev.getMajorVersion(),
                            prev.getMinorVersion(),
                            prev.getPatchVersion() + commitCount)
                    .toString();
            explainVersion(log, result, "deducted version based on previous tag: '" + prev + "'");
            return result;
        } else {
            String result = config.newPatchVersion();
            explainVersion(log, result, "found no tags matching version spec: '" + config + "'");
            return result;
        }
    }

    /**
     * Explain version in a consistent, human-readable way
     */
    static void explainVersion(Logger log, String version, String reason) {
        log.lifecycle("Building version '"+ version + "'\n" +
                "  - reason: shipkit-auto-version " + reason);
    }
}
