# Quarkus Codebase Analyzer

This project is built with Quarkus, the Supersonic Subatomic Java Framework.

The goal of this simple app is to analyze the tags of the Quarkus codebase and report the number of java source files, java types (classes, interfaces, etc.), [build items](https://quarkus.io/guides/writing-extensions#build-items) and [config items](https://quarkus.io/guides/writing-extensions#configuration-keys).

## Get Started

1. Clone the repo
2. Build the application: `mvn clean package`
3. Start the analysis: `java -jar target/quarkus-app/quarkus-run.jar`
4. Open the generated `work/report/report.html`

NOTE: Run `java -jar target/quarkus-app/quarkus-run.jar --help` to display the help message.

## Generate the Report for Specific Tags

If you need to analyze a specific set of tags, you can use the `--tags` option, e.g. `java -jar target/quarkus-app/quarkus-run.jar --tags "2.0.0.Final,2.1.1.Final"`.

## Sample Report

A sample report is available at: https://mkouba.github.io/quarkus-codebase-analyzer/samples/sample-report
