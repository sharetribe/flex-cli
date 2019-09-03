(ns sharetribe.flex-cli.commands.process.util)

(defn keyword->str
  "Convert an keyword to a string

  Unqualified keyword:

  (keyword->str :foo) => \"foo\"

  Qualified keyword:

  (keyword->str :foo/bar) => \"foo/bar\"
  "
  [k]
    (if (qualified-keyword? k)
      (str (namespace k) "/" (name k))
      (name k)))
