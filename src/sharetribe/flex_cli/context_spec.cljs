(ns sharetribe.flex-cli.context-spec
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

;; UUID
;;

(defn str-uuid? [x]
  ;; The default #uuid literal doesn't check it's content syntax, so
  ;; just a string check should be fine here.
  (string? x))

;; Decimal
;;

;; In the backend we check the amount to be an instance of BigDecimal,
;; but in JSON we only have JS numbers (i.e. floats).
(defn decimal? [x]
  (number? x))

;; Namespaced string
;;

(defn qualified-string?
  "Returns true if the string represents a qualified keyword, i.e. it
  has namespace part and name part separated by slash"
  [s]
  (boolean
   (when (string? s)
     (let [[ns name :as parts] (str/split s #"/")]
       (and (= 2 (count parts))
            (seq ns)
            (seq name))))))

;; Date
;;

(s/def :mail.date/year nat-int?)
(s/def :mail.date/month nat-int?)
(s/def :mail.date/day nat-int?)
(s/def :mail.date/hours nat-int?)
(s/def :mail.date/minutes nat-int?)
(s/def :mail.date/seconds nat-int?)
(s/def :mail.date/milliseconds nat-int?)

(s/def :mail/date (s/keys :req-un [:mail.date/year
                                   :mail.date/month
                                   :mail.date/day
                                   :mail.date/hours
                                   :mail.date/minutes
                                   :mail.date/seconds
                                   :mail.date/milliseconds]))

;; Money
;;

(s/def :mail.money/amount decimal?)
(s/def :mail.money/currency string?)
(s/def :mail/money (s/keys :req-un [:mail.money/amount
                                    :mail.money/currency]))

;; Extended data
;;

;; As context is parsed from JSON, any JSON object is fine
(s/def :mail/extended-data map?)

;; User
;;

(s/def :mail.user/id str-uuid?)
(s/def :mail.user/first-name string?)
(s/def :mail.user/last-name string?)
(s/def :mail.user/display-name string?)
(s/def :mail.user/private-data :mail/extended-data)
(s/def :mail.user/public-data :mail/extended-data)
(s/def :mail.user/protected-data :mail/extended-data)

(s/def :mail/user (s/keys :req-un [:mail.user/id
                                   :mail.user/first-name
                                   :mail.user/last-name
                                   :mail.user/display-name
                                   :mail.user/private-data
                                   :mail.user/public-data
                                   :mail.user/protected-data]))

(s/def :mail/transaction-role #{"provider" "customer"})
(s/def :mail/recipient :mail/user)
(s/def :mail/recipient-role :mail/transaction-role)
(s/def :mail/other-party :mail/user)

;; Availability plan
;;

(s/def :mail.availability-plan/type string?)
(s/def :mail.availability-plan/timezone string?)
(s/def :mail/availability-plan (s/keys :req-un [:mail.availability-plan/type]
                                       :opt-un [:mail.availability-plan/timezone]))

;; Listing
;;

(s/def :mail.listing/id str-uuid?)
(s/def :mail.listing/title string?)
(s/def :mail.listing/availability-plan :mail/availability-plan)
(s/def :mail.listing/private-data :mail/extended-data)
(s/def :mail.listing/public-data :mail/extended-data)
(s/def :mail.listing/metadata :mail/extended-data)
(s/def :mail/listing (s/keys :req-un [:mail.listing/id
                                      :mail.listing/title
                                      :mail.listing/private-data
                                      :mail.listing/public-data
                                      :mail.listing/metadata]
                             :opt-un [:mail.listing/availability-plan]))

;; Reviews
;;

(s/def :mail.review/content string?)
(s/def :mail.review/subject :mail/user)
(s/def :mail/review (s/keys :req-un [:mail.review/content
                                     :mail.review/subject]))

;; Delayed transition
;;

(s/def :mail.delayed-transition/run-at :mail/date)
(s/def :mail.transaction/delayed-transition
  (s/keys :req-un [:mail.delayed-transition/run-at]))

;; Booking
;;

(s/def :mail.booking/start :mail/date)
(s/def :mail.booking/end :mail/date)
(s/def :mail.booking/displayStart :mail/date)
(s/def :mail.booking/displayEnd :mail/date)
(s/def :mail/booking (s/keys :req-un [:mail.booking/start
                                      :mail.booking/end
                                      :mail.booking/displayStart
                                      :mail.booking/displayEnd]))

;; Line item
;;

(s/def :mail.tx-line-item.include-for/role :mail/transaction-role)

(s/def :mail.tx-line-item/code qualified-string?)
(s/def :mail.tx-line-item/quantity decimal?)
(s/def :mail.tx-line-item/percentage decimal?)
(s/def :mail.tx-line-item/units decimal?)
(s/def :mail.tx-line-item/seats int?)
(s/def :mail.tx-line-item/unit-price :mail/money)
(s/def :mail.tx-line-item/line-total :mail/money)
(s/def :mail.tx-line-item/include-for (s/coll-of :mail.tx-line-item.include-for/role :kind vector?))

(s/def :mail/tx-line-item
  (s/keys :req-un [:mail.tx-line-item/code
                   :mail.tx-line-item/unit-price
                   :mail.tx-line-item/line-total
                   :mail.tx-line-item/include-for]
          :opt-un [:mail.tx-line-item/quantity
                   :mail.tx-line-item/percentage
                   :mail.tx-line-item/units
                   :mail.tx-line-item/seats]))

;; Transaction
;;

(s/def :mail.transaction/id str-uuid?)
(s/def :mail.transaction/provider :mail/user)
(s/def :mail.transaction/customer :mail/user)
(s/def :mail.transaction/listing :mail/listing)
(s/def :mail.transaction/protected-data :mail/extended-data)
(s/def :mail.transaction/booking :mail/booking)
(s/def :mail.transaction/tx-line-items (s/coll-of :mail/tx-line-item :kind vector?))
(s/def :mail.transaction/reviews (s/coll-of :mail/review :kind vector?))
(s/def :mail.transaction/payout-total :mail/money)
(s/def :mail.transaction/payin-total :mail/money)


(s/def :mail/transaction (s/keys :req-un [:mail.transaction/id
                                          :mail.transaction/provider
                                          :mail.transaction/customer
                                          :mail.transaction/listing
                                          :mail.transaction/protected-data]
                                 :opt-un [:mail.transaction/booking
                                          :mail.transaction/tx-line-items
                                          :mail.transaction/delayed-transition
                                          :mail.transaction/reviews
                                          :mail.transaction/payout-total
                                          :mail.transaction/payin-total]))

;; Marketplace
;;

(s/def :mail.marketplace/name string?)
(s/def :mail.marketplace/url string?)
(s/def :mail/marketplace (s/keys :req-un [:mail.marketplace/name
                                          :mail.marketplace/url]))

;; Context
;;

(s/def :context/transaction-transition
  (s/keys :req-un [:mail/recipient
                   :mail/marketplace
                   :mail/recipient-role
                   :mail/other-party
                   :mail/transaction]))
