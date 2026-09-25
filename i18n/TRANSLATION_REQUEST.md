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

## Strings to translate

Every row is one key in `translations.properties`. Return a file `translations_<tag>.properties` with exactly these keys in this order, one `key=translation` per line, UTF-8, no additions and no omissions.

| Key | English | Where it appears | Max length | Notes |
|-----|---------|------------------|------------|-------|
| `sideNav.login` | Login | Navigation drawer · menu item | 20 |  |
| `sideNav.about` | About | Navigation drawer · menu item | 20 |  |
| `sideNav.logout` | Logout | Navigation drawer · menu item | 20 |  |
| `common.language` | Language | Header · language selector accessible name | 20 |  |
| `common.logoAlt` | {0} logo | Header · logo image alternative text | 40 | params: {0} = organization name |
| `common.appTitle` | {0} {1} | Header · application title | 40 | params: {0} = organization name, {1} = application type; identical in most languages (reorder only) |
| `common.appTitle.default` | Elicit {0} | Header · application title when no brand is mounted | 40 | params: {0} = application type; identical in most languages, Elicit stays |
| `common.appType.survey` | Survey | Header · the word appended to the organization name | 20 |  |
| `common.close` | Close | Modal question · button that dismisses the dialog | 15 |  |
| `common.error.sessionExpired` | Session expired. Please log in again. | Any page · notification | 80 |  |
| `common.error.navigationUnavailable` | Navigation data not available. Please refresh the page. | Any page · notification | 80 |  |
| `mainView.pageTitle` | Login | Login page · browser tab title | 30 |  |
| `mainView.txtAccessCode` | Enter your access code | Login page · access code field label | 40 |  |
| `mainView.txtAccessCode.tooltip` | Access code is case-sensitive | Login page · access code field tooltip | 60 |  |
| `mainView.accessCode.empty` | Access code cannot be empty | Login page · field error | 60 |  |
| `mainView.accessCode.tooShort` | Access code must be at least 5 characters | Login page · field error | 60 |  |
| `mainView.accessCode.tooLong` | Access code must be at most 12 characters | Login page · field error | 60 |  |
| `mainView.btnLogin` | Login | Login page · primary button | 20 |  |
| `mainView.error.invalidAccessCode` | Invalid access code. Please check your access code and try again. | Login page · notification | 100 |  |
| `mainView.error.fixErrors` | Please correct the errors before logging in. | Login page · notification | 80 |  |
| `mainView.surveys.isEmpty` | There are no surveys to login | Login page · paragraph shown when no survey exists | 60 |  |
| `mainView.comboBox` | Surveys | Login page · survey selector label | 20 |  |
| `mainView.autoRegister.instructions` | To test, enter any value between 5 and 12 characters.<br/>Access codes are case sensitive. | Login page (test deployments only) · paragraph | 160 | html |
| `mainView.autoRegister.warning` | <h5>Warning: the property accessCode.autoRegister is set to true.<br/>This is for testing only and should be removed for production.</h5> | Login page (test deployments only) · warning banner | 200 | html |
| `sectionView.btnPrevious` | Previous | Survey section page · button | 20 |  |
| `sectionView.btnNext` | Next | Survey section page · primary button | 20 |  |
| `sectionView.btnReview` | Review | Survey section page · primary button on the last section | 20 |  |
| `sectionView.savingChanges` | Saving changes... | Survey section page · short notification | 40 |  |
| `sectionView.error.loadSurvey` | Error loading survey. Please try again. | Survey section page · notification | 80 |  |
| `sectionView.error.fixValidation` | Please fix the validation errors. | Survey section page · notification | 60 |  |
| `sectionView.error.saveAnswer` | Error saving answer. Please try again. | Survey section page · notification | 80 |  |
| `sectionView.error.noNext` | No next section available. | Survey section page · notification | 60 |  |
| `sectionView.error.loadNext` | Error loading next section data. | Survey section page · notification | 60 |  |
| `sectionView.error.navigateNext` | Error navigating to next section. Please try again. | Survey section page · notification | 80 |  |
| `sectionView.error.noPrevious` | No previous section available. | Survey section page · notification | 60 |  |
| `sectionView.error.loadPrevious` | Error loading previous section data. | Survey section page · notification | 60 |  |
| `sectionView.error.navigatePrevious` | Error navigating to previous section. Please try again. | Survey section page · notification | 80 |  |
| `reviewView.btnPrevious` | Previous | Review page · button | 20 |  |
| `reviewView.btnFinish` | Finish | Review page · primary button that submits the survey | 20 |  |
| `reviewView.surveyLabel` | Survey: {0} | Review page · heading | 60 | params: {0} = survey name |
| `reviewView.thanks` | Thank you for taking this survey. | Review page · paragraph | 80 |  |
| `reviewView.instructions` | Please review your answers for each section below. If you need to change them, press "Edit" to go to that section. When you are done editing, press "Review" to return to this page. | Review page · paragraph; "Edit" and "Review" name buttons on this page | 300 |  |
| `reviewView.note` | Note: After submission you will not be able to edit your answers. | Review page · paragraph | 100 |  |
| `reportView.btnGeneratePdf` | Generate PDF | Report page · button | 25 |  |
| `reportView.btnNext` | Next | Report page · button leading to the post-survey site | 20 |  |
| `reportView.error.pdf` | Failed to generate PDF: {0} | Report page · notification | 100 | params: {0} = technical error text |
| `reportView.error.service` | Service error: {0} | Report page · error text | 100 | params: {0} = technical error text |
| `reportView.error.forbidden` | Access forbidden - License validation may have failed. Please check your license configuration. | Report page · error text | 160 |  |
| `reportView.error.serviceHttp` | Service error (HTTP {0}): {1} | Report page · error text | 100 | params: {0} = HTTP status number, {1} = technical error text |
| `reportView.error.license` | License validation failed - {0} | Report page · error text | 100 | params: {0} = error text |
| `reportView.error.title` | Error - {0} | Report page · error card title | 60 | params: {0} = report name; may be identical |
| `reportView.error.html` | <div style="color: red; padding: 20px; border: 1px solid red; background-color: #ffe6e6;"><h3>Report Generation Error</h3><p><strong>Service:</strong> {0}</p><p><strong>Error:</strong> {1}</p>{2}</div> | Report page · error card body | 400 | html params: {0} = report name, {1} = error text, {2} = optional hint paragraph; keep the style attribute |
| `reportView.error.licenseHint` | <p><em>If this is a license error, please ensure your PREMM5 license is valid and properly configured.</em></p> | Report page · error card hint | 200 | html |
| `aboutView.version` | Version: {0} | About page · build information | 40 | params: {0} = version number |
| `aboutView.built` | Built: {0} | About page · build information | 40 | params: {0} = build timestamp |
| `versionView.title` | Version Information | Version page (diagnostics) · heading | 40 |  |
| `versionView.container` | Container Information | Version page (diagnostics) · heading | 40 |  |
| `versionView.containerTime` | Container Current Time: | Version page (diagnostics) · label | 40 |  |
| `versionView.imageCreated` | Image Creation Date: | Version page (diagnostics) · label | 40 |  |
| `versionView.imageCreatedUnavailable` | Image creation date not available. | Version page (diagnostics) · paragraph | 60 |  |
| `validation.required` | This question requires an answer. | Any question · field error when a required question is empty | 60 |  |
| `validation.length` | Enter between {0} and {1} characters. | Any text question · field error | 60 | params: {0} = minimum, {1} = maximum length |
| `validation.range` | Enter a value between {0} and {1}. | Any numeric question · field error | 60 | params: {0} = minimum, {1} = maximum value |
| `validation.email` | Enter a valid email address. | Email question · field error | 60 |  |
| `pdf.error.title` | Report Generation Error | PDF report · error block title | 60 |  |
| `pdf.error.content` | Failed to generate report content. | PDF report · error block text | 80 |  |
| `pdf.header` | {0} - Page {1} of {2} | PDF report · page header | 80 | params: {0} = survey title, {1} = page number, {2} = page count |
| `pdf.page` | Page {0} of {1} | PDF report · page footer | 30 | params: {0} = page number, {1} = page count |
| `pdf.download.missingKey` | Missing key parameter | PDF download link · plain-text error | 60 |  |
| `pdf.download.expired` | PDF not found or expired | PDF download link · plain-text error | 60 |  |

