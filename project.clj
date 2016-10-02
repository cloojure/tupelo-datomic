(defproject tupelo-datomic "0.1.68"
  :description "Tupelo Datomic:  Making Datomic Even Sweeter"
  :url "http://github.com/cloojure/tupelo-datomic"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [ [tupelo                           "0.1.67"]
                  [org.clojure/clojure              "1.7.0"]
                  [org.clojure/core.incubator       "0.1.3"]
                  [org.clojure/test.check           "0.5.9"]
                  [org.clojure/core.match           "0.3.0-alpha4"]
                  [clj-time                         "0.7.0"]
                  [criterium                        "0.4.3"]
                  [cheshire                         "5.5.0"]
                  [prismatic/schema                 "1.0.4"]
                  [com.datomic/datomic-pro          "0.9.5350" :exclusions [joda-time]]
                ]
  :plugins  [ [codox "0.8.10"] ]
  :codox {:src-dir-uri "http://github.com/cloojure/tupelo-datomic/blob/master/"
          :src-linenum-anchor-prefix "L"}
  :deploy-repositories {  "snapshots" :clojars
                          "releases"  :clojars }
  :update :always; :daily  
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
