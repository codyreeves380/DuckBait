# PTS preset document templates

Add DOCX templates to this folder (or subfolders), then register each one in `catalog.json`.

## Catalog format

```json
{
  "states": [
    {
      "name": "Georgia",
      "counties": [
        {
          "name": "Example County",
          "template": "pts_templates/georgia/example_county.docx"
        }
      ]
    }
  ]
}
```

- `name` under `states` is the State dropdown label.
- Each county `name` is also the document name shown in the County / Document dropdown.
- `template` is the path to the DOCX file relative to the app's `assets` directory.
- Keep the exact placeholder tags used by the form in each DOCX, such as `[[DEFENDANT]]`, `[[CAUSENO]]`, `[[DATE]]`, and `[[REQU]]`.

The app currently starts with an empty catalog. Add real state/county entries and matching DOCX files here when they are ready.