## English source file

```properties
sideNav.login=Login
sideNav.about=About
sideNav.logout=Logout
common.language=Language
common.logoAlt={0} logo
common.appTitle={0} {1}
common.appTitle.default=Elicit {0}
common.appType.survey=Survey
common.close=Close
common.error.sessionExpired=Session expired. Please log in again.
common.error.navigationUnavailable=Navigation data not available. Please refresh the page.
mainView.pageTitle=Login
mainView.txtAccessCode=Enter your access code
mainView.txtAccessCode.tooltip=Access code is case-sensitive
mainView.accessCode.empty=Access code cannot be empty
mainView.accessCode.tooShort=Access code must be at least 5 characters
mainView.accessCode.tooLong=Access code must be at most 12 characters
mainView.btnLogin=Login
mainView.error.invalidAccessCode=Invalid access code. Please check your access code and try again.
mainView.error.fixErrors=Please correct the errors before logging in.
mainView.surveys.isEmpty=There are no surveys to login
mainView.comboBox=Surveys
mainView.autoRegister.instructions=To test, enter any value between 5 and 12 characters.<br/>Access codes are case sensitive.
mainView.autoRegister.warning=<h5>Warning: the property accessCode.autoRegister is set to true.<br/>This is for testing only and should be removed for production.</h5>
sectionView.btnPrevious=Previous
sectionView.btnNext=Next
sectionView.btnReview=Review
sectionView.savingChanges=Saving changes...
sectionView.error.loadSurvey=Error loading survey. Please try again.
sectionView.error.fixValidation=Please fix the validation errors.
sectionView.error.saveAnswer=Error saving answer. Please try again.
sectionView.error.noNext=No next section available.
sectionView.error.loadNext=Error loading next section data.
sectionView.error.navigateNext=Error navigating to next section. Please try again.
sectionView.error.noPrevious=No previous section available.
sectionView.error.loadPrevious=Error loading previous section data.
sectionView.error.navigatePrevious=Error navigating to previous section. Please try again.
reviewView.btnPrevious=Previous
reviewView.btnFinish=Finish
reviewView.surveyLabel=Survey: {0}
reviewView.thanks=Thank you for taking this survey.
reviewView.instructions=Please review your answers for each section below. If you need to change them, press "Edit" to go to that section. When you are done editing, press "Review" to return to this page.
reviewView.note=Note: After submission you will not be able to edit your answers.
reportView.btnGeneratePdf=Generate PDF
reportView.btnNext=Next
reportView.error.pdf=Failed to generate PDF: {0}
reportView.error.service=Service error: {0}
reportView.error.forbidden=Access forbidden - License validation may have failed. Please check your license configuration.
reportView.error.serviceHttp=Service error (HTTP {0}): {1}
reportView.error.license=License validation failed - {0}
reportView.error.title=Error - {0}
reportView.error.html=<div style="color: red; padding: 20px; border: 1px solid red; background-color: #ffe6e6;"><h3>Report Generation Error</h3><p><strong>Service:</strong> {0}</p><p><strong>Error:</strong> {1}</p>{2}</div>
reportView.error.licenseHint=<p><em>If this is a license error, please ensure your PREMM5 license is valid and properly configured.</em></p>
aboutView.version=Version: {0}
aboutView.built=Built: {0}
versionView.title=Version Information
versionView.container=Container Information
versionView.containerTime=Container Current Time:
versionView.imageCreated=Image Creation Date:
versionView.imageCreatedUnavailable=Image creation date not available.
validation.required=This question requires an answer.
validation.length=Enter between {0} and {1} characters.
validation.range=Enter a value between {0} and {1}.
validation.email=Enter a valid email address.
pdf.error.title=Report Generation Error
pdf.error.content=Failed to generate report content.
pdf.header={0} - Page {1} of {2}
pdf.page=Page {0} of {1}
pdf.download.missingKey=Missing key parameter
pdf.download.expired=PDF not found or expired
```
