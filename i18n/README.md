# Survey interface translations

The Survey image carries **English only**. Every other language of the application's own interface
strings (navigation, buttons, validation messages, notifications, page titles) is supplied by
the deployment from a mounted directory. They are not survey content: question text, answer
options and step and section names are translated in Author, stored in `survey.translations`, and
travel inside the `.elicit` definition file.

| Where | What it is |
| --- | --- |
| `src/main/resources/vaadin-i18n/translations.properties` | English, authored here in Vaadin's standard location so the Copilot internationalization panel keeps editing it in place. It is the fallback for every key in every language. |
| `i18n/TRANSLATION_REQUEST.md` | Generated hand-off document: every English string with its context, length budget and flags, ready to give to a translator or an AI agent for one new language. |
| umbrella `elicit-i18n/survey/translations_<tag>.properties` | The curated translations: Latin American Spanish (`es_419`) and Arabic (`ar`). |

## How a site gets a language

The application reads `translations_<tag>.properties` from `<i18n.file.system.path>/survey/`
(`/i18n/survey/` by default). In docker compose, the umbrella's `elicit-i18n/` is mounted read-only
there. Each file there:

- adds its language to the selector;
- overlays English key by key, so it may hold only the keys it changes, and a missing key falls back
  to English.

With nothing mounted the site offers English only and shows no language selector.

To offer fewer languages than are mounted, list them:

```properties
i18n.offered.locales=en,es-419   # Spanish offered; a mounted Arabic file is ignored
```

A language left out is not offered, `?lang=` for it is refused, and survey content is not served in
it either, because content is only served in a language the interface also has.

Per-language typography is layered:

1. `META-INF/i18n/i18n-config.json` on the classpath.
2. `i18n-config.json` at the mount root.
3. The `i18n.direction.<tag>` and `i18n.font-scale.<tag>` properties.

Each layer overrides the one before.

## Adding a language

Hand `TRANSLATION_REQUEST.md` to a translator or an AI agent. Put the returned
`translations_<tag>.properties` in the umbrella's `elicit-i18n/survey/`, and run the i18n tests.
`TranslationBundleConsistencyTest` reads `../elicit-i18n` and fails if a curated file does not carry
exactly the English key set with matching placeholders.

Survey and Admin must gain the language together, or the two applications would offer different
languages. The umbrella's `checkLanguages.sh` checks that before `buildDockerImages.sh` builds
anything.
