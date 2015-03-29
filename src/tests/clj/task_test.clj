(ns task-test
  (:require [boot.core :as core]
            [clojure.test :refer :all]
            [boot-clojurescript.test.tasks :refer :all]))

(defn- mock-fileset []

)
(defn- mock-environment []
  (core/set-env!
   :source-paths   #{ "src/tests/mock_env/main/cljs" }
   :test-paths     #{ "src/tests/mock_env/test/cljs" }
   :dependencies   '[
                     [org.clojure/clojure               "1.6.0" :scope "provided"]
                     [boot/core                         "2.0.0-rc12" :scope "provided"]
                     [adzerk/boot-cljs                  "0.0-2760-0" :scope "compile"]
                     [pandeiro/boot-http                "0.6.2"      :scope "compile"]
                     [adzerk/bootlaces                  "0.1.10"     :scope "test"]
                     [adzerk/boot-test "1.0.4"]
                     [com.cemerick/clojurescript.test "0.3.3"]])


  (println (core/get-env)))


(deftest fileset-add-tests-test []
  (mock-environment)
  (core/init!) ;; offending function.
  (println (core/get-env))
  (is (= 1 1))
  ;; (((fileset-add-tests)) mock-fileset)
)
