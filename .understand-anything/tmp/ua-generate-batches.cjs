#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

const projectRoot = process.argv[2];
const skillDir = process.argv[3];
if (!projectRoot || !skillDir) {
  console.error("usage: ua-generate-batches.cjs <projectRoot> <skillDir>");
  process.exit(1);
}

const interDir = path.join(projectRoot, ".understand-anything", "intermediate");
const tmpDir = path.join(projectRoot, ".understand-anything", "tmp");
const batches = JSON.parse(fs.readFileSync(path.join(interDir, "batches.json"), "utf8")).batches;

function nodeType(file) {
  if (file.fileCategory === "config") return "config";
  if (file.fileCategory === "docs") return "document";
  if (file.fileCategory === "infra") {
    if (file.path.includes(".github/workflows/") || file.path.includes(".gitlab-ci") || file.path.includes("Jenkinsfile")) return "pipeline";
    if (file.path.endsWith(".tf") || file.path.endsWith(".tfvars")) return "resource";
    return "service";
  }
  if (file.fileCategory === "data") {
    if (file.path.endsWith(".sql")) return "table";
    if (file.path.endsWith(".graphql") || file.path.endsWith(".gql") || file.path.endsWith(".proto") || file.path.endsWith(".prisma")) return "schema";
  }
  return "file";
}

function baseTags(file, type) {
  const tags = new Set();
  tags.add(file.fileCategory === "docs" ? "documentation" : file.fileCategory);
  tags.add(file.language || "unknown");
  if (file.path.includes("/test/") || file.path.includes("Test.")) tags.add("test");
  if (file.path.endsWith("Application.java")) tags.add("entry-point");
  if (type === "config") tags.add("configuration");
  if (type === "service") tags.add("infrastructure");
  return Array.from(tags).slice(0, 5);
}

function complexity(lines, metrics) {
  const count = metrics && (metrics.functionCount || 0) + (metrics.classCount || 0);
  if (lines > 220 || count > 8) return "complex";
  if (lines > 70 || count > 2) return "moderate";
  return "simple";
}

function safeName(filePath) {
  return path.basename(filePath);
}

function fileSummary(file, result) {
  const type = nodeType(file);
  if (type === "document") return `Documents ${file.path.replace(/^docs\//, "")} for the GraphRAG project.`;
  if (type === "config") return `Configures ${safeName(file.path)} for the GraphRAG application and its tooling.`;
  if (type === "service" || type === "pipeline" || type === "resource") return `Defines infrastructure or runtime support in ${file.path}.`;
  const parts = [];
  if (result.classes && result.classes.length) parts.push(`${result.classes.length} class(es)`);
  if (result.functions && result.functions.length) parts.push(`${result.functions.length} function(s)`);
  const detail = parts.length ? ` with ${parts.join(" and ")}` : "";
  return `Implements ${file.path.replace(/^src\/main\/java\//, "").replace(/^src\/test\/java\//, "")}${detail}.`;
}

function addUnique(array, seen, item, key) {
  if (seen.has(key)) return;
  seen.add(key);
  array.push(item);
}

for (const batch of batches) {
  const inputPath = path.join(tmpDir, `ua-file-analyzer-input-${batch.batchIndex}.json`);
  const outputPath = path.join(tmpDir, `ua-file-extract-results-${batch.batchIndex}.json`);
  const batchOut = path.join(interDir, `batch-${batch.batchIndex}.json`);
  fs.writeFileSync(inputPath, JSON.stringify({
    projectRoot,
    batchFiles: batch.files,
    batchImportData: batch.batchImportData || {}
  }, null, 2));

  const run = spawnSync("node", [
    path.join(skillDir, "extract-structure.mjs"),
    inputPath,
    outputPath
  ], { encoding: "utf8" });
  if (run.status !== 0) {
    process.stderr.write(run.stderr || run.stdout || `extract failed for batch ${batch.batchIndex}\n`);
    process.exit(run.status || 1);
  }

  const extracted = JSON.parse(fs.readFileSync(outputPath, "utf8"));
  const byPath = new Map(batch.files.map((file) => [file.path, file]));
  const nodes = [];
  const edges = [];
  const seenNodes = new Set();
  const seenEdges = new Set();

  for (const result of extracted.results || []) {
    const file = byPath.get(result.path) || {
      path: result.path,
      language: result.language || "unknown",
      sizeLines: result.totalLines || 0,
      fileCategory: result.fileCategory || "code"
    };
    const type = nodeType(file);
    const fileId = `${type}:${file.path}`;
    addUnique(nodes, seenNodes, {
      id: fileId,
      type,
      name: safeName(file.path),
      filePath: file.path,
      summary: fileSummary(file, result),
      tags: baseTags(file, type),
      complexity: complexity(result.nonEmptyLines || file.sizeLines || 0, result.metrics || {})
    }, fileId);

    for (const target of Object.values(batch.batchImportData || {}).length ? (batch.batchImportData[file.path] || []) : []) {
      const targetFile = target.startsWith("file:") || target.startsWith("config:") ? target : `file:${target}`;
      const key = `${fileId}->${targetFile}:imports`;
      addUnique(edges, seenEdges, { source: fileId, target: targetFile, type: "imports", direction: "forward", weight: 0.7 }, key);
    }

    for (const cls of result.classes || []) {
      const range = cls.lineRange || [cls.startLine || 1, cls.endLine || cls.startLine || 1];
      const lineCount = Math.max(0, range[1] - range[0] + 1);
      if ((cls.methods || []).length < 2 && lineCount < 20) continue;
      const id = `class:${file.path}:${cls.name}`;
      addUnique(nodes, seenNodes, {
        id,
        type: "class",
        name: cls.name,
        filePath: file.path,
        lineRange: range,
        summary: `Represents ${cls.name} in ${safeName(file.path)}.`,
        tags: ["class", file.language || "code"],
        complexity: complexity(lineCount, { classCount: 1, functionCount: (cls.methods || []).length })
      }, id);
      addUnique(edges, seenEdges, { source: fileId, target: id, type: "contains", direction: "forward", weight: 1 }, `${fileId}->${id}:contains`);
    }

    for (const fn of result.functions || []) {
      const range = fn.lineRange || [fn.startLine || 1, fn.endLine || fn.startLine || 1];
      const lineCount = Math.max(0, range[1] - range[0] + 1);
      const exported = (result.exports || []).some((exp) => exp.name === fn.name);
      if (!exported && lineCount < 10) continue;
      const id = `function:${file.path}:${fn.name}`;
      addUnique(nodes, seenNodes, {
        id,
        type: "function",
        name: fn.name,
        filePath: file.path,
        lineRange: range,
        summary: `Provides ${fn.name} behavior in ${safeName(file.path)}.`,
        tags: ["function", file.language || "code"],
        complexity: complexity(lineCount, { functionCount: 1 })
      }, id);
      addUnique(edges, seenEdges, { source: fileId, target: id, type: "contains", direction: "forward", weight: 1 }, `${fileId}->${id}:contains`);
    }
  }

  fs.writeFileSync(batchOut, JSON.stringify({ nodes, edges }, null, 2));
  console.log(`batch ${batch.batchIndex}: ${nodes.length} nodes, ${edges.length} edges`);
}
