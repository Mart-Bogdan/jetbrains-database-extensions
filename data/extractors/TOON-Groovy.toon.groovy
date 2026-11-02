/*
 * Available context bindings:
 *   COLUMNS     List<DataColumn>
 *   ROWS        Iterable<DataRow>
 *   OUT         { append() }
 *   FORMATTER   { format(row, col); formatValue(Object, col); getTypeName(Object, col); isStringLiteral(Object, col); }
 *   TRANSPOSED  Boolean
 * plus ALL_COLUMNS, TABLE, DIALECT
 *
 * where:
 *   DataRow     { rowNumber(); first(); last(); data(): List<Object>; value(column): Object }
 *   DataColumn  { columnNumber(), name() }
 */

// TOON encoder: https://github.com/toon-format/toon-java (MIT), fetched from Maven Central on first use.
// transitive=false: jtoon then runs on the Jackson 3 bundled with the IDE, which always wins over a
// downloaded copy (the script's classloader asks its parents first). Only the Blackbird module is
// missing there, and it must match the IDE's jackson-databind, so it is grabbed below at that version.
@Grab(group = 'dev.toonformat', module = 'jtoon', version = '3.0.0', transitive = false)
import dev.toonformat.jtoon.JToon
import groovy.grape.Grape
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.PackageVersion

// jtoon loads Blackbird on its first encode, through the loader that holds jtoon, so it goes there.
def jackson = PackageVersion.VERSION
Grape.grab(group: 'tools.jackson.module', module: 'jackson-module-blackbird',
           version: "${jackson.majorVersion}.${jackson.minorVersion}.${jackson.patchLevel}".toString(),
           transitive: false, classLoader: JToon.classLoader)

JSON = new ObjectMapper()

// Keep plain JSON-like values; JSON columns become nested data; anything else
// becomes the IDE's text for it (jtoon would turn unknown types into null).
def toToonValue(col, o) {
  switch (o) {
    case null:
    case Boolean:
    case Number:
    case String:
      if (o instanceof String && isJsonType(o, col)) return parseJson(o)
      return o
    default:
      def str = FORMATTER.formatValue(o, col)
      return isJsonType(o, col) ? parseJson(str) : str
  }
}

def isJsonType(o, col) {
  def typeName = FORMATTER.getTypeName(o, col)
  typeName != null && (typeName.equalsIgnoreCase("json") || typeName.equalsIgnoreCase("jsonb"))
}

def parseJson(String str) {
  try { JSON.readValue(str, Object) } catch (Exception ignored) { str }
}

def rows = ROWS.collect { row ->
  def map = new LinkedHashMap<String, Object>()
  COLUMNS.each { col ->
    if (row.hasValue(col)) map.put(col.name(), toToonValue(col, row.value(col)))
  }
  map
}

OUT.append(JToon.encode(rows))
