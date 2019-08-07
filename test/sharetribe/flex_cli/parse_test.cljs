(ns sharetribe.flex-cli.parse-test
  (:require [sharetribe.flex-cli.args-parse :as parse]
            [cljs.test :as t :refer-macros [deftest is testing]]))

(deftest main
  (let [cmd {:handler ::main-handler
             :opts [{:id :version
                     :short-opt "-V"
                     :long-opt "-v"}]}
        parse-result (parse/parse ["-V"] cmd)]
    (is (= ::main-handler (:handler parse-result)))
    (is (= {:version true} (:options parse-result)))))

(deftest subcommands
  (let [cmd {:sub-cmds [{:name "process"
                         :handler ::process
                         :sub-cmds [{:name "list"
                                     :handler ::process-list}]}]}]
    (is (= ::process (:handler (parse/parse ["process"] cmd))))
    (is (= ::process-list (:handler (parse/parse ["process" "list"] cmd))))))

(deftest subcommands+opts
  (let [cmd {:sub-cmds [{:name "process"
                         :sub-cmds [{:name "list"
                                     :handler ::process-list
                                     :opts [{:id :process-name
                                             :long-opt "--process"
                                             :required "PROCESS NAME"}]}]}]}
        parse-result (parse/parse ["process" "list" "--process=nightly-booking"] cmd)]
    (is (= ::process-list (:handler parse-result)))
    (is (= {:process-name "nightly-booking"} (:options parse-result)))))

(deftest params-coercion+validation
  (let [cmd {:opts [{:id :number
                     :short-opt "-n"
                     :long-opt "--number"
                     :parse-fn #(js/parseInt %)
                     :validate-fn #(< 0 % 1000)
                     :validate-msg "Must be a number between 0 and 1000"
                     :required "NUMBER"
                     :missing "Missing number"}]}]
    (testing "missing"
      (let [e (try
                (parse/parse [""] cmd)
                (catch js/Error e e))]
        (is (= (:type (ex-data e)) :command/parse-error))
        (is (contains? (-> (ex-data e) :data :errors set) "Missing number"))))

    (testing "not a number"
      (let [e (try
                (parse/parse ["--number=abc"] cmd)
                (catch js/Error e e))]
        (is (= (:type (ex-data e)) :command/parse-error))
        (is (every? (-> (ex-data e) :data :errors set)
                    ["Failed to validate \"--number abc\": Must be a number between 0 and 1000" "Missing number"]))))

    (testing "too big number"
      (let [e (try
                (parse/parse ["--number=12345"] cmd)
                (catch js/Error e e))]
        (is (= (:type (ex-data e)) :command/parse-error))
        (is (every? (-> (ex-data e) :data :errors set)
                    ["Failed to validate \"--number 12345\": Must be a number between 0 and 1000" "Missing number"]))))

    (testing "success"
      (let [parse-result (parse/parse ["--number=123"] cmd)]
        (is (= nil (:error parse-result)))
        (is (= nil (-> parse-result :data :errors)))))))

(deftest strict-mode
  (let [cmd {:sub-cmds [{:name "process"
                         :sub-cmds [{:name "list"
                                     :handler ::process-list
                                     :opts [{:id :marketplace
                                             :long-opt "--marketplace"
                                             :short-opt "-m"
                                             :required "MARKETPLACE"}
                                            {:id :process-name
                                             :long-opt "--process"
                                             :short-opt "-p"
                                             :required "PROCESS NAME"}]}]}]}
        e (try
            (parse/parse ["process" "list" "-m" "-p" "nightly-booking"] cmd)
            (catch js/Error e e))]
    (is (= (:type (ex-data e)) :command/parse-error))
    (is (contains? (-> (ex-data e) :data :errors set) "Missing required argument for \"-m MARKETPLACE\""))))

(deftest catch-all
  (let [cmd {:sub-cmds [{:name "help"
                         :handler ::help
                         :catch-all? true}
                        {:name "process"
                         :sub-cmds [{:name "list"}]}]}]
    (is (= ::help (:handler (parse/parse ["help" "process" "list"] cmd))))
    (is (thrown-with-msg? js/Error #":command/not-found"
                          (parse/parse ["process" "this-command-does-not-exist"] cmd)))))
