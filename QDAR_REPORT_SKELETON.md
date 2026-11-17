# QDAR — Data At Rest Quality Analysis
## Summary Report

**Project:** QDAR (`gov.nist.healthcare.iz:darq`)
**Branch:** `restyling` · **API v:** 3.1.4.5 · **WebApp v:** 4.1.8.2
**Date:** 2026-11-17
**Author:** _[fill]_

---

## 1. Overview

**What QDAR is.** QDAR (Data At Rest Quality Analysis) is a multi-module tool that samples, validates, and analyzes the quality of immunization data held in an Immunization Information System (IIS). It ingests patient/immunization records, runs them through validation (MQE) and quality checks, and produces analysis reports that can be reviewed, published, and compared.

**Audience / users.** IIS Administrators, AIRA IIS Data testers 

**Key value proposition.** _[fill: one or two sentences]_

---

## 2. High-Level Architecture

```
[ Records / IIS export ] ──► [ CLI: extract process ] ──► [ ADF (encrypted data file) ]
                                                              │
                                                              ▼
                                              [ WebApp: upload / merge / analyze / report ]
                                                              │
                                                              ▼
                                              [ Reports (published / compared) ]
```

- **Core modules:** `darq-extract-process`, `darq-analyzer`, `darq-patient-matching`, `darq-common`
- **Web application:** `darq-webapp` (Angular 9 frontend + Spring Boot backend)
- **Clients:** `darq-aart-client`, `darq-cli-app`
- **Utilities:** `file-parser`, `fake-record-generator`, `crypto-helper`

_[fill: deployment model, auth, storage (ADF store), external deps]_

---

## 3. Components

### 3.1 CLI — Extract Processing (`darq-cli-app` / `darq-extract-process`)

**Purpose.** Command-line tool that takes raw IIS exports, parses them, validates records, deduplicates patients, and produces an encrypted **ADF** (Analysis Data File) consumed by the WebApp.

**Responsibilities.**
- Parse input formats (HL7, CSV, …) via `file-parser`
- Validate records with MQE / code sets (AIRA `Compiled.xml`)
- Patient matching / deduplication (`darq-patient-matching`)
- Encrypt & package output ADF (`crypto-helper`, `darq-adf-utils`)
- Digest/summary generation (`darq-digest-service`)

**Status.** Implemented and tested

**Open items.** upgrade of the MQE-validator dependency including new StepIntoCDSI validation logic

### 3.2 Web Application (`darq-webapp`)

**Backend (Spring Boot).** REST API organized by controllers: ADF, Analysis, Report, Configuration, Facility, Query, Template, Admin, Users, AART client.

**Frontend (Angular 9).** Analysis client (`qdar-analysis-client`) for upload, job monitoring, report viewing, comparison, templates.

**Key capabilities exposed.**
- ADF upload / management / merge jobs
- Analysis jobs (query by facility)
- Reports: create, publish, delete, compare, export (CSV)
- Report templates & configuration
- Facility & user management, AART integration

**Status.** Implemented and tested

**Open items.** None

### 3.3 Core Functionalities

| Functionality | Module(s) | Status  | Notes                                            |
|---|---|---------|--------------------------------------------------|
| Data extraction & parsing | `file-parser`, `darq-extract-process` | Done    |                                                  |
| Validation (MQE) | external `mqe-*` | pending | In process of being upgraded                     |
| Patient matching / dedup | `darq-patient-matching` | Done    |                                                  |
| ADF encryption & storage | `crypto-helper`, `darq-adf-utils`, `ADFStore` | Done    |                                                  |
| Analysis engine | `darq-analyzer` | Done    |                                                  |
| Report generation & publishing | `darq-webapp` (ReportController) | Done    |                                                  |
| Report comparison (incl. CSV export) | `ReportController` | Done    | Latest functionality to be implemented           |
| ADF merge | `ADFMergeJobManagementService` | Done    | Was recently upgraded to be asynchronous process |
| Configuration import/export | `ConfigurationController` | Done    |                                          |
| AART client integration | `darq-aart-client` | Done    |                                          |

---

## 4. What Has Been Implemented So Far

_Group by area; replace bullets with concrete items._

**CLI / extraction**
- _[fill]_

**WebApp / backend**
- _[fill: report comparison endpoints, published-report comparison, CSV output, …]_

**WebApp / frontend**
- _[fill: comparison table filtering/ordering, ADF stats comparison dialog, report template sidebar, …]_

**Quality / testing**
- _[fill: ReportComparisonEndpointTest, basic tests]_

**Build / release**
- Docker build (`docker-build.sh`), manual Maven + Angular builds, versioning via `qdar-versions.sh`, external dependency management via `dependencies.json`/`dependencies.sh`, code-set loading via `load-codebase.sh`.

---

## 5. Remaining Work / Roadmap

- _[fill: pending features]_
- _[fill: known issues / bugs]_
- _[fill: testing gaps]_
- _[fill: documentation]_
- Upgrade of the MQE Validator to validate the data against the StepIntoCDSi project's rules
- Technological Upgrade of project and dependencies from java 8 to the latest stable versions


---

## 6. Build & Run (Quick Reference)

```bash
# Full docker build
sh ./docker-build.sh ./dist

# Backend
mvn clean install

# Frontend
cd darq-webapp/qdar-analysis-client
npm install && npm run build-prod

# Code sets
sh ./load-codebase.sh
```

**Prerequisites:** JDK 8, Node v14.20.0, Maven 3.6+, Docker.

---

## 7. Notes / Decisions

- _[fill: architectural decisions, constraints, NIST/AIRA disclaimer vocabulary, etc.]_

---

_Appendix: module list, version history, contact._