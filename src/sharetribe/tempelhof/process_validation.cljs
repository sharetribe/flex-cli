(ns sharetribe.tempelhof.process-validation
  (:require [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.string :as str]
            [phrase.alpha :as phrase :refer [defphraser]]
            [chalk]
            [sharetribe.tempelhof.spec]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.tempelhof.spec :as tempelhof.spec]))

;; TODO Error messages for invalid .edn files?

(defn- location
  "Pull out location info for the given form / process description
  part."
  [part]
  (let [loc (select-keys (meta part) [:row :col])]
    (if (seq loc) loc nil)))

(defn- find-first-loc
  "Find the most accurate possible location for a problem. Primitive
  values don't have locations so we must look into parent form
  instead. This fn is intended for generic error phrasers that don't
  know which part in the process they are reporting. Use `location`
  when you know from context what you are reporting against."
  [tx-process problem]
  (let [{:keys [in]} problem
        node (get-in tx-process in)
        parent (get-in tx-process (drop-last in))]
    (or (location node)
        (location parent))))

(defn- config-type
  "Return a human readable name for the type of config the problem
  relates to, e.g. transition, notification or action."
  [problem]
  (let [{:keys [path]} problem
        [f s] path]
    (cond
      (= [:transitions :actions] [f s]) "action"
      (= [:transitions] [f]) "transition"
      (= [:notifications] [f]) "notification"
      :else "process")))

(defn- invalid-key
  "Return the key that the problem is about."
  [problem]
  (-> problem :path last))

(defn- invalid-val
  "Return a string version of the invalid primitive value. If the
  invalid value is a string we add extra \"\" to clearly differentiate
  from keywords."
  [val]
  (if (string? val)
    (str "\"" val "\"")
    (str val)))

(defphraser :default
  [{:keys [tx-process]} {:keys [val] :as problem}]
  {:msg (str "Invalid " (config-type problem)
             ". Unspecified validation error. :(.\n"
             "Key: " (invalid-key problem) "\n"
             "Value: " (invalid-val val))
   :loc (find-first-loc tx-process problem)})

;; Fallback phraser for missing mandatory keys. Seeing output from
;; this means we have a missing phraser.
;; TODO Can we make this handle all?
(defphraser #(contains? % missing-key)
  [_ {:keys [val]} missing-key]
  {:msg (str "Missing mandatory key " missing-key ".")
   :loc (location val)})

(defphraser keyword?
  [{:keys [tx-process]} {:keys [val] :as problem}]
  {:msg (str "Invalid " (config-type problem) ". "
             (invalid-key problem) " must be a keyword. "
             "You gave: " (invalid-val val))
   :loc (find-first-loc tx-process problem)})

(defphraser simple-keyword?
  [{:keys [tx-process]} {:keys [val] :as problem}]
  {:msg (str "Invalid " (config-type problem) ". "
             (invalid-key problem) " must be a plain, unqualified keyword. "
             "You gave: " (invalid-val val))
   :loc (find-first-loc tx-process problem)})


;; Process
;;

(defphraser #{:v3}
  [{:keys [tx-process]} {:keys [val]}]
  {:msg (str "The process :format must be :v3 instead of " (pr-str val) ".")
   :loc nil})

(defphraser #(contains? % missing-key)
  {:via [:tempelhof/tx-process]}
  [_ {:keys [val]} missing-key]
  {:msg (str "Missing mandatory key. The process description must specify " missing-key ".")
   :loc nil})

;; Transitions
;;

(defphraser #(contains? % missing-key)
  {:via [:tempelhof/tx-process :tx-process/transitions :tx-process/transition]}
  [_ {:keys [val]} missing-key]
  {:msg (str "Missing mandatory key. Transitions must specify " missing-key ".")
   :loc (location val)})

(defphraser tempelhof.spec/transition-has-either-actor-or-at?
  [_ {:keys [val]}]
  {:msg (str "Invalid transition " (:name val)
             ". You must specify exactly one of :actor or :at.")
   :loc (location val)})

(defphraser tempelhof.spec/unique-transition-names?
  [{:keys [tx-process]} _]
  (let [tr-names (->> tx-process :transitions (map :name) frequencies)
        invalid-trs (filter (fn [{:keys [name]}]
                              (> (get tr-names name) 1))
                            (:transitions tx-process))]
    (map (fn [tr]
           {:msg (str "Invalid transition " (:name tr) ". "
                      "Transition names must be unique.")
            :loc (location tr)})
         invalid-trs)))

(defphraser tempelhof.spec/valid-transition-role?
  [{:keys [tx-process]} {:keys [val] :as problem}]
  {:msg (str "Transition actor role must be one of: "
             (str/join ", " tempelhof.spec/transition-roles) ".\n"
             "You gave: " (invalid-val val) ".")
   :loc (find-first-loc tx-process problem)})

;; Actions
;;

