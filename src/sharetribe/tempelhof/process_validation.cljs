(ns sharetribe.tempelhof.process-validation
  "User friendly error reporting for process validation errors."
  (:require [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.string :as str]
            [phrase.alpha :as phrase :refer [defphraser]]
            [chalk]
            [sharetribe.tempelhof.spec]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.flex-cli.phrasing :as phrasing]
            [sharetribe.tempelhof.spec :as tempelhof.spec]))

(defphraser #(contains? % missing-key)
  [{:keys [tx-process]} {:keys [val] :as problem} missing-key]
  {:msg (str "Invalid " (phrasing/config-type problem) ". "
             "Missing mandatory key " missing-key ".")
   :loc (phrasing/find-first-loc tx-process problem)})

(defphraser keyword?
  [{:keys [tx-process]} {:keys [val] :as problem}]
  {:msg (str "Invalid " (phrasing/config-type problem) ". "
             (phrasing/invalid-key problem) " must be a keyword. "
             "You gave: " (phrasing/invalid-val val))
   :loc (phrasing/find-first-loc tx-process problem)})

(defphraser simple-keyword?
  [{:keys [tx-process]} {:keys [val] :as problem}]
  {:msg (str "Invalid " (phrasing/config-type problem) ". "
             (phrasing/invalid-key problem) " must be a plain, unqualified keyword. "
             "You gave: " (phrasing/invalid-val val))
   :loc (phrasing/find-first-loc tx-process problem)})


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
   :loc (phrasing/location val)})

(defphraser tempelhof.spec/unique-transition-names?
  [{:keys [tx-process]} _]
  (let [tr-name->count (->> tx-process :transitions (map :name) frequencies)
        invalid-trs (filter (fn [{:keys [name]}]
                              (> (get tr-name->count name) 1))
                            (:transitions tx-process))]
    (map (fn [tr]
           {:msg (str "Invalid transition " (:name tr) ". "
                      "Transition names must be unique.")
            :loc (phrasing/location tr)})
         invalid-trs)))

(defphraser tempelhof.spec/valid-transition-role?
  [{:keys [tx-process]} {:keys [val] :as problem}]
  {:msg (str "Transition actor role must be one of: "
             (str/join ", " tempelhof.spec/transition-roles) ".\n"
             "You gave: " (phrasing/invalid-val val) ".")
   :loc (phrasing/find-first-loc tx-process problem)})

(defphraser tempelhof.spec/all-states-reachable?
  [{:keys [tx-process]} {:keys [val] :as problem}]
  (let [trs (:transitions tx-process)
        state (first (tempelhof.spec/sorted-unreachable-states trs))
        tr (first (filter #(= state (:from %)) trs))]
    {:msg (str "Unreachable state: " state)
     :loc (phrasing/location tr)}))

;; Actions
;;

(defphraser tempelhof.spec/known-action-name?
  [{:keys [tx-process]} {:keys [val] :as problem}]
  {:msg (str "Unknown action name: " (phrasing/invalid-val val) ". Available actions are:\n"
             (->> tempelhof.spec/action-names
                  (map #(str "  " %))   ; Padding
                  (str/join "\n")))     ; Print one per line
   :loc (phrasing/find-first-loc tx-process problem)})

(defphraser map?
  {:via [:tempelhof/tx-process :tx-process/transitions :tx-process/transition :tx-process.transition/actions :tx-process.transition/action :tx-process.action/config]}
  [{:keys [tx-process]} problem]
  {:msg "Invalid action. The value for :config must be a map."
   :loc (phrasing/find-first-loc tx-process problem)})

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
            :loc (phrasing/location n)})
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
            :loc (phrasing/location n)})
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
   :loc (phrasing/find-first-loc tx-process problem)})

(defphraser tempelhof.spec/valid-transitions-in-transition-timepoints?
  [{:keys [tx-process]} _]
  (let [invalid-timepoints (tempelhof.spec/invalid-transitions-in-transition-timepoints tx-process)]
    (map (fn [n]
           {:msg (str "Unknown transition " (:ref n) " used in time expression of transition " (:source n) " :at")
            :loc (phrasing/location (:at n))})
         invalid-timepoints)))

(defphraser tempelhof.spec/valid-states-in-transition-timepoints?
  [{:keys [tx-process]} _]
  (let [invalid-timepoints (tempelhof.spec/invalid-states-in-transition-timepoints tx-process)]
    (map (fn [n]
           {:msg (str "Unknown state " (:ref n) " used in time expression of transition " (:source n) " :at")
            :loc (phrasing/location (:at n))})
         invalid-timepoints)))

(defphraser tempelhof.spec/valid-transitions-in-notification-timepoints?
  [{:keys [tx-process]} _]
  (let [invalid-timepoints (tempelhof.spec/invalid-transitions-in-notification-timepoints tx-process)]
    (map (fn [n]
           {:msg (str "Unknown transition " (:ref n) " used in time expression of notification " (:source n) " :at")
            :loc (phrasing/location (:at n))})
         invalid-timepoints)))

(defphraser tempelhof.spec/valid-states-in-notification-timepoints?
  [{:keys [tx-process]} _]
  (let [invalid-timepoints (tempelhof.spec/invalid-states-in-notification-timepoints tx-process)]
    (map (fn [n]
           {:msg (str "Unknown state " (:ref n) " used in time expression of notification " (:source n) " :at")
            :loc (phrasing/location (:at n))})
         invalid-timepoints)))

(defmethod exception/format-exception :tx-process/invalid-process [_ _ data]
  (str "The process description is not valid. " (phrasing/format-validation-exception data)))

(defn validate!
  "Validates a v3 process map. Throws an exception if the process is
  invalid. Returns the process unmodified when it is valid."
  [tx-process]
  (when-not (s/valid? :tempelhof/tx-process tx-process)
    (exception/throw! :tx-process/invalid-process
                      {:data tx-process
                       :spec (s/spec :tempelhof/tx-process)}))
  tx-process)

;; :tx-process/parse-error is thrown when reading the edn string fails
;; because of syntax error.
(defmethod exception/format-exception :tx-process/parse-error [_ _ {:keys [msg loc]}]
  (str "Failed to parse the process.\n"
       (phrasing/error-report 1 0 {:msg msg :loc loc})))


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
