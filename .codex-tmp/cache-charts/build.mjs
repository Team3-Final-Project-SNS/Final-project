import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outputDir = "/Users/t2025-m0040/Final-project/outputs/caching-before-after";
const workbook = Workbook.create();

const tests = ["Smoke", "Load", "Stress", "Spike"];
const metrics = [
  {
    sheetName: "p95",
    title: "Before/After p(95)",
    unit: "ms",
    before: [41.43, 75.17, 1410, 2710],
    after: [38.14, 84, 277.41, 1950],
    numberFormat: '0.00" ms"',
  },
  {
    sheetName: "p99",
    title: "Before/After p(99)",
    unit: "ms",
    before: [51.44, 253.37, 3410, 3600],
    after: [48.48, 296.4, 1110, 3450],
    numberFormat: '0.00" ms"',
  },
  {
    sheetName: "RPS",
    title: "Before/After RPS",
    unit: "req/s",
    before: [9.03, 407.98, 566.03, 798.07],
    after: [9.06, 407.46, 710.68, 1345.79],
    numberFormat: '0.00',
  },
];

for (const metric of metrics) {
  const sheet = workbook.worksheets.add(metric.sheetName);
  sheet.showGridlines = false;

  sheet.getRange("A1:N2").merge();
  sheet.getRange("A1").values = [[metric.title]];
  sheet.getRange("A1:N2").format = {
    fill: "#172033",
    font: { bold: true, color: "#FFFFFF", size: 22 },
    horizontalAlignment: "center",
    verticalAlignment: "center",
  };

  sheet.getRange("A4:C8").values = [
    ["Test", "Before", "After"],
    ...tests.map((test, index) => [
      test,
      metric.before[index],
      metric.after[index],
    ]),
  ];
  sheet.getRange("A4:C4").format = {
    fill: "#334155",
    font: { bold: true, color: "#FFFFFF" },
    horizontalAlignment: "center",
  };
  sheet.getRange("A5:A8").format = {
    fill: "#F1F5F9",
    font: { bold: true, color: "#172033" },
  };
  sheet.getRange("B5:C8").format.numberFormat = metric.numberFormat;
  sheet.getRange("A4:C8").format.borders = {
    top: { color: "#CBD5E1", style: "continuous", weight: 1 },
    bottom: { color: "#CBD5E1", style: "continuous", weight: 1 },
    left: { color: "#CBD5E1", style: "continuous", weight: 1 },
    right: { color: "#CBD5E1", style: "continuous", weight: 1 },
    insideHorizontal: { color: "#E2E8F0", style: "continuous", weight: 1 },
    insideVertical: { color: "#E2E8F0", style: "continuous", weight: 1 },
  };

  sheet.getRange("A10:C10").merge();
  sheet.getRange("A10").values = [[
    metric.unit === "ms"
      ? "Lower is better. All latency values are normalized to milliseconds."
      : "Higher is better. Throughput is requests per second.",
  ]];
  sheet.getRange("A10:C10").format = {
    fill: "#E0F2FE",
    font: { italic: true, color: "#075985", size: 10 },
    horizontalAlignment: "left",
  };

  sheet.getRange("A:A").format.columnWidth = 16;
  sheet.getRange("B:C").format.columnWidth = 14;
  sheet.getRange("1:2").format.rowHeight = 28;

  const chart = sheet.charts.add("bar", sheet.getRange("A4:C8"));
  chart.title = `${metric.title} (${metric.unit})`;
  chart.titleTextStyle.fontSize = 16;
  chart.hasLegend = true;
  chart.legend = { position: "bottom" };
  chart.xAxis = { axisType: "textAxis" };
  chart.yAxis = { numberFormatCode: metric.unit === "ms" ? '0" ms"' : "0" };
  chart.setPosition("E4", "N22");

  const [beforeSeries, afterSeries] = chart.series.items;
  if (beforeSeries) beforeSeries.fill = "#94A3B8";
  if (afterSeries) afterSeries.fill = "#2563EB";
}

await fs.mkdir(outputDir, { recursive: true });

for (const metric of metrics) {
  const preview = await workbook.render({
    sheetName: metric.sheetName,
    range: "A1:N22",
    scale: 1.5,
    format: "png",
  });
  await fs.writeFile(
    `${outputDir}/before-after-${metric.sheetName.toLowerCase()}.png`,
    new Uint8Array(await preview.arrayBuffer()),
  );
}

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(`${outputDir}/caching-before-after.xlsx`);

const inspection = await workbook.inspect({
  kind: "table",
  range: "p95!A4:C8",
  include: "values,formulas",
  tableMaxRows: 10,
  tableMaxCols: 5,
});
console.log(inspection.ndjson);

const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 50 },
  summary: "final formula error scan",
});
console.log(errors.ndjson);
