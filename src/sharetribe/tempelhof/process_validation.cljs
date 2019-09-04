(ns sharetribe.tempelhof.process-validation
  (:require [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.string :as str]
            [expound.alpha :as expound]
            [phrase.alpha :as phrase :refer [defphraser]]
            [chalk]
            [sharetribe.tempelhof.spec]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.tempelhof.spec :as tempelhof.spec]))

;; Message formatting for specific key specs
;;

(expound/defmsg :tempelhof/tx-process "Process should be defined as a map. Example: {:format :v3 :transitions [ ... ]}")

;; :tx-process/format - default message ok

(expound/defmsg :tx-process/transitions ":transitions should be collection of transitions with unique names. Example: :transitions [ ... ]")
(expound/defmsg :tx-process/notifications ":notifications should be collection of notifications with unique names. Example: :notifications [ ... ]")

;; Transitions

(expound/defmsg :tx-process/transition "Transition should be defined as a map. Example: {:name :transition/my-transition ... }")
(expound/defmsg :tx-process.transition/name ":name should be a keyword that uniquely names the transition in the process. Example: :transition/request")
(expound/defmsg :tx-process.transition/to ":to should be a keyword that references a state in the process. Example: :state/requested")
(expound/defmsg :tx-process.transition/from ":from should be a keyword that references a state in the process. It can be left out if this is an initial transition. Example: :state/requested")
(expound/defmsg :tx-process.transition/actions ":actions should be collection of actions. Example: :actions [ ... ]")

;; :tx-process.transition/actor - ok as default
;; :tx-process.transition/at - default is probably better that anything generic

;; Actions

(expound/defmsg :tx-process.transition/action "Transition's action should be defined as an action description map. Example: {:name :action/action-name :config {:config-property \"configuration value\"}}")

;; :tx-process.action/name - default message ok

(expound/defmsg :tx-process.action/config ":config should be a map with configuration properties specific to the action. Example: :config {:config-property \"configuration value\"}")

;; Notifications

(expound/defmsg :tx-process/notification "Notification should be defined as a map. Example: {:name :notification/my-notification ... }")
(expound/defmsg :tx-process.notification/name ":name should be a keyword that uniquely names the notification in the process. Example: :notification/my-notification-name")
(expound/defmsg :tx-process.notification/on ":on should be keyword that references a state in the process. Example :state/requested")

;; :tx-process.notification/to - default message ok

(expound/defmsg :tx-process.notification/template ":template should be a keyword that matches the corresponding email template name. Example: :new-booking-request")

;; :tx-process.notification/at - default is probably better that anything generic

(expound/defmsg :tx-process.notification/on-transition-name "Notification :on should point to an existing transition name.")


#_(defmethod exception/format-exception :tx-process/invalid-process [_ _ {:keys [tx-process spec]}]
  (let [printer (expound/custom-printer {:print-specs? false
                                         :theme :figwheel-theme})
        explain-data (s/explain-data spec tx-process)
        validation-errors (with-out-str (printer explain-data))]
    (str "The process description is not valid\n\n"
         validation-errors)))

(defn- expound-error-str [tx-process spec]
  (let [printer (expound/custom-printer {:print-specs? false
                                         :theme :none})
        explain-data (s/explain-data spec tx-process)
        validation-errors (with-out-str (printer explain-data))]
    validation-errors))


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


;; Time expressions
;;

;; TODO!!!


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
        problem (first problems)]
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

