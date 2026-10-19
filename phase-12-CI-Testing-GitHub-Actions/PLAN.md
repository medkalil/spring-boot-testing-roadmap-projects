# Phase 12 — CI Testing with GitHub Actions, Maven, Docker, and Testcontainers

This is the final phase of your Spring Boot testing roadmap. The goal is to move from knowing how to write tests to building a workflow that automatically verifies software whenever you change it.

You've already learned JUnit, Mockito, MockMvc, JPA tests, Spring Security tests, integration testing with PostgreSQL/Testcontainers, and browser-based E2E testing. Now we'll connect those skills into a CI pipeline.

The important distinction: CI is not another testing framework. It is the automation that builds your project, runs the appropriate tests, collects results, and prevents broken changes from being merged unnoticed.

## 1. Understand Continuous Integration

### 1.1 Without CI

```
Developer changes code

Developer runs tests manually

Developer pushes code

Broken code may reach the shared branch
```

The problem is that testing depends on developers remembering to do it. A developer might forget to run tests, use a different Java version, have a locally installed database that hides a configuration problem, or push a change that breaks another feature.

### 1.2 With CI

1. Developer pushes a commit or opens a pull request

2. GitHub Actions starts a workflow

3. Checkout code and configure Java/Maven

4. Compile and run unit tests

5. Run integration tests and Testcontainers

6. Publish test reports and mark the check

A CI pipeline gives you repeatable checks on a clean runner. It can discover problems before changes are merged.

For your project, the central flow will be:

```
Git push / Pull request
          |
          v
    GitHub Actions
          |
          v
     Checkout code
          |
          v
    Set up Java + Maven
          |
          v
     Compile + tests
          |
          v
  Testcontainers starts
     PostgreSQL
          |
          v
   Integration tests
          |
          v
  Publish test results
          |
          v
      PASS / FAIL
```

One nuance: unit tests and integration tests do not have to run in separate workflow jobs. We'll start with one job to make the pipeline easier to understand. Later, you can split jobs to run independent checks in parallel.

## 2. What are GitHub Actions, workflows, jobs, and steps?

GitHub Actions is GitHub's automation system. It executes commands in response to events such as pushes, pull requests, scheduled runs, or manual triggers.

A workflow is defined in a YAML file, usually under:

```
.github/
└── workflows/
    └── ci.yml
```

There are four important concepts.

| Concept | Meaning |
|---|---|
| Workflow | The entire automation definition, such as ci.yml. |
| Trigger (on) | Defines when it runs: for example, a push or pull request. |
| Job | A group of steps executed on a runner. Separate jobs normally run on separate machines, unless dependencies or configuration change their scheduling. |
| Step | One operation within a job: check out code, install Java, execute Maven, or upload reports. |

Example:

```yaml
name: CI

on:
  push:
  pull_request:

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout source
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Run tests
        run: ./mvnw test
```

This is a basic example, not the complete pipeline we'll build. It assumes your repository contains a Maven wrapper and is configured for Java 17. We'll improve it with dependency caching, Testcontainers, reports, and pull request checks.

### What is a runner?

`runs-on: ubuntu-latest` tells GitHub to execute the job on a hosted Ubuntu runner.

Think of it as a temporary Linux machine with the tools GitHub provides for that runner image. Your workflow checks out the repository and executes commands there.

Do not assume your local environment exists on the runner. Files installed in /opt/lampp, local environment variables, local databases, and uncommitted files on your laptop are not automatically available in CI.

That is one of the main reasons CI is valuable: it exposes hidden dependencies on your development machine.

## 3. Understand the Maven test lifecycle

Before writing a proper CI pipeline, you need to understand what Maven actually does when you execute a command.

### 3.1 mvn test versus mvn verify

These commands are not equivalent.

Maven executes lifecycle phases in order. When you request a later phase, Maven executes the earlier phases in that lifecycle first.

| Phase | Meaning |
|---|---|
| validate | Check project configuration |
| compile | Compile main source code |
| test-compile | Compile test source code |
| test | Run tests handled by Maven Surefire |
| package | Create the JAR or WAR |
| verify | Run configured verification checks, including integration tests handled by Failsafe |

The important commands:

