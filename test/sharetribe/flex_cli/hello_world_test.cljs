(ns sharetribe.flex-cli.hello-world-test
  (:require [cljs.test :refer-macros [deftest is]]))

(deftest passing
  (is (= "hello!" "hello!")))
