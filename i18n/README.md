# Survey interface translations

The files here are the **Survey application's own interface strings** — navigation, buttons,
validation messages, notifications, page titles. They are not survey content: question text, answer
options and step and section names are translated in Author, stored in `survey.translations`, and
travel inside the `.elicit` definition file.

| File | What it is |
| --- | --- |
| `TRANSLATION_REQUEST.md` | Generated hand-off document: every English string with its context, length budget and flags, ready to give to a translator or an AI agent for one new language. |
| `translations_es_419.properties` | Latin American Spanish. |
| `translations_ar.properties` | Arabic (right to left). |

## These are what the application ships

They are packaged onto the classpath at `vaadin-i18n/` by a `<resource>` block in `pom.xml`, next
to the English bundle, so a released image renders the translation it was built and tested with.
There is no mounted directory and no local override: a deployment can neither add a language nor
patch one, and the version of a translation travels with the version of the code.

English is the exception, and it lives elsewhere on purpose: it is **authored** at
`src/main/resources/vaadin-i18n/translations.properties`, Vaadin's standard location, so the
Copilot internationalization panel keeps editing it in place. Everything here is **received** from
a translator, which is why it sits apart from `src/`.

The packaging include is `translations_*.properties`, not `translations*.properties`, so a stray
English copy in this directory could never shadow the authored one.

## What a site controls

One setting, `i18n.bundled.locales`. It both declares what the image carries — classpath resources
cannot be listed, so nothing can discover which bundles are in the jar — and lets a site offer
fewer than it carries:

```properties
i18n.bundled.locales=en,es-419   # this site offers English and Spanish; Arabic stays unreachable
```

A language left out is not offered, `?lang=` for it is refused, and survey content is not served
in it either, because content is only served in a language the interface also has.

Per-language typography is `META-INF/i18n/i18n-config.json` on the classpath, overridable per tag
by `i18n.direction.<tag>` and `i18n.font-scale.<tag>`.

## Adding a language

Languages are curated and arrive in a release. Hand `TRANSLATION_REQUEST.md` to a translator or an
AI agent, put the returned `translations_<tag>.properties` here, add the tag to
`i18n.bundled.locales`, and run the i18n tests. `TranslationBundleConsistencyTest` fails if the new
file does not carry exactly the English key set with matching placeholders.

All three applications must gain the language together — Survey, Admin and Author — or an author
could publish survey content in a language a respondent's Survey cannot render. The umbrella's
`buildDockerImages.sh` gates that.
