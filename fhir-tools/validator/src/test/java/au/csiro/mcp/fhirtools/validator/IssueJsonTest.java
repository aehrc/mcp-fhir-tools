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

import java.util.List;

import org.junit.jupiter.api.Test;

import au.csiro.fhir.validation.common.IssueSeverity;
import au.csiro.fhir.validation.common.IssueType;
import au.csiro.fhir.validation.common.ValidationIssue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** JSON shape the MCP server consumes on stdout. */
class IssueJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Each issue becomes an object with lower-case FHIR issue-severity / issue-type codes.
    @Test
    void serialisesIssuesAsArrayOfObjects() throws Exception {
        final List<ValidationIssue> issues = List.of(
                ValidationIssue.of(IssueSeverity.ERROR, IssueType.CODE_INVALID,
                        "Patient.gender", "Unknown code 'malee'"),
                new ValidationIssue(IssueSeverity.WARNING, IssueType.NOT_FOUND,
                        List.of("Patient.meta.profile[0]", "Patient"), "Profile not found"));

        final JsonNode out = MAPPER.readTree(IssueJson.toJson(issues));

        assertEquals(2, out.size());
        assertEquals("error", out.get(0).get("severity").asText());
        assertEquals("code-invalid", out.get(0).get("code").asText());
        assertEquals("Patient.gender", out.get(0).get("expression").get(0).asText());
        assertEquals("Unknown code 'malee'", out.get(0).get("diagnostics").asText());
        assertEquals("warning", out.get(1).get("severity").asText());
        assertEquals("not-found", out.get(1).get("code").asText());
        assertEquals(2, out.get(1).get("expression").size());
    }

    // No issues is an empty array, not an absent or null value.
    @Test
    void serialisesEmptyListAsEmptyArray() throws Exception {
        final JsonNode out = MAPPER.readTree(IssueJson.toJson(List.of()));
        assertEquals(0, out.size());
        assertEquals(true, out.isArray());
    }
}