(defphraser #(contains? % missing-key)
  {:via [:tempelhof/tx-process :tx-process/transitions :tx-process/transition :tx-process.transition/actions :tx-process.transition/action]}
  [_ {:keys [val]} missing-key]
  {:msg (str "Missing mandatory key. Actions must specify " missing-key ".")
   :loc (location val)})

(defphraser tempelhof.spec/known-action-name?
  [{:keys [tx-process]} {:keys [val] :as problem}]
  {:msg (str "Unknown action name: " (invalid-val val) ". Available actions are:\n"
             (->> tempelhof.spec/action-names
                  (map #(str "  " %))   ; Padding
                  (str/join "\n")))     ; Print one per line
   :loc (find-first-loc tx-process problem)})

(defphraser map?
  {:via [:tempelhof/tx-process :tx-process/transitions :tx-process/transition :tx-process.transition/actions :tx-process.transition/action :tx-process.action/config]}
  [{:keys [tx-process]} problem]
  {:msg "Invalid action. The value for :config must be a map."
   :loc (find-first-loc tx-process problem)})

;; Notifications
;;

(defphraser #(contains? % missing-key)
  {:via [:tempelhof/tx-process :tx-process/notifications :tx-process/notification]}
  [_ {:keys [val]} missing-key]
  {:msg (str "Missing mandatory key. Notifications must specify " missing-key ".")
   :loc (location val)})

(defphraser tempelhof.spec/notification-on-is-valid-transition-name?
  [{:keys [tx-process]} _]
  ;; TODO call stuff, parse process to print exactly which transition fails.
  (let [tr-names (->> tx-process :transitions (map :name) set)
        invalid-notifications (remove (fn [n] (contains? tr-names (:on n)))
                                      (:notifications tx-process))]
    (map (fn [n]
           {:msg (str "Invalid notification " (:name n) ". "
                      "The value of :on must point to an existing transition. "
                      "The process doesn't define transition by name: " (:on n) ".")
            :loc (location n)})
         invalid-notifications)))

(defphraser tempelhof.spec/unique-notification-names?
  [{:keys [tx-process]} _]
  (let [notification-names (->> tx-process :notifications (map :name) frequencies)
        invalid (filter (fn [{:keys [name]}]
                          (> (get notification-names name) 1))
                        (:notifications tx-process))]
    (map (fn [n]
           {:msg (str "Invalid notification " (:name n) ". "
                      "Notification names must be unique.")
            :loc (location n)})
         invalid)))


;; Time expressions
;;

;; TODO This is a bare bones minimum time expression validation that
;; doesn't offer any insights into why the expression is not valid
;; (did you misspell fn name? gave it wrong params? Used vector
;; instead of map?). However, to improve upon this requires to rewrite
;; the validation itself and/or replicate it here without spec!

(defphraser tempelhof.spec/valid-time-expression?
  [{:keys [tx-process]} {:keys [val] :as problem}]
  {:msg "Invalid time expression."
   :loc (find-first-loc tx-process problem)})


;; TODO remove me
(defonce d (atom nil))

;; Not sure if this is a good idea?... But if it is it should be moved
;; to a util lib.
(def error-arrow (.bold.red chalk "\u203A"))

(defn- error-report
  "Given an error description format is as a error report string (with
  multiple lines)"
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

(defn- phrase-problem
  "Phrases a spec problem and returns a sequence of error descriptions
  for the problem."
  [ctx problem]
  (let [errors (phrase/phrase ctx problem)]
    ;; Let's give phrasers the freedom to return either a plain map or
    ;; a seq of error descriptions because most problems map to
    ;; exactly one error description.
    (if (sequential? errors) errors [errors])))

(defmethod exception/format-exception :tx-process/invalid-process [_ _ {:keys [tx-process spec] :as data}]
  ;; TODO remove me
  (reset! d data)
  (let [problems (-> (s/explain-data spec tx-process)
                     :cljs.spec.alpha/problems)
        errors (mapcat #(phrase-problem data %) problems)
        total-errors (count errors)]

    (apply str
           (str "The process description is not valid. "
                "Found " total-errors " error(s).\n")
           (map-indexed (partial error-report total-errors) errors))))

;; TODO remove me
(comment
  (keys @d)

  (-> @d :tx-process meta)
  (-> @d :tx-process location)
  (let [data @d
        {:keys [tx-process spec]} data
        problems (:cljs.spec.alpha/problems (s/explain-data spec tx-process))
        problem (first problems)
        {:keys [val]} problem]
    #_(find-first-loc tx-process
                    (first (:cljs.spec.alpha/problems (s/explain-data spec tx-process))))
    #_(get-in tx-process (:in ))
    #_(find-first-loc tx-process problem)
    problems
    )

  )


(defn validate!
  "Validates a v3 process map. Throws an exception if the process is
  invalid. Returns the process unmodified when it is valid."
  [tx-process]
  (when-not (s/valid? :tempelhof/tx-process tx-process)
    (exception/throw! :tx-process/invalid-process
                      {:tx-process tx-process
                       :spec (s/spec :tempelhof/tx-process)}))
  tx-process)
