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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.NpmPackage.PackageResourceInformation;

import au.csiro.fhir.validation.pipeline.StructureDefinitionSource;

/**
 * A {@link StructureDefinitionSource} over one or more FHIR packages, serving each
 * StructureDefinition as the raw JSON bytes stored in the package.
 *
 * <p>Indexing uses the package's {@code .index.json} so no StructureDefinition is parsed until it
 * is asked for, and the JSON is never round-tripped through a typed model (validodo's input
 * contract).
 */
final class PackageProfileSource implements StructureDefinitionSource {

    private record Entry(NpmPackage pkg, PackageResourceInformation info) {
    }

    private final Map<String, Entry> byUrl = new HashMap<>();

    /**
     * Indexes the StructureDefinitions of the given packages. Earlier packages win on a duplicate
     * canonical.
     */
    PackageProfileSource(List<NpmPackage> packages) throws IOException {
        for (NpmPackage pkg : packages) {
            for (PackageResourceInformation info : pkg.listIndexedResources("StructureDefinition")) {
                final String url = info.getUrl();
                if (url == null) {
                    continue;
                }
                final Entry entry = new Entry(pkg, info);
                byUrl.putIfAbsent(url, entry);
                if (info.getVersion() != null) {
                    byUrl.putIfAbsent(url + "|" + info.getVersion(), entry);
                }
            }
        }
    }

    /**
     * Loads the named packages and, transitively, their declared dependencies from the local FHIR
     * package cache (downloading from the package registry as needed). Packages are ordered as
     * given, each followed by its dependencies, so the most specific IG wins on a duplicate
     * canonical. A package already loaded is not loaded again.
     *
     * @param cache the package cache manager
     * @param packageIds {@code id#version} strings
     */
    static PackageProfileSource load(FilesystemPackageCacheManager cache, List<String> packageIds)
            throws IOException {
        final Set<String> seen = new LinkedHashSet<>();
        final List<NpmPackage> loaded = new java.util.ArrayList<>();
        for (String id : packageIds) {
            loadRecursive(cache, id, seen, loaded);
        }
        return new PackageProfileSource(loaded);
    }

    private static void loadRecursive(FilesystemPackageCacheManager cache, String idAndVersion,
            Set<String> seen, List<NpmPackage> out) throws IOException {
        if (!seen.add(idAndVersion)) {
            return;
        }
        final int hash = idAndVersion.indexOf('#');
        final NpmPackage pkg = cache.loadPackage(idAndVersion.substring(0, hash),
                idAndVersion.substring(hash + 1));
        out.add(pkg);
        for (String dep : pkg.dependencies()) {
            loadRecursive(cache, dep, seen, out);
        }
    }

    /** Number of index entries (url and url|version both count). */
    int size() {
        return byUrl.size();
    }

    @Override
    public String fetchStructureDefinition(String url) {
        final Entry entry = byUrl.get(url);
        if (entry == null) {
            return null;
        }
        try (InputStream in = entry.pkg().load(entry.info())) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + url + " from package "
                    + entry.pkg().name() + "#" + entry.pkg().version(), e);
        }
    }
}
