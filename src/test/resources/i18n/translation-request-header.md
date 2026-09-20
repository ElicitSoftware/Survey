# Translation request: Elicit Survey

This document is generated from the application's English text file and is meant to be handed,
as is, to a translator or to an AI translation agent. It contains everything needed to produce a
complete language file for one target language. The application itself ships only the English
file; a finished translation is placed in the deployment's translations directory
(`elicit-i18n/survey/` in the Elicit umbrella repository, mounted at `/opt/i18n`), never inside
the application.

## About the application

Elicit Survey is the respondent-facing part of the Elicit survey platform. Respondents (patients,
study participants, members of the public) receive an invitation link with an access code, log in,
answer a questionnaire section by section, review their answers, submit, and may download a PDF
summary. The texts below are the application's own words: buttons, labels, page titles, error
messages and short instructions. The survey questions themselves are not included; they are
written by the survey author in their own language.

**Audience and tone:** adults with no technical background, often in a healthcare setting. Use
plain, polite, gender-neutral language at roughly an 8th-grade reading level. Prefer the formal
register where the language distinguishes one (for example *usted* in Spanish).

## Glossary and words to keep

| Term | Meaning | Rule |
|------|---------|------|
| Elicit | Product name | Never translate or transliterate |
| access code | The credential a respondent types to open their survey | Translate consistently; never call it a "token" or "password" |
| survey | The questionnaire the respondent completes | Translate consistently |
| section | One page of questions | Translate consistently |
| review | The page where answers are checked before submitting | The button label and the page must use the same word |
| PDF, HTTP, PREMM5 | Technical names | Keep as written |

## Rules for the translation

1. Keep every placeholder such as `{0}`, `{1}` exactly as written; move it inside the sentence
   where the language needs it, but never remove, rename or reorder it with another placeholder.
2. Where a row is flagged **params**, the value is processed by Java MessageFormat: any apostrophe
   in your translation must be doubled (`l''accès`). Rows without the flag may use a single
   apostrophe normally.
3. Where a row is flagged **html**, keep the HTML tags (`<br/>`, `<h5>`, `<strong>`, `<em>`, the
   `<div style=...>` wrapper) and translate only the text between them.
4. Respect the *Max length* column where given; these strings sit in buttons and menus.
5. Do not translate the keys (the first column). Do not add, remove or reorder keys.
6. For right-to-left languages, write the text naturally; the application mirrors the layout.
7. Return exactly one file named `translations_<tag>.properties` (for example
   `translations_es_419.properties`, `translations_ar.properties`), UTF-8 encoded, with the same
   keys in the same order as the English file at the end of this document, one `key=translation`
   per line, and a first line `# Reviewed by <name or agent>, <date>`.

## Brand strings

Deployments mount their own brand. The organization name and description shown by the
application come from the brand's own files and are translated there, not in this file. To
translate them, add a `localized` block keyed by language tag:

```json
{
  "name": "Healthcare Test Brand",
  "organization": "Healthcare Test Organization",
  "localized": {
    "es-419": { "name": "Marca de prueba de salud", "organization": "Organización de prueba de salud" },
    "ar": { "name": "علامة الرعاية الصحية التجريبية", "organization": "مؤسسة الرعاية الصحية التجريبية" }
  }
}
```

The same block inside `brand-info.json` may carry a translated `description`. The `name` is also
used to derive a technical identifier and the base value must stay as it is.
