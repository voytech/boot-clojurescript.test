 (set-env!
 :source-paths   #{"src"}
 :dependencies   '[
                   [org.clojure/clojure               "1.6.0" :scope "provided"]
                   [boot/core                         "2.0.0-rc12" :scope "provided"]
                   [adzerk/boot-cljs                  "0.0-2760-0" :scope "compile"]
                   [pandeiro/boot-http                "0.6.2"      :scope "compile"]
                   [adzerk/bootlaces                  "0.1.10"     :scope "test"]
                   [adzerk/boot-test "1.0.4"]
                   [com.cemerick/clojurescript.test "0.3.3"]])

(require '[adzerk.bootlaces :refer :all])
(require '[adzerk.boot-test :refer :all])

(def +version+ "0.1.0-SNAPSHOT")

(bootlaces! +version+)

(task-options!
 pom {:project     'voytech/boot-cemerick-clojurescript-test
      :version     +version+
      :description "Boot task to test ClojureScript namespaces using cemerick's clojurescript.test port."
      })
