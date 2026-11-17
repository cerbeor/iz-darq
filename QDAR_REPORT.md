# QDAR — Data At Rest Quality Analysis
## Summary Report

**Project:** QDAR (`gov.nist.healthcare.iz:darq`)
**Branch:** `restyling` · **API v:** 3.1.4.5 · **WebApp v:** 4.1.8.2
**Date:** 2026-11-17
**Author:** Clement Hennequin clement.hennequin@nist.gov

---

## 1. Overview

**What QDAR is.** QDAR (Data At Rest Quality Analysis) is a multi-module tool that samples, validates, and analyzes the quality of immunization data held in an Immunization Information System (IIS). It ingests patient/immunization records, runs them through validation (MQE) and quality checks, and produces analysis reports that can be reviewed, published, and compared.

**Audience / users.** IIS Administrators, AIRA IIS Data testers 

**Key value proposition.** QDAR turns raw Immunization Information System (IIS) exports into validated, deduplicated, analysis-ready data files and produces reviewable, publishable, and comparable quality reports — giving IIS administrators and public-health analysts a single, auditable view of data quality. 
Analysis runs locally on an IIS's own private data so sensitive records never leave the organization's environment.

---

## 2. High-Level Architecture

```
[ Records / IIS export ] ──► [ CLI: extract process ] ──► [ Local Reports (Bad Zip Codes, Mismerged records, Lot Numbers,...)]
                                         │
                                         ▼
                           [ ADF file (encrypted data file) ]
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

**Deployment model**: webapp is deployed as a Java web app with `qdar.war` , CLI is a Jar file downloadable from the WebApp.

**Core logic dependencies**:
- **_MQE Validator_** for detection of anomalies on the Vaccination histories
- **_LonestarForecaster_** & **_StepIntoCDSi_** Vaccination forecasters MQE validates against
- **_Mismo_**  for duplicate records evaluations

**Authentication**: webapp is password secured using JWTs and JKS.

**Encryption** ADF files are encrypted by CLI in order to be only readable by a webapp having the same set of keys

**Database**: Webapp uses MongoDB for the database and JKS for encryption.

---

## 3. Components

### 3.1 CLI — Extract Processing (`darq-cli-app` / `darq-extract-process`)

**Purpose.** Command-line tool that takes raw IIS exports, parses them, validates records, deduplicates patients, and produces an encrypted **ADF** (Analysis Data File) consumed by the WebApp.

**Responsibilities.**
- Parse input formats (HL7, CSV, …) via `file-parser`
- Validate records with MQE / code sets (AIRA `Compiled.xml`)
- Patient matching / deduplication (`darq-patient-matching`)
- Encrypt & package output ADF (`crypto-helper`, `darq-adf-utils`)
- Digest/summary and Local reports generation (`darq-digest-service`)

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

**CLI / extraction**
- Detection and corrections of MVX including lowercase char
- Local Reports generation
  - _Mismerged Records_ Records suspected of being mismerged through certain detections
  - _Bad Zip Codes_ 
  - _Bad Phone Number_
  - _Lowercase MVX_ Mvx codes that had ot be corrected by the CLI
  - _Vaccine Code_ statistics on Vaccine codes present in Records
  - _Reporting Groups_ statistics on the Reporting Groups defined in Records
- Upgrades of MQE
  - Detections upgraded and expanded
  - Detections can now be characterized as _Deprecated_ _Experimental_ with _MQE Detection lifecycle support_
- Codeset were upgraded to the latest versions published by AIRA.

**WebApp / backend**
- Analysis Report Comparison Endpoints and CSV export
- ReportTemplate cloning endpoint now allows configuration override
- Version bumps & build fixes — API/WebApp/CLI version updates and POM fixes.

**WebApp / frontend**
- Users now allowed to select a compatible configuration when cloning a Report Template
- New disclaimers and headers were added for the transition to prometheus
- ADF summary display ergonomics improved with Bold fonts for statistics above 0%
- Form bug fixed for ADF names
- Filtering of detections possible on _MQE Detection lifecycle support_
- ADF summary comparison and csv export
- Analysis & published report comparison form & display

**Quality / testing**

- Basic Unit testing of API services functionalities
- Report Comparison full endpoint test
- Basic UI angular tests

**Build / release**
- Docker build (`docker-build.sh`), manual Maven + Angular builds, versioning via `qdar-versions.sh`, external dependency management via `dependencies.json`/`dependencies.sh`, code-set loading via `load-codebase.sh`.

---

## 5. Remaining Work / Roadmap

- Upgrade of the MQE Validator to validate the data against the StepIntoCDSi project's rules
- Technological Upgrade of project and dependencies from java 8 to the latest stable versions
- Upgrade of the CI, which is currently very consistent at the price of time efficiency
- Creation of more complete UI tests with framework similar to _Playwright_
- Expanding functionalities of import/export on all models to prepare global export

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