(ns sharetribe.tempelhof.process-validation
  (:require [clojure.spec.alpha :as s]
            [expound.alpha :as expound]
            [sharetribe.tempelhof.spec]
            [sharetribe.flex-cli.exception :as exception]))

;; Message formatting for specific key specs
;;

(expound/defmsg :tempelhof/tx-process "Process should be defined as a map. Example: {:format :v3 :transitions [ ... ]}")

;; :tx-process/format - default message ok

(expound/defmsg :tx-process/transitions ":transitions should be collection of transitions. Example: :transitions [ ... ]")
(expound/defmsg :tx-process/notifications ":notifications should be collection of notifications. Example: :notifications [ ... ]")

;; Transitions

(expound/defmsg :tx-process/transition "Transition should be defined as a map. Example: {:name :transition/my-transition ... }")
(expound/defmsg :tx-process.transition/name ":name should be a keyword that uniquely names the transition in the process. Example: :transition/request")
(expound/defmsg :tx-process.transition/to ":to should be a keyword that references a state in the process. Example: :state/requested")
(expound/defmsg :tx-process.transition/from ":from should be a keyword that references a state in the process. It can be left out if this is an initial transition. Example: :state/requested")
(expound/defmsg :tx-process.transition/actions ":actions should be collection of actions. Example: :actions [ ... ]")

;; :tx-process.transition/actor - ok as default
;; :tx-process.transition/at - TODO: port time expression validation from Tegel

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

;; :tx-process.notification/at - TODO: port time expression validation from Tegel


(defmethod exception/format-exception :tx-process/invalid-process [_ _ {:keys [tx-process spec]}]
  (let [printer (expound/custom-printer {:print-specs? false
                                         :theme :figwheel-theme})
        explain-data (s/explain-data spec tx-process)
        validation-errors (with-out-str (printer explain-data))]
    (str "The process description is not valid\n\n"
         validation-errors)))

(defn validate!
  "Validates a v3 process map. Throws an exception if the process is
  invalid. Returns the process unmodified when it is valid."
  [tx-process]
  (when-not (s/valid? :tempelhof/tx-process tx-process)
    (exception/throw! :tx-process/invalid-process
                      {:tx-process tx-process
                       :spec (s/spec :tempelhof/tx-process)}))
  tx-process)
