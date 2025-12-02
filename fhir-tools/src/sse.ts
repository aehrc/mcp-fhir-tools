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

import { SSEServerTransport } from "@modelcontextprotocol/sdk/server/sse.js";
import express from "express";
import server from "./server.js";

const appPort = process.env.PORT ?? 3003
const basePath = process.env.BASE_PATH ?? "";

const logger = console;
const app = express();

let transport: SSEServerTransport;

app.get("/sse", async (_req: express.Request, res: express.Response) => {
  logger.info("Received SSE connection request");
  transport = new SSEServerTransport(`${basePath}/messages`, res);
  await server.connect(transport);
  logger.info("SSE transport connected");
});

app.post("/messages", async (req: express.Request, res: express.Response) => {
  logger.debug("Received message", req);
  await transport.handlePostMessage(req, res);
});


app.listen(appPort, () =>
  logger.info(`Generic FHIR Tools server listening on port ${appPort}`),
);
