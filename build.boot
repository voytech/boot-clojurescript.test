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
         '[adzerk.boot-cljs :refer  :all]

)

(def +version+ "0.1.0-SNAPSHOT")

(bootlaces! +version+)

(deftask continous-testing []
   (set-env! :resource-paths #{"resources"}) ;;something apparently adds src to resrouce-paths - beat it.
   (comp (watch) (log-fileset) (cljs-tests))
)

(deftask testing2 []
   (set-env! :resource-paths #{"resources"}) ;;something apparently adds src to resrouce-paths - beat it.
   (comp
     (add-tests) ;; add test sources to fileset
     (make-edn)  ;; generate edn files and add to file set
     (cljs)      ;; clojurescript compile with test namespaces appended.
     )
)
(deftask build-local-install []
  (set-env! :resource-paths #{"resources" "src/main/clj"})
  (build-jar))

(task-options!
 pom {:project     'voytech/boot-clojurescript.test
      :version     +version+
      :description "Boot task to test ClojureScript namespaces using framework clojurescript.test (A maximal port of clojure.test)."
      }
 cljs {
      ;; :main "mock.sample-test"
      ;; :output-to "tutaruputa.js"
       ;;:asset-path "/"
       }
 make-edn     {:namespaces #{'mock.sample-test}})

(set-env! :resource-paths #{"resources"})
