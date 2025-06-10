(ns sharetribe.tempelhof.process-validation
  "User friendly error reporting for process validation errors."
  (:require [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.string :as str]
            [phrase.alpha :as phrase :refer [defphraser]]
            [chalk]
            [sharetribe.tempelhof.spec]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.tempelhof.spec :as tempelhof.spec]))

(defn- location
  "Pull out location info for the given form / process description
  part."
  [part]
  (let [loc (select-keys (meta part) [:row :col])]
    (when (seq loc) loc)))

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

(defphraser #(contains? % missing-key)
  [{:keys [tx-process]} {:keys [val] :as problem} missing-key]
  {:msg (str "Invalid " (config-type problem) ". "
             "Missing mandatory key " missing-key ".")
   :loc (find-first-loc tx-process problem)})

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

(defphraser boolean?
  [{:keys [tx-process]} {:keys [val] :as problem}]
  {:msg (str "Invalid " (config-type problem) ". "
             (invalid-key problem) " must be a boolean value true or false. "
             "You gave: " (invalid-val val))
   :loc (find-first-loc tx-process problem)})

;; Process
;;

(defphraser #{:v3}
  [{:keys [tx-process]} {:keys [val]}]
  {:msg (str "The process :format must be :v3 instead of " (pr-str val) ".")
   :loc nil})

;; Transitions
;;

(defphraser tempelhof.spec/transition-has-either-actor-or-at?
  [_ {:keys [val]}]
  {:msg (str "Invalid transition " (:name val)
             ". You must specify exactly one of :actor or :at.")
   :loc (location val)})

