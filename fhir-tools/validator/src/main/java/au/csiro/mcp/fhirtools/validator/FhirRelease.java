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

import au.csiro.fhir.validation.common.FhirVersionContext;

/**
 * The FHIR releases validodo supports, with the core package and default terminology endpoint
 * for each.
 */
public enum FhirRelease {
    R4("4.0.1", "hl7.fhir.r4.core#4.0.1", "https://tx.fhir.org/r4"),
    R5("5.0.0", "hl7.fhir.r5.core#5.0.0", "https://tx.fhir.org/r5");

    private final String fhirVersion;
    private final String corePackage;
    private final String defaultTxServer;

    FhirRelease(String fhirVersion, String corePackage, String defaultTxServer) {
        this.fhirVersion = fhirVersion;
        this.corePackage = corePackage;
        this.defaultTxServer = defaultTxServer;
    }

    /** The FHIR version string as passed on the command line, e.g. {@code 4.0.1}. */
    public String fhirVersion() {
        return fhirVersion;
    }

    /** The core definitions package, as {@code id#version}. */
    public String corePackage() {
        return corePackage;
    }

    /** The terminology server used when none is given. */
    public String defaultTxServer() {
        return defaultTxServer;
    }

    /** The validodo version context for this release. */
    public FhirVersionContext context() {
        return switch (this) {
            case R4 -> FhirVersionContext.r4();
            case R5 -> FhirVersionContext.r5();
        };
    }

    /**
     * Looks up a release by FHIR version string.
     *
     * @throws IllegalArgumentException if the version is not supported
     */
    public static FhirRelease fromVersion(String fhirVersion) {
        for (FhirRelease r : values()) {
            if (r.fhirVersion.equals(fhirVersion)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unsupported FHIR version '" + fhirVersion
                + "'; supported: 4.0.1, 5.0.0");
    }
}
