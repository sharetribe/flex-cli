(ns sharetribe.flex-cli.render
  (:refer-clojure :exclude [eval])
  (:require [chalk]
            [clojure.string :as str]))

(defn extract-opts [content]
  (let [[first & rest :as all] content]
    (if (map? first)
      [first rest]
      [nil all])))

(defn format [format-opts s]
  (if-let [chalk-builder
           (when (seq format-opts)
             (goog.object/getValueByKeys chalk (clj->js format-opts)))]
    (chalk-builder s)
    s))

(defn br [_]
  ["\n"])

(defn line [content]
  (let [[opts content] (extract-opts content)]
    [(format (:format opts) (str/join content))
     "\n"]))

(defn eval-tag
  "Evaluate a built-in tag"
  [tag content]
  (case tag
    :line (line content)
    :br (br content)))

;; TODO Can be improved with trampoline
(declare eval-many)

(defn eval-one
  "Evaluate a single component. Returns a collection, because evaluating
  a single component may result in many child components."
  [comp]
  (let [[f & xs] comp]
    (if (keyword? f)
      (eval-tag f xs)
      (eval-many (apply f xs)))))

(defn eval-many
  "Evaluate collection of components. Returns a collection of evaluated
  components."
  [comps]
  (mapcat eval-one comps))

(defn eval
  "Evaluate components. Returns a string."
  [comps]
  (apply str (eval-many comps)))

(defn render
  "Evaluate and print components."
  [comps]
  (print (eval comps)))
