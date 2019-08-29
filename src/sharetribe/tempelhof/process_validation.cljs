(ns sharetribe.tempelhof.process-validation
  (:require [clojure.spec.alpha :as s]
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


(defn- location [part]
  (meta part))

(defphraser :default
  [{:keys [tx-process spec]} problem]
  {:msg "Unspecified process error. :("
   :loc (location tx-process)})

;; Process
;;

(defphraser #{:v3}
  [{:keys [tx-process]} {:keys [val]}]
  {:msg (str "The process :format must be :v3 instead of " (pr-str val) ".")
   :loc nil})

;; Fallback phraser for missing mandatory keys. Seeing output from
;; this means we have a missing phraser.
(defphraser #(contains? % missing-key)
  [_ {:keys [val]} missing-key]
  {:msg (str "Missing mandatory key " missing-key ".")
   :loc (location val)})

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

(defphraser tempelhof.spec/notification-on-is-valid-transition-name?
  [{:keys [tx-process]} {:keys [val] :as problem}]
  ;; TODO call stuff, parse process to print exactly which transition fails.
  {:msg "Notification's :on refers to a transition that does not exist."
   :loc (location tx-process) ;; Foo location, just testing
   })

;; Actions
;;

(defphraser #(contains? % missing-key)
  {:via [:tempelhof/tx-process :tx-process/transitions :tx-process/transition :tx-process.transition/actions :tx-process.transition/action]}
  [_ {:keys [val]} missing-key]
  {:msg (str "Missing mandatory key. Actions must specify " missing-key ".")
   :loc (location val)})

;; Notifications
;;

(defphraser #(contains? % missing-key)
  {:via [:tempelhof/tx-process :tx-process/notifications :tx-process/notification]}
  [_ {:keys [val]} missing-key]
  {:msg (str "Missing mandatory key. Notifications must specify " missing-key ".")
   :loc (location val)})


(defonce d (atom nil))

;; Not sure if this is a good idea?...
(def error-arrow (.bold.red chalk "\u203A"))

(defn- error-report [total index error]
  (let [{:keys [loc msg]} error
        {:keys [row col]} loc
        header (if loc
                 (str (inc index) "/" total
                      " [at line " row ", column " col "]"
                      ":\n")
                 (str (inc index) "/" total
                      ":\n"))]
    (str "\n" error-arrow " " header msg "\n")))

(defmethod exception/format-exception :tx-process/invalid-process [_ _ {:keys [tx-process spec] :as data}]
  (reset! d data)
  (let [problems (-> (s/explain-data spec tx-process)
                     :cljs.spec.alpha/problems)
        errors (map #(phrase/phrase data %) problems)
        total-errors (count problems)]

    (apply str
           (str "The process description is not valid. "
                "Found " total-errors " error(s).\n")
           (map-indexed (partial error-report total-errors) errors))))


(comment
  @txp
  @s
  (keys @d)

  (let [data @d
        {:keys [tx-process spec]} data]
    (meta tx-process)
    #_(meta (first (:transitions tx-process)))
    (:cljs.spec.alpha/problems (s/explain-data spec tx-process))
    )

  (def s "
{:format :v2
 :transitions []
 :notifications []}")

  (meta (edamame.core/parse-string s))
  (meta (-> (edamame.core/parse-string s) :format))

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
