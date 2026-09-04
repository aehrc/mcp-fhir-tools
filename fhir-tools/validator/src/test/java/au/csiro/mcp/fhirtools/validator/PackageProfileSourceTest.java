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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.hl7.fhir.utilities.npm.NpmPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** StructureDefinition lookup over unpacked FHIR packages. */
class PackageProfileSourceTest {

    @TempDir
    Path tmp;

    // The SD is served as the raw bytes from the package, keyed by url and url|version.
    @Test
    void indexesStructureDefinitionsByUrlAndVersionedUrl() throws IOException {
        final String sd = """
            {"resourceType":"StructureDefinition","id":"a","url":"http://example.org/a",
             "version":"1.2.3","name":"A","kind":"resource","type":"Patient","abstract":false,
             "status":"active","baseDefinition":"http://hl7.org/fhir/StructureDefinition/Patient",
             "derivation":"constraint","minValueDuration":{"value":1}}
            """;
        final NpmPackage pkg = packageWith("example.pkg", "0.1.0", sd);

        final PackageProfileSource source = new PackageProfileSource(List.of(pkg));

        assertEquals(sd, source.fetchStructureDefinition("http://example.org/a"),
                "raw JSON, byte for byte - never round-tripped through a typed model");
        assertEquals(sd, source.fetchStructureDefinition("http://example.org/a|1.2.3"));
        assertNull(source.fetchStructureDefinition("http://example.org/a|9.9.9"));
        assertNull(source.fetchStructureDefinition("http://example.org/missing"));
    }

    // Earlier packages win: the caller lists the most specific package first.
    @Test
    void firstPackageWinsOnDuplicateCanonical() throws IOException {
        final String first = sdJson("http://example.org/dup", "1.0.0", "First");
        final String second = sdJson("http://example.org/dup", "2.0.0", "Second");
        final NpmPackage p1 = packageWith("pkg.one", "1.0.0", first);
        final NpmPackage p2 = packageWith("pkg.two", "1.0.0", second);

        final PackageProfileSource source = new PackageProfileSource(List.of(p1, p2));

        assertEquals(first, source.fetchStructureDefinition("http://example.org/dup"));
        // The versioned keys do not collide, so both remain reachable.
        assertEquals(second, source.fetchStructureDefinition("http://example.org/dup|2.0.0"));
    }

    // Non-SD resources in the package are ignored by the index.
    @Test
    void ignoresOtherResourceTypes() throws IOException {
        final Path dir = tmp.resolve("vs");
        writePackageJson(dir, "pkg.vs", "1.0.0");
        Files.writeString(dir.resolve("package").resolve("ValueSet-x.json"),
                "{\"resourceType\":\"ValueSet\",\"url\":\"http://example.org/vs\"}");
        final NpmPackage pkg = NpmPackage.fromFolder(dir.toString());

        final PackageProfileSource source = new PackageProfileSource(List.of(pkg));

        assertNull(source.fetchStructureDefinition("http://example.org/vs"));
        assertTrue(source.size() == 0);
    }

    private NpmPackage packageWith(String id, String version, String sdJson) throws IOException {
        final Path dir = tmp.resolve(id);
        writePackageJson(dir, id, version);
        Files.writeString(dir.resolve("package").resolve("StructureDefinition-x.json"), sdJson);
        return NpmPackage.fromFolder(dir.toString());
    }

    private static void writePackageJson(Path dir, String id, String version) throws IOException {
        Files.createDirectories(dir.resolve("package"));
        Files.writeString(dir.resolve("package").resolve("package.json"),
                "{\"name\":\"" + id + "\",\"version\":\"" + version
                        + "\",\"fhirVersions\":[\"4.0.1\"]}");
    }

    private static String sdJson(String url, String version, String name) {
        return "{\"resourceType\":\"StructureDefinition\",\"url\":\"" + url + "\",\"version\":\""
                + version + "\",\"name\":\"" + name + "\",\"kind\":\"resource\",\"type\":\"Patient\","
                + "\"abstract\":false,\"status\":\"active\"}";
    }
}
