(ns sharetribe.tempelhof.spec
  (:require [clojure.spec.alpha :as s]
            [loom.graph :as loom.graph]
            [loom.alg :as loom.alg]))

;; Time expressions
;;

(s/def :tx-process.time/state-name (s/cat :state-name keyword?))
(s/def :tx-process.time/transition-name (s/cat :transition-name keyword?))

(s/def :tx-process.time/timepoint-args
  (s/or :tx-initiated (s/tuple #{:time/tx-initiated})
        :first-entered-state (s/cat :tx-process.time/timepoint-name #{:time/first-entered-state} :tx-process.time/timepoint-state-name :tx-process.time/state-name)
        :first-transitioned (s/cat :tx-process.time/timepoint-name #{:time/first-transitioned} :tx-process.time/timepoint-transition-name :tx-process.time/transition-name)
        :booking-start (s/tuple #{:time/booking-start})
        :booking-end (s/tuple #{:time/booking-end})
        :booking-display-start (s/tuple #{:time/booking-display-start})
        :booking-display-end (s/tuple #{:time/booking-display-end})))

(s/def :tx-process.time/timepoint
  (s/map-of #{:fn/timepoint}
            :tx-process.time/timepoint-args
            :count 1))

(s/def :tx-process.time/period
  (s/map-of #{:fn/period}
            (s/tuple string?) ;; period string format not checked
            :count 1))

(s/def :tx-process.time/timepoints
  (s/coll-of :tx-process.time/expression
             :kind vector?
             :min-count 1))

(s/def :tx-process.time/timepoint+periods
  (s/cat :timepoint :tx-process.time/expression
         :periods (s/+ :tx-process.time/period)))

(s/def :tx-process.time/expression
  (s/or :timepoint  :tx-process.time/timepoint

        :min (s/map-of #{:fn/min}
                       :tx-process.time/timepoints
                       :count 1)

        :plus (s/map-of #{:fn/plus}
                        :tx-process.time/timepoint+periods
                        :count 1)

        :minus (s/map-of #{:fn/minus}
                         :tx-process.time/timepoint+periods
                         :count 1)

        :ignore-if-past (s/map-of #{:fn/ignore-if-past}
                                  (s/coll-of :tx-process.time/expression
                                             :kind vector?
                                             :count 1)
                                  :count 1)))

(defn valid-time-expression? [expr]
  (s/valid? :tx-process.time/expression expr))

;; Transaction process spec
;;

(def transition-roles #{:actor.role/customer
                        :actor.role/provider
                        :actor.role/operator})

(defn valid-transition-role? [role]
  (contains? transition-roles role))

(s/def :tx-process.transition/actor valid-transition-role?)
(s/def :tx-process.transition/name keyword?)
(s/def :tx-process.transition/from keyword?)
(s/def :tx-process.transition/to keyword?)

;; Note: We may want to force migration to single actor by saying that
;;
;; multi actor processes are not valid in CLI.
;; (s/def :tx-process.transition/actor
;;   (s/or :single-actor #{:actor.role/customer
;;                         :actor.role/provider
;;                         :actor.role/operator}
;;         :multi-actor (s/every #{:actor.role/customer
;;                                 :actor.role/provider
;;                                 :actor.role/operator}
;;                               :kind set?
;;                               :into #{})))


(def action-names #{:action.initializer/init-listing-tx
                    :action/create-booking
                    :action/accept-booking
                    :action/decline-booking
                    :action/cancel-booking
                    :action/calculate-tx-total ;; backward compatibility
                    :action/calculate-tx-daily-total ;; deprecated
                    :action/calculate-tx-nightly-total ;;deprecated
                    :action/calculate-tx-daily-total-price
                    :action/calculate-tx-nightly-total-price
                    :action/calculate-tx-total-daily-booking-exclude-start
                    :action/calculate-tx-unit-total-price
                    :action/calculate-tx-two-units-total-price
                    :action/calculate-tx-provider-commission
                    :action/calculate-tx-customer-commission
                    :action/calculate-full-refund
                    :action/set-line-items-and-total
                    :action/set-negotiated-total-price
                    :action/post-review-by-customer
                    :action/post-review-by-provider
                    :action/publish-reviews
                    :action/reveal-customer-protected-data
                    :action/reveal-provider-protected-data
                    :action/stripe-create-charge
                    :action/stripe-create-charge-pi
                    :action/stripe-create-payment-intent
                    :action/stripe-confirm-payment-intent
                    :action/stripe-capture-payment-intent
                    :action/stripe-capture-charge
                    :action/stripe-capture-charge-pi
                    :action/stripe-refund-charge
                    :action/stripe-refund-payment
                    :action/stripe-create-payout
                    :action/update-protected-data
                    :action/fail})

(defn known-action-name? [name] (contains? action-names name))

(s/def :tx-process.action/name known-action-name?)

(s/def :tx-process.action/config map?)
(s/def :tx-process.transition/action (s/keys :req-un [:tx-process.action/name]
                                             :opt-un [:tx-process.action/config]))

(s/def :tx-process.transition/actions
  (s/coll-of :tx-process.transition/action))

(s/def :tx-process.transition/at valid-time-expression?)

(defn transition-has-either-actor-or-at? [transition]
  (or (and (:actor transition) (not (:at transition)))
      (and (not (:actor transition)) (:at transition))))

(s/def :tx-process/transition
  (s/and (s/keys :req-un [:tx-process.transition/name
                          :tx-process.transition/to]
                 :opt-un [:tx-process.transition/actions
                          :tx-process.transition/actor
                          :tx-process.transition/at
                          :tx-process.transition/from])
         transition-has-either-actor-or-at?))

(defn unique-transition-names? [transitions]
  (let [names (map :name transitions)]
    (= (count names) (count (set names)))))

(defn- loom-graph
  [transitions]
  (let [edges (map (fn [{:keys [from to] :as transition}]
                     [(or from ::initial-state) to])
                   transitions)]
    (apply loom.graph/digraph edges)))

(defn all-states-reachable? [transitions]
  (if (empty? transitions)
    true
    (let [g (loom-graph transitions)]
      (= (loom.graph/nodes g)
         (set (loom.alg/pre-traverse g ::initial-state))))))

(s/def :tx-process/transitions
  (s/and
   (s/coll-of :tx-process/transition)
   unique-transition-names?
   all-states-reachable?))

(s/def :tx-process.notification/name keyword?)
(s/def :tx-process.notification/on keyword?)

(s/def :tx-process.notification/to
  #{:actor.role/customer :actor.role/provider})

(s/def :tx-process.notification/template
  simple-keyword?)

(s/def :tx-process.notification/at valid-time-expression?)

(s/def :tx-process/notification
  (s/keys :req-un [:tx-process.notification/name
                   :tx-process.notification/on
                   :tx-process.notification/to
                   :tx-process.notification/template]
          :opt-un [:tx-process.notification/at]))

(defn unique-notification-names? [notifications]
  (let [names (map :name notifications)]
    (= (count names) (count (set names)))))

(s/def :tx-process/notifications
  (s/and
   (s/coll-of :tx-process/notification)
   unique-notification-names?))

(s/def :tx-process/format #{:v3})

(defn notification-on-is-valid-transition-name? [process]
  (let [names (->> process
                   :transitions
                   (map :name)
                   set)
        ons (->> process
                 :notifications
                 (map :on))]
    (every? #(names %) ons)))

(s/def :tempelhof/tx-process
  (s/and
   (s/keys :req-un [:tx-process/format
                    :tx-process/transitions]
           :opt-un [:tx-process/notifications])
   notification-on-is-valid-transition-name?))