(defphraser tempelhof.spec/unique-transition-names?
  [{:keys [tx-process]} _]
  (let [tr-name->count (->> tx-process :transitions (map :name) frequencies)
        invalid-trs (filter (fn [{:keys [name]}]
                              (> (get tr-name->count name) 1))
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

(defphraser tempelhof.spec/all-states-reachable?
  [{:keys [tx-process]} {:keys [val] :as problem}]
  (let [trs (:transitions tx-process)
        state (first (tempelhof.spec/sorted-unreachable-states trs))
        tr (first (filter #(= state (:from %)) trs))]
    {:msg (str "Unreachable state: " state)
     :loc (location tr)}))

(defphraser tempelhof.spec/transition-with-trusted-context-if-privileged-actions?
  [{:keys [tx-process]} {:keys [val] :as problems}]
  (let [offending-actions (tempelhof.spec/privileged-actions val)]
    {:msg (str "Invalid transition " (:name val)
               ".\nActions that require trusted context have been defined but the transition is not marked as privileged"
               ". Either add :privileged? true to transition properties or set actor to :actor.role/operator."
               "\nActions that require privileged context are: "
               (str/join ", " offending-actions))
     :loc (location val)}))

;; Actions
;;

(defphraser tempelhof.spec/known-action-name?
  [{:keys [tx-process]} {:keys [val] :as problem}]
  {:msg (str "Unknown action name: " (invalid-val val) ". Available actions are:\n"
             (->> tempelhof.spec/action-names
                  sort
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

(defphraser tempelhof.spec/notification-on-is-valid-transition-name?
  [{:keys [tx-process]} _]
  (let [tr-names (->> tx-process :transitions (map :name) set)
        invalid-notifications (remove (fn [n] (contains? tr-names (:on n)))
                                      (:notifications tx-process))]
    (map (fn [n]
           {:msg (str "Invalid notification " (:name n) ". "
                      "The value of :on must point to an existing transition.\n"
                      "The process doesn't define a transition by name: " (:on n) ".")
            :loc (location n)})
         invalid-notifications)))

(defphraser tempelhof.spec/unique-notification-names?
  [{:keys [tx-process]} _]
  (let [n-name->count (->> tx-process :notifications (map :name) frequencies)
        invalid (filter (fn [{:keys [name]}]
                          (> (get n-name->count name) 1))
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

(defphraser tempelhof.spec/valid-transitions-in-transition-timepoints?
  [{:keys [tx-process]} _]
  (let [invalid-timepoints (tempelhof.spec/invalid-transitions-in-transition-timepoints tx-process)]
    (map (fn [n]
           {:msg (str "Unknown transition " (:ref n) " used in time expression of transition " (:source n) " :at")
            :loc (location (:at n))})
         invalid-timepoints)))

(defphraser tempelhof.spec/valid-states-in-transition-timepoints?
  [{:keys [tx-process]} _]
  (let [invalid-timepoints (tempelhof.spec/invalid-states-in-transition-timepoints tx-process)]
    (map (fn [n]
           {:msg (str "Unknown state " (:ref n) " used in time expression of transition " (:source n) " :at")
            :loc (location (:at n))})
         invalid-timepoints)))

(defphraser tempelhof.spec/valid-transitions-in-notification-timepoints?
  [{:keys [tx-process]} _]
  (let [invalid-timepoints (tempelhof.spec/invalid-transitions-in-notification-timepoints tx-process)]
    (map (fn [n]
           {:msg (str "Unknown transition " (:ref n) " used in time expression of notification " (:source n) " :at")
            :loc (location (:at n))})
         invalid-timepoints)))

(defphraser tempelhof.spec/valid-states-in-notification-timepoints?
  [{:keys [tx-process]} _]
  (let [invalid-timepoints (tempelhof.spec/invalid-states-in-notification-timepoints tx-process)]
    (map (fn [n]
           {:msg (str "Unknown state " (:ref n) " used in time expression of notification " (:source n) " :at")
            :loc (location (:at n))})
         invalid-timepoints)))

;; Actor validation
;;

(defphraser tempelhof.spec/valid-initial-transition-actor?
  [{:keys [tx-process]} _]
  (let [invalid-transitions (tempelhof.spec/invalid-actor-in-initial-transitions tx-process)]
    (map (fn [t]
           {:msg (str "Invalid transition " (:name t) ". "
                      "The value of :actor must be :actor.role/customer or :actor.role/provider for all initial transitions.")
            :loc (location t)})
         invalid-transitions)))

;; Not sure if this is a good idea?... But if it is it should be moved
;; to a util lib.
(def error-arrow (.bold.red chalk "\u203A"))

(defn- error-report
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

(defn- phrase-problem
  "Phrases a spec problem and returns a sequence of error descriptions
  for the problem."
  [ctx problem]
  (let [errors (phrase/phrase ctx problem)]
    ;; Let's give phrasers the freedom to return either a plain map or
    ;; a seq of error descriptions because most problems map to
    ;; exactly one error description.
    (if (sequential? errors) errors [errors])))

(defn- format-exception
  "Format validation exception as error report string."
  [{:keys [tx-process spec] :as data}]
  (let [problems (-> (s/explain-data spec tx-process)
                     :cljs.spec.alpha/problems)
        errors (mapcat #(phrase-problem data %) problems)
        total-errors (count errors)]

    (apply str
           (str "The process description is not valid. "
                "Found " total-errors (if (= 1 total-errors)
                                        " error.\n"
                                        " errors.\n"))
           (map-indexed (partial error-report total-errors) errors))))

(defmethod exception/format-exception :tx-process/invalid-process [_ _ {:keys [tx-process spec] :as data}]
  (format-exception data))

(defn validate!
  "Validates a v3 process map. Throws an exception if the process is
  invalid. Returns the process unmodified when it is valid."
  [tx-process]
  (when-not (s/valid? :tempelhof/tx-process tx-process)
    (exception/throw! :tx-process/invalid-process
                      {:tx-process tx-process
                       :spec (s/spec :tempelhof/tx-process)}))
  tx-process)

;; :tx-process/parse-error is thrown when reading the edn string fails
;; because of syntax error.
(defmethod exception/format-exception :tx-process/parse-error [_ _ {:keys [msg loc]}]
  (str "Failed to parse the process.\n"
       (error-report 1 0 {:msg msg :loc loc})))


(comment

  ;; To debug and improve phrasing it's immensely useful to capture
  ;; the problems as they are. To do that, eval the atom and defmethod
  ;; below.
  (def d (atom nil))

  (defmethod exception/format-exception :tx-process/invalid-process [_ _ {:keys [tx-process spec] :as data}]
    (reset! d data)
    (format-exception data))

  (let [data @d
        {:keys [tx-process spec]} data
        problems (:cljs.spec.alpha/problems (s/explain-data spec tx-process))
        problem (first problems)
        {:keys [val]} problem]
    problems)
  )
