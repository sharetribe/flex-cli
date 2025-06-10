(ns sharetribe.flex-cli.spec-test
  (:require [cljs.test :as t :refer-macros [deftest is testing]]
            [clojure.spec.alpha :as s]
            [sharetribe.tempelhof.spec :as tempelhof.spec]))

(defn- validate [process]
  (let [problems (s/explain-data :tempelhof/tx-process process)]
    (::s/problems problems)))

(deftest process

  (testing "process is nil"
    (let [problems (validate nil)
          p (first problems)]
      (is (= 1 (count problems)))
      (is (= `map? (:pred p)))))

  (testing "process is an empty string"
    (let [problems (validate "")
          p (first problems)]
      (is (= 1 (count problems)))
      (is (= `map? (:pred p)))))

  (testing "invalid format"
    (let [problems (validate {:format :v2 :transitions []})
          p (first problems)]
      (is (= 1 (count problems)))
      (is (= [:tempelhof/tx-process :tx-process/format] (:via p)))
      (is (= [:format] (:in p)))))

  (testing "minimal valid process"
    (is (nil? (validate {:format :v3 :transitions []}))))

  (testing "valid with empty transitions and notifications"
    (is (nil? (validate {:format :v3 :transitions [] :notifications []})))))

(deftest transitions

  (testing "minimal valid transition with actor"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer}]}]
      (is (nil? (validate process)))))

  (testing "minimal valid transition with at"
    (let [process {:format :v3
                   :transitions [{:name :transition/init
                                  :to :state/init
                                  :actor :actor.role/customer}
                                 {:name :transition/request
                                  :from :state/init
                                  :to :state/preauthorized
                                  :at {:fn/timepoint [:time/booking-end]}}]}]
      (is (nil? (validate process)))))

  (testing "invalid transition with both actor and at"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer
                                  :at {:fn/timepoint [:time/booking-end]}}]}
          problems (validate process)
          p (first problems)]
      (is (= 1 (count problems)))
      (is (= `tempelhof.spec/transition-has-either-actor-or-at? (:pred p)))
      (is (= [:transitions 0] (:in p)))))

  (testing "transition names are not unique"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer}
                                 {:name :transition/request
                                  :from :state/enquiry
                                  :to :state/preauthorized
                                  :actor :actor.role/customer}]}
          problems (validate process)
          p (first problems)]
      (is (= 1 (count problems)))
      (is (= `tempelhof.spec/unique-transition-names? (:pred p)))
      (is (= [:transitions] (:in p)))))

  (testing "unreachable states because graph is disconnected"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer}
                                 {:name :transition/accept
                                  :from :state/pending
                                  :to :state/accepted
                                  :actor :actor.role/provider}]}
          problems (validate process)
          p (first problems)]

      (is (= 1 (count problems)))
      (is (= `tempelhof.spec/all-states-reachable? (:pred p)))
      (is (= [:transitions] (:in p)))))

  (testing "unreachable states in connected graph because of directions"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer}
                                 {:name :transition/accept
                                  :from :state/preauthorized
                                  :to :state/accepted
                                  :actor :actor.role/provider}
                                 {:name :transition/accept-pending
                                  :from :state/pending
                                  :to :state/accepted
                                  :actor :actor.role/provider}]}
          problems (validate process)
          p (first problems)]

      (is (= 1 (count problems)))
      (is (= `tempelhof.spec/all-states-reachable? (:pred p)))
      (is (= [:transitions] (:in p)))))

  (testing "initial transition actor"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer}
                                 {:name :transition/offer
                                  :to :state/offer
                                  :actor :actor.role/operator}]}
          problems (validate process)
          p (first problems)]

      (is (= 1 (count problems)))
      (is (= `tempelhof.spec/valid-initial-transition-actor? (:pred p)))))

  (testing "trusted context required"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer
                                  :actions [{:name :action/accept-booking}
                                            {:name :action/privileged-set-line-items}
                                            {:name :action/decline-booking}
                                            {:name :action/cancel-booking}]}]}
          problems (validate process)
          p (first problems)]
      (is (= 1 (count problems)))
      (is (= `tempelhof.spec/transition-with-trusted-context-if-privileged-actions? (:pred p)))
      (is (= [:transitions 0] (:in p)))))

  (testing "privileged transition is trusted context"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer
                                  :privileged? true
                                  :actions [{:name :action/accept-booking}
                                            {:name :action/privileged-set-line-items}
                                            {:name :action/decline-booking}
                                            {:name :action/cancel-booking}]}]}]
      (is (nil? (validate process)))))

  (testing "operator transition is trusted context"
    (let [process {:format :v3
                   :transitions [{:name :transition/init
                                  :actor :actor.role/customer
                                  :to :state/init}
                                 {:name :transition/request
                                  :from :state/init
                                  :to :state/preauthorized
                                  :actor :actor.role/operator
                                  :actions [{:name :action/accept-booking}
                                            {:name :action/privileged-set-line-items}
                                            {:name :action/decline-booking}
                                            {:name :action/cancel-booking}]}]}]
      (is (nil? (validate process)))))
  )

