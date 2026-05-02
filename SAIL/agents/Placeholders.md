# Agent Prompt Values

Fill in the values below before using any agent prompt. Copy an agent's AI Prompt, then find-and-replace each `{{PLACEHOLDER}}` with the corresponding value.

---

## PROJECT_NAME
*Short project identifier.*

```
Elicit Survey
```

---

## PROJECT_GOAL
*One sentence: what the project does and for whom.*

```
Elicit Survey runs in <a href=https://github.com/ElicitSoftware/>Elicit Software</a>, a modular survey system for building and running complex surveys. It is the module that presents questions and records the answers of respondents.
```

---

## SPEC_FILES
*Paths to the spec and plan files the agent should read. One path per line.*

```
specs/
https://github.com/ElicitSoftware/Admin
https://github.com/ElicitSoftware/Author
https://github.com/ElicitSoftware/FHHS
/.vscode/mcp.json
```

---

## AGENTS_DIRECTORY
*Root path where findings will be saved. Include trailing slash.*

```
SAIL/agents/
```

---

## TECH_STACK
*Required by agents 01 and 02 only. Describe the technology stack in plain text.*

```
Java 21, Quarkus 3.x, Vaadin 25, PostgreSQL, OIDC
```
