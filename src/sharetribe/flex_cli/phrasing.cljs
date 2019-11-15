(ns sharetribe.flex-cli.phrasing
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [phrase.alpha :as phrase :refer [defphraser]]
            [sharetribe.flex-cli.exception :as exception]
            [chalk]))

(defn- tx-process-config-type [problem]
  (let [{:keys [path via]} problem
        [f s] path]
    (cond
      (= [:transitions :actions] [f s]) "action"
      (= [:transitions] [f]) "transition"
      (= [:notifications] [f]) "notification"
      :else "process")))

(defn- context-config-type [problem]
  (let [{:keys [path via]} problem
        [f s] path]
    (cond
      (= [:recipient] [f]) "recipient"
      (= [:marketplace] [f]) "marketplace"
      (= [:recipient-role] [f]) "recipient-role"
      (= [:other-party] [f]) "other-party"
      (= [:transaction :listing] [f s]) "listing"
      (= [:transaction :customer] [f s]) "customer"
      (= [:transaction :delayed-transition] [f s]) "delayed-transition"
      (= [:transaction] [f]) "transaction"
      :else "context")))

(defn- config-type
  "Return a human readable name for the type of config the problem
  relates to, e.g. transition, notification or action."
  [problem]
  (let [{:keys [via]} problem]
    (case (-> problem :via first)
      :context/transaction-transition (context-config-type problem)
      (tx-process-config-type problem))))

(def error-arrow (.bold.red chalk "\u203A"))

(defn- phrase-problem
  "Phrases a spec problem and returns a sequence of error descriptions
  for the problem."
  [ctx problem]
  (let [errors (phrase/phrase ctx problem)]
    ;; Let's give phrasers the freedom to return either a plain map or
    ;; a seq of error descriptions because most problems map to
    ;; exactly one error description.
    (if (sequential? errors) errors [errors])))

(defn invalid-key
  "Return the key that the problem is about."
  [problem]
  (-> problem :path last))

(defn invalid-val
  "Return a string version of the invalid primitive value. If the
  invalid value is a string we add extra \"\" to clearly differentiate
  from keywords."
  [val]
  (if (string? val)
    (str "\"" val "\"")
    (str val)))

(defn location
  "Pull out location info for the given form / process description
  part."
  [part]
  (let [loc (select-keys (meta part) [:row :col])]
    (when (seq loc) loc)))

(defn find-first-loc
  "Find the most accurate possible location for a problem. Primitive
  values don't have locations so we must look into parent form
  instead. This fn is intended for generic error phrasers that don't
  know which part in the process they are reporting. Use `location`
  when you know from context what you are reporting against."
  [data problem]
  (let [{:keys [in]} problem
        node (get-in data in)
        parent (get-in data (drop-last in))]
    (or (location node)
        (location parent))))

(defphraser :default
  [{:keys [data]} {:keys [val path] :as problem}]
  (println "================")
  (println :val (:val problem))
  (println :path (:path problem))
  (println :pred (:pred problem))
  (println :via (:via problem))
  (println :in (:in problem))
  (println "================")
  {:msg (str "Invalid " (config-type problem)
             ". Unspecified validation error. :(.\n"
             "Key: " (invalid-key problem) "\n"
             "Value: " (invalid-val val) "\n"
             "Path: " (str/join "." (map name path)))
   :loc (find-first-loc data problem)})

(defn error-report
  "Given an error description map, format it as an error report string
  (a multi line string)."
  [total index error]
  (let [{:keys [loc msg]} error
        {:keys [row col]} loc
        header (if loc
                 (str (inc index) "/" total
                      " [at line " row ", column " col "]"
                      ":\n")
                 (str (inc index) "/" total
                      ":\n"))]
    (str "\n" error-arrow " " header msg "\n")))

(defn format-validation-exception
  "Format validation exception as error report string."
  [{:keys [data spec] :as exception-data}]
  (let [problems (-> (s/explain-data spec data)
                     :cljs.spec.alpha/problems)
        errors (mapcat #(phrase-problem exception-data %) problems)
        total-errors (count errors)]
    (apply str
           (str "Found " total-errors (if (= 1 total-errors)
                                        " error.\n"
                                        " errors.\n"))
           (map-indexed (partial error-report total-errors) errors))))
