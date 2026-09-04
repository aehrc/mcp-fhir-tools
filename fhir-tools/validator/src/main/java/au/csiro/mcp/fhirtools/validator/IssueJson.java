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

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import au.csiro.fhir.validation.common.ValidationIssue;

/** Serialises validodo issues to the JSON array the MCP server reads from stdout. */
final class IssueJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IssueJson() {
    }

    /**
     * @return a JSON array of {@code {severity, code, expression[], diagnostics}} objects, with
     *         severity and code as lower-case hyphenated FHIR codes
     */
    static String toJson(List<ValidationIssue> issues) {
        final ArrayNode out = MAPPER.createArrayNode();
        for (ValidationIssue issue : issues) {
            final ObjectNode node = out.addObject();
            node.put("severity", fhirCode(issue.severity().name()));
            node.put("code", fhirCode(issue.code().name()));
            final ArrayNode expression = node.putArray("expression");
            issue.expression().forEach(expression::add);
            node.put("diagnostics", issue.diagnostics());
        }
        return out.toString();
    }

    // Enum constants match the FHIR value set members; the FHIR code is the same name lower-cased
    // with hyphens (e.g. CODE_INVALID -> code-invalid).
    private static String fhirCode(String enumName) {
        return enumName.toLowerCase().replace('_', '-');
    }
}