| Command | Typical purpose |
|---|---|
| `./mvnw compile` | Compile application code |
| `./mvnw test` | Run tests configured for the test phase |
| `./mvnw package` | Build the artifact and run preceding phases |
| `./mvnw verify` | Run the lifecycle through verification, including configured integration tests |

For a serious Spring Boot project, I recommend making `./mvnw verify` your main CI command, provided you've configured your integration tests correctly.

### 3.2 Surefire versus Failsafe

These are Maven plugins, not separate testing frameworks.

- **Maven Surefire** runs tests in the `test` phase, commonly your unit tests.
- **Maven Failsafe** runs integration tests in the `integration-test` and `verify` phases.

Why have two plugins?

Because you may want a fast unit-test phase and a separate integration-test phase that starts PostgreSQL through Testcontainers.

A conventional naming scheme is:

```
src/test/java/
├── com/example/
│   ├── service/
│   │   └── PriceCalculatorTest.java
│   ├── repository/
│   │   └── ProductRepositoryTest.java
│   └── integration/
│       └── OrderWorkflowIT.java
```

The `*Test.java` and `*IT.java` suffixes help separate the test categories.

However, naming alone isn't enough: you need Maven plugin configuration that tells Surefire and Failsafe which tests to run.

### 3.3 Configure Maven to run both test types

