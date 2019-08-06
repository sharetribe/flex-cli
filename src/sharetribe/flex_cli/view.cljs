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

(defn transpose
  ;; https://stackoverflow.com/a/10347404
  [m]
  (apply map list m))

(defn right-pad [s length]
  (str s (apply str (repeat (- length (count s)) " "))))

(defn longest-width
  "Takes collection of strings `xs` and returns the count of the longest
  one."
  [xs]
  ;; Assumes strings and uses simple count. This could be improved in
  ;; the future to accept Fipp primitives and correctly
  ;; count :escaped, :pass, etc.
  (apply max (map count xs)))

(defn align-cols
  "Takes 2d collection `rows` and makes the columns equal size.

  Example:

  (align-cols
   [[\"abc\"\"1\"]
    [\"d\" \"efghij\"]])
  =>
  ((\"abc\" \"1     \")
   (\"d  \" \"efghij\"))
  "
  [rows]
  (let [cols (transpose rows)
        col-widths (mapv longest-width cols)]
    (map #(map right-pad % col-widths) rows)))
