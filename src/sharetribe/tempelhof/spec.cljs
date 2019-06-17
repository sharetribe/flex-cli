(ns sharetribe.tempelhof.spec
  (:require [clojure.spec.alpha :as s]))

;; Transaction process spec
;;

(s/def :tx-process.transition/actor #{:actor.role/customer
                                      :actor.role/provider
                                      :actor.role/operator
                                      :actor.role/system})
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


(s/def :tx-process.action/name #{:action.initializer/init-listing-tx
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

(s/def :tx-process.action/config map?)
(s/def :tx-process.transition/action (s/keys :req-un [:tx-process.action/name]
                                             :opt-un [:tx-process.action/config]))

(s/def :tx-process.transition/actions
  (s/coll-of :tx-process.transition/action))

;; TODO Port time expression spec
;; (s/def :tx-process.transition/at ::tegel.time/expression)

(defn- transition-has-either-actor-or-at? [transition]
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

(s/def :tx-process/transitions
  (s/coll-of :tx-process/transition))

(s/def :tx-process.notification/after-transitions
  (s/every keyword?
           :kind set?
           :into #{}))

(s/def :tx-process.notification/name keyword?)
(s/def :tx-process.notification/on keyword?)

(s/def :tx-process.notification/to
  #{:actor.role/customer :actor.role/provider})

(s/def :tx-process.notification/template
  simple-keyword?)

(s/def :tx-process/notification
  (s/keys :req-un [:tx-process.notification/name
                   :tx-process.notification/on
                   :tx-process.notification/to
                   :tx-process.notification/template]
          :opt-un [:tx-process.notification/at]))

(s/def :tx-process/notifications
  (s/coll-of :tx-process/notification))

(s/def :tx-process/format #{:v3})

(s/def :tempelhof/tx-process
  (s/keys :req-un [:tx-process/format
                   :tx-process/transitions]
          :opt-un [:tx-process/notifications]))

