// Probe: reports whether this Groovy runtime could fetch Maven dependencies (@Grab needs Apache Ivy),
// and which classloaders the script and its libraries live in. It only inspects; nothing is downloaded.
def r = []
def t = { name, c -> try { r << "$name: ${c()}" } catch (Throwable e) { r << "$name: ERROR $e" } }
def cl = this.class.classLoader

// One line per loader, from the given one up to the bootstrap loader.
def chain = { ClassLoader l ->
  def out = []
  while (l != null) { out << "${l.class.name}@${Integer.toHexString(System.identityHashCode(l))}${l.name ? " name=$l.name" : ""}"; l = l.parent }
  out << "<bootstrap>"
  out.withIndex().collect { s, i -> "\n    $i. $s" }.join()
}
// Which loader actually defined a class, and the jar it came from.
def origin = { String cn ->
  def c = Class.forName(cn, false, cl)
  "${c.classLoader ? "${c.classLoader.class.name}@${Integer.toHexString(System.identityHashCode(c.classLoader))}" : "<bootstrap>"} <- ${c.protectionDomain?.codeSource?.location}"
}

t("groovy", { GroovySystem.version ?: "(empty) jar: ${GroovyObject.protectionDomain?.codeSource?.location}" })
t("java", { System.getProperty("java.version") })
t("script classloader chain", { chain(cl) })
t("thread context classloader chain", { chain(Thread.currentThread().contextClassLoader) })
t("script class loader == context loader", { cl.is(Thread.currentThread().contextClassLoader) })
t("Grape class", { origin("groovy.grape.Grape") })
t("Grape engine", { groovy.grape.Grape.instance?.class?.name })
t("Ivy on classpath", { origin("org.apache.ivy.Ivy") })
t("Jackson 2 (com.fasterxml)", { origin("com.fasterxml.jackson.databind.ObjectMapper") })
t("Jackson 2 version", { Class.forName("com.fasterxml.jackson.databind.cfg.PackageVersion", true, cl).getField("VERSION").get(null) })
t("Jackson 3 (tools.jackson)", { origin("tools.jackson.databind.ObjectMapper") })
t("Jackson 3 databind version", { Class.forName("tools.jackson.databind.cfg.PackageVersion", true, cl).getField("VERSION").get(null) })
t("Jackson 3 core version", { Class.forName("tools.jackson.core.json.PackageVersion", true, cl).getField("VERSION").get(null) })
t("Jackson 3 Blackbird (IDE)", { origin("tools.jackson.module.blackbird.BlackbirdModule") })
t("jtoon (from earlier @Grab)", { origin("dev.toonformat.jtoon.JToon") })
// Parent-first or child-first? If a child loader prefers its own copy, a grabbed Jackson would shadow the IDE's.
t("GroovyClassLoader delegation", { cl instanceof GroovyClassLoader ? "GroovyClassLoader (parent-first by default)" : "not a GroovyClassLoader" })
t("script engines", { new javax.script.ScriptEngineManager(cl).engineFactories.collect { "${it.engineName} ${it.engineVersion} (${it.languageVersion})" }.join("; ") })

OUT.append(r.join(System.lineSeparator()))
