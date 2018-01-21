(ns herb.macro
  #?(:clj
     (:require
       [garden.core :refer [css]]
       [clojure.string :as str]
       [garden.stylesheet :refer [at-media at-keyframes]]))
  #?(:cljs
     (:require
       [garden.core :refer [css]]
       [clojure.string :as str]
       [garden.stylesheet :refer [at-media at-keyframes]]
       [herb.runtime])))

(defn cljs-env?
  "Take the &env from a macro, and tell whether we are expanding into cljs."
  [env]
  (boolean (:ns env)))

(defmacro with-style
  [style]
  (let [css (symbol "garden.core" "css")
        ns* (str/replace (name (ns-name *ns*)) #"\." "-")
        classname (str ns* "-" style)
        inject-style-fn (symbol "herb.runtime" "inject-style!")]
    `(do
       (~inject-style-fn ~classname (css [(str "." ~classname) ~style]))
       ~classname)
    ;; `(do (.log js/console ~classname)
    ;;      "asd")

    )
  )
