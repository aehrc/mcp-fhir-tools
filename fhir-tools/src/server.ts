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
 *
 * Author: John Grimes
 */

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { execFile } from "child_process";
import crypto from "crypto";
import fs from "fs";
import path from "path";
import { z } from "zod";

/**
 * FHIR Tools Server
 *
 * This server provides tools for working with FHIR resources:
 * - Resource validation against official FHIR schemas and implementation guides
 * - Generation of identifiers (UUIDs) for FHIR resources
 *
 * The tools are designed to help with common FHIR development tasks and
 * can be integrated into healthcare software development workflows.
 */
const server = new McpServer({
  name: "FHIR Tools",
  version: "0.1.0",
  description: "Tools for validating FHIR resources and generating identifiers",
});

// Register FHIR validation tool
server.tool(
  "validate",
  `Validate a FHIR resource using the validodo validation library. If the resource contains coded 
  fields, use the lookup-code tool to make sure that valid codes are populated before attempting 
  validation.`,
  {
    resource: z.string().describe(`FHIR resource in JSON format`),
    fhirVersion: z.enum(["4.0.1", "5.0.0"])
      .describe(`The version of FHIR to validate against:
        - "4.0.1": R4 (use this if version not specified)
        - "5.0.0": R5`),
    txServer: z
      .string()
      .url()
      .optional()
      .describe(
        `Optional URL of the terminology server to use for validation. If not provided, 
        "https://tx.fhir.org/r4" (R4) or "https://tx.fhir.org/r5" (R5) will be used. For 
        Australian FHIR content, use "https://tx.hl7.org.au/fhir".`,
      ),
    packages: z
      .array(z.string())
      .optional()
      .describe(
        `Optional FHIR packages to load profiles from, each in the form "id#version" (e.g. 
        "hl7.fhir.au.core#2.0.0"). Declare the implementation guide(s) the resource claims 
        conformance to; their dependencies are loaded automatically. The core definitions for 
        the chosen FHIR version are always loaded.`,
      ),
    profiles: z
      .array(z.string())
      .optional()
      .describe(
        `Optional profile canonical URLs to validate against, in addition to any profiles 
        declared in the resource's meta.profile.`,
      ),
  },
  async ({ resource, fhirVersion, txServer, packages, profiles }) => {
    // Create a temporary file to store the resource
    const tempFile = `/tmp/resource-${crypto.randomUUID()}.json`;
    try {
      await fs.promises.writeFile(tempFile, resource);

      // Determine the path to the validodo launcher JAR, which lives in fhir-tools/bin/
      // (a sibling of the src/ and dist/ directories). VALIDODO_CLI_JAR overrides it.
      const jarPath =
        process.env.VALIDODO_CLI_JAR ??
        path.join(
          path.dirname(path.resolve(process.argv[1] ?? ".")),
          "../bin/validodo-cli.jar",
        );

      // Construct the validation command arguments for execFile
      const commandArgs = ["-jar", jarPath, "--fhir-version", fhirVersion];
      if (txServer) {
        commandArgs.push("--tx", txServer);
      }
      for (const pkg of packages ?? []) {
        commandArgs.push("--package", pkg);
      }
      for (const profile of profiles ?? []) {
        commandArgs.push("--profile", profile);
      }
      commandArgs.push(tempFile);

      // Execute the command. The launcher exits 0 whenever validation ran, so a non-zero
      // exit code means the process itself failed.
      const { stdout } = await new Promise<{ stdout: string }>(
        (resolve, reject) => {
          execFile("java", commandArgs, (error, stdout, stderr) => {
            if (error) {
              const message = stderr || error.message;
              reject(
                new Error(
                  message +
                    (message.includes("validodo-cli.jar")
                      ? ` (build the launcher JAR with \`npm run build-validator\`)`
                      : ""),
                ),
              );
            } else {
              resolve({ stdout });
            }
          });
        },
      );

      // Process the output: one issue per line in a JSON array
      let issues: Array<{
        severity: string;
        code: string;
        expression: string[];
        diagnostics: string;
      }>;
      try {
        issues = JSON.parse(stdout);
      } catch (error) {
        throw new Error(
          `Unexpected output from validator: ${error instanceof Error ? error.message : String(error)}`,
        );
      }

      if (issues.length === 0) {
        return {
          content: [
            {
              type: "text",
              text: "Validation completed with no issues",
            },
          ],
          isError: false,
        };
      }

      const renderedIssues = issues
        .map((issue) => {
          const location =
            issue.expression.length > 0
              ? ` at ${issue.expression.join(", ")}`
              : "";
          return `[${issue.severity}] ${issue.diagnostics} (${issue.code}${location})`;
        })
        .join("\n");

      return {
        content: [
          {
            type: "text",
            text: `Validation completed with ${issues.length} issue${issues.length === 1 ? "" : "s"}:\n${renderedIssues}`,
          },
        ],
        isError: false,
      };
    } catch (error) {
      const result = `Error during validation: ${error instanceof Error ? error.message : String(error)}`;
      return {
        content: [{ type: "text", text: result }],
        isError: true,
      };
    } finally {
      // Clean up the temp file whether validation succeeded or failed
      await fs.promises.unlink(tempFile).catch(() => undefined);
    }
  },
);

// Register UUID v4 generator tool
server.tool(
  "generate-uuid",
  "Generate a random UUID v4 string. Use this when you need to create unique identifiers.",
  {},
  async () => {
    // Generate UUID v4
    const uuid = crypto.randomUUID();
    return {
      content: [{ type: "text", text: uuid }],
    };
  },
);

export default server;
