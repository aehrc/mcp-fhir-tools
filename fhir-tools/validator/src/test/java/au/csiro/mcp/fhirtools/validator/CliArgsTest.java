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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Command-line parsing for the launcher. */
class CliArgsTest {

    // The minimal invocation: a FHIR version and a file. Everything else defaults.
    @Test
    void parsesVersionAndFileWithDefaults() {
        final CliArgs args = CliArgs.parse(
                new String[] {"--fhir-version", "4.0.1", "resource.json"});

        assertEquals(FhirRelease.R4, args.release());
        assertEquals("https://tx.fhir.org/r4", args.txServer(), "tx defaults per release");
        assertEquals(List.of(), args.packages());
        assertEquals(List.of(), args.profiles());
        assertEquals(Path.of("resource.json"), args.file());
    }

    // R5 selects the R5 default terminology endpoint.
    @Test
    void r5DefaultsToR5TerminologyEndpoint() {
        final CliArgs args = CliArgs.parse(
                new String[] {"--fhir-version", "5.0.0", "r.json"});

        assertEquals(FhirRelease.R5, args.release());
        assertEquals("https://tx.fhir.org/r5", args.txServer());
    }

    // Repeated --package and --profile accumulate in order; --tx overrides the default.
    @Test
    void parsesRepeatedPackagesProfilesAndTx() {
        final CliArgs args = CliArgs.parse(new String[] {
            "--fhir-version", "4.0.1",
            "--tx", "https://tx.hl7.org.au/fhir",
            "--package", "hl7.fhir.au.core#2.0.0",
            "--package", "hl7.fhir.au.base#5.1.0",
            "--profile", "http://hl7.org.au/fhir/core/StructureDefinition/au-core-patient",
            "r.json"});

        assertEquals("https://tx.hl7.org.au/fhir", args.txServer());
        assertEquals(List.of("hl7.fhir.au.core#2.0.0", "hl7.fhir.au.base#5.1.0"), args.packages());
        assertEquals(
                List.of("http://hl7.org.au/fhir/core/StructureDefinition/au-core-patient"),
                args.profiles());
    }

    // Unsupported versions are rejected up front rather than failing deep in the pipeline.
    @Test
    void rejectsUnsupportedFhirVersion() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CliArgs.parse(new String[] {"--fhir-version", "3.0.2", "r.json"}));
        assertTrue(e.getMessage().contains("3.0.2"), e.getMessage());
    }

    // Both the version and the file are mandatory.
    @Test
    void rejectsMissingVersionOrFile() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgs.parse(new String[] {"r.json"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgs.parse(new String[] {"--fhir-version", "4.0.1"}));
    }

    // A package must be spelled id#version so the cache manager can resolve it exactly.
    @Test
    void rejectsPackageWithoutVersion() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CliArgs.parse(new String[] {
                    "--fhir-version", "4.0.1", "--package", "hl7.fhir.au.core", "r.json"}));
        assertTrue(e.getMessage().contains("hl7.fhir.au.core"), e.getMessage());
    }

    // An option missing its value, or an unknown option, is a usage error.
    @Test
    void rejectsDanglingOrUnknownOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArgs.parse(new String[] {"--fhir-version", "4.0.1", "r.json", "--tx"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliArgs.parse(new String[] {"--fhir-version", "4.0.1", "--sct", "au", "r.json"}));
    }
}
