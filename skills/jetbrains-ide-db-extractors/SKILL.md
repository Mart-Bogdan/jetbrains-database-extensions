---
name: jetbrains-ide-db-extractors
description: Write, fix or debug data extractors for the Database Tools and SQL plugin of JetBrains IDEs (Rider, DataGrip, IntelliJ), the Groovy or JavaScript scripts that turn a result grid into text for copy or export (CSV, JSON, Markdown, SQL inserts, TOON, …). Use whenever the user wants a new copy/export format for query results, asks about the "Data Extractors" list or the `extensions/com.intellij.database/data/extractors` folder, wants a library from Maven in an extractor, or an extractor misbehaves, e.g. a `.js` extractor that outputs plain CSV or a Groovy one failing with classloader or Jackson errors. Even if they don't say "extractor".
---

# JetBrains IDE data extractors

A data extractor is a script the IDE runs over a result grid; whatever it appends to `OUT` is what **Copy** or **Export** produces. This skill records what has been verified in practice, on Rider 2026.2.3.1 (Java 25) on Windows. Other IDEs share the same plugin, but treat their details as unverified until checked.

Official documentation: [Data extractors | DataGrip](https://www.jetbrains.com/help/datagrip/data-extractors.html) (adding a custom extractor, the API bindings, example extractors).

## Where extractors live and how they are named

- In the IDE: **Project** tool window → **Scratches and Consoles | Extensions | Database Tools and SQL | data | extractors**.
- On disk: `<IDE config dir>\extensions\com.intellij.database\data\extractors`. For Rider 2026.2 on Windows: `%APPDATA%\JetBrains\Rider2026.2\extensions\com.intellij.database\data\extractors`. The config dir is per major version.
- File name: `<Name>.<output extension>.<groovy|js>`, e.g. `TOON-Groovy.toon.groovy`. `<Name>` is what the extractor list shows. Every script in this folder becomes a list entry, so keep helper code inside the extractor file rather than next to it.

## Start from a bundled extractor

The plugin ships its own extractors and copies them into that same folder, so every user has them. In Rider 2026.2 they are:

| File | Good starting point for |
| --- | --- |
| `JSON-Groovy.json.groovy` | nested or structured formats; handles JSON columns, `hasValue`, value types |
| `XML-Groovy.xml.groovy` | markup with escaping; also uses `hasValue` |
| `CSV-Groovy.csv.groovy`, `Pretty-Groovy.txt.groovy`, `Markdown-Groovy.md.groovy`, `HTML-Groovy.html.groovy` | row-per-line and table formats |
| `SQL-Insert-Statements.sql.groovy`, `SQL-Insert-Multirow.sql.groovy` | code generation that uses `TABLE` and `DIALECT` |
| `One-row.sql.groovy` | code generation that uses `DIALECT` |
| `Python-DataFrame.py.groovy` | code in another language |
| `HTML-JavaScript.html.js` | the only JavaScript example |

For transposed grids (`TRANSPOSED` is true), `CSV-Groovy`, `HTML-Groovy`, `Pretty-Groovy` and `XML-Groovy` show the handling; the others only mention the binding in their header comment.

Before writing a new extractor, list the folder and read the one closest to the target format. It is the most reliable reference for the API in the user's IDE version, more so than this skill or the online docs. If you can't read the folder (it lives in the user's IDE config, often outside the working directory), ask the user to grant access or paste the closest file, rather than writing from memory.

## The script API

The bundled scripts document their bindings in a header comment; copy it into new extractors:

```
COLUMNS     List<DataColumn>
ROWS        Iterable<DataRow>
OUT         { append() }
FORMATTER   { format(row, col); formatValue(Object, col); getTypeName(Object, col); isStringLiteral(Object, col); }
TRANSPOSED  Boolean
plus ALL_COLUMNS, TABLE, DIALECT

DataRow     { rowNumber(); first(); last(); data(): List<Object>; value(column): Object }
DataColumn  { columnNumber(), name() }
```

`JSON-Groovy` also calls `row.hasValue(col)` before `row.value(col)`, and leaves the column out of that row when it returns false. Do the same in every extractor: a row does not necessarily hold a value for every column, and following the bundled extractor's check is safer than assuming it does. `NEWLINE` is not a binding either: the bundled scripts define it themselves (`NEWLINE = System.getProperty("line.separator")`), so an extractor that uses it without that line fails.

Patterns from the bundled JSON extractor that are worth reusing:

- Pass `null`, `Boolean`, `Number` and `String` through; turn anything else into text with `FORMATTER.formatValue(o, col)`, which is the IDE's own rendering of dates, UUIDs and other database types.
- `FORMATTER.getTypeName(o, col)` gives the column's database type, e.g. `json` or `jsonb`. JSON-Groovy uses it to emit JSON columns unquoted; an extractor for another format can parse such values to get nested data.
- `FORMATTER.isStringLiteral(o, col)` tells whether a value needs quoting as a string.

## Groovy or JavaScript

**Prefer Groovy.** It works without any setup.

JavaScript needs two things on each machine:

1. The **IntelliJ Scripting: JavaScript** plugin (GraalJS). Without it the IDE shows *No script engine found for file extension 'js'*, and every `.js` extractor, including the bundled `HTML-JavaScript.html.js`, silently outputs CSV without a header row. So if a JS extractor "outputs CSV", the engine is missing; the script is not running at all.
2. The VM option `-Dpolyglot.js.ecmascript-version=latest` (**Help | Edit Custom VM Options**, then restart). Without it GraalJS runs as ECMAScript 5: `class`, arrow functions, generators, spread, `??` and `?.` are syntax errors. With it, ECMAScript 2024 works.

Only suggest JavaScript when the user already has both, or wants to reuse an npm library and accepts the setup.

## Using libraries in Groovy extractors

`@Grab` works: Apache Ivy is available, and Grape downloads from Maven Central into `~/.groovy/grapes`, so only the first run needs network access.

How scripts are loaded matters for dependencies:

```
0. GroovyClassLoader$InnerLoader      the compiled script
1. GroovyClassLoader                  @Grab adds jars here
2. "Cancellable Engine Classloader"
3. ScriptingClassLoader               exposes the IDE's own libraries (Groovy, Ivy, Jackson 2 + 3, …)
4. <bootstrap>
```

- **The IDE's copy of a library always wins.** The Groovy loaders ask their parents first. If the IDE bundles a library (it bundles Jackson 2.19.0 and Jackson 3.1.4 in Rider 2026.2.3.1), a grabbed copy of it is ignored. A grabbed add-on module of that library still loads, though, and then runs against the IDE's version. For tightly coupled modules, such as Jackson's Blackbird, that mismatch fails with `NoSuchMethodError` or `AbstractMethodError`.
- So grab the library with `transitive = false` and let it use the IDE's dependencies. For any add-on module the IDE lacks, grab it at run time at the IDE's exact version, into the classloader that holds the library:

  ```groovy
  @Grab(group = 'dev.toonformat', module = 'jtoon', version = '3.0.0', transitive = false)
  import dev.toonformat.jtoon.JToon
  import groovy.grape.Grape
  import tools.jackson.databind.cfg.PackageVersion

  def jackson = PackageVersion.VERSION
  Grape.grab(group: 'tools.jackson.module', module: 'jackson-module-blackbird',
             version: "${jackson.majorVersion}.${jackson.minorVersion}.${jackson.patchLevel}".toString(),
             transitive: false, classLoader: JToon.classLoader)
  ```

- Each run gets fresh classloaders, so jars grabbed in one run are not visible to the next; the download cache is what persists.

## Testing

There is no way to run an extractor from outside the IDE here, so testing needs the user:

1. Have them run a query, pick the extractor in the result grid's extractor list (next to the Export Data icon), copy the output and paste it back.
2. Errors appear as IDE notifications; ask for the text.
3. When the environment itself is in question (engine, library versions, which classloader provides a class), write a small probe extractor that appends its findings to `OUT`, for example `Class.forName(name, false, this.class.classLoader)` for each class of interest, the defining loader of each, and `tools.jackson.databind.cfg.PackageVersion.VERSION`. Keep probes read-only, and have the user delete them afterwards, because they clutter the extractor list.

Rerun probes after an IDE update: the bundled library versions move with it.

The extractor docs link to [DataGrip/dg-scripted-extensions-environment](https://github.com/DataGrip/dg-scripted-extensions-environment), a "pre-configured project for scripted extensions development". It does not run or test extractors: it is a Gradle build that downloads IntelliJ IDEA Ultimate 2022.1.1 with the Database Tools plugin, to give a human code completion and type checks while editing (Rider shows syntax highlighting for these scripts, but says code insight needs IntelliJ IDEA). An agent gains nothing from it: edit extractors as plain files, and test them through the IDE as described above. The project is a single commit from January 2023, pinned to that IDE version and the old `org.jetbrains.intellij` 1.x Gradle plugin.

## Keeping copies in sync

The IDE runs the copy in its config folder. If the extractor is also kept in a repository, nothing syncs the two, so after editing one, copy it to the other and say which copy you changed.
