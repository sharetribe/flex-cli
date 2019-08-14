(ns sharetribe.flex-cli.async-util
  (:require [clojure.core.async :as async :refer [go <!]]
            [clojure.core.async.impl.protocols :as async.protocols])
  (:require-macros sharetribe.flex-cli.async-util))

(defn throw-err
  "Throw if e is Error. This fn will be called by the defined macro in
  client.clj."
  [e]
  (when (instance? js/Error e) (throw e))
  e)

(defn- read-port?
  "Checks if x is read port. There's no predicate for `chan?`, thus this
  is the way to do it. See:
  https://clojure.atlassian.net/browse/ASYNC-74"
  [x]
  (satisfies? async.protocols/ReadPort x))

(defn ensure-chan
  "Takes x and ensures it's a channel. If not, it will wrap the value in
  a go block (which returns values). Helps to normalize operations
  with may be async or sync."
  [x]
  (if (read-port? x)
    x
    (go x)))
