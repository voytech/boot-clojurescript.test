(ns mock.sample-test
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing test-var)])
  (:require [cemerick.cljs.test :as t]
            [mock.sample :as  sample]))

(deftest tester-test
  (is (= true true)))

(deftest get-element-by-id-fake
  (is (= "aaa" (sample/get-element-by-id))))
