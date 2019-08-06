(ns sharetribe.flex-cli.view
  "Namespace for view functions. Functions should be pure data in, fipp
  docs or string out."
  (:require [clojure.string :as str]))

(defn interpose-some
  "Same as interpose but removes nils from the coll. As interpose,
  returns transducers if coll is not provided."
  ([sep] (comp
          (filter some?)
          (interpose sep)))
  ([sep coll]
   (sequence (interpose-some sep) coll)))

(defn join-some
  "Same as str/join but removes nils"
  ([coll] (join-some "" coll))
  ([sep coll]
   (str/join sep (filter some? coll))))
