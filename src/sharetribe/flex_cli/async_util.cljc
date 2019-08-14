(ns sharetribe.flex-cli.async-util
  (:require [clojure.core.async :as async]))

(defmacro <?
  "Takes from channel and throws if error. The throw-err function is defined in CLJS corresponsing cljs namespace.

  See more about this pattern:
  https://martintrojer.github.io/clojure/2014/03/09/working-with-coreasync-exceptions-in-go-blocks"
  [ch]
  `(throw-err (async/<! ~ch)))

#?(:cljs
   (defmacro go-try [& body]
     `(async/go
        (try
          ~@body
          (catch js/Error e#
            e#)))))
