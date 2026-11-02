// Probe: reports which JavaScript features this extractor engine supports.
// Written in ES5 so it runs on any engine; newer syntax is tested through eval.
var r = [];
function t(name, f) { try { r.push(name + ": " + f()); } catch (e) { r.push(name + ": ERROR " + e); } }

t("java.version", function () { return java.lang.System.getProperty("java.version"); });
t("typeof Graal", function () { return typeof Graal === "undefined" ? "undefined" : JSON.stringify(Graal); });
t("typeof Polyglot", function () { return typeof Polyglot; });
t("typeof Java", function () { return typeof Java; });
t("typeof load", function () { return typeof load; });
t("ES2015 class, arrow, spread", function () { return eval("class A { m() { return [1, ...[2]].map(x => x * 2).join(); } }; new A().m()"); });
t("ES2015 generators", function () { return eval("(function* () { yield 'ok' })().next().value"); });
t("ES2018 object spread", function () { return eval("JSON.stringify({ ...{ a: 1 } })"); });
t("ES2020 ?? and ?.", function () { return eval("var o = null; (o?.x) ?? 'ok'"); });
t("ES2022 class fields", function () { return eval("class B { #p = 'ok'; get p() { return this.#p; } }; new B().p"); });

OUT.append(r.join("\n"));
