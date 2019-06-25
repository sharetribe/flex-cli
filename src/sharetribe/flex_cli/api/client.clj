(ns sharetribe.flex-cli.api.client
  "Macro namespace for API client"
  (:require [clojure.core.async :as async]))

(defmacro <?
  "Takes from channel and throws if error. The throw-err function is defined in CLJS corresponsing cljs namespace.

  See more about this pattern:
  https://martintrojer.github.io/clojure/2014/03/09/working-with-coreasync-exceptions-in-go-blocks"
  [ch]
  `(throw-err (async/<! ~ch)))
