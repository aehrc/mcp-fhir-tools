/*
 * Copyright © 2026, Commonwealth Scientific and Industrial Research Organisation
 * (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: John Grimes
 */
package au.csiro.mcp.fhirtools.validator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsed command line.
 *
 * @param release the FHIR release to validate as
 * @param txServer terminology server base URL
 * @param packages FHIR packages to load, each {@code id#version}, most specific first
 * @param profiles explicit profile canonical URLs to validate against
 * @param file the resource to validate
 */
public record CliArgs(FhirRelease release, String txServer, List<String> packages,
        List<String> profiles, Path file) {

    public static final String USAGE = """
        usage: java -jar validodo-cli.jar --fhir-version <4.0.1|5.0.0> [--tx <url>]
               [--package <id#version>]... [--profile <canonical>]... <resource.json>""";

    /**
     * Parses the command line.
     *
     * @throws IllegalArgumentException on any usage error; the message is suitable for stderr
     */
    public static CliArgs parse(String[] args) {
        String version = null;
        String tx = null;
        final List<String> packages = new ArrayList<>();
        final List<String> profiles = new ArrayList<>();
        Path file = null;

        for (int i = 0; i < args.length; i++) {
            final String arg = args[i];
            if (!arg.startsWith("--")) {
                if (file != null) {
                    throw new IllegalArgumentException("Unexpected argument '" + arg + "'");
                }
                file = Path.of(arg);
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Option '" + arg + "' requires a value");
            }
            final String value = args[++i];
            switch (arg) {
                case "--fhir-version" -> version = value;
                case "--tx" -> tx = value;
                case "--package" -> {
                    if (!value.contains("#")) {
                        throw new IllegalArgumentException(
                                "Package '" + value + "' must be given as id#version");
                    }
                    packages.add(value);
                }
                case "--profile" -> profiles.add(value);
                default -> throw new IllegalArgumentException("Unknown option '" + arg + "'");
            }
        }

        if (version == null) {
            throw new IllegalArgumentException("--fhir-version is required");
        }
        if (file == null) {
            throw new IllegalArgumentException("A resource file is required");
        }
        final FhirRelease release = FhirRelease.fromVersion(version);
        return new CliArgs(release, tx != null ? tx : release.defaultTxServer(),
                List.copyOf(packages), List.copyOf(profiles), file);
    }
}
