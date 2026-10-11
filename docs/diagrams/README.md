# Diagram sources and PNG exports

Every diagram in this directory has a Draw.io source with the same base name.
The `.drawio` file is canonical. The `.png` file is the rendered copy embedded
by the Markdown documentation.

Edit the Draw.io source first, export it with the Draw.io desktop application
and verify the result visually before committing both files. Do not redraw a
diagram in a raster editor and do not resize an exported PNG afterwards.

## Required PNG dimensions

| Diagram | Width | Height |
|---|---:|---:|
| `bot-architecture.png` | 2873 | 1789 |
| `bot-decision-flow.png` | 2852 | 3070 |
| `coronapoker-frontend-uml.png` | 2314 | 1335 |
| `coronapoker-module-map.png` | 1714 | 1268 |
| `crypto-architecture.png` | 3014 | 2602 |
| `crypto-hand-sequence.png` | 3341 | 19444 |
| `dos-resistance.png` | 3051 | 1665 |
| `testing-certification-flow.png` | 2976 | 1954 |

The product architecture sources can be regenerated before opening them in
Draw.io:

```powershell
node .\tools\diagrams\gen_product_arch.mjs
```

The crypto and bot generators under `tools/diagrams` follow the same rule.
Export at the dimensions in the table and compare the final PNG dimensions
before committing. This preserves small labels and connector detail in the
rendered documentation.
