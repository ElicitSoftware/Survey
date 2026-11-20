# Security Policy

Elicit sites hold what respondents say about themselves, often about their health. If you have
found a way to read, change or disrupt what a site holds, please tell us privately first.

## Reporting a vulnerability

Report it through GitHub's private vulnerability reporting:
<https://github.com/ElicitSoftware/Survey/security/advisories/new> (the **Report a vulnerability**
button under this repository's **Security** tab). The report is visible only to you and the
maintainers.

**Please do not open a public issue, pull request or discussion for a vulnerability.**

If you are not sure which Elicit repository the problem belongs to, report it in
[ElicitSoftware/Elicit](https://github.com/ElicitSoftware/Elicit/security/advisories/new), the
platform's umbrella repository, and we will route it.

It helps to include:

- the module and the version, image tag or commit you tested;
- what you did, step by step, and what happened;
- what you think someone could do with it;
- whether the site was the bundled compose stack or an installation of your own.

Never include real respondent data in a report.

## What to expect

- We acknowledge a report within five working days.
- We tell you whether we can reproduce it and what we intend to do, and keep you informed until
  it is resolved. We do not promise a date for a fix.
- We credit you in the release note if you would like that.
- Please give us the chance to release a fix before you publish.

## Supported versions

Security fixes are made in the 3.x line and arrive in its releases. Earlier lines, 2.x included,
are not patched: the remedy for a vulnerability there is the upgrade to 3.x.

## The bundled stack

`docker-compose.yml`, the services bundled with it and the example Keycloak realm in the
umbrella repository exist for development and evaluation. They publish their ports and carry
documented passwords on purpose, and that is not a vulnerability. A real site is installed
against its own database, identity provider and network, as the installation manual's chapter
"Securing the site" describes. A weakness in how the applications themselves behave is always
in scope, whichever stack you found it on.
