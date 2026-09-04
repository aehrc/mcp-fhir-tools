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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import au.csiro.fhir.validation.common.IssueSeverity;
import au.csiro.fhir.validation.common.ValidationIssue;

/**
 * End-to-end through the real validodo pipeline. Needs the FHIR package cache (~/.fhir/packages,
 * downloaded from packages.fhir.org on first run) and a reachable terminology server.
 */
class ValidateCliIntegrationTest {

    @TempDir
    Path tmp;

    // A structurally invalid R4 Patient: unknown element and a gender outside the required binding.
    @Test
    void reportsErrorsForInvalidR4Patient() throws Exception {
        final Path file = tmp.resolve("patient.json");
        Files.writeString(file, """
            {"resourceType":"Patient","id":"p1","gender":"malee","foo":"bar"}
            """);

        final List<ValidationIssue> issues = ValidateCli.run(CliArgs.parse(
                new String[] {"--fhir-version", "4.0.1", file.toString()}));

        final List<String> errors = issues.stream()
                .filter(i -> i.severity() == IssueSeverity.ERROR)
                .map(ValidationIssue::diagnostics)
                .toList();
        assertTrue(errors.stream().anyMatch(d -> d.contains("foo")),
                "unknown element reported: " + errors);
        assertTrue(errors.stream().anyMatch(d -> d.contains("malee")),
                "code outside required binding reported: " + errors);
    }

    // A valid R4 Patient yields no errors.
    @Test
    void reportsNoErrorsForValidR4Patient() throws Exception {
        final Path file = tmp.resolve("patient.json");
        Files.writeString(file, """
            {"resourceType":"Patient","id":"p1","gender":"male","birthDate":"1970-01-01",
             "name":[{"family":"Smith","given":["Jane"]}]}
            """);

        final List<ValidationIssue> issues = ValidateCli.run(CliArgs.parse(
                new String[] {"--fhir-version", "4.0.1", file.toString()}));

        assertEquals(List.of(), issues.stream()
                .filter(i -> i.severity() == IssueSeverity.ERROR).toList());
    }

    // An IG package (and its transitive dependencies) resolves the profile named in meta.profile.
    @Test
    void validatesAgainstPackageProfile() throws Exception {
        final Path file = tmp.resolve("patient.json");
        Files.writeString(file, """
            {"resourceType":"Patient","id":"p1",
             "meta":{"profile":["http://hl7.org.au/fhir/core/StructureDefinition/au-core-patient"]},
             "gender":"male"}
            """);

        final List<ValidationIssue> issues = ValidateCli.run(CliArgs.parse(new String[] {
            "--fhir-version", "4.0.1",
            "--tx", "https://tx.hl7.org.au/fhir",
            "--package", "hl7.fhir.au.core#2.0.0",
            file.toString()}));

        final List<String> all = issues.stream().map(ValidationIssue::diagnostics).toList();
        assertTrue(all.stream().noneMatch(d -> d.contains("could not be found")),
                "profile resolved from the package: " + all);
        // AU Core Patient requires Patient.name (1..*), which is absent here.
        assertTrue(issues.stream().anyMatch(i -> i.severity() == IssueSeverity.ERROR
                        && i.expression().contains("Patient")
                        && i.diagnostics().contains("name")),
                "AU Core cardinality violation reported: " + all);
    }
}