This is a representative configuration for a project using Spring Boot and Java 17. Add these plugins under the existing `build/plugins` section of your `pom.xml`; retain your existing Spring Boot parent and dependencies.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.4</version>
    <configuration>
        <excludes>
            <exclude>**/*IT.java</exclude>
        </excludes>
    </configuration>
</plugin>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>3.5.4</version>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

This is an illustrative plugin configuration. If your project uses custom naming patterns, profiles, or JUnit tags, adapt the includes/excludes accordingly. Verify that Surefire and Failsafe do not run the same tests twice.

Now you can organize tests as follows:

```
*Test.java  → Surefire → test phase
*IT.java    → Failsafe → integration-test + verify phases
```

Run:

```bash
./mvnw test
```

This runs your regular test suite, but does not normally execute `*IT.java` through Failsafe.

Run:

```bash
./mvnw verify
```

This runs the earlier lifecycle phases, including Surefire tests, and then runs the integration tests configured for Failsafe.

**Critical lesson:** if your CI only runs `./mvnw test`, your Testcontainers integration tests may never execute. A green pipeline can therefore give you false confidence if the integration-test phase is missing.

Before adding CI, verify locally that `./mvnw verify` actually runs both categories.

## 4. Understand Docker and Testcontainers in CI

You've already used Testcontainers with PostgreSQL in your Spring Boot integration-testing phase. Now we need to understand how it behaves on GitHub's runner.

### 4.1 How Testcontainers works

A test such as this:

```java
@Testcontainers
@SpringBootTest
class OrderWorkflowIT {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16");

    // Integration tests...
}
```

asks Testcontainers to manage a PostgreSQL container for the test.

The general flow is:

```
GitHub Actions runner
        |
        v
Maven starts integration tests
        |
        v
Testcontainers contacts Docker
        |
        v
PostgreSQL container starts
        |
        v
Spring Boot connects to PostgreSQL
        |
        v
Integration tests execute
        |
        v
Container is stopped and removed
```

A GitHub-hosted Ubuntu runner normally provides the Docker capabilities needed for standard Testcontainers use. You generally don't need to configure a separate PostgreSQL service for tests that already start PostgreSQL with Testcontainers.

The important requirement is that the Docker environment is available to the test process.

### 4.2 Why use Testcontainers instead of H2 in CI?

Your production database may be PostgreSQL. H2 is a different database with different SQL behavior, types, constraints, and compatibility characteristics.

Testcontainers allows your CI tests to use a real PostgreSQL instance.

This helps catch issues involving:

- SQL syntax and reserved words.
- PostgreSQL-specific data types.
- Constraints and indexes.
- Migrations.
- Database-specific query behavior.
- Transaction behavior and persistence.

It doesn't guarantee production equivalence: the container may use a different PostgreSQL version, configuration, extensions, or data volume from production. Still, it's much more representative than assuming H2 behaves identically.

### 4.3 Do you need Docker Compose in CI?

Not necessarily.

There are two different jobs Docker might perform in your project:

1. **Test infrastructure:** Testcontainers launches PostgreSQL for integration tests.
2. **Application packaging and deployment:** Docker builds your Spring Boot application image, and Docker Compose can run the application alongside its dependencies.

These are related but separate concerns.

For your first CI workflow, let Testcontainers manage PostgreSQL. Add Docker image building after the tests pass. Use Docker Compose for local development or for a CI smoke test if you want to validate that the packaged application can start with its database.

## 5. Create the GitHub Actions workflow

Let's create the first complete CI workflow.

Your repository should contain:

```
your-project/
├── .github/
│   └── workflows/
│       └── ci.yml
├── src/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── Dockerfile
├── docker-compose.yml
├── .gitignore
└── README.md
```

The `mvnw` and `.mvn/` wrapper files are important. Commit them so CI uses the project's Maven wrapper rather than relying on a locally installed Maven version.

### Step 1 — Create .github/workflows/ci.yml

Use this as the starting workflow:

```yaml
name: Spring Boot CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read

jobs:
  test:
    name: Build and test
    runs-on: ubuntu-latest
    timeout-minutes: 20

    steps:
      - name: Checkout source code
        uses: actions/checkout@v4

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Make Maven wrapper executable
        run: chmod +x mvnw

      - name: Build and run tests
        run: ./mvnw --batch-mode --no-transfer-progress verify

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: maven-test-reports
          path: |
            target/surefire-reports/
            target/failsafe-reports/
          if-no-files-found: warn
          retention-days: 7
```

This assumes that `pom.xml` and `mvnw` are at the repository root. If the backend is in a subdirectory, configure the workflow's working directory accordingly.

### Step 2 — Understand each part

**on**

The workflow runs when a commit is pushed to main, a pull request targets main, or you manually start the workflow.

You can later add other branches, tags, or release events as needed.

**permissions**

The workflow receives read access to repository contents. Start with minimal permissions and add more only when the workflow requires them.

**runs-on**

GitHub provides a hosted Ubuntu runner.

**actions/setup-java**

Installs the requested Java distribution and configures Maven dependency caching. The cache can reduce build times by reusing downloaded dependencies.

**chmod +x mvnw**

Ensures that the Maven wrapper is executable on Linux. It's best to commit the correct executable permission in Git too.

**./mvnw --batch-mode --no-transfer-progress verify**

Runs the Maven lifecycle through `verify`. Batch mode avoids interactive prompts; disabling transfer progress keeps the logs cleaner.

**if: always()**

The report-upload step can run even if the Maven step fails. This is valuable because the failure report is often the most useful output from CI.

**actions/upload-artifact**

Stores the generated Surefire and Failsafe report files with the workflow run. It doesn't turn a failed test into a successful one; it preserves diagnostic information.

The action versions shown are established examples. Before adopting a workflow in a public repository, follow your organization's policy for pinning and updating GitHub Actions, and review upstream security guidance.

## 6. Environment variables and secrets

CI runs in a clean environment, so your application needs an explicit configuration strategy.

Suppose your Spring Boot application uses:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

In CI, you can set environment variables for a step or job:

```yaml
env:
  DB_URL: jdbc:postgresql://localhost:5432/ecommerce_test
  DB_USERNAME: test_user
  DB_PASSWORD: test_password
```

However, don't copy this configuration blindly into your Testcontainers setup.

If Testcontainers dynamically configures the datasource through `@DynamicPropertySource`, the container provides the connection details for the test. You generally shouldn't hardcode a localhost PostgreSQL connection in those tests.

For a real CI environment, distinguish these categories:

| Configuration | Example | Recommended handling |
|---|---|---|
| Non-sensitive settings | `SPRING_PROFILES_ACTIVE=e2e` | Workflow or environment configuration |
| Local test database credentials | Disposable test credentials | Test environment only |
| Production database credentials | Real database password | GitHub Secrets or an external secret manager |
| External API credentials | Payment provider key | Secret store; preferably mock the provider in tests |
| Signing keys | JWT signing secret | Dedicated test key or test configuration |

Never commit real passwords, tokens, signing keys, or production credentials to Git.

GitHub repository secrets can be referenced like this:

```yaml
env:
  EXTERNAL_API_KEY: ${{ secrets.EXTERNAL_API_KEY }}
```

Only add the secret if the tests genuinely need it. For a learning project, avoid requiring production-like external credentials merely to run CI.

For pull requests originating from forks, GitHub normally withholds repository secrets from the workflow for security reasons. Design your tests so they can run without privileged secrets wherever possible.

## 7. Dockerfile and Docker Compose

### 7.1 Dockerfile

A Dockerfile defines how to build the application image.

For a simple Spring Boot Maven application, you could start with:

```dockerfile
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

This assumes you've already built the application JAR and that `target/` contains exactly the intended runnable JAR.

For a more production-ready image, use a pinned base-image version or digest, run as a non-root user, and review your container's security and patching policy. The example above prioritizes simplicity for learning.

Build it locally:

```bash
./mvnw package
docker build -t ecommerce-api:local .
```

Run it:

```bash
docker run --rm -p 8080:8080 ecommerce-api:local
```

This verifies that the packaged application can launch in a container. It doesn't prove that the application can connect to a database or that the whole system is healthy.

### 7.2 Docker Compose

Docker Compose describes how to run multiple services together.

For example, a local development configuration might contain:

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: ecommerce
      POSTGRES_USER: ecommerce
      POSTGRES_PASSWORD: local_dev_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  api:
    build: .
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/ecommerce
      SPRING_DATASOURCE_USERNAME: ecommerce
      SPRING_DATASOURCE_PASSWORD: local_dev_password
    ports:
      - "8080:8080"
    depends_on:
      - postgres

volumes:
  postgres_data:
```

Here, `postgres` is the database's Compose service name. Containers on the Compose network can use that name for communication.

The password is a disposable local-development example, not a credential to reuse in production.

Also, `depends_on` by itself does not guarantee that PostgreSQL is ready to accept connections. For a robust setup, add a PostgreSQL health check and configure the API to wait for a healthy database, or implement connection retries.

### 7.3 Should CI build the Docker image?

Yes, once your tests pass, building the image is a useful additional validation.

Add a step after the test step:

```yaml
- name: Build Docker image
  run: docker build -t ecommerce-api:${{ github.sha }} .
```

This validates that the Dockerfile can build an image from the JAR produced by Maven.

If you put this step after `verify`, a failing test prevents the image from being built. That is usually a sensible default for a simple CI pipeline.

For this learning project, build the image but don't push it to a registry yet. Publishing and deploying images are separate concerns that you can add later.

## 8. Make pull requests depend on CI

A CI workflow is most useful when its result influences whether code can be merged.

A common workflow is:

```
Developer creates a branch
          |
          v
     Opens pull request
          |
          v
     CI workflow runs
          |
          +---- Tests fail ----> Fix required
          |
          v
     All required checks pass
          |
          v
     Pull request can be merged
```

### Configure a branch protection rule

In your GitHub repository:

1. Open Settings.
2. Navigate to Rules or Branches, depending on the repository settings UI.
3. Create a ruleset or branch protection rule for `main`.
4. Enable the requirement for status checks before merging.
5. Select the CI job check, such as **Build and test**.
6. Save the rule.

The exact menu wording may vary.

Make sure the required check corresponds to the workflow and job that actually run on your pull requests. If the workflow never runs for the target branch or the required check name is wrong, merging can become blocked unexpectedly.

A required status check doesn't guarantee perfect software. It means that the checks you've configured must pass before a merge is allowed under that rule.

## 9. Test reports and failure handling

A professional CI pipeline must do more than print `BUILD FAILURE`.

You should be able to answer:

- Which test failed?
- Was it a unit test or an integration test?
- What was the expected result?
- What actually happened?
- Did Testcontainers start PostgreSQL?
- Did the application connect to the database?
- Where can I find the stack trace?

### 9.1 Understand the Maven reports

Maven normally produces:

```
target/
├── surefire-reports/
│   ├── TEST-com.example.PriceCalculatorTest.xml
│   └── com.example.PriceCalculatorTest.txt
└── failsafe-reports/
    ├── TEST-com.example.OrderWorkflowIT.xml
    └── com.example.OrderWorkflowIT.txt
```

The exact filenames depend on your test classes and Maven configuration.

Surefire reports cover tests run through Surefire. Failsafe reports cover integration tests run through Failsafe.

The XML files are machine-readable and can be consumed by CI reporting tools. The text reports often contain helpful failure details and stack traces.

### 9.2 What happens when a test fails?

Suppose `OrderWorkflowIT` fails because the order total is wrong.

Maven returns a non-zero exit code. GitHub Actions marks the step as failed, and the job normally stops executing later steps unless their conditions permit them to run.

Because the report-upload step uses `if: always()`, GitHub can still preserve the generated reports.

A typical debugging workflow is:

1. Open the failed GitHub Actions run.
2. Expand the Maven step and inspect the logs.
3. Download the report artifact if needed.
4. Identify the first meaningful failure and stack trace.
5. Reproduce it locally with `./mvnw -Dtest=... test` or `./mvnw -Dit.test=... verify`, as appropriate.
6. Fix the actual cause and push the change.

For a Failsafe integration test, use the `-Dit.test=... verify` form so that the integration-test phase actually runs.

### 9.3 What if Testcontainers fails?

A Testcontainers failure doesn't always mean the application or the test is wrong.

Investigate:

- Did the container image download successfully?
- Is Docker available to the runner?
- Did PostgreSQL start?
- Did the test configure the correct datasource properties?
- Did the database become ready?
- Did Flyway apply the migrations successfully?
- Did the test fail because of application behavior after the database started?

Distinguish infrastructure failures from real test assertion failures. Don't blindly retry every failure; repeated retries can hide a flaky test or a genuine defect.

### 9.4 Preserve logs and reports even when the workflow fails

You already have the report artifact step in the sample workflow. You can also upload relevant application logs or test-generated diagnostics if your test setup produces them.

Be cautious with logs and artifacts: they can contain secrets, access tokens, customer data, or database connection information. Use synthetic data and avoid printing credentials.

## 10. A complete CI workflow for your Spring Boot project

Let's combine the concepts into one more complete pipeline.

This version:

- Runs on pushes and pull requests targeting main.
- Uses Java 17 and Maven caching.
- Runs the full Maven verification lifecycle, including configured Testcontainers integration tests.
- Uploads Surefire and Failsafe reports.
- Builds the Docker image only if the tests pass.

Create `.github/workflows/ci.yml`:

```yaml
name: Spring Boot CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read

jobs:
  verify:
    name: Build and test
    runs-on: ubuntu-latest
    timeout-minutes: 20

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Configure Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Prepare Maven wrapper
        run: chmod +x mvnw

      - name: Run unit and integration tests
        run: ./mvnw --batch-mode --no-transfer-progress clean verify

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: |
            target/surefire-reports/
            target/failsafe-reports/
          if-no-files-found: warn
          retention-days: 7

      - name: Build Docker image
        run: docker build -t spring-boot-app:${{ github.sha }} .
```

**Why use clean verify?**

`clean` removes the previous build output, and `verify` executes the configured Maven lifecycle through verification.

That makes the result less likely to depend on stale compiled classes or old reports.

The trade-off is that `clean` removes reusable build output, so it can increase build time. Maven dependency caching still helps because dependencies can be reused without being downloaded again.

**Why build the image after testing?**

You don't want a failed test suite to be followed by a successful image build that someone mistakenly treats as a valid release.

In this workflow, a failed Maven step prevents the Docker build step from running. Report uploading is the deliberate exception because diagnostics are still useful after a failure.

This is a CI pipeline, not yet a complete deployment pipeline. It validates the application and creates a local image on the runner, but it doesn't publish that image or deploy it.

**A note on Java version**

This workflow uses Java 17 because that matches your existing Spring Boot learning projects. Use the Java version your project's `pom.xml` and runtime actually require. If you later migrate to Java 21 or another version, update the workflow and project configuration together.

## 11. Prepare your repository for CI

### 11.1 Commit the Maven wrapper

The repository should include:

```
mvnw
mvnw.cmd
.mvn/
└── wrapper/
    ├── maven-wrapper.properties
    └── ...
```

The exact wrapper files depend on the Maven Wrapper version generated by your project.

Use the wrapper to make local and CI Maven versions more consistent:

```bash
./mvnw --version
```

Avoid relying on `mvn` being installed globally on every machine.

### 11.2 Add a useful .gitignore

For a Maven-based Java project:

```gitignore
# Maven build output
target/

# IDE files
.idea/
*.iml
.vscode/

# OS files
.DS_Store
Thumbs.db

# Local environment files
.env
.env.*
!.env.example

# Logs
*.log

# Local test output
playwright-report/
test-results/
```

If your repository contains a frontend or Playwright project, include the appropriate additional exclusions for its generated output and installed dependencies.

Don't ignore files needed to reproduce the build, such as `pom.xml`, the Maven wrapper, Flyway migrations, or test source code.

### 11.3 Keep the README useful

Your README.md should explain:

- What the application does.
- The architecture and technologies.
- How to run the application locally.
- How to run unit tests.
- How to run integration tests with Testcontainers.
- How to run the complete Maven verification lifecycle.
- How to start services with Docker Compose.
- Which environment variables are required.
- What CI checks run on pull requests.

For example, your testing section could document:

```bash
# Run the ordinary test phase
./mvnw test

# Run the full configured verification lifecycle
./mvnw verify

# Run a specific unit test
./mvnw -Dtest=PriceCalculatorTest test

# Run a specific integration test
./mvnw -Dit.test=OrderWorkflowIT verify
```

The last command assumes Failsafe is configured to recognize the `*IT.java` naming convention.

## 12. Troubleshooting common CI failures

Use this table as a first diagnostic guide.

| Failure | Likely cause | What to investigate |
|---|---|---|
| Permission denied: ./mvnw | Wrapper isn't executable | Git file permissions and `chmod +x mvnw` |
| Java version error | Local and CI Java versions differ | `java-version`, `pom.xml`, compiler configuration |
| Tests pass locally but fail in CI | Environment or data dependency | Environment variables, timezone, test ordering, database state |
| Docker is not available | Docker environment unavailable to the test process | Runner configuration and Testcontainers diagnostics |
| PostgreSQL connection refused | Container not ready or datasource misconfigured | Container logs, dynamic properties, readiness |
| Flyway migration fails | Schema or migration problem | Migration order, PostgreSQL compatibility, schema state |
| `mvn test` passes but integration tests don't run | Failsafe lifecycle wasn't invoked or test patterns don't match | Use `verify` and inspect plugin configuration |
| Build succeeds locally but Docker build fails | Incorrect Dockerfile or missing JAR | Build order, JAR location, Docker build context |
| Duplicate user or order errors | Shared test data | Unique identifiers, cleanup, parallel interference |
| Pull request cannot be merged | Required check missing or failing | Workflow triggers, job names, branch rules |

When a failure occurs, don't start by disabling the test or adding a retry. Determine whether the issue is in the code, test, environment, or pipeline configuration.

## 13. Your final project roadmap

The project for this phase should be a real public GitHub repository that demonstrates not only that you can write Spring Boot code, but that you can maintain a reproducible, tested application.

### Open-Source Spring Boot Project — Implementation milestones

- [ ] 1. **Select a project** — Choose a small but meaningful Spring Boot API, such as an order-management API or webhook-processing service.
- [ ] 2. **Define the test layers** — Organize unit tests, repository tests, security tests, and integration tests with clear naming conventions.
- [ ] 3. **Configure PostgreSQL and Testcontainers** — Make integration tests independent of a manually running local database.
- [ ] 4. **Configure Maven lifecycle** — Use Surefire for the test phase and Failsafe for integration tests; confirm `verify` runs both.
- [ ] 5. **Create the GitHub Actions workflow** — Build and test on pushes and pull requests.
- [ ] 6. **Preserve test reports** — Upload Surefire and Failsafe reports even when tests fail.
- [ ] 7. **Build the Docker image** — Verify that the tested application can be packaged into a container image.
- [ ] 8. **Add branch protection** — Require the CI check before merging into main.
- [ ] 9. **Document everything** — Write reproducible local setup and testing instructions in the README.
- [ ] 10. **Demonstrate a real failure** — Introduce a controlled failing assertion, observe CI fail, then fix it and observe CI pass.

### What should the final repository look like?

```
spring-boot-project/
├── .github/
│   └── workflows/
│       └── ci.yml
├── .mvn/
│   └── wrapper/
├── src/
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   │       └── db/migration/
│   └── test/
│       └── java/
│           ├── unit/
│           └── integration/
├── .gitignore
├── Dockerfile
├── docker-compose.yml
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

The package names and test directories should follow your actual Java package structure; the tree above illustrates the intended separation, not a mandatory Maven layout.

## 14. Final exercises: prove that the pipeline works

Don't stop after seeing one green CI run. Complete these exercises.

**Exercise 1 — Test the happy path**

Push a working project and verify that GitHub Actions builds it and runs both unit and integration tests.

**Exercise 2 — Force a test failure**

Temporarily change a test to assert an incorrect result. Push the change to a branch and verify that CI fails.

**Exercise 3 — Investigate the failure**

Open the Actions run, find the failing test, and inspect its report and logs.

**Exercise 4 — Fix and rerun**

Restore the correct assertion, push again, and verify that CI passes.

**Exercise 5 — Verify Testcontainers**

Confirm that the integration test actually starts PostgreSQL and runs. Do not assume it runs just because `mvn test` is green.

**Exercise 6 — Test a clean build**

Run:

```bash
./mvnw clean verify
```

This should pass on your machine without depending on stale build output or an accidentally running local database.

**Exercise 7 — Enforce the check**

Open a pull request and verify that the required CI status check appears before merging.

## 15. What you've learned across the entire roadmap

| Phase | Skill you developed |
|---|---|
| 1–3 | Unit testing, assertions, Mockito, and business rules |
| 4–5 | HTTP controller testing with MockMvc |
| 6 | JPA and repository testing |
| 7 | Full Spring application-context testing |
| 8 | PostgreSQL integration testing with Testcontainers |
| 9 | Spring Security and authorization testing |
| 10 | Complete backend workflows and tenant isolation |
| 11 | Browser-driven E2E testing with Playwright |
| 12 | Automated CI, test reports, repeatable builds, and pull request checks |

The bigger lesson is that a test suite is only useful if it is actually executed, its failures are visible, and the environment makes its results trustworthy.

My recommendation: for your first real repository, prioritize a reliable `./mvnw clean verify` pipeline with PostgreSQL/Testcontainers and retained test reports. Then build the Docker image and require the CI check on pull requests. Don't add deployment, Kubernetes, or complex multi-job orchestration until this basic pipeline is dependable.

That gives you a practical foundation for maintaining a Spring Boot application rather than just demonstrating isolated testing techniques.

---

## Environment notes (this machine)

Context for implementing this plan locally — the design above is unchanged, these are the concrete facts:

- **Java 17** (Temurin 17.0.13), **Maven 3.6.3** global — but this project commits and uses `./mvnw` (wrapper) so local and CI run the same Maven.
- **Docker 28.1.1** available; the **`postgres:16` image is already cached** locally, so Testcontainers starts it without pulling.
- **Local PostgreSQL occupies `127.0.0.1:5432`** → irrelevant for tests: Testcontainers maps a *random* host port. `docker-compose.yml` (dev only, not used by tests) maps host port **5434** → container 5432 to avoid the clash.
- Maven dependencies (Boot 3.4.1, Testcontainers, Flyway…) come from `~/.m2`; the first `./mvnw` run and the pinned Surefire/Failsafe `3.5.4` plugins may download once from Maven Central.
- **This repo is a monorepo** (`phase-1…phase-12` in one git repo), so the workflow lives at the **repository root** `.github/workflows/ci.yml` and points at this folder with:
  - `on.push/pull_request.paths: ['phase-12-CI-Testing-GitHub-Actions/**', '.github/workflows/ci.yml']`
  - `defaults.run.working-directory: phase-12-CI-Testing-GitHub-Actions`
  - artifact paths and `docker build` context explicitly prefixed with the phase folder (upload-artifact and per-step overrides don't inherit the run default)
- Project chosen (per user decision): a simple **task/todo API** instead of the order-management suggestion.
- Exercises 1–10 stay **manual** for the user (real GitHub push, branch protection, forced-failure demo).
