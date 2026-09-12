# SonarCloud — Continuous Code Quality

This guide covers how SonarCloud is wired into Snaplink and how to read the results.

## What it does

- **Every push to `feature/add_premium_ui`**: GitHub Actions (`.github/workflows/ci.yml`) builds the project
  with `mvn -B -Pci package`, aggregates JaCoCo coverage via `target/site/jacoco/jacoco.xml`, and pushes the
  results to SonarCloud.
- **Every pull request**: the same analysis runs with **PR decoration** — issues and coverage are posted
  directly on the PR diff.
- The analysis uses the config in `sonar-project.properties` (project key `vaibhavGala262_SnapLink`,
  organization `vaibhavgala262`, Java 23).

## Prerequisites (one-time, manual)

1. Sign in at [sonarcloud.io](https://sonarcloud.io) with your GitHub account.
2. The project `vaibhavGala262_SnapLink` is already created under organization `vaibhavgala262`.
3. Generate a token:
   - Project page → **Administration** → **Analysis Method** → **Generate a token**, or
   - Avatar → **My Account** → **Access Tokens** → **Generate Token**.
   - Copy it immediately — it is only shown once.
4. Add it as a GitHub repository secret:
   - GitHub → repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**
   - Name: `SONAR_TOKEN`, value: your `sqp_...` token.
   - Never commit the token to the repo.

## Running analysis locally (optional)

```
mvn clean test
mvn sonar:sonar -Dsonar.token=<your-token>
```

Or open the JaCoCo report in a browser:

```
target/site/jacoco/index.html
```

## Reading the dashboard

After the first CI run, open the project on sonarcloud.io:

| Metric | Meaning |
|--------|---------|
| **Quality Gate** | Red (fail) / Green (pass). Set your own target in **Quality Gates** if the default is too strict. |
| **Coverage** | % of lines reached by tests. `Line coverage` shown per package/file. |
| **Bugs / Vulnerabilities** | Reliability & security — open each to see the exact line and fix. |
| **Code Smells** | Maintainability issues. |
| **Duplications** | Repeated code blocks across files. |
| **Security Hotspots** | Areas worth a manual review. |

Filters: **Coverage on New Code** / **Issues on New Code** matter most for the default quality gate.

## The CI profile & coverage

- `mvn test` locally runs all **39 tests** (including `UrlShortnerApplicationTests`, which needs live Supabase).
- `mvn -Pci package` (used in CI) excludes only `UrlShortnerApplicationTests`, so the pipeline needs no
  database — **38 tests** run and coverage is aggregated from those.

## Quality gate upgrade path

The pipeline currently **reports** coverage but does not fail the build on a low threshold. When you're happy
with the baseline, raise the bar:

1. In `pom.xml` inside the `jacoco-maven-plugin` `report` execution, add a `check` goal with `<minimum>`.
2. Or set `sonar.qualitygate.wait=true` + `sonar.qualitygate.timeout` in the workflow.

## Security notes

- `.env`, `*.mmdb`, and `target/` are git-ignored (`.gitignore`). Never commit secrets.
- Old Render database connection strings may exist in early git history — rotate/delete anything that was
  ever committed. Supabase/business credentials should be regenerated if they were pasted into chat or logs.
- Tokens used in this setup: `SONAR_TOKEN` (GitHub secret). If a token is ever exposed, revoke it in
  SonarCloud **My Account → Access Tokens** and re-create the GitHub secret.

## Troubleshooting

- **`Unknown quality gate status` / no report**: confirm `SONAR_TOKEN` exists and the first CI run finished.
- **`Project not found`**: `sonar.organization` / `sonar.projectKey` in `sonar-project.properties` must match
  the org + project created on SonarCloud (case-sensitive).
- **Coverage empty**: check `target/site/jacoco/jacoco.xml` was produced during `mvn package` (JaCoCo binds
  to the `test` phase).
- **CI failing on context test**: the `ci` Maven profile excludes `UrlShortnerApplicationTests` — profile is
  only active when you pass `-Pci`.