(deftest actions

  (testing "empty actions"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer
                                  :actions []}]}]
      (is (nil? (validate process)))))

  (testing "valid known actions"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer
                                  :actions [{:name :action/accept-booking}
                                            {:name :action/decline-booking}
                                            {:name :action/cancel-booking}]}]}]
      (is (nil? (validate process)))))

  (testing "unknown actions"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer
                                  :actions [{:name :action/accept-booking}
                                            {:name :action/decline-booking}
                                            {:name :action/invalid-action}
                                            {:name :action/cancel-booking}]}]}
          problems (validate process)
          p (first problems)]
      (is (= 1 (count problems)))
      (is (= :tx-process.action/name (-> p :via last)))
      (is (= [:transitions 0 :actions 2 :name] (:in p)))))

  )

(deftest notifications

  (testing "empty notifications"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer}]
                   :notifications []}]
      (is (nil? (validate process)))))

  (testing "minimal notification"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer}]
                   :notifications [{:name :notification/new-booking-request
                                    :on :transition/request
                                    :to :actor.role/provider
                                    :template :new-booking-request}]}]
      (is (nil? (validate process)))))

  (testing "minimal delayed notification"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer}]
                   :notifications [{:name :notification/new-booking-request
                                    :on :transition/request
                                    :to :actor.role/provider
                                    :template :new-booking-request
                                    :at {:fn/plus
                                         [{:fn/timepoint
                                           [:time/first-entered-state :state/preauthorized]}
                                          {:fn/period ["P1D"]}]}}]}]
      (is (nil? (validate process)))))

  (testing "notification names are not unique"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer}]
                   :notifications [{:name :notification/new-booking-request
                                    :on :transition/request
                                    :to :actor.role/provider
                                    :template :new-booking-request}
                                   {:name :notification/new-booking-request
                                    :on :transition/request
                                    :to :actor.role/customer
                                    :template :new-booking-request}]}
          problems (validate process)
          p (first problems)]
      (is (= 1 (count problems)))
      (is (= `tempelhof.spec/unique-notification-names? (:pred p)))
      (is (= [:notifications] (:in p)))))

  (testing "notification refers to unknown transition"
    (let [process {:format :v3
                   :transitions [{:name :transition/request
                                  :to :state/preauthorized
                                  :actor :actor.role/customer}]
                   :notifications [{:name :notification/new-booking-request
                                    :on :transition/unknown-transition
                                    :to :actor.role/provider
                                    :template :new-booking-request}]}
          problems (validate process)
          p (first problems)]
      (is (= 1 (count problems)))
      (is (= [] (:in p)))))

  )

(deftest timepoints
  (testing "invalid timepoint definition"
    (let [process {:format :v3
                   :transitions [{:name :transition/init
                                  :to :state/init
                                  :actor :actor.role/customer}
                                 {:name :transition/request
                                  :from :state/init
                                  :to :state/preauthorized
                                  :at {:fn/timepoint [:time/unknown-timepoint]}}]}
          problems (validate process)
          p (first problems)]
      (is (= [:transitions :at] (:path p)))
      (is (= `tempelhof.spec/valid-time-expression? (:pred p))))))
