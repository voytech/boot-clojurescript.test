 (set-env!
  :source-paths #{ "src/main/clj" "src/main/cljs" }
  :resource-paths #{ "resources" }
  :test-paths     #{ "src/tests/cljs" } ;; if we do not test then test are not compiled
  :dependencies   '[
                    [org.clojure/clojure               "1.6.0" :scope "provided"]
                    [boot/core                         "2.0.0-rc12" :scope "provided"]
                    [adzerk/boot-cljs                  "0.0-2760-0" :scope "compile"]
                    [pandeiro/boot-http                "0.6.2"      :scope "compile"]
                    [adzerk/bootlaces                  "0.1.10"     :scope "test"]
                    [adzerk/boot-test "1.0.4"]
                    [com.cemerick/clojurescript.test "0.3.3"]])

(require '[adzerk.bootlaces :refer :all]
         '[boot-clojurescript.test.tasks :refer :all]
         '[boot.core :refer :all]
         '[clojure.walk :refer :all]
         '[adzerk.boot-cljs :refer  :all]
)

(def +version+ "0.1.0-SNAPSHOT")

(bootlaces! +version+)

(declare generate-edn)

(deftask fileset-log []
  (with-pre-wrap fileset
    (println "current environment:")
    (println "----------------------------------------")
    (println (get-env))
    (println "----------------------------------------")
    (println "fileset :")
    (println "-----------------------------------------")
    (postwalk #(when (map? %) (when (contains? % :path) (println (:path %))))
              fileset)
     fileset
))

(deftask test-runner-wrapper []
  (set-env! :resource-paths #{"resources"})
  (test-runner))

(deftask add-tests-to-fileset []
  (set-env! :resource-paths #{"resources"}) ;;something apparently adds src to resrouce-paths - beat it.
  (comp (fileset-log) (fileset-add-tests) (fileset-log)))

(deftask end-2-end[]
   (set-env! :resource-paths #{"resources"}) ;;something apparently adds src to resrouce-paths - beat it.
   (comp (fileset-log)
         (fileset-add-tests)
         (fileset-log)
         (gen-test-edn)
         (fileset-log)
         (cljs)  ;;ehh compile try it well.
         (fileset-log)
         (test-runner)
         ))

(task-options!
 pom {:project     'voytech/boot-cemerick-clojurescript-test
      :version     +version+
      :description "Boot task to test ClojureScript namespaces using cemerick's clojurescript.test port."
      }
 test-runner  {:slimer-version "0.9.5"}
 gen-test-edn {:namespaces #{'mock.sample-test}})
