# FHIR Tools

Tools for working with FHIR resources, including validation and identifier
generation.

## Features

### FHIR resource validation

Validates FHIR resources using [validodo](https://github.com/aehrc/validodo), a
fast standalone FHIR validation library, with terminology checks delegated to a
FHIR terminology server.

### UUID generation

Generates random UUID v4 strings for use as unique identifiers in FHIR
resources.

## Building the validator launcher

The `validate` tool invokes a small Java launcher, built against the validodo
library. Build it with:

```bash
npm run build-validator
```

This requires Java 21 and Maven, plus a local build of validodo in your Maven
repository (clone [aehrc/validodo](https://github.com/aehrc/validodo) and run
`mvn install -DskipTests`). The launcher JAR is written to
`fhir-tools/bin/validodo-cli.jar`.

Until validodo is published to a public Maven repository, the launcher cannot be
built by plain `npx` consumers; build it from a checkout as above.

## Installation

### Adding to Claude Desktop

To add this tool to your Claude Desktop configuration:

1. Open Claude Desktop and go to Settings > Developer Settings
2. Find your Claude desktop configuration file:
   - macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`
   - Windows: `%APPDATA%\Claude\claude_desktop_config.json`
3. Add the following to your configuration file:

```json
{
  "mcpServers": {
    "fhir-tools": {
      "command": "npx",
      "args": ["-y", "mcp-fhir-tools"]
    }
  }
}
```

4. If you already have other MCP servers configured, just add the "fhir-tools"
   entry to the existing "mcpServers" object
5. Save the file and restart Claude Desktop
6. You should now see the FHIR Tools available in the tools menu (hammer icon)

### Adding to Goose

To add this tool as a Command-Line Extension in
[Goose](https://block.github.io/goose/):

1.  Run `goose configure` in your terminal.
2.  Select `Add Extension` from the menu.
3.  Choose `Command-line Extension`.
4.  When prompted for "What would you like to call this extension?", you can
    enter a descriptive name, for example, "FHIR Tools".
5.  For "What command should be run?", enter:
    ```bash
    npx -y mcp-fhir-tools
    ```
6.  You can set a timeout (e.g., `300` seconds) or accept the default.
7.  Choose whether to add environment variables (not required for this tool).

Goose will then confirm that the extension has been added. You can enable or
disable it via `goose configure` > `Toggle Extensions`.

## Usage

### Starting the Server

#### CLI Mode (stdio)

```bash
bun run start-stdio
```

#### Web Server Mode (SSE)

```bash
bun run start-sse
```

This starts a server on port 3003 that can be accessed via Server-Sent Events
(SSE).

### Tool Documentation

#### validate

Validates a FHIR resource using validodo. Profiles declared in the resource's
`meta.profile` are always applied.

Parameters:

- `resource`: FHIR resource in JSON format
- `fhirVersion`: The version of FHIR to validate against (`4.0.1` or `5.0.0`)
- `txServer` (optional): URL of the terminology server to use. Defaults to
  `https://tx.fhir.org/r4` (R4) or `https://tx.fhir.org/r5` (R5). For Australian
  FHIR content, use `https://tx.hl7.org.au/fhir`.
- `packages` (optional): FHIR packages to load profiles from, each as
  `id#version` (e.g. `hl7.fhir.au.core#2.0.0`). Declare the implementation
  guide(s) the resource claims conformance to; dependencies are loaded
  automatically.
- `profiles` (optional): profile canonical URLs to validate against, in addition
  to `meta.profile`.

#### generate-uuid

Generates a random UUID v4 string.

Parameters: None

Copyright © 2025, Commonwealth Scientific and Industrial Research Organisation
(CSIRO) ABN 41 687 119 230. Licensed under the
[Apache License, version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
