# jetbrains-database-extensions

Custom scripts for the **Database Tools and SQL** plugin of JetBrains IDEs. The folder layout mirrors the IDE's own extensions folder, so each file can be copied to the same relative path there.

| Script | What it does |
| --- | --- |
| [`data/extractors/TOON-Groovy.toon.groovy`](data/extractors/TOON-Groovy.toon.groovy) | Copies or exports result grids as [TOON](https://github.com/toon-format/toon) (Token-Oriented Object Notation), a compact format for LLM prompts. |
| [`probes/`](probes) | Diagnostic extractors that report what the IDE's scripting environment offers. Not for everyday use. |

## Installing

In the IDE, open the **Project** tool window and go to **Scratches and Consoles | Extensions | Database Tools and SQL**. Put each script at the same relative path as in this repo, for example `data/extractors/TOON-Groovy.toon.groovy`.

On disk, that folder is `<IDE config dir>\extensions\com.intellij.database`. For Rider 2026.2 on Windows it is:

```
%APPDATA%\JetBrains\Rider2026.2\extensions\com.intellij.database
```

The extractor then appears as **TOON-Groovy** in the result grid's extractor list, next to the Export Data icon.

## TOON-Groovy

Each result row becomes an object keyed by column name, so a plain result set comes out as one TOON table:

```
[3]{name,orders}:
  Alice,3
  "Smith, John",7
  null,0
```

Values:

- Numbers, booleans, strings and nulls pass through unchanged.
- `json` and `jsonb` columns are parsed, so nested data becomes nested TOON. A value that doesn't parse stays a string.
- Anything else (dates, UUIDs, other database types) becomes the IDE's text for it. The TOON library would otherwise turn types it doesn't know into `null`.

### Dependencies

The encoder is [jtoon](https://github.com/toon-format/toon-java) 3.0.0 (MIT), downloaded from Maven Central with `@Grab` on first use and cached in `~/.groovy/grapes`. Later runs work offline.

jtoon needs Jackson 3 and its Blackbird module:

- **Jackson 3** comes from the IDE itself. `@Grab` uses `transitive = false`, so no second copy is downloaded. One would be ignored anyway: the script's classloader asks its parents first, and they already have the IDE's Jackson.
- **Blackbird** isn't bundled with the IDE. It hooks into Jackson's internals, so it must match the IDE's `jackson-databind` version. The script reads that version at run time and grabs the matching Blackbird release. After an IDE update changes its Jackson, the first run downloads the new Blackbird.

## Scripting environment

Measured on Rider 2026.2.3.1 (Java 25.0.4) with the probes in [`probes/`](probes). Versions change with IDE updates, so rerun the probes after one.

### Groovy

Works without setup.

- Apache Ivy is available, so `@Grab` downloads from Maven Central (Grape engine: `groovy.grape.GrapeIvy`).
- Jackson 2 (2.19.0) and Jackson 3 (3.1.4) are visible to scripts. Both come from the IDE's core classloader.
- Each run gets a fresh classloader. Jars added by `@Grab` in one run aren't visible in the next, but the downloads stay cached on disk.

Classloader chain of a script:

```
0. groovy.lang.GroovyClassLoader$InnerLoader      the compiled script
1. groovy.lang.GroovyClassLoader                  @Grab adds jars here
2. ExtensionScriptsUtil$1 "Cancellable Engine Classloader"
3. DatabaseExtensionScriptRunnerInIde$ScriptingClassLoader
       Groovy, Ivy    -> a plugin's PluginClassLoader
       Jackson 2 + 3  -> the IDE's core PathClassLoader
4. <bootstrap>
```

### JavaScript

Doesn't work out of the box.

- Without a JavaScript engine, a `.js` extractor silently outputs CSV without a header row, including the built-in `HTML-JavaScript.html.js`. The IDE shows *No script engine found for file extension 'js'* and offers the plugin below.
- The **IntelliJ Scripting: JavaScript** plugin adds GraalJS, but in ECMAScript 5 mode: classes, arrow functions, generators, spread, `??` and `?.` are all syntax errors.
- Adding `-Dpolyglot.js.ecmascript-version=latest` under **Help | Edit Custom VM Options** and restarting switches it to ECMAScript 2024.

Groovy is the better choice for extractors meant to work on any machine, because JavaScript needs the plugin and the VM option on each one.

## Probes

To run a probe, copy it to `data/extractors` in the IDE's extensions folder, pick it in a result grid's extractor list, and copy the result. Probes only inspect the environment and download nothing. Remove them afterwards, since they clutter the extractor list.

- [`Probe-Groovy.txt.groovy`](probes/Probe-Groovy.txt.groovy): Groovy and Java versions, Ivy, Jackson versions, the classloader chain and where each library is loaded from, and the registered script engines.
- [`Probe-JS.txt.js`](probes/Probe-JS.txt.js): the JavaScript engine and which ECMAScript features it accepts.

## License

[MIT](LICENSE).
