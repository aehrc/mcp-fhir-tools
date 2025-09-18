/*
 * Copyright 2025 Commonwealth Scientific and Industrial Research Organisation (CSIRO) ABN 41 687 119 230
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
 */

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { Parameters, ValueSet } from "fhir/r4.js";
import fetch from "node-fetch";
import { z } from "zod";

/**
 * Terminology Services Tools Server
 *
 * This server provides tools for interacting with FHIR terminology services, including code lookup
 * based on text descriptions.
 */
const server = new McpServer({
  name: "Terminology Tools",
  version: "0.1.0",
  description:
    "Tools for querying terminology using a FHIR terminology service.",
});

const defaultTXServer = process.env.TX_SERVER ?? 'https://tx.ontoserver.csiro.au/fhir';

// Register code lookup tool
server.tool(
  "lookup-code",
  `
  Look up a clinical code based on text description using a FHIR terminology server. Use this when 
  populating coded fields within FHIR resources to ensure that the code is valid and compliant with 
  the value set binding of the element. Returns the most relevant coding from the value set.
  `,
  {
    filter: z.string()
      .describe(`Text to search for (e.g. "hypertension", "tracheotomy", "left quad 
    laceration")`),
    url: z.string().describe(`ValueSet URL to search within.
    Common values:
    - "http://snomed.info/sct?fhir_vs" (all of SNOMED CT, use this if not specified)
    - "http://loinc.org/vs" (all of LOINC)
    - "http://snomed.info/sct?fhir_vs=isa/71388002" (SNOMED CT procedures, i.e. all codes that are a 
      subtype of Procedure (71388002))`),
    txServer: z.string().optional().describe(`Optional URL of the terminology server to use for validation. 
    If not provided, will use ${defaultTXServer}`),
  },
  async ({ filter, url, txServer }) => {
    const serverBase = txServer ?? defaultTXServer;

    const expandUrl = new URL(`${serverBase}/ValueSet/$expand`);
    expandUrl.searchParams.set("url", url);
    expandUrl.searchParams.set("filter", filter);

    try {
      const response = await fetch(expandUrl.toString(), {
        method: "GET",
        headers: {
          Accept: "application/fhir+json",
        },
      });

      if (!response.ok) {
        const errorText = await response.text();
        return {
          content: [
            {
              type: "text",
              text: `Terminology server error (${response.status}): ${errorText}`,
            },
          ],
          isError: true,
        };
      }

      const result = (await response.json()) as ValueSet;

      // Check if we have any results
      if (!result.expansion?.contains?.length) {
        return {
          content: [{ type: "text", text: "No matching codes found" }],
        };
      }

      // Get just the first result
      const firstMatch = result.expansion.contains[0];

      return {
        content: [{ type: "text", text: JSON.stringify(firstMatch, null, 2) }],
      };
    } catch (error) {
      return {
        content: [
          {
            type: "text",
            text: `Error looking up codes: ${error instanceof Error ? error.message : String(error)}`,
          },
        ],
        isError: true,
      };
    }
  },
);

// Register validate-code tool
server.tool(
  "validate-code",
  `
  Validate whether a code is valid within a specified value set using a FHIR terminology server.
  Use this to verify that a code is appropriate for a particular context or value set binding.
  Returns validation result including whether the code is valid and its display text.
  `,
  {
    system: z
      .string()
      .describe(
        "The code system URI (e.g. 'http://snomed.info/sct', 'http://loinc.org')",
      ),
    code: z
      .string()
      .describe("The code to validate (e.g. '30371007', '72133-2')"),
    url: z.string().describe(`ValueSet URL to validate against.
    Common values:
    - "http://snomed.info/sct?fhir_vs" (all of SNOMED CT)
    - "http://loinc.org/vs" (all of LOINC)
    - A specific value set URL from a FHIR profile binding`),
    version: z
      .string()
      .optional()
      .describe(
        "Optional version of the code system (e.g. 'http://snomed.info/sct/32506021000036107/version/20250831')",
      ),
  },
  async ({ system, code, url, version }) => {
    const serverBase =
      process.env.TX_SERVER ?? "https://tx.ontoserver.csiro.au/fhir";

    const validateUrl = new URL(`${serverBase}/ValueSet/$validate-code`);
    validateUrl.searchParams.set("url", url);
    validateUrl.searchParams.set("system", system);
    validateUrl.searchParams.set("code", code);
    if (version) {
      validateUrl.searchParams.set("version", version);
    }

    try {
      const response = await fetch(validateUrl.toString(), {
        method: "GET",
        headers: {
          Accept: "application/fhir+json",
        },
      });

      if (!response.ok) {
        const errorText = await response.text();
        return {
          content: [
            {
              type: "text",
              text: `Terminology server error (${response.status}): ${errorText}`,
            },
          ],
          isError: true,
        };
      }

      const result = (await response.json()) as Parameters;

      // Extract validation result from Parameters resource
      const resultParam = result.parameter?.find((p) => p.name === "result");
      const displayParam = result.parameter?.find((p) => p.name === "display");
      const codeParam = result.parameter?.find((p) => p.name === "code");
      const systemParam = result.parameter?.find((p) => p.name === "system");
      const versionParam = result.parameter?.find((p) => p.name === "version");

      const validationResult = {
        valid: resultParam?.valueBoolean ?? false,
        code: codeParam?.valueCode ?? code,
        system: systemParam?.valueUri ?? system,
        version: versionParam?.valueString,
        display: displayParam?.valueString,
      };

      return {
        content: [
          { type: "text", text: JSON.stringify(validationResult, null, 2) },
        ],
      };
    } catch (error) {
      return {
        content: [
          {
            type: "text",
            text: `Error validating code: ${error instanceof Error ? error.message : String(error)}`,
          },
        ],
        isError: true,
      };
    }
  },
);

export default server;
