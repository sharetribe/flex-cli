(ns sharetribe.flex-cli.commands.assets-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [sharetribe.flex-cli.commands.assets :as assets]))

(deftest filter-assets-to-upload-tests
  (testing "unchanged assets are skipped"
    (is (empty?
         (assets/filter-assets-to-upload
          [{:path "emails/foo" :content-hash "abc"}]
          [{:path "emails/foo" :content-hash "abc"}]))))

  (testing "assets without stored metadata default to changed"
    (is (= [{:path "emails/foo" :content-hash "abc"}]
           (vec (assets/filter-assets-to-upload
                 nil
                 [{:path "emails/foo" :content-hash "abc"}])))))

  (testing "assets missing stored hash are re-uploaded"
    (is (= [{:path "emails/foo" :content-hash "abc"}]
           (vec (assets/filter-assets-to-upload
                 [{:path "emails/foo"}]
                 [{:path "emails/foo" :content-hash "abc"}])))))

  (testing "hash mismatches trigger upload"
    (is (= [{:path "emails/foo" :content-hash "def"}]
           (vec (assets/filter-assets-to-upload
                 [{:path "emails/foo" :content-hash "abc"}]
                 [{:path "emails/foo" :content-hash "def"}]))))))
