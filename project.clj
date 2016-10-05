(defproject tupelo-datomic "0.9.1"
  :description "Tupelo Datomic:  Datomic With A Spoonful of Honey"
  :url "http://github.com/cloojure/tupelo-datomic"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
    [com.datomic/datomic-pro          "0.9.5350" :exclusions [joda-time]]
    [org.clojure/clojure              "1.9.0-alpha13"]
    [org.clojure/core.match           "0.3.0-alpha4"]
    [org.clojure/test.check           "0.5.9"]
    [prismatic/schema                 "1.0.4"]
    [tupelo                           "0.1.67"]
  ]
  :plugins  [ [codox "0.8.10"] ]
  :codox {:src-dir-uri "http://github.com/cloojure/tupelo-datomic/blob/master/"
          :src-linenum-anchor-prefix "L"}
  :deploy-repositories {  "snapshots" :clojars
                          "releases"  :clojars }
  :update :daily ;  :always 
  :main ^:skip-aot tupelo-datomic.core
  :target-path "target/%s"
  :clean-targets [ "target" ]
  :profiles { ; :dev      { :certificates ["clojars.pom"] }
              :uberjar  { :aot :all }
            }

  ; "lein test"         will not  run tests marked with the ":slow" metadata
  ; "lein test :slow"   will only run tests marked with the ":slow" metadata
  ; "lein test :all"    will run all  tests (built-in)
  :test-selectors { :default    (complement :slow)
                    :slow       :slow }

  :jvm-opts ["-Xms1g" "-Xmx4g" ]
; :jvm-opts ["-Xms4g" "-Xmx8g" "-server"]
)
