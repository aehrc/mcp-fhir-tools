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

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import au.csiro.fhir.validation.common.ValidationIssue;
import au.csiro.fhir.validation.pipeline.ValidationPipeline;
import au.csiro.fhir.validation.pipeline.ValidationPipelineBuilder;
import au.csiro.fhir.validation.terminology.CommonCodeSystems;
import au.csiro.fhir.validation.terminology.ExpansionResult;
import au.csiro.fhir.validation.terminology.FhirTerminologyClient;

/**
 * Validates one FHIR resource with validodo and prints the issues as JSON on stdout.
 *
 * <p>Exit codes: 0 when validation ran (issues, if any, are in the output); 1 on an unexpected
 * failure; 2 on a usage error.
 */
public final class ValidateCli {

    private ValidateCli() {
    }

    public static void main(String[] args) {
        final CliArgs parsed;
        try {
            parsed = CliArgs.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(CliArgs.USAGE);
            System.exit(2);
            return;
        }
        try {
            System.out.println(IssueJson.toJson(run(parsed)));
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /** Validates the file named by {@code args} and returns the issues. */
    static List<ValidationIssue> run(CliArgs args) throws Exception {
        final String resource = Files.readString(args.file());

        // User packages first so an IG's definition wins over core on a duplicate canonical.
        final List<String> packages = new ArrayList<>(args.packages());
        packages.add(args.release().corePackage());
        final FilesystemPackageCacheManager cache =
                new FilesystemPackageCacheManager.Builder().build();
        final PackageProfileSource profiles = PackageProfileSource.load(cache, packages);

        final FhirTerminologyClient txClient = new FhirTerminologyClient(args.txServer());
        final ValidationPipeline pipeline = ValidationPipelineBuilder
                .forVersion(args.release().context())
                .structureDefinitions(profiles)
                .terminology(new CommonCodeSystems(txClient))
                // Bare `code` bindings are only checked against a compile-time expansion - with
                // deferAllBindings they are silently skipped. Expand each bound ValueSet once
                // through the tx server so required code bindings are enforced.
                .compileTimeValueSets(url -> expandedValueSet(txClient, url))
                .build();

        return pipeline.validate(resource, args.profiles());
    }

    /**
     * Expands a ValueSet through the terminology server, in the form validodo's compiler
     * consumes: a ValueSet whose {@code expansion.contains} lists the codes and whose
     * {@code expansion.total} matches. A capped expansion is reported as such (total exceeds the
     * contains count) so the compiler declines it and defers to the runtime resolver.
     *
     * @return the expanded ValueSet as JSON, or null when the server could not expand
     */
    private static JsonNode expandedValueSet(FhirTerminologyClient txClient, String canonicalUrl) {
        final ExpansionResult expansion = txClient.expandValueSet(canonicalUrl);
        if (!expansion.success()) {
            System.err.println("Could not expand " + canonicalUrl + ": "
                    + expansion.errorMessage() + "; binding deferred to validation time");
            return null;
        }
        final var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        final ObjectNode vs = mapper.createObjectNode();
        vs.put("resourceType", "ValueSet");
        vs.put("url", canonicalUrl);
        final ObjectNode expansionNode = vs.putObject("expansion");
        final ArrayNode contains = expansionNode.putArray("contains");
        for (ExpansionResult.ExpandedCoding coding : expansion.codes()) {
            final ObjectNode c = contains.addObject();
            if (coding.system() != null) {
                c.put("system", coding.system());
            }
            c.put("code", coding.code());
            if (coding.version() != null) {
                c.put("version", coding.version());
            }
        }
        expansionNode.put("total", contains.size());
        return vs;
    }
}
