/* eslint-disable @typescript-eslint/no-var-requires */

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

import server from "../src/server";

type ExecFileCallback = (
  error: Error | null,
  stdout: string,
  stderr: string,
) => void;

/**
 * Stubs child_process.execFile so no Java process is spawned. The stub is handed the full
 * (file, args, callback) invocation, which the tests assert on.
 */
function stubExecFile(
  result: { stdout?: string; error?: Error } | { callback: ExecFileCallback },
): { calls: unknown[][] } {
  const calls: unknown[][] = [];
  jest
    .spyOn(require("child_process"), "execFile")
    .mockImplementation((...args: unknown[]) => {
      calls.push(args);
      const callback = args[args.length - 1] as ExecFileCallback;
      if ("callback" in result) {
        result.callback(null, "", "");
      } else if (result.error) {
        process.nextTick(() => callback(result.error as Error, "", ""));
      } else {
        process.nextTick(() => callback(null, result.stdout ?? "", ""));
      }
      return {} as unknown;
    });
  return { calls };
}

describe("FHIR Tools", () => {
  test("generate-uuid returns a valid UUID v4", async () => {
    const tool = server["_registeredTools"]["generate-uuid"];
    const result = await tool.callback({});
    expect(result.content[0].text).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  });

  describe("validate tool", () => {
    afterEach(() => {
      jest.restoreAllMocks();
    });

    test("invokes the validodo launcher jar with the parsed arguments", async () => {
      const { calls } = stubExecFile({ stdout: "[]" });
      const tool = server["_registeredTools"]["validate"];
      const response = await tool.callback({
        resource: "{}",
        fhirVersion: "4.0.1",
        txServer: "https://tx.hl7.org.au/fhir",
        packages: ["hl7.fhir.au.core#2.0.0"],
        profiles: [
          "http://hl7.org.au/fhir/core/StructureDefinition/au-core-patient",
        ],
      });

      // The launcher is invoked as: java -jar <jar> [options] <temp file>, where the options
      // are passed to execFile as a single array argument.
      const execArgs = calls[0][1] as string[];
      expect(calls[0][0]).toBe("java");
      expect(execArgs[0]).toBe("-jar");
      expect(execArgs[1]).toMatch(/validodo-cli\.jar$/);
      expect(execArgs).toContain("--fhir-version");
      expect(execArgs).toContain("4.0.1");
      expect(execArgs).toContain("--tx");
      expect(execArgs).toContain("https://tx.hl7.org.au/fhir");
      expect(execArgs).toContain("--package");
      expect(execArgs).toContain("hl7.fhir.au.core#2.0.0");
      expect(execArgs).toContain("--profile");
      expect(execArgs).toContain(
        "http://hl7.org.au/fhir/core/StructureDefinition/au-core-patient",
      );
      expect(execArgs[execArgs.length - 1]).toMatch(
        /^\/tmp\/resource-.*\.json$/,
      );
      expect(response.isError).toBeFalsy();
      expect(response.content[0].text).toContain("no issues");
    });

    test("reports each issue returned by the launcher", async () => {
      stubExecFile({
        stdout: JSON.stringify([
          {
            severity: "error",
            code: "code-invalid",
            expression: ["Patient.gender"],
            diagnostics:
              "The value provided ('malee') was not found in the value set",
          },
          {
            severity: "warning",
            code: "not-found",
            expression: [],
            diagnostics: "Profile 'http://example.org/x' could not be found",
          },
        ]),
      });
      const tool = server["_registeredTools"]["validate"];
      const response = await tool.callback({
        resource: "{}",
        fhirVersion: "4.0.1",
      });

      expect(response.isError).toBeFalsy();
      expect(response.content[0].text).toContain("2 issues");
      expect(response.content[0].text).toContain("error");
      expect(response.content[0].text).toContain("Patient.gender");
      expect(response.content[0].text).toContain("malee");
      expect(response.content[0].text).toContain("warning");
    });

    test("returns an error when the launcher process fails", async () => {
      stubExecFile({ error: new Error("jar not found") });
      const tool = server["_registeredTools"]["validate"];
      const response = await tool.callback({
        resource: "{}",
        fhirVersion: "4.0.1",
      });

      expect(response.isError).toBe(true);
      expect(response.content[0].text).toContain("jar not found");
    });

    test("returns an error when the launcher output is not valid JSON", async () => {
      stubExecFile({ stdout: "not json" });
      const tool = server["_registeredTools"]["validate"];
      const response = await tool.callback({
        resource: "{}",
        fhirVersion: "4.0.1",
      });

      expect(response.isError).toBe(true);
      expect(response.content[0].text).toContain("Unexpected");
    });
  });
});